package app.haven.launcher.system

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import app.haven.launcher.Haven
import app.haven.launcher.MainActivity
import app.haven.launcher.bedroom.Bedroom
import app.haven.launcher.data.AppKind
import app.haven.launcher.media.TvPlayback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Makes Haven the home screen on boxes that don't let you change the default launcher (the DIRECTV box, Google TV,
 * Fire TV):
 * - the remote's Home button opens Haven (press it twice quickly for the original home screen);
 * - after boot, Haven opens once the service starts;
 * - if Google's or Amazon's own home screen opens anyway (for example when an app closes), Haven is brought back.
 *
 * TV-provider home apps are never redirected: on the DIRECTV STREAM box the "launcher" is the DIRECTV app
 * itself (live TV, guide, DVR), so Haven only steps in when Home is pressed.
 * It also lets the sleep timer put the box to sleep. It reads no screen content, only which app and screen
 * opened (kept in memory for the troubleshooting list in Settings).
 */
class HomeButtonService : AccessibilityService() {
    private var lastHomeUp = 0L
    private var homeDown = false
    private var stockHomes: Set<String> = emptySet()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _running.value = true
        stockHomes = stockHomePackages(this)
        // Just booted: the stock home screen is up before we were connected, so open Haven now.
        if (Haven.settings.value.homeOverride && SystemClock.elapsedRealtime() < 4 * 60_000L && !recentlyCrashed()) {
            openHaven()
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME || !Haven.settings.value.homeOverride || recentlyCrashed()) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> homeDown = true
            KeyEvent.ACTION_UP -> {
                if (!homeDown) return false
                homeDown = false
                val now = SystemClock.uptimeMillis()
                if (now - lastHomeUp < DOUBLE_PRESS_MS) {
                    lastHomeUp = 0L
                    openStockHome(this)
                } else {
                    lastHomeUp = now
                    openHaven()
                }
            }
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        remember(pkg, event.className?.toString())
        if (Haven.apps.apps.value.any { it.pkg == pkg && AppKind.Music in it.kinds }) TvPlayback.lastMusicApp = pkg
        if (pkg !in stockHomes) {
            stockAllowed = false
            return
        }
        // Only Google's and Amazon's plain home screens are replaced automatically; a provider's home app is also its TV app.
        if (pkg !in GENERIC_LAUNCHERS) return
        if (!Haven.settings.value.homeOverride || stockAllowed || recentlyCrashed()) return
        openHaven()
    }

    private fun remember(pkg: String, cls: String?) {
        val entry = if (cls.isNullOrBlank()) pkg else "$pkg / ${cls.removePrefix(pkg)}"
        if (_recentScreens.value.firstOrNull() == entry) return
        _recentScreens.value = (listOf(entry) + _recentScreens.value).take(12)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent): Boolean {
        instance = null
        _running.value = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        _running.value = false
        super.onDestroy()
    }

    private fun openHaven() {
        stockAllowed = false
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
            )
        }
    }

    private fun recentlyCrashed() = System.currentTimeMillis() - Haven.lastCrashAt(this) < 60_000L

    companion object {
        private const val DOUBLE_PRESS_MS = 450L

        @Volatile
        private var instance: HomeButtonService? = null

        /** True after the user asked for the original home screen, until they leave it. */
        @Volatile
        private var stockAllowed = false

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running

        private val _recentScreens = MutableStateFlow<List<String>>(emptyList())

        /** The last few app screens that opened (package / activity), newest first, for troubleshooting. */
        val recentScreens: StateFlow<List<String>> = _recentScreens

        /** The stock Android TV, Google TV and Fire TV home screens: plain launchers that are safe to replace. */
        val GENERIC_LAUNCHERS = setOf(
            "com.google.android.tvlauncher",
            "com.google.android.apps.tv.launcherx",
            "com.google.android.leanbacklauncher",
            "com.android.launcher",
            "com.android.launcher3",
            "com.amazon.tv.launcher",
        )

        /** Puts the box to sleep (and the TV, through HDMI-CEC). Needs the service to be on and Android 9+. */
        fun sleepDevice(): Boolean {
            val s = instance ?: return false
            if (Build.VERSION.SDK_INT < 28) return false
            return s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        }

        /** Every installed home screen except Haven; the stock one is usually the only system app here. */
        fun stockHomePackages(ctx: Context): Set<String> {
            val pm = ctx.packageManager
            val homes = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_ALL)
            return homes.mapNotNull { it.activityInfo?.packageName }
                .filter { it != ctx.packageName && !it.contains("settings", ignoreCase = true) }
                .toSet()
        }

        fun openStockHome(ctx: Context) {
            val pm = ctx.packageManager
            // The box's own home screen first: a system app, and a known stock launcher before any other.
            val homes = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_ALL)
                .filter { it.activityInfo.packageName != ctx.packageName && !it.activityInfo.packageName.contains("settings", true) }
                .sortedWith(
                    compareByDescending<android.content.pm.ResolveInfo> { (it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 }
                        .thenByDescending { it.activityInfo.packageName.contains("directv", true) }
                        .thenByDescending { it.activityInfo.packageName in GENERIC_LAUNCHERS },
                )
            val target = homes.firstOrNull() ?: run {
                Toast.makeText(ctx, "No other home screen found", Toast.LENGTH_SHORT).show()
                return
            }
            stockAllowed = true
            runCatching {
                ctx.startActivity(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .setClassName(target.activityInfo.packageName, target.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}

/** Alarms are cleared by a reboot or an update; put them back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Bedroom.restore(ctx.applicationContext)
    }
}
