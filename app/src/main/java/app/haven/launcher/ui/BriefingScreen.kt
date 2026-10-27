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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
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
    val timeFormat = DateTimeFormatter.ofPattern(if (s.use24h) "H:mm" else "h:mm a", Locale.getDefault())

    Box(
        Modifier
            .fillMaxSize()
            .background(C.Hearth)
            .focusRequester(focus)
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown) nav.go(Screen.Home)
                true
            }
            .focusable(),
    ) {
        val d = data
        if (d == null) {
            Txt("Getting your briefing…", T.Title, Modifier.align(Alignment.Center), C.Stone)
            return@Box
        }
        Row(Modifier.fillMaxSize().padding(start = 116.dp, end = 116.dp, top = 96.dp, bottom = 128.dp)) {
            // ---- left: greeting, weather, headlines
            Column(Modifier.width(840.dp), verticalArrangement = Arrangement.spacedBy(40.dp)) {
                Column {
                    val name = s.profileName.trim()
                    Txt(greeting(now.hour) + (if (name.isEmpty()) "." else ", $name."), T.Headline, size = 72, maxLines = 2)
                    Txt(formatDate(now), T.Body, Modifier.padding(top = 8.dp), C.Stone, size = 30)
                }
                val w = d.weather
                if (w != null) {
                    val t = w.attrs.optDouble("temperature", Double.NaN)
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                            Icon(weatherIcon(w.state), 112.dp)
                            if (!t.isNaN()) Txt("${Math.round(t)}°", T.Display, size = 160)
                            Column {
                                Txt(weatherLabel(w.state), T.CardTitle, size = 34)
                                val today = d.today
                                if (today?.temperature != null) {
                                    val lo = today.low?.let { " · Low ${Math.round(it)}°" }.orEmpty()
                                    Txt("High ${Math.round(today.temperature)}°$lo", T.Body, color = C.Stone)
                                }
                            }
                        }
                        if (d.hourly.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(56.dp)) {
                                for (h in d.hourly) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val label = h.time?.atZoneSameInstant(ZoneId.systemDefault())
                                            ?.format(DateTimeFormatter.ofPattern(if (s.use24h) "H:mm" else "h a", Locale.getDefault())).orEmpty()
                                        Txt(label, T.Label, color = C.Stone)
                                        Icon(weatherIcon(h.condition), 40.dp, C.Stone)
                                        Txt(h.temperature?.let { "${Math.round(it)}°" } ?: "", T.Numeric, size = 28)
                                    }
                                }
                            }
                        }
                    }
                } else if (!s.haConfigured) {
                    Txt("Connect Home Assistant in Settings for weather, your calendar and the house.", T.Body, color = C.Stone, maxLines = 2)
                }
                if (d.headlines.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SectionTitle("Headlines")
                        for (h in d.headlines.take(3)) Txt(h, T.Body, maxLines = 2)
                    }
                }
            }
            Box(Modifier.width(96.dp))
            // ---- right: calendar, commute, home, games
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                if (d.events.isNotEmpty() || d.hasCalendars) {
                    BriefCard(Icons.Calendar, "Today", if (d.events.isEmpty()) null else "${d.events.size} event${if (d.events.size == 1) "" else "s"}") {
                        if (d.events.isEmpty()) Txt("Nothing on the calendar", T.Body, color = C.Stone)
                        for (e in d.events) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val time = when {
                                    e.allDay || e.start == null -> "All day"
                                    else -> e.start.atZoneSameInstant(ZoneId.systemDefault()).format(timeFormat)
                                }
                                Txt(time, T.Numeric, Modifier.width(170.dp))
                                Txt(e.summary, T.Body, Modifier.weight(1f))
                            }
                        }
                    }
                }
                d.commute?.let { c ->
                    BriefCard(Icons.Car, "${c.minutes} ${c.unit} to ${c.label}") {
                        if (c.route.isNotBlank()) Txt("Via ${c.route}", T.Body, color = C.Stone)
                    }
                }
                if (d.status.isNotEmpty()) {
                    BriefCard(Icons.House, "Home") {
                        for (st in d.status) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Txt(st.name, T.Body, Modifier.weight(1f))
                                Txt(st.text, T.Label, color = if (st.ok) C.Sage else C.Clay)
                            }
                        }
                    }
                }
                if (d.games.isNotEmpty()) {
                    BriefCard(Icons.Trophy, "Your teams today") {
                        for (g in d.games.take(3)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                when (g.state) {
                                    GameState.Live, GameState.Final -> {
                                        Txt(g.scoreLine, T.Numeric, Modifier.weight(1f))
                                        Txt(g.detail.ifBlank { if (g.state == GameState.Live) "Live" else "Final" }, T.Label, color = if (g.state == GameState.Live) C.Clay else C.Stone)
                                    }
                                    else -> {
                                        Txt(g.matchup, T.Body, Modifier.weight(1f))
                                        Txt(
                                            g.start.atZone(ZoneId.systemDefault()).format(timeFormat) + (g.broadcasts.firstOrNull()?.let { " · $it" } ?: ""),
                                            T.Label, color = C.Stone,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Txt("Press any button to close", T.Label, Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp), C.Dust)
    }
}

@Composable
private fun BriefCard(icon: ImageVector, title: String, trailing: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Surface).padding(horizontal = 36.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, 32.dp, C.Stone)
            Txt(title, T.CardTitle, Modifier.weight(1f))
            if (trailing != null) Txt(trailing, T.Label, color = C.Stone)
        }
        content()
    }
}
