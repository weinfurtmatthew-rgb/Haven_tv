package app.haven.launcher.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.haven.launcher.Haven
import app.haven.launcher.Nav
import app.haven.launcher.Screen
import app.haven.launcher.bedroom.AlarmActivity
import app.haven.launcher.bedroom.Bedroom
import app.haven.launcher.data.AlarmConfig
import app.haven.launcher.system.HomeButtonService
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun AlarmScreen(nav: Nav) {
    val ctx = LocalContext.current
    val settings by Haven.settings.state.collectAsState()
    val a = settings.alarm
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { first.requestFocus() }
    }
    fun edit(block: (AlarmConfig) -> AlarmConfig) {
        Haven.settings.update { it.copy(alarm = block(it.alarm)) }
        Bedroom.scheduleAlarm(ctx)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 96.dp, vertical = 56.dp), verticalArrangement = Arrangement.spacedBy(40.dp)) {
        ScreenHeader("Alarm", settings.roomName, onBack = { nav.go(Screen.Home) })
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            Panel(Modifier.weight(1.1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val h12 = if (a.hour % 12 == 0) 12 else a.hour % 12
                    TimeField(if (settings.use24h) a.hour.toString() else h12.toString(), first) { d ->
                        edit { it.copy(hour = (it.hour + d + 24) % 24, enabled = true) }
                    }
                    Txt(":", 120, weight = FontWeight.ExtraBold)
                    TimeField(a.minute.toString().padStart(2, '0')) { d ->
                        edit { it.copy(minute = (it.minute + d * 5 + 60) % 60 / 5 * 5, enabled = true) }
                    }
                    if (!settings.use24h) {
                        TimeField(if (a.hour < 12) "AM" else "PM", small = true) { _ ->
                            edit { it.copy(hour = (it.hour + 12) % 24, enabled = true) }
                        }
                    }
                }
                Txt("▲ ▼ change the time  ·  minutes move in steps of 5", 18, weight = FontWeight.Medium, color = C.Muted)
                SettingRow("Alarm", if (a.enabled) "On" else "Off", toggle = a.enabled) { edit { it.copy(enabled = !it.enabled) } }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (day in WEEK) {
                        val on = day in a.days
                        Focusable(
                            onClick = { edit { it.copy(days = if (on) it.days - day else it.days + day) } },
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape,
                            color = if (on) C.Accent else C.CardHi,
                            focusedColor = if (on) C.Accent else C.Line,
                            focusedScale = 1.1f,
                        ) {
                            Txt(
                                day.getDisplayName(TextStyle.NARROW, Locale.getDefault()), 24, Modifier.align(Alignment.Center),
                                FontWeight.Bold, if (on) C.Bg else C.Text,
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PillButton("Weekdays", { edit { it.copy(days = AlarmConfig.WEEKDAYS) } }, selected = a.days == AlarmConfig.WEEKDAYS)
                    PillButton("Every day", { edit { it.copy(days = AlarmConfig.EVERY_DAY) } }, selected = a.days == AlarmConfig.EVERY_DAY)
                    PillButton("Weekends", { edit { it.copy(days = AlarmConfig.WEEKENDS) } }, selected = a.days == AlarmConfig.WEEKENDS)
                }
            }
            Panel(Modifier.weight(1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.AlarmClock, 36.dp, C.Accent)
                    Txt(nextAlarmText(a), 28, weight = FontWeight.ExtraBold, maxLines = 2)
                }
                SettingRow(
                    "Sunrise lights",
                    when {
                        !settings.haConfigured -> "Needs Home Assistant"
                        a.sunriseMin == 0 -> "Off"
                        else -> "Fade in over ${a.sunriseMin} min"
                    },
                ) { edit { it.copy(sunriseMin = SUNRISE_STEPS.next(it.sunriseMin)) } }
                SettingRow("Snooze", "${a.snoozeMin} min") { edit { it.copy(snoozeMin = SNOOZE_STEPS.next(it.snoozeMin)) } }
                SettingRow("Try the alarm now", "Rings for real") {
                    ctx.startActivity(Intent(ctx, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                Txt(
                    "Leave the box plugged in. If it goes into deep standby, it may not be able to wake the TV; " +
                        "try the alarm once at night to check.",
                    18, weight = FontWeight.Medium, color = C.Muted, maxLines = 3,
                )
            }
        }
    }
}

@Composable
private fun TimeField(text: String, focus: FocusRequester? = null, small: Boolean = false, onStep: (Int) -> Unit) {
    Focusable(
        onClick = { onStep(1) },
        modifier = Modifier.size(if (small) 140.dp else 200.dp, 180.dp),
        color = C.CardHi,
        focusedColor = C.Line,
        focusedScale = 1.05f,
        focusRequester = focus,
        onKey = { ev ->
            if (ev.type != KeyEventType.KeyDown) {
                false
            } else when (ev.key) {
                Key.DirectionUp -> { onStep(1); true }
                Key.DirectionDown -> { onStep(-1); true }
                else -> false
            }
        },
    ) { focused ->
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Txt("▲", 20, color = if (focused) C.Accent else C.CardHi)
            Txt(text, if (small) 60 else 110, weight = FontWeight.ExtraBold, align = TextAlign.Center)
            Txt("▼", 20, color = if (focused) C.Accent else C.CardHi)
        }
    }
}

@Composable
fun SleepScreen(nav: Nav) {
    val ctx = LocalContext.current
    val settings by Haven.settings.state.collectAsState()
    val endsAt by Bedroom.sleepEndsAt.collectAsState()
    val serviceOn by HomeButtonService.running.collectAsState()
    val now by rememberNow()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { first.requestFocus() }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 96.dp, vertical = 56.dp), verticalArrangement = Arrangement.spacedBy(40.dp)) {
        ScreenHeader("Sleep timer", settings.roomName, onBack = { nav.go(Screen.Home) })
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            Panel(Modifier.weight(1.2f).fillMaxHeight()) {
                val left = if (endsAt > 0) Duration.ofMillis(endsAt - System.currentTimeMillis()).coerceAtLeast(Duration.ZERO) else null
                now.second // tick every second
                Txt(
                    if (left == null) "Off" else "Sleeping in %d:%02d".format(left.toMinutes(), left.seconds % 60),
                    56, weight = FontWeight.ExtraBold,
                )
                Txt("Choose how long until the TV goes to sleep.", 22, weight = FontWeight.Medium, color = C.Muted)
                val options = listOf(15, 30, 45, 60, 90, 120)
                for (row in options.chunked(3)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        for (m in row) {
                            Focusable(
                                onClick = { Bedroom.startSleep(ctx, m) },
                                modifier = Modifier.size(220.dp, 120.dp),
                                color = C.CardHi,
                                focusedColor = C.Line,
                                focusRequester = if (m == options.first()) first else null,
                            ) {
                                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Txt(if (m < 60) "$m" else "${m / 60}${if (m % 60 == 30) "½" else ""}", 44, weight = FontWeight.ExtraBold)
                                    Txt(if (m < 60) "minutes" else if (m == 60) "hour" else "hours", 18, weight = FontWeight.Medium, color = C.Muted)
                                }
                            }
                        }
                    }
                }
                if (left != null) {
                    PillButton("Cancel timer", { Bedroom.cancelSleep(ctx) }, icon = Icons.X)
                }
            }
            Panel(Modifier.weight(1f).fillMaxHeight()) {
                Txt("When time is up", 28, weight = FontWeight.ExtraBold)
                Txt("Playback pauses first.", 20, weight = FontWeight.Medium, color = C.Muted)
                SettingRow("Turn off the room's lights", if (settings.haConfigured) "" else "Needs Home Assistant", toggle = settings.sleepLightsOff) {
                    Haven.settings.update { it.copy(sleepLightsOff = !it.sleepLightsOff) }
                }
                SettingRow("Put the box and TV to sleep", if (serviceOn) "" else "Needs the Home button service", toggle = settings.sleepBox) {
                    Haven.settings.update { it.copy(sleepBox = !it.sleepBox) }
                }
                if (!serviceOn) {
                    Txt(
                        "To let Haven put the box to sleep, turn on \"Haven Home button\" in Settings › Accessibility.",
                        18, weight = FontWeight.Medium, color = C.Muted, maxLines = 3,
                    )
                }
            }
        }
    }
}

private val WEEK = listOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
)
private val SUNRISE_STEPS = listOf(0, 5, 10, 15, 20, 30)
private val SNOOZE_STEPS = listOf(5, 9, 10, 15)

private fun List<Int>.next(current: Int): Int = this[(indexOf(current).coerceAtLeast(-1) + 1) % size]

private fun nextAlarmText(a: AlarmConfig): String {
    val next = Bedroom.nextAlarm(a, ZonedDateTime.now()) ?: return if (a.enabled) "Pick at least one day" else "Alarm is off"
    val mins = Duration.between(ZonedDateTime.now(), next).toMinutes()
    val day = next.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    return "Rings $day in ${mins / 60} hr ${mins % 60} min"
}
