package app.haven.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.haven.launcher.system.CrashActivity
import app.haven.launcher.system.CrashReport
import app.haven.launcher.ui.AlarmScreen
import app.haven.launcher.ui.ArtScreen
import app.haven.launcher.ui.BedtimeScreen
import app.haven.launcher.ui.BgPreview
import app.haven.launcher.ui.BriefingScreen
import app.haven.launcher.ui.GrownUp
import app.haven.launcher.ui.PinDialog
import app.haven.launcher.ui.C
import app.haven.launcher.ui.DesignCanvas
import app.haven.launcher.ui.HavenBackground
import app.haven.launcher.ui.HomeScreen
import app.haven.launcher.ui.LightsScreen
import app.haven.launcher.ui.Look
import app.haven.launcher.ui.ProfilesScreen
import app.haven.launcher.ui.SettingsScreen
import app.haven.launcher.ui.SleepScreen
import app.haven.launcher.ui.SportsScreen
import app.haven.launcher.ui.rememberKeepVisible

enum class Screen { Home, Lights, Art, Settings, Alarm, Sleep, Briefing, Sports, Profiles, Bedtime }

/** Which full screen is showing. [homeResets] bumps whenever Home is pressed, so the home screen can scroll back. */
class Nav {
    var screen by mutableStateOf(Screen.Home)
    var homeResets by mutableIntStateOf(0)
        private set

    /** The Settings group to open on (for "Manage profiles"), used once. */
    var settingsStart: String? = null

    fun go(s: Screen) {
        screen = s
    }

    fun home() {
        screen = Screen.Home
        homeResets++
    }
}

class MainActivity : ComponentActivity() {
    private val nav = Nav()

    /**
     * The TV woke up: ask who's watching, if the household wants that. If another app is on screen, the question
     * waits for the next time Home brings Haven up.
     */
    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (!askWhoIsWatching()) return
            if (resumed) nav.go(Screen.Profiles) else askPending = true
        }
    }
    private var listening = false
    private var resumed = false
    private var askPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Haven crashed last time: show the report instead of closing straight back to the stock home screen.
        if (CrashReport.read(this) != null) {
            CrashActivity.open(this)
            finish()
            return
        }
        setContent { HavenRoot(nav) }
        if (!openFromIntent(intent) && askWhoIsWatching()) nav.go(Screen.Profiles)
        ContextCompat.registerReceiver(this, screenOn, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED)
        listening = true
    }

    override fun onDestroy() {
        if (listening) runCatching { unregisterReceiver(screenOn) }
        super.onDestroy()
    }

    private fun askWhoIsWatching(): Boolean {
        val m = Haven.settings.meta.value
        return m.askOnStart && m.order.size > 1
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Haven is already showing and Home was pressed (or another part of Haven asked for a screen).
        if (openFromIntent(intent)) return
        if (askPending && askWhoIsWatching()) {
            askPending = false
            nav.go(Screen.Profiles)
        } else {
            nav.home()
        }
    }

    private fun openFromIntent(intent: Intent?): Boolean {
        val name = intent?.getStringExtra(EXTRA_SCREEN) ?: return false
        intent.removeExtra(EXTRA_SCREEN)
        val target = runCatching { Screen.valueOf(name) }.getOrNull() ?: return false
        nav.go(target)
        return true
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        Haven.lastInput = SystemClock.uptimeMillis()
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        Haven.lastInput = SystemClock.uptimeMillis()
        return super.dispatchKeyEvent(event)
    }

    companion object {
        const val EXTRA_SCREEN = "app.haven.launcher.SCREEN"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HavenRoot(nav: Nav) {
    val settings by Haven.settings.state.collectAsState()
    DesignCanvas(Modifier.background(C.Hearth)) {
        // Back never leaves the launcher; it only returns to the home screen.
        BackHandler { if (nav.screen != Screen.Home) nav.go(Screen.Home) }
        // Lists scroll only as far as needed and keep focus inside the 116 px safe area (DESIGN.md, "Layout").
        CompositionLocalProvider(LocalBringIntoViewSpec provides rememberKeepVisible(116.dp)) {
            val screen = nav.screen
            // The profile's background sits behind every screen but art mode and the briefing, which draw their own.
            if (screen != Screen.Art && screen != Screen.Briefing) HavenBackground(BgPreview.value ?: settings.background)
            key(screen) {
                // Each screen fades in over 240 ms; the old one is gone at once, so focus never lands behind.
                val alpha = remember { Animatable(if (Look.reduced) 1f else 0f) }
                LaunchedEffect(Unit) { alpha.animateTo(1f, tween(240)) }
                Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value }) {
                    // A profile with a grown-up PIN asks for it before Settings and switching profiles, however they're reached.
                    val guarded = screen == Screen.Settings || screen == Screen.Profiles
                    if (guarded && !GrownUp.isOpen(settings)) {
                        PinDialog(settings.kids.pin, onOk = { GrownUp.unlock() }, onDismiss = { nav.home() })
                        return@Box
                    }
                    when (screen) {
                        Screen.Home -> HomeScreen(nav)
                        Screen.Lights -> LightsScreen(nav)
                        Screen.Art -> ArtScreen(onExit = { nav.go(Screen.Home) })
                        Screen.Settings -> SettingsScreen(nav)
                        Screen.Alarm -> AlarmScreen(nav)
                        Screen.Sleep -> SleepScreen(nav)
                        Screen.Briefing -> BriefingScreen(nav)
                        Screen.Sports -> SportsScreen(nav)
                        Screen.Profiles -> ProfilesScreen(nav)
                        Screen.Bedtime -> BedtimeScreen(nav)
                    }
                }
            }
        }
    }
}
