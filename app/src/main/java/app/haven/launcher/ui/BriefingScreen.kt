package app.haven.launcher.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.haven.launcher.Haven
import app.haven.launcher.Nav
import app.haven.launcher.Screen
import app.haven.launcher.brief.BriefData
import app.haven.launcher.brief.Briefing
import app.haven.launcher.data.BriefBackground
import app.haven.launcher.data.BriefClose
import app.haven.launcher.data.BriefConfig
import app.haven.launcher.data.BriefLayout
import app.haven.launcher.data.BriefSection
import app.haven.launcher.data.BriefWhen
import app.haven.launcher.data.Settings
import app.haven.launcher.sports.GameState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "Every morning" briefings: the first time Haven opens each morning, once per profile per day. */
object BriefingTrigger {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("haven_brief", Context.MODE_PRIVATE)

    fun due(ctx: Context): Boolean {
        if (Haven.settings.value.brief.showWhen != BriefWhen.Mornings) return false
        val now = LocalDateTime.now()
        if (now.hour !in 4..11) return false
        return prefs(ctx).getString("shown:${Haven.settings.activeId}", "") != now.toLocalDate().toString()
    }

    fun shown(ctx: Context) {
        prefs(ctx).edit().putString("shown:${Haven.settings.activeId}", LocalDate.now().toString()).apply()
    }
}

/**
 * The morning briefing from the "day in the life" film: the profile's sections in their order and layout, over
 * their background, read aloud if they like. Any button closes it (or only Back, if they chose that).
 */
@Composable
fun BriefingScreen(nav: Nav) {
    val ctx = LocalContext.current
    val s by Haven.settings.state.collectAsState()
    val cfg = s.brief
    val data by produceState<BriefData?>(null) { value = runCatching { Briefing.load() }.getOrNull() ?: BriefData() }
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        BriefingTrigger.shown(ctx)
        onDispose { view.keepScreenOn = false }
    }
    val focus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { focus.requestFocus() }
        if (cfg.autoCloseMin > 0) {
            delay(cfg.autoCloseMin * 60_000L)
            nav.go(Screen.Home)
        }
    }
    val now = remember { LocalDateTime.now() }
    val d = data
    if (cfg.readAloud && d != null) ReadAloud(speech(s, d, now))

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .onPreviewKeyEvent {
                if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                when {
                    cfg.closeWith == BriefClose.AnyButton -> nav.go(Screen.Home)
                    it.key == Key.Back -> nav.go(Screen.Home)
                    it.key == Key.DirectionDown -> scope.launch { scroll.animateScrollBy(240f) }
                    it.key == Key.DirectionUp -> scope.launch { scroll.animateScrollBy(-240f) }
                }
                true
            }
            .focusable(),
    ) {
        when (cfg.background) {
            BriefBackground.Theme -> Box(Modifier.fillMaxSize().background(C.Hearth))
            BriefBackground.Background -> HavenBackground(s.background)
            BriefBackground.Sunrise -> {
                Box(Modifier.fillMaxSize().background(C.Hearth))
                SunriseGlow(0.7f, Modifier.fillMaxSize())
            }
            BriefBackground.Art -> {
                Box(Modifier.fillMaxSize().background(C.Hearth))
                PaintingBackdrop()
            }
            BriefBackground.Black -> Box(Modifier.fillMaxSize().background(Color.Black))
        }
        if (d == null) {
            Txt("Getting your briefing…", T.Title, Modifier.align(Alignment.Center), C.Stone)
            return@Box
        }
        val greeting = greetingLine(cfg.greeting, cfg.message, s.profileName, now.hour, stop = ".")
        when (cfg.layout) {
            // Long briefings scroll (Up and Down when only Back closes it) and fade out above the hint line.
            BriefLayout.Columns -> Row(Modifier.fillMaxSize().fadeBottom(180.dp).verticalScroll(scroll).padding(start = 116.dp, end = 116.dp, top = 96.dp, bottom = 160.dp)) {
                val left = cfg.sections.filter { it in WIDE }
                val right = cfg.sections.filter { it !in WIDE }
                Column(Modifier.width(840.dp), verticalArrangement = Arrangement.spacedBy(40.dp)) {
                    Greeting(greeting, now, big = false)
                    for (sec in left) Section(sec, d, s, cards = false)
                }
                Box(Modifier.width(96.dp))
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    for (sec in right) Section(sec, d, s, cards = true)
                }
            }
            BriefLayout.Single -> Column(
                Modifier.fillMaxSize().fadeBottom(180.dp).verticalScroll(scroll).padding(top = 96.dp, bottom = 160.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.width(1200.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                    Greeting(greeting, now, big = false)
                    for (sec in cfg.sections) Section(sec, d, s, cards = true)
                }
            }
            BriefLayout.Minimal -> Minimal(greeting, d, s, now)
        }
        val hint = when {
            cfg.closeWith == BriefClose.Back -> "Press Back to close"
            else -> "Press any button to close"
        }
        Txt(hint, T.Label, Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp), C.Dust)
    }
}

/** Sections that fill the left column in the two-column layout; the rest are cards on the right. */
private val WIDE = setOf(BriefSection.Weather, BriefSection.Hourly, BriefSection.Headlines)

@Composable
private fun Greeting(text: String?, now: LocalDateTime, big: Boolean) {
    Column {
        if (text != null) Txt(text, T.Headline, size = if (big) 96 else 72, maxLines = 2)
        Txt(formatDate(now), T.Body, Modifier.padding(top = 8.dp), C.Stone, size = if (big) 40 else 30)
    }
}

/** Large and calm: the greeting, the time, the weather and the first thing on the calendar. */
@Composable
private fun Minimal(greeting: String?, d: BriefData, s: Settings, now: LocalDateTime) {
    val clock by rememberNow()
    Column(Modifier.fillMaxSize().padding(top = 150.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (greeting != null) Txt(greeting, T.Headline, size = 80, align = TextAlign.Center)
        Txt(formatTime(clock, s.use24h), T.Display, Modifier.padding(top = 16.dp), size = 200)
        val w = d.weather
        if (w != null) {
            val t = w.attrs.optDouble("temperature", Double.NaN)
            Row(Modifier.padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Icon(weatherIcon(w.state), 64.dp, C.Stone)
                Txt((if (t.isNaN()) "" else "${Math.round(t)}°  ") + weatherLabel(w.state), T.Title, size = 48, color = C.Stone)
            }
        }
        d.events.firstOrNull()?.let { e ->
            val time = if (e.allDay || e.start == null) "Today" else e.start.atZoneSameInstant(ZoneId.systemDefault()).format(timeFormat(s.use24h))
            Txt("$time · ${e.summary}", T.Body, Modifier.padding(top = 32.dp), C.Stone, size = 36)
        }
    }
}

private fun timeFormat(use24h: Boolean) = DateTimeFormatter.ofPattern(if (use24h) "H:mm" else "h:mm a", Locale.getDefault())

@Composable
private fun Section(sec: BriefSection, d: BriefData, s: Settings, cards: Boolean) {
    val fmt = timeFormat(s.use24h)
    when (sec) {
        BriefSection.Weather -> {
            val w = d.weather
            if (w == null) {
                if (!s.haConfigured) Txt("Connect Home Assistant in Settings for weather, your calendar and the house.", T.Body, color = C.Stone, maxLines = 2)
            } else {
                val t = w.attrs.optDouble("temperature", Double.NaN)
                val body: @Composable ColumnScope.() -> Unit = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        Icon(weatherIcon(w.state), if (cards) 72.dp else 112.dp)
                        if (!t.isNaN()) Txt("${Math.round(t)}°", T.Display, size = if (cards) 96 else 160)
                        Column {
                            Txt(weatherLabel(w.state), T.CardTitle, size = 34)
                            val today = d.today
                            if (today?.temperature != null) {
                                val lo = today.low?.let { " · Low ${Math.round(it)}°" }.orEmpty()
                                Txt("High ${Math.round(today.temperature)}°$lo", T.Body, color = C.Stone)
                            }
                        }
                    }
                }
                if (cards) BriefCard(weatherIcon(w.state), "Weather", content = body) else Column(content = body)
            }
        }
        BriefSection.Hourly -> if (d.hourly.isNotEmpty()) {
            val body: @Composable ColumnScope.() -> Unit = {
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
            if (cards) BriefCard(Icons.Clock, "Next hours", content = body) else Column(content = body)
        }
        BriefSection.Headlines -> if (d.headlines.isNotEmpty()) {
            if (cards) {
                BriefCard(Icons.Newspaper, "Headlines") {
                    for (h in d.headlines) Txt(h, T.Body, maxLines = 2)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SectionTitle("Headlines")
                    for (h in d.headlines) Txt(h, T.Body, maxLines = 2)
                }
            }
        }
        BriefSection.Calendar -> if (d.events.isNotEmpty() || d.hasCalendars) {
            BriefCard(Icons.Calendar, "Today", if (d.events.isEmpty()) null else "${d.events.size} event${if (d.events.size == 1) "" else "s"}") {
                if (d.events.isEmpty()) Txt("Nothing on the calendar", T.Body, color = C.Stone)
                for (e in d.events) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val time = when {
                            e.allDay || e.start == null -> "All day"
                            else -> e.start.atZoneSameInstant(ZoneId.systemDefault()).format(fmt)
                        }
                        Txt(time, T.Numeric, Modifier.width(170.dp))
                        Txt(e.summary, T.Body, Modifier.weight(1f))
                    }
                }
            }
        }
        BriefSection.Commute -> {
            val c = d.commute
            if (c != null) {
                BriefCard(Icons.Car, "${c.minutes} ${c.unit} to ${c.label}") {
                    if (c.route.isNotBlank()) Txt("Via ${c.route}", T.Body, color = C.Stone)
                }
            }
        }
        BriefSection.Home -> if (d.status.isNotEmpty()) {
            BriefCard(Icons.House, "Home") {
                for (st in d.status) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Txt(st.name, T.Body, Modifier.weight(1f))
                        Txt(st.text, T.Label, color = if (st.ok) C.Sage else C.Clay)
                    }
                }
            }
        }
        BriefSection.Teams -> if (d.games.isNotEmpty()) {
            val hide = s.sports.noSpoilers
            BriefCard(Icons.Trophy, "Your teams today") {
                for (g in d.games.take(3)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (g.state == GameState.Live || g.state == GameState.Final) {
                            Txt(if (hide) g.matchup else g.scoreLine, T.Numeric, Modifier.weight(1f))
                            val status = if (hide) (if (g.state == GameState.Live) "Live" else "Final")
                            else g.detail.ifBlank { if (g.state == GameState.Live) "Live" else "Final" }
                            Txt(status, T.Label, color = if (g.state == GameState.Live) C.Clay else C.Stone)
                        } else {
                            Txt(g.matchup, T.Body, Modifier.weight(1f))
                            Txt(
                                g.start.atZone(ZoneId.systemDefault()).format(fmt) + (g.broadcasts.firstOrNull()?.let { " · $it" } ?: ""),
                                T.Label, color = C.Stone,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BriefCard(icon: ImageVector, title: String, trailing: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(L.panelShape).background(C.Surface).padding(horizontal = 36.dp, vertical = 28.dp),
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

/** What the TV says when the briefing is read aloud: the same things, in sentences. */
private fun speech(s: Settings, d: BriefData, now: LocalDateTime): String {
    val cfg: BriefConfig = s.brief
    val parts = mutableListOf<String>()
    greetingLine(cfg.greeting, cfg.message, s.profileName, now.hour, stop = ".")?.let { parts += it }
    parts += "It's " + formatTime(now, s.use24h, withAmPm = true) + "."
    val want = cfg.sections.toSet()
    val w = d.weather
    if (BriefSection.Weather in want && w != null) {
        val t = w.attrs.optDouble("temperature", Double.NaN)
        val high = d.today?.temperature?.let { ", with a high of ${Math.round(it)}" }.orEmpty()
        parts += (if (t.isNaN()) "" else "It's ${Math.round(t)} degrees and ") + weatherLabel(w.state).lowercase() + "$high."
    }
    if (BriefSection.Calendar in want && d.events.isNotEmpty()) {
        val first = d.events.first()
        val time = if (first.allDay || first.start == null) "" else " at " + first.start.atZoneSameInstant(ZoneId.systemDefault()).format(timeFormat(s.use24h))
        parts += "You have ${d.events.size} thing${if (d.events.size == 1) "" else "s"} today. First, ${first.summary}$time."
    }
    d.commute?.let { parts += "It's ${it.minutes} ${it.unit} to ${it.label}." }
    if (BriefSection.Headlines in want && d.headlines.isNotEmpty()) parts += "In the news: " + d.headlines.take(2).joinToString(". ") + "."
    return parts.joinToString(" ")
}

/** Speaks [text] once with the TV's text-to-speech voice, and stops when the briefing closes. */
@Composable
private fun ReadAloud(text: String) {
    val ctx = LocalContext.current
    DisposableEffect(text) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(ctx.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "haven-briefing")
            }
        }
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }
}
