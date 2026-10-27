package app.haven.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.haven.launcher.Haven
import app.haven.launcher.Nav
import app.haven.launcher.Screen
import app.haven.launcher.brief.BriefData
import app.haven.launcher.brief.Briefing
import app.haven.launcher.sports.GameState
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Sunny = Color(0xFFFFD27A)
private val Good = Color(0xFF86EFAC)
private val Warn = Color(0xFFFF8A7A)

/** The morning briefing from the "day in the life" film, shown after the alarm is stopped. Any button closes it. */
@Composable
fun BriefingScreen(nav: Nav) {
    val s = Haven.settings.value
    val data by produceState<BriefData?>(null) { value = runCatching { Briefing.load() }.getOrNull() ?: BriefData() }
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { focus.requestFocus() }
        delay(s.brief.autoCloseMin * 60_000L)
        nav.go(Screen.Home)
    }
    val now = remember { LocalDateTime.now() }
    val use24 = s.use24h

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF121829))
            .focusRequester(focus)
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown) nav.go(Screen.Home)
                true
            }
            .focusable(),
    ) {
        Box(Modifier.offset(1100.dp, (-500).dp).size(1500.dp).background(Brush.radialGradient(listOf(Color(0x5CFF9F6B), Color.Transparent))))
        Box(Modifier.offset((-500).dp, 400.dp).size(1400.dp).background(Brush.radialGradient(listOf(Color(0x474F8FE8), Color.Transparent))))

        val d = data
        if (d == null) {
            Txt("Getting your briefing…", 36, Modifier.align(Alignment.Center), FontWeight.SemiBold, C.Muted)
            return@Box
        }
        Row(Modifier.fillMaxSize().padding(start = 140.dp, end = 140.dp, top = 110.dp, bottom = 70.dp)) {
            // ---- left: greeting, weather, hourly, headlines
            Column(Modifier.width(820.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
                val name = s.profileName.trim()
                Txt(if (name.isEmpty()) "Good morning." else "Good morning, $name.", 72, weight = FontWeight.ExtraBold, spacing = -1f)
                Txt("Here's your ${now.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())}.", 36, weight = FontWeight.Medium, color = C.Muted)
                val w = d.weather
                if (w != null) {
                    val unit = "°"
                    val t = w.attrs.optDouble("temperature", Double.NaN)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        Icon(weatherIcon(w.state), 120.dp, Sunny)
                        if (!t.isNaN()) Txt("${Math.round(t)}$unit", 150, weight = FontWeight.Medium, spacing = -4f)
                        Column {
                            Txt(weatherLabel(w.state), 34, weight = FontWeight.SemiBold)
                            val today = d.today
                            if (today?.temperature != null) {
                                val lo = today.low?.let { "  ·  Low ${Math.round(it)}$unit" }.orEmpty()
                                Txt("High ${Math.round(today.temperature)}$unit$lo", 28, weight = FontWeight.Medium, color = C.Muted)
                            }
                        }
                    }
                    if (d.hourly.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(44.dp)) {
                            for (h in d.hourly) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val label = h.time?.atZoneSameInstant(ZoneId.systemDefault())
                                        ?.format(DateTimeFormatter.ofPattern(if (use24) "H:mm" else "h a", Locale.getDefault())).orEmpty()
                                    Txt(label, 22, weight = FontWeight.Medium, color = C.Muted)
                                    Icon(weatherIcon(h.condition), 40.dp, Color(0xFFFFE0A6))
                                    Txt(h.temperature?.let { "${Math.round(it)}$unit" } ?: "", 26, weight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                } else if (!s.haConfigured) {
                    Txt("Connect Home Assistant in Settings for weather, calendar and home status.", 24, weight = FontWeight.Medium, color = C.Muted, maxLines = 2)
                }
                if (d.headlines.isNotEmpty()) {
                    Card(Icons.Newspaper, Color(0xFFB9A2FF), "Headlines") {
                        for (h in d.headlines) Txt(h, 22, weight = FontWeight.Medium, maxLines = 1)
                    }
                }
            }
            Box(Modifier.width(80.dp))
            // ---- right: calendar, commute, home, games
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                Card(Icons.Calendar, C.Accent, "Today", if (d.events.isEmpty()) null else "${d.events.size} event${if (d.events.size == 1) "" else "s"}") {
                    if (d.events.isEmpty()) {
                        Txt("Nothing on the calendar", 24, weight = FontWeight.Medium, color = C.Muted)
                    }
                    for (e in d.events) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val time = when {
                                e.allDay || e.start == null -> "All day"
                                else -> e.start.atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(if (use24) "H:mm" else "h:mm", Locale.getDefault()))
                            }
                            Txt(time, 26, Modifier.width(130.dp), FontWeight.Bold, C.Accent)
                            Txt(e.summary, 26, weight = FontWeight.Medium)
                        }
                    }
                }
                d.commute?.let { c ->
                    Card(Icons.Car, Color(0xFFF7B955), "${c.minutes} ${c.unit} to ${c.label}") {
                        if (c.route.isNotBlank()) Txt("Via ${c.route}", 22, weight = FontWeight.Medium, color = C.Muted)
                    }
                }
                if (d.status.isNotEmpty()) {
                    Card(Icons.House, Good, "Home") {
                        for (st in d.status) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Txt(st.name, 24, Modifier.weight(1f), FontWeight.Medium)
                                Txt(st.text, 22, weight = FontWeight.Bold, color = if (st.ok) Good else Warn)
                            }
                        }
                    }
                }
                if (d.games.isNotEmpty()) {
                    Card(Icons.Trophy, Color(0xFFFF8A7A), "Your teams today") {
                        for (g in d.games.take(3)) {
                            val line = when (g.state) {
                                GameState.Live, GameState.Final -> "${g.scoreLine} · ${g.detail}"
                                else -> "${g.away.team.abbr} at ${g.home.team.abbr} · " +
                                    g.start.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(if (use24) "H:mm" else "h:mm a", Locale.getDefault())) +
                                    (g.broadcasts.firstOrNull()?.let { " · $it" } ?: "")
                            }
                            Txt(line, 22, weight = FontWeight.Medium, maxLines = 1)
                        }
                    }
                }
            }
        }
        Txt(
            "Press any button to close", 18, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            FontWeight.Medium, C.Muted.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun Card(icon: ImageVector, tint: Color, title: String, trailing: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(Color(0x991A2234)).padding(horizontal = 36.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, 34.dp, tint)
            Txt(title, 30, Modifier.weight(1f), FontWeight.Bold)
            if (trailing != null) Txt(trailing, 22, weight = FontWeight.Medium, color = C.Muted)
        }
        Box(Modifier.height(2.dp))
        content()
    }
}
