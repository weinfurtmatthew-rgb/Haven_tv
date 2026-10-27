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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
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
import app.haven.launcher.sports.Side
import app.haven.launcher.sports.Team
import app.haven.launcher.sports.Watch
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
            value = runCatching { Haven.sports.board(league) }.getOrDefault(emptyList())
            delay(if (value.any { it.state == GameState.Live }) 30_000L else 5 * 60_000L)
        }
    }

    Column(Modifier.fillMaxSize().padding(start = 116.dp, end = 116.dp, top = 48.dp), verticalArrangement = Arrangement.spacedBy(40.dp)) {
        ScreenHeader("Sports", if (followed.isEmpty()) null else followed.joinToString(", ") { it.abbr }, onBack = { nav.go(Screen.Home) }, backFocus = backFocus)
        if (followed.isEmpty()) {
            EmptyState(
                "Pick your teams",
                "Open Settings and scan the code with your phone to follow teams in the NFL, MLB, NHL, NBA, Premier League, La Liga and Champions League.",
                "Open settings", first,
            ) { nav.go(Screen.Settings) }
            return@Column
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(96.dp)) {
            Column(Modifier.width(960.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                SectionTitle("Your teams")
                if (games.isEmpty()) Txt("Looking for games…", T.Body, color = C.Stone)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        Modifier.offset(x = (-16).dp).fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 64.dp),
                    ) {
                        items(games, key = { it.id }) { g ->
                            GameCard(g, if (g == games.first()) first else null) { Watch.open(ctx, g) }
                        }
                    }
                    BottomFade(48.dp)
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                SectionTitle("Today")
                LazyRow(
                    Modifier.offset(x = (-16).dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    items(League.values().toList(), key = { it.id }) { l ->
                        Chip(l.label, { league = l }, selected = l == league, height = 56.dp)
                    }
                }
                if (board.isEmpty()) Txt("No ${league.label} games today", T.Body, color = C.Stone)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        Modifier.offset(x = (-16).dp).fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 64.dp),
                    ) {
                        items(board, key = { it.id }) { g -> CompactGame(g) { Watch.open(ctx, g) } }
                    }
                    BottomFade(48.dp)
                }
            }
        }
    }
}

/** A team's logo, in ESPN's dark-background version when there is one. */
@Composable
private fun TeamLogo(team: Team, size: Dp) {
    var model by remember(team.logo) { mutableStateOf(team.darkLogo) }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (team.logo.isNotBlank()) {
            AsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier.size(size),
                onError = { if (model != team.logo) model = team.logo },
            )
        } else {
            Txt(team.abbr, T.Label)
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
private fun TeamScore(side: Side, showScore: Boolean, focused: Boolean) {
    Row(Modifier.width(300.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        TeamLogo(side.team, 64.dp)
        Txt(side.team.abbr, T.CardTitle, Modifier.weight(1f), ink(focused), size = 32)
        if (showScore) Txt(side.score ?: "", T.Numeric, color = ink(focused), size = 48)
    }
}

@Composable
private fun GameCard(g: Game, focus: FocusRequester?, onClick: () -> Unit) {
    val use24 = Haven.settings.value.use24h
    val scored = g.state != GameState.Scheduled
    Focusable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(144.dp),
        color = C.Surface,
        focusedScale = 1.03f,
        focusRequester = focus,
    ) { focused ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            TeamScore(g.first, scored, focused)
            TeamScore(g.second, scored, focused)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (g.state == GameState.Live) Box(Modifier.size(12.dp).clip(CircleShape).background(if (focused) C.Ink else C.Clay))
                    Txt(gameStatus(g, use24), T.Label, color = if (g.state == GameState.Live && !focused) C.Clay else ink(focused))
                }
                Txt(listOfNotNull(g.league.label, g.broadcasts.firstOrNull()).joinToString(" · "), T.Label, color = inkMuted(focused))
            }
        }
    }
}

@Composable
private fun CompactGame(g: Game, onClick: () -> Unit) {
    val use24 = Haven.settings.value.use24h
    Focusable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(88.dp),
        color = C.Surface,
        focusedScale = 1.03f,
    ) { focused ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            for (side in listOf(g.first, g.second)) {
                TeamLogo(side.team, 40.dp)
                Txt(
                    if (g.state == GameState.Scheduled) side.team.abbr else "${side.team.abbr} ${side.score ?: ""}",
                    T.Numeric, Modifier.width(120.dp), ink(focused),
                )
            }
            Txt(
                gameStatus(g, use24), T.Label, Modifier.weight(1f), if (g.state == GameState.Live && !focused) C.Clay else inkMuted(focused),
                align = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
    }
}
