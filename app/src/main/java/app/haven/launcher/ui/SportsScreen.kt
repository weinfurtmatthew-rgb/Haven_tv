package app.haven.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.haven.launcher.Haven
import app.haven.launcher.Nav
import app.haven.launcher.Screen
import app.haven.launcher.sports.Game
import app.haven.launcher.sports.GameState
import app.haven.launcher.sports.League
import app.haven.launcher.sports.ScoreAlerts
import app.haven.launcher.sports.Team
import app.haven.launcher.sports.Watch
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val LiveRed = Color(0xFFFF5A5F)

/** Followed teams' games on the left (OK opens the game), and any league's games today on the right. */
@Composable
fun SportsScreen(nav: Nav) {
    val ctx = LocalContext.current
    val settings by Haven.settings.state.collectAsState()
    val games by Haven.sports.games.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val first = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    val followed = settings.sports.teams
    var league by remember {
        mutableStateOf(followed.firstNotNullOfOrNull { League.byId(it.league) } ?: League.NFL)
    }

    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val g = Haven.sports.refresh()
                ScoreAlerts.sync(ctx.applicationContext, g)
                delay(if (g.any { it.state == GameState.Live }) 30_000L else 5 * 60_000L)
            }
        }
    }
    LaunchedEffect(games.isNotEmpty()) {
        delay(80)
        runCatching { first.requestFocus() }.recoverCatching { backFocus.requestFocus() }
    }
    val board by produceState(emptyList<Game>(), league) {
        while (true) {
            value = Haven.sports.provider.scoreboard(league, LocalDate.now())
            delay(if (value.any { it.state == GameState.Live }) 30_000L else 5 * 60_000L)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 96.dp, vertical = 56.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
        ScreenHeader("Sports", if (followed.isEmpty()) null else followed.joinToString(", ") { it.abbr }, onBack = { nav.go(Screen.Home) }, backFocus = backFocus)
        if (followed.isEmpty()) {
            EmptyState(
                "Pick your teams",
                "Open Settings and scan the code with your phone to choose teams from the NFL, MLB, NHL, NBA, Premier League, La Liga and Champions League.",
                "Open settings", first,
            ) { nav.go(Screen.Settings) }
            return@Column
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            Column(Modifier.width(1000.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                SectionTitle("Your teams")
                if (games.isEmpty()) {
                    Txt("Looking for games…", 24, weight = FontWeight.Medium, color = C.Muted)
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 6.dp)) {
                    items(games, key = { it.id }) { g ->
                        GameCard(g, if (g == games.first()) first else null) { Watch.open(ctx, g) }
                    }
                }
            }
            Panel(Modifier.weight(1f).fillMaxHeight()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(League.values().toList(), key = { it.id }) { l ->
                        PillButton(l.label, { league = l }, selected = l == league, height = 48.dp)
                    }
                }
                Txt("${league.label} today", 26, weight = FontWeight.ExtraBold)
                if (board.isEmpty()) Txt("No games today", 22, weight = FontWeight.Medium, color = C.Muted)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(board, key = { it.id }) { g -> CompactGame(g) { Watch.open(ctx, g) } }
                }
            }
        }
    }
}

@Composable
private fun TeamLogo(team: Team, size: Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (team.logo.isNotBlank()) {
            AsyncImage(model = team.logo, contentDescription = null, modifier = Modifier.size(size))
        } else {
            Txt(team.abbr, (size.value / 3).toInt(), weight = FontWeight.Bold)
        }
    }
}

fun gameStatus(g: Game, use24h: Boolean): String = when (g.state) {
    GameState.Live -> g.detail.ifBlank { "Live" }
    GameState.Final -> g.detail.ifBlank { "Final" }
    GameState.Postponed -> "Postponed"
    GameState.Scheduled -> {
        val t = g.start.atZone(ZoneId.systemDefault())
        val day = when (t.toLocalDate()) {
            LocalDate.now() -> "Today"
            LocalDate.now().plusDays(1) -> "Tomorrow"
            else -> t.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
        }
        day + " " + t.format(DateTimeFormatter.ofPattern(if (use24h) "H:mm" else "h:mm a", Locale.getDefault()))
    }
}

@Composable
private fun GameCard(g: Game, focus: FocusRequester?, onClick: () -> Unit) {
    val use24 = Haven.settings.value.use24h
    Focusable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(150.dp),
        color = C.Card,
        focusedColor = C.CardHi,
        focusedScale = 1.02f,
        focusRequester = focus,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            TeamLogo(g.away.team, 72.dp)
            Column(Modifier.width(150.dp)) {
                Txt(g.away.team.abbr, 30, weight = FontWeight.ExtraBold)
                if (g.state != GameState.Scheduled) Txt(g.away.score ?: "", 40, weight = FontWeight.Bold)
            }
            Txt("at", 24, weight = FontWeight.Medium, color = C.Muted)
            TeamLogo(g.home.team, 72.dp)
            Column(Modifier.width(150.dp)) {
                Txt(g.home.team.abbr, 30, weight = FontWeight.ExtraBold)
                if (g.state != GameState.Scheduled) Txt(g.home.score ?: "", 40, weight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (g.state == GameState.Live) Box(Modifier.size(14.dp).clip(CircleShape).background(LiveRed))
                    Txt(gameStatus(g, use24), 24, weight = FontWeight.Bold, color = if (g.state == GameState.Live) LiveRed else C.Text)
                }
                Txt(
                    listOfNotNull(g.league.label, g.broadcasts.firstOrNull()).joinToString(" · "),
                    20, weight = FontWeight.Medium, color = C.Muted,
                )
            }
        }
    }
}

@Composable
private fun CompactGame(g: Game, onClick: () -> Unit) {
    val use24 = Haven.settings.value.use24h
    Focusable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(76.dp),
        color = C.CardHi,
        focusedColor = C.Line,
        focusedScale = 1.02f,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TeamLogo(g.away.team, 36.dp)
            Txt(if (g.state == GameState.Scheduled) g.away.team.abbr else "${g.away.team.abbr} ${g.away.score ?: ""}", 22, Modifier.width(110.dp), FontWeight.Bold)
            TeamLogo(g.home.team, 36.dp)
            Txt(if (g.state == GameState.Scheduled) g.home.team.abbr else "${g.home.team.abbr} ${g.home.score ?: ""}", 22, Modifier.width(110.dp), FontWeight.Bold)
            Txt(gameStatus(g, use24), 20, Modifier.weight(1f), FontWeight.Medium, if (g.state == GameState.Live) LiveRed else C.Muted)
        }
    }
}
