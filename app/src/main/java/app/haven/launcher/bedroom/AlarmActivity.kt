package app.haven.launcher.bedroom

import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.haven.launcher.Haven
import app.haven.launcher.MainActivity
import app.haven.launcher.data.AfterAlarm
import app.haven.launcher.data.AlarmBackground
import app.haven.launcher.data.IfIgnored
import app.haven.launcher.data.Settings
import app.haven.launcher.data.StopMethod
import app.haven.launcher.ui.C
import app.haven.launcher.ui.DesignCanvas
import app.haven.launcher.ui.FocusOnOpen
import app.haven.launcher.ui.Focusable
import app.haven.launcher.ui.HavenBackground
import app.haven.launcher.ui.Icon
import app.haven.launcher.ui.Icons
import app.haven.launcher.ui.L
import app.haven.launcher.ui.PaintingBackdrop
import app.haven.launcher.ui.SunriseGlow
import app.haven.launcher.ui.T
import app.haven.launcher.ui.Txt
import app.haven.launcher.ui.formatDate
import app.haven.launcher.ui.formatTime
import app.haven.launcher.ui.greetingLine
import app.haven.launcher.ui.ink
import app.haven.launcher.ui.isSelectKey
import app.haven.launcher.ui.rememberNow
import app.haven.launcher.ui.weatherIcon
import app.haven.launcher.ui.weatherLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Full-screen alarm from the bedroom mockup: the alarm owner's look, sound and words, a big clock, Snooze and
 * Stop. Stopping can take a press, a long hold or a sum, as the profile chose.
 */
class AlarmActivity : ComponentActivity() {
    private var tone: AlarmTone? = null
    private var done = false
    private var profile = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        getSystemService(NotificationManager::class.java)?.cancel(Bedroom.NOTIFICATION_ID)
        profile = intent.getStringExtra(Bedroom.EXTRA_PROFILE)?.takeIf { Haven.settings.exists(it) } ?: Haven.settings.activeId
        // Whoever this alarm wakes is the one at the TV now: their look, their name, their briefing.
        Haven.settings.switchTo(profile)
        val s = Haven.settings.settingsOf(profile)
        val a = s.alarm
        tone = AlarmTone(a.sound, a.volumePct / 100f, a.rampSec).also { it.start() }
        val canSnooze = a.maxSnoozes != 0 && (a.maxSnoozes < 0 || Bedroom.snoozesUsed(this, profile) < a.maxSnoozes)
        setContent {
            BackHandler { if (canSnooze) snooze() }
            DesignCanvas { AlarmRinging(s, canSnooze, onSnooze = ::snooze, onStop = ::stop) }
        }
        // Nobody answered: snooze (or stop) rather than ringing forever.
        lifecycleScope.launch {
            delay(a.ringMin * 60_000L)
            if (a.ifIgnored == IfIgnored.Snooze && canSnooze) snooze() else quiet()
        }
    }

    override fun onDestroy() {
        tone?.stop()
        super.onDestroy()
    }

    private fun snooze() {
        if (done) return
        done = true
        Bedroom.snooze(this, profile)
        finish()
    }

    /** Rang out with nobody there: stop without opening anything. */
    private fun quiet() {
        if (done) return
        done = true
        Bedroom.stopped(this, profile)
        finish()
    }

    private fun stop() {
        if (done) return
        done = true
        Bedroom.stopped(this, profile)
        finish()
        when (Haven.settings.settingsOf(profile).alarm.after) {
            AfterAlarm.Briefing -> openScreen("Briefing")
            AfterAlarm.Home -> openScreen("Home")
            AfterAlarm.LiveTv -> Haven.apps.provider()?.let { Haven.apps.launch(it) } ?: openScreen("Home")
            AfterAlarm.Close -> Unit
        }
    }

    private fun openScreen(screen: String) {
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_SCREEN, screen).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun AlarmRinging(s: Settings, canSnooze: Boolean, onSnooze: () -> Unit, onStop: () -> Unit) {
    val now by rememberNow()
    val a = s.alarm
    val stopFocus = remember { FocusRequester() }
    FocusOnOpen(stopFocus)
    // The light comes up over the first minute, like the sunrise lights in the room.
    val rise = remember { Animatable(0.15f) }
    LaunchedEffect(Unit) { rise.animateTo(1f, tween(60_000, easing = LinearEasing)) }
    Box(Modifier.fillMaxSize().background(C.Hearth)) {
        when (a.background) {
            AlarmBackground.Sunrise -> SunriseGlow(rise.value, Modifier.fillMaxSize())
            AlarmBackground.Theme -> Unit
            AlarmBackground.Background -> HavenBackground(s.background)
            AlarmBackground.Art -> PaintingBackdrop()
            AlarmBackground.Black -> Box(Modifier.fillMaxSize().background(Color.Black))
        }
        Column(
            Modifier.fillMaxSize().padding(top = if (a.stopWith == StopMethod.Sum) 72.dp else 136.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (a.showDate) Txt(formatDate(now), T.Body, color = C.Stone, size = 34)
            Txt(formatTime(now, s.use24h), T.Display, Modifier.padding(top = 8.dp))
            greetingLine(a.greeting, a.message, s.profileName, now.hour)?.let { Txt(it, T.Headline, Modifier.padding(top = 8.dp)) }
            Row(Modifier.padding(top = 40.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                if (a.showWeather) {
                    Haven.ha.weather(s)?.takeIf { it.available }?.let { w ->
                        val t = w.attrs.optDouble("temperature", Double.NaN)
                        Info(weatherIcon(w.state), (if (t.isNaN()) "" else "${Math.round(t)}° ") + weatherLabel(w.state))
                    }
                }
                if (a.sunriseMin > 0 && s.haConfigured) Info(Icons.Sunrise, "Sunrise lights are on")
            }
            when (a.stopWith) {
                StopMethod.Sum -> {
                    SumToStop(onStop, stopFocus)
                    if (canSnooze) Row(Modifier.padding(top = 32.dp)) { BigButton("Snooze ${a.snoozeMin} min", onSnooze) }
                }
                else -> Row(Modifier.padding(top = 56.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    if (canSnooze) BigButton("Snooze ${a.snoozeMin} min", onSnooze)
                    if (a.stopWith == StopMethod.Hold) HoldToStop(onStop, stopFocus) else BigButton("Stop", onStop, stopFocus)
                }
            }
        }
    }
}

@Composable
private fun BigButton(label: String, onClick: () -> Unit, focus: FocusRequester? = null, width: Dp = 300.dp) {
    Focusable(
        onClick = onClick,
        modifier = Modifier.size(width, 96.dp),
        shape = L.pill(96.dp),
        color = C.Raised,
        focusedScale = 1.06f,
        focusRequester = focus,
    ) { focused ->
        Txt(label, T.CardTitle, Modifier.align(Alignment.Center), ink(focused), size = 32)
    }
}

/** Stop only after OK is held for three seconds; the button fills while it's held. */
@Composable
private fun HoldToStop(onDone: () -> Unit, focus: FocusRequester) {
    var holding by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(holding) {
        if (!holding) {
            progress = 0f
            return@LaunchedEffect
        }
        val start = SystemClock.uptimeMillis()
        while (progress < 1f) {
            progress = ((SystemClock.uptimeMillis() - start) / 3000f).coerceAtMost(1f)
            delay(16)
        }
        onDone()
    }
    Box(
        Modifier.onPreviewKeyEvent { ev ->
            if (ev.isSelectKey()) {
                holding = ev.type == KeyEventType.KeyDown
                true
            } else {
                false
            }
        },
    ) {
        Focusable(
            onClick = {},
            modifier = Modifier.size(380.dp, 96.dp),
            shape = L.pill(96.dp),
            color = C.Raised,
            focusedScale = 1.06f,
            focusRequester = focus,
        ) { focused ->
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(C.Lamp))
            Txt(if (holding) "Keep holding…" else "Hold OK to stop", T.CardTitle, Modifier.align(Alignment.Center), ink(focused && progress == 0f), size = 30)
        }
    }
}

private class Sum(val a: Int, val b: Int, val answers: List<Int>) {
    val right: Int get() = a + b
}

private fun newSum(): Sum {
    val a = Random.nextInt(3, 13)
    val b = Random.nextInt(3, 13)
    val wrong = listOf(a + b - 1, a + b + 1, a + b + 2, a + b - 2, a + b + 10).shuffled().take(2)
    return Sum(a, b, (wrong + (a + b)).shuffled())
}

/** Stop by picking the answer to an easy sum: hard to do in your sleep, easy once you're up. */
@Composable
private fun SumToStop(onDone: () -> Unit, focus: FocusRequester) {
    var sum by remember { mutableStateOf(newSum()) }
    var missed by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Txt("To stop the alarm: ${sum.a} + ${sum.b} =", T.Title, size = 40)
        Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            for ((i, n) in sum.answers.withIndex()) {
                BigButton(n.toString(), {
                    if (n == sum.right) {
                        onDone()
                    } else {
                        missed = true
                        sum = newSum()
                    }
                }, if (i == 0) focus else null, width = 180.dp)
            }
        }
        if (missed) Txt("Not quite. Here's another.", T.Body, Modifier.padding(top = 16.dp), C.Clay)
    }
}

@Composable
private fun Info(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, 36.dp, C.Stone)
        Txt(text, T.Body, color = C.Stone, size = 30)
    }
}
