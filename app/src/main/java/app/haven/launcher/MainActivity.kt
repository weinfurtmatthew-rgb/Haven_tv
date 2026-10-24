package app.haven.launcher

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.haven.launcher.ui.AlarmScreen
import app.haven.launcher.ui.ArtScreen
import app.haven.launcher.ui.C
import app.haven.launcher.ui.DesignCanvas
import app.haven.launcher.ui.HomeScreen
import app.haven.launcher.ui.LightsScreen
import app.haven.launcher.ui.SettingsScreen
import app.haven.launcher.ui.SleepScreen

enum class Screen { Home, Lights, Art, Settings, Alarm, Sleep }

/** Which full screen is showing. [homeResets] bumps whenever Home is pressed, so the home screen can scroll back. */
class Nav {
    var screen by mutableStateOf(Screen.Home)
    var homeResets by mutableIntStateOf(0)
        private set

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HavenRoot(nav) }
        openFromIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Haven is already showing and Home was pressed (or another part of Haven asked for a screen).
        if (!openFromIntent(intent)) nav.home()
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
        Haven.lastInput = SystemClock.uptimeMillis()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        Haven.lastInput = SystemClock.uptimeMillis()
        return super.dispatchKeyEvent(event)
    }

    companion object {
        const val EXTRA_SCREEN = "app.haven.launcher.SCREEN"
    }
}

@Composable
fun HavenRoot(nav: Nav) {
    DesignCanvas(Modifier.background(C.Bg)) {
        // Back never leaves the launcher; it only returns to the home screen.
        BackHandler { if (nav.screen != Screen.Home) nav.go(Screen.Home) }
        when (nav.screen) {
            Screen.Home -> HomeScreen(nav)
            Screen.Lights -> LightsScreen(nav)
            Screen.Art -> ArtScreen(onExit = { nav.go(Screen.Home) })
            Screen.Settings -> SettingsScreen(nav)
            Screen.Alarm -> AlarmScreen(nav)
            Screen.Sleep -> SleepScreen(nav)
        }
    }
}
