package app.haven.launcher.ui

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
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
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun AlarmScreen(nav: Nav) {
    val ctx = LocalContext.current
    val settings by Haven.settings.state.collectAsState()
    val now by rememberNow()
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

    Column(Modifier.fillMaxSize().padding(start = 116.dp, end = 116.dp, top = 48.dp), verticalArrangement = Arrangement.spacedBy(48.dp)) {
        ScreenHeader("Alarm", settings.roomName, onBack = { nav.go(Screen.Home) })
        Row(horizontalArrangement = Arrangement.spacedBy(96.dp)) {
            Column(Modifier.width(800.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val h12 = if (a.hour % 12 == 0) 12 else a.hour % 12
                    TimeField(if (settings.use24h) a.hour.toString().padStart(2, '0') else h12.toString(), first) { d ->
                        edit { it.copy(hour = (it.hour + d + 24) % 24, enabled = true) }
                    }
                    Txt(":", T.Display, color = C.Stone, size = 120)
                    TimeField(a.minute.toString().padStart(2, '0')) { d ->
                        edit { it.copy(minute = (it.minute + d * 5 + 60) % 60 / 5 * 5, enabled = true) }
                    }
                    if (!settings.use24h) {
                        Spacer(width = 8.dp)
                        TimeField(if (a.hour < 12) "AM" else "PM", small = true) { _ ->
                            edit { it.copy(hour = (it.hour + 12) % 24, enabled = true) }
                        }
                    }
                }
                KeyHint("change the time. Minutes move in steps of 5", icon = Icons.ChevronsUpDown)
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (day in WEEK) {
                            val on = day in a.days
                            DayChip(day.getDisplayName(TextStyle.SHORT, Locale.getDefault()), on) {
                                edit { it.copy(days = if (on) it.days - day else it.days + day) }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Chip("Weekdays", { edit { it.copy(days = AlarmConfig.WEEKDAYS) } }, selected = a.days == AlarmConfig.WEEKDAYS)
                        Chip("Every day", { edit { it.copy(days = AlarmConfig.EVERY_DAY) } }, selected = a.days == AlarmConfig.EVERY_DAY)
                        Chip("Weekends", { edit { it.copy(days = AlarmConfig.WEEKENDS) } }, selected = a.days == AlarmConfig.WEEKENDS)
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                now.minute // re-read "in 7 hr 12 min" every minute
                SettingRow("Alarm", nextAlarmText(a, settings.use24h), toggle = a.enabled) { edit { it.copy(enabled = !it.enabled) } }
                SettingRow(
                    "Sunrise lights",
                    when {
                        !settings.haConfigured -> "Needs Home Assistant"
                        a.sunriseMin == 0 -> "Off"
                        else -> "The room's lights fade in over ${a.sunriseMin} min"
                    },
                ) { edit { it.copy(sunriseMin = SUNRISE_STEPS.next(it.sunriseMin)) } }
                SettingRow("Snooze", "${a.snoozeMin} min") { edit { it.copy(snoozeMin = SNOOZE_STEPS.next(it.snoozeMin)) } }
                SettingRow("Try the alarm now", "It rings for real") {
                    ctx.startActivity(Intent(ctx, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                Txt(
                    "Leave the box plugged in. In deep standby it may not wake the TV, so try the alarm once at night.",
                    T.Body, Modifier.padding(top = 8.dp), C.Dust, maxLines = 3,
                )
            }
        }
    }
}

/** One part of the alarm time. Up and down change it; the chevrons show only while it has focus. */
@Composable
private fun TimeField(text: String, focus: FocusRequester? = null, small: Boolean = false, onStep: (Int) -> Unit) {
    Focusable(
        onClick = { onStep(1) },
        modifier = Modifier.size(if (small) 168.dp else 208.dp, 224.dp),
        color = C.Surface,
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
            Icon(Icons.ChevronUp, 36.dp, if (focused) C.Ink else Color.Transparent)
            if (small) {
                Txt(text, T.CardTitle, Modifier.padding(vertical = 30.dp), ink(focused), size = 44)
            } else {
                Txt(text, T.Display, color = ink(focused), size = 120, align = TextAlign.Center)
            }
            Icon(Icons.ChevronDown, 36.dp, if (focused) C.Ink else Color.Transparent)
        }
    }
}

/** A day of the week: lamp when the alarm rings that day. */
@Composable
private fun DayChip(label: String, on: Boolean, onClick: () -> Unit) {
    Focusable(
        onClick = onClick,
        modifier = Modifier.size(100.dp, 72.dp),
        shape = RoundedCornerShape(36.dp),
        color = if (on) C.LampSoft else C.Raised,
        focusedScale = 1.08f,
    ) { focused ->
        Txt(
            label, T.Label, Modifier.align(Alignment.Center),
            color = when {
                focused -> C.Ink
                on -> C.Lamp
                else -> C.Stone
            },
        )
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

    Column(Modifier.fillMaxSize().padding(start = 116.dp, end = 116.dp, top = 48.dp), verticalArrangement = Arrangement.spacedBy(48.dp)) {
        ScreenHeader("Sleep timer", settings.roomName, onBack = { nav.go(Screen.Home) })
        Row(horizontalArrangement = Arrangement.spacedBy(96.dp)) {
            Column(Modifier.width(800.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
                val left = if (endsAt > 0) Duration.ofMillis(endsAt - System.currentTimeMillis()).coerceAtLeast(Duration.ZERO) else null
                now.second // tick every second
                if (left == null) {
                    Column {
                        Txt("Off", T.Headline)
                        Txt("Choose how long until the TV goes to sleep.", T.Body, color = C.Stone)
                    }
                } else {
                    Column {
                        Txt("%d:%02d".format(left.toMinutes(), left.seconds % 60), T.Numeric, color = C.Lamp, size = 72)
                        Txt("until the TV goes to sleep", T.Body, color = C.Stone)
                    }
                }
                val options = listOf(15, 30, 45, 60, 90, 120)
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    for (row in options.chunked(3)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            for (m in row) {
                                Focusable(
                                    onClick = { Bedroom.startSleep(ctx, m) },
                                    modifier = Modifier.size(240.dp, 128.dp),
                                    color = C.Surface,
                                    focusRequester = if (m == options.first()) first else null,
                                ) { focused ->
                                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Txt(if (m < 60) "$m" else "${m / 60}${if (m % 60 == 30) "½" else ""}", T.Numeric, color = ink(focused), size = 48)
                                        Txt(if (m < 60) "minutes" else if (m == 60) "hour" else "hours", T.Label, color = inkMuted(focused))
                                    }
                                }
                            }
                        }
                    }
                }
                if (left != null) Chip("Cancel the timer", { Bedroom.cancelSleep(ctx) }, icon = Icons.X)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SectionTitle("When time is up")
                Txt("Playback pauses first.", T.Body, Modifier.padding(bottom = 8.dp), C.Stone)
                SettingRow(
                    "Turn off the room's lights",
                    if (settings.haConfigured) "" else "Needs Home Assistant",
                    toggle = settings.sleepLightsOff && settings.haConfigured,
                    toggleEnabled = settings.haConfigured,
                ) {
                    if (settings.haConfigured) Haven.settings.update { it.copy(sleepLightsOff = !it.sleepLightsOff) }
                }
                SettingRow(
                    "Put the box and TV to sleep",
                    if (serviceOn) "" else "Needs the Haven Home button service. Select to turn it on",
                    toggle = settings.sleepBox && serviceOn,
                    toggleEnabled = serviceOn,
                ) {
                    if (serviceOn) Haven.settings.update { it.copy(sleepBox = !it.sleepBox) }
                    else openSystem(ctx, AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)
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

/** "On. Rings tomorrow at 6:30 AM, in 7 hr 12 min". */
private fun nextAlarmText(a: AlarmConfig, use24h: Boolean): String {
    if (!a.enabled) return "Off"
    val next = Bedroom.nextAlarm(a, ZonedDateTime.now()) ?: return "On, but no days are picked"
    val mins = Duration.between(ZonedDateTime.now(), next).toMinutes()
    val day = when (next.toLocalDate()) {
        LocalDate.now() -> "today"
        LocalDate.now().plusDays(1) -> "tomorrow"
        else -> next.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    }
    return "On. Rings $day at ${Bedroom.timeLabel(a.hour, a.minute, use24h)}, in ${mins / 60} hr ${mins % 60} min"
}
