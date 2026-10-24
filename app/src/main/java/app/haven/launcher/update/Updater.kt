package app.haven.launcher.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.core.content.FileProvider
import app.haven.launcher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Checks the GitHub repo's latest release (built by CI as v1.0.<build>) and installs it.
 * The build number doubles as the Android versionCode, so newer always means larger.
 */
class Updater(private val context: Context) {
    data class Release(val versionCode: Int, val name: String, val apkUrl: String)

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Downloading(val version: String, val percent: Int) : State
        data class Installing(val version: String) : State
        data class Failed(val message: String) : State
    }

    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val prefs = context.getSharedPreferences("haven_update", Context.MODE_PRIVATE)

    private val _available = MutableStateFlow<Release?>(null)
    val available: StateFlow<Release?> = _available
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    suspend fun check(): Release? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        val release = http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("GitHub answered ${r.code}")
            val o = JSONObject(r.body!!.string())
            val tag = o.optString("tag_name")
            val code = tag.substringAfterLast('.').toIntOrNull() ?: error("Unexpected release tag $tag")
            val assets = o.optJSONArray("assets")
            val apk = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }
                ?.optString("browser_download_url") ?: error("Release $tag has no APK")
            Release(code, tag.removePrefix("v"), apk)
        }
        prefs.edit().putLong("checkedAt", System.currentTimeMillis()).apply()
        val newer = release.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
        _available.value = newer
        newer
    }

    /** Quietly looks for an update at most once a day; the home screen shows a chip when one exists. */
    suspend fun checkDaily() {
        if (System.currentTimeMillis() - prefs.getLong("checkedAt", 0L) < 20 * 3600_000L) return
        runCatching { check() }
    }

    suspend fun checkAndInstall() {
        if (_state.value is State.Checking || _state.value is State.Downloading) return
        _state.value = State.Checking
        val release = try {
            check()
        } catch (e: Exception) {
            _state.value = State.Failed("Couldn't check for updates: ${e.message}")
            return
        }
        if (release == null) {
            _state.value = State.UpToDate
            return
        }
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.value = State.Failed("Allow Haven to install apps (opening that setting now), then select Updates again")
            runCatching {
                context.startActivity(
                    Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return
        }
        val file = try {
            download(release)
        } catch (e: Exception) {
            _state.value = State.Failed("Download failed: ${e.message}")
            return
        }
        _state.value = State.Installing(release.name)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { _state.value = State.Failed("Couldn't open the installer: ${it.message}") }
    }

    private suspend fun download(r: Release): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, "haven.apk")
        http.newCall(Request.Builder().url(r.apkUrl).build()).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val body = resp.body!!
            val total = body.contentLength()
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) _state.value = State.Downloading(r.name, (done * 100 / total).toInt())
                    }
                }
            }
        }
        out
    }
}
