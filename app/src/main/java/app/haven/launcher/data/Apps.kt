package app.haven.launcher.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import app.haven.launcher.media.TvPlayback
import app.haven.launcher.system.HomeButtonService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

data class TvApp(
    val pkg: String,
    val label: String,
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
    val launchIntent: Intent,
    /** What kind of app it is, for the movie, sports, music and kids rows ([AppKinds]). */
    val kinds: Set<AppKind> = emptySet(),
)

/**
 * Installed apps a TV launcher should show: leanback apps first, then phone-style launcher apps. [profile] says
 * whose "recently used" list a launch counts towards.
 */
class AppsRepository(private val context: Context, private val scope: CoroutineScope, private val profile: () -> String) {
    private val pm: PackageManager = context.packageManager
    private val prefs = context.getSharedPreferences("haven_apps", Context.MODE_PRIVATE)

    private val _apps = MutableStateFlow<List<TvApp>>(emptyList())
    val apps: StateFlow<List<TvApp>> = _apps

    private val _lastUsed = MutableStateFlow(loadLastUsed())

    /** Profile id to (package to last launch time). */
    val lastUsed: StateFlow<Map<String, Map<String, Long>>> = _lastUsed

    /** Pictures people chose for apps (from the phone page). */
    val imagesDir = File(context.filesDir, "appimages").apply { mkdirs() }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = refresh()
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        refresh()
    }

    fun refresh() {
        scope.launch(Dispatchers.IO) {
            val loaded = runCatching { load() }.getOrNull() ?: return@launch
            _apps.value = loaded
        }
    }

    private fun load(): List<TvApp> {
        val seen = HashSet<String>()
        val out = ArrayList<TvApp>()
        fun add(list: List<ResolveInfo>, category: String) {
            for (ri in list) {
                val ai = ri.activityInfo ?: continue
                if (ai.packageName == context.packageName || !seen.add(ai.packageName)) continue
                val intent = Intent(Intent.ACTION_MAIN)
                    .addCategory(category)
                    .setClassName(ai.packageName, ai.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                val banner = (ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm))?.toImage(400, 225)
                // Every app gets its icon too: a profile may prefer icons on tiles to banners.
                val icon = ri.loadIcon(pm)?.toImage(160, 160)
                val label = ri.loadLabel(pm).toString().trim()
                val kinds = AppKinds.of(ai.packageName, label, ai.applicationInfo?.category ?: -1) +
                    if (category == Intent.CATEGORY_HOME) setOf(AppKind.Tv) else emptySet()
                out += TvApp(ai.packageName, label, banner, icon, intent, kinds)
            }
        }
        add(query(Intent.CATEGORY_LEANBACK_LAUNCHER), Intent.CATEGORY_LEANBACK_LAUNCHER)
        add(query(Intent.CATEGORY_LAUNCHER), Intent.CATEGORY_LAUNCHER)
        // A TV provider's home app (the DIRECTV app on its own box) may only register as a home screen;
        // list it like any other app so live TV is one click away. Google's plain home screens are skipped.
        add(
            query(Intent.CATEGORY_HOME).filter { ri ->
                val p = ri.activityInfo?.packageName ?: return@filter false
                p !in HomeButtonService.GENERIC_LAUNCHERS && !p.contains("settings", ignoreCase = true)
            },
            Intent.CATEGORY_HOME,
        )
        return out.sortedBy { it.label.lowercase() }
    }

    private fun query(category: String): List<ResolveInfo> =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)

    private fun Drawable.toImage(maxW: Int, maxH: Int): ImageBitmap? = runCatching {
        val w = intrinsicWidth.takeIf { it > 0 } ?: maxW
        val h = intrinsicHeight.takeIf { it > 0 } ?: maxH
        val s = minOf(1f, maxW.toFloat() / w, maxH.toFloat() / h)
        toBitmap((w * s).toInt().coerceAtLeast(1), (h * s).toInt().coerceAtLeast(1)).asImageBitmap()
    }.getOrNull()

    /** The TV provider's app (DIRECTV on its own box): live TV. Its home-screen entry wins over a plain one. */
    fun provider(): TvApp? = _apps.value
        .filter { it.pkg.contains("directv", true) || it.label.contains("directv", true) }
        .sortedByDescending { it.launchIntent.hasCategory(Intent.CATEGORY_HOME) }
        .firstOrNull()

    fun launch(app: TvApp) {
        try {
            context.startActivity(app.launchIntent)
            markUsed(app.pkg)
            if (AppKind.Music in app.kinds) TvPlayback.lastMusicApp = app.pkg
        } catch (e: Exception) {
            Toast.makeText(context, "Couldn't open ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openAppInfo(pkg: String) {
        runCatching {
            context.startActivity(
                Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun markUsed(pkg: String) {
        val id = profile()
        val all = _lastUsed.value
        val mine = (all[id].orEmpty() + (pkg to System.currentTimeMillis())).entries
            .sortedByDescending { it.value }.take(40).associate { it.key to it.value }
        val next = all + (id to mine)
        _lastUsed.value = next
        val o = JSONObject()
        for ((p, m) in next) o.put(p, JSONObject(m))
        prefs.edit().putString("lastUsedByProfile", o.toString()).apply()
    }

    private fun loadLastUsed(): Map<String, Map<String, Long>> = runCatching {
        fun map(o: JSONObject): Map<String, Long> = o.keys().asSequence().associateWith { o.optLong(it) }
        val saved = prefs.getString("lastUsedByProfile", null)
        if (saved != null) {
            val o = JSONObject(saved)
            o.keys().asSequence().associateWith { k -> o.optJSONObject(k)?.let(::map).orEmpty() }
        } else {
            // From before profiles: the history belongs to the first profile.
            mapOf(ProfilesMeta.FIRST to map(JSONObject(prefs.getString("lastUsed", "{}")!!)))
        }
    }.getOrDefault(emptyMap())
}
