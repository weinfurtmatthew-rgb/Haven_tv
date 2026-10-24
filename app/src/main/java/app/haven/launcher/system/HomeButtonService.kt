package app.haven.launcher.system

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import app.haven.launcher.Haven
import app.haven.launcher.MainActivity
import app.haven.launcher.bedroom.Bedroom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Makes Haven the home screen on boxes that don't let you change the default launcher:
 * - the remote's Home button opens Haven (press it twice quickly for the original home screen);
 * - if the stock home screen opens anyway (after boot, or when an app closes), Haven is brought back.
 * It also lets the sleep timer put the box to sleep. It reads no screen content.
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
        if (pkg !in stockHomes) {
            stockAllowed = false
            return
        }
        if (!Haven.settings.value.homeOverride || stockAllowed || recentlyCrashed()) return
        openHaven()
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
            val homes = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_ALL)
                .filter { it.activityInfo.packageName != ctx.packageName && !it.activityInfo.packageName.contains("settings", true) }
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
