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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

data class TvApp(
    val pkg: String,
    val label: String,
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
    val launchIntent: Intent,
)

/** Installed apps a TV launcher should show: leanback apps first, then phone-style launcher apps. */
class AppsRepository(private val context: Context, private val scope: CoroutineScope) {
    private val pm: PackageManager = context.packageManager
    private val prefs = context.getSharedPreferences("haven_apps", Context.MODE_PRIVATE)

    private val _apps = MutableStateFlow<List<TvApp>>(emptyList())
    val apps: StateFlow<List<TvApp>> = _apps

    private val _lastUsed = MutableStateFlow(loadLastUsed())
    val lastUsed: StateFlow<Map<String, Long>> = _lastUsed

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
                val icon = if (banner == null) ri.loadIcon(pm)?.toImage(160, 160) else null
                out += TvApp(ai.packageName, ri.loadLabel(pm).toString().trim(), banner, icon, intent)
            }
        }
        add(query(Intent.CATEGORY_LEANBACK_LAUNCHER), Intent.CATEGORY_LEANBACK_LAUNCHER)
        add(query(Intent.CATEGORY_LAUNCHER), Intent.CATEGORY_LAUNCHER)
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

    fun launch(app: TvApp) {
        try {
            context.startActivity(app.launchIntent)
            markUsed(app.pkg)
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
        val next = _lastUsed.value + (pkg to System.currentTimeMillis())
        _lastUsed.value = next
        prefs.edit().putString("lastUsed", JSONObject(next).toString()).apply()
    }

    private fun loadLastUsed(): Map<String, Long> = runCatching {
        val o = JSONObject(prefs.getString("lastUsed", "{}")!!)
        o.keys().asSequence().associateWith { o.optLong(it) }
    }.getOrDefault(emptyMap())
}
