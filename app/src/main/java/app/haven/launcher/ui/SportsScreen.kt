package app.haven.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.haven.launcher.Haven
import app.haven.launcher.Nav
import app.haven.launcher.Screen
import app.haven.launcher.data.SportsConfig
import app.haven.launcher.data.SportsLayout
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

/**
 * Followed teams' games on the left (OK opens the game the profile's way), and any league's games today on the
 * right. Without spoilers, scores stay hidden until a game is held with OK.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SportsScreen(nav: Nav) {
    val ctx = LocalContext.current
    val settings by Haven.settings.state.collectAsState()
    val games by Haven.sports.games.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val first = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    val cfg = settings.sports
    val followed = cfg.teams
    var league by remember {
        mutableStateOf(followed.firstNotNullOfOrNull { League.byId(it.league) } ?: League.NFL)
    }
    val revealed = remember { mutableStateListOf<String>() }
    var asking by remember { mutableStateOf<List<Watch.Way>?>(null) }
    fun watch(g: Game) {
        asking = Watch.open(ctx, g)
    }
    fun reveal(g: Game) {
        if (g.id in revealed) revealed.remove(g.id) else revealed.add(g.id)
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
    val board by produceState(emptyList<Game>(), league, cfg.board) {
        if (!cfg.board) return@produceState
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
            val wide = !cfg.board
            Column(Modifier.then(if (wide) Modifier.weight(1f) else Modifier.width(960.dp)).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    SectionTitle("Your teams")
                    if (cfg.noSpoilers) KeyHint("hold to see the score", key = "OK")
                }
                if (games.isEmpty()) Txt("Looking for games…", T.Body, color = C.Stone)
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth().bleed(16.dp).fadeBottom(48.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 64.dp),
                ) {
                    items(games, key = { it.id }) { g ->
                        val focus = if (g == games.first()) first else null
                        val show = !cfg.noSpoilers || g.id in revealed
                        when (cfg.layout) {
                            SportsLayout.Cards -> GameCard(g, cfg, show, focus, { watch(g) }) { reveal(g) }
                            SportsLayout.Big -> BigGameCard(g, cfg, show, focus, { watch(g) }) { reveal(g) }
                            SportsLayout.Compact -> CompactGame(g, cfg, show, focus, { watch(g) }) { reveal(g) }
                        }
                    }
                }
            }
            if (cfg.board) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    SectionTitle("Today")
                    // Wrapped, not scrolled: every league is visible and none is cut mid-word.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (l in League.values()) {
                            Chip(l.label, { league = l }, selected = l == league, height = 56.dp)
                        }
                    }
                    if (board.isEmpty()) Txt("No ${league.label} games today", T.Body, color = C.Stone)
                    LazyColumn(
                        Modifier.weight(1f).fillMaxWidth().bleed(16.dp).fadeBottom(48.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 64.dp),
                    ) {
                        items(board, key = { it.id }) { g ->
                            CompactGame(g, cfg, !cfg.noSpoilers || g.id in revealed, null, { watch(g) }) { reveal(g) }
                        }
                    }
                }
            }
        }
    }

    asking?.let { ways ->
        ChoiceDialog(
            "Watch on",
            ways.mapIndexed { i, w -> Choice(i.toString(), w.label, w.message) },
            "0",
            onPick = { v ->
                asking = null
                v.toIntOrNull()?.let { ways.getOrNull(it) }?.let { Watch.go(ctx, it) }
            },
            onDismiss = { asking = null },
        )
    }
}

/** A team's logo, in ESPN's dark-background version when there is one. */
@Composable
fun TeamLogo(team: Team, size: Dp) {
    var model by remember(team.logo) { mutableStateOf(if (Look.r.palette.light) team.logo else team.darkLogo) }
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

/** The team's own colour from the feed, for the stripe beside its name. */
fun teamColor(team: Team): Color? =
    team.color.takeIf { it.length == 6 }?.toLongOrNull(16)?.let { Color(0xFF000000 or it) }

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

/** What the status says without spoilers: whether it's on or over, not how it's going. */
fun safeStatus(g: Game, use24h: Boolean, show: Boolean): String = when {
    show -> gameStatus(g, use24h)
    g.state == GameState.Live -> "Live"
    g.state == GameState.Final -> "Final"
    else -> gameStatus(g, use24h)
}

@Composable
private fun TeamScore(side: Side, cfg: SportsConfig, showScore: Boolean, focused: Boolean, logo: Dp, abbr: Int, score: Int, width: Dp) {
    Row(Modifier.width(width), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        if (cfg.teamColors) {
            val c = teamColor(side.team)
            Box(Modifier.size(8.dp, logo).clip(RoundedCornerShape(4.dp)).background(c ?: C.Line))
        }
        if (cfg.logos) TeamLogo(side.team, logo)
        Txt(side.team.abbr, T.CardTitle, Modifier.weight(1f), ink(focused), size = abbr)
        if (showScore) Txt(side.score ?: "", T.Numeric, color = ink(focused), size = score)
    }
}

@Composable
private fun StatusColumn(g: Game, show: Boolean, focused: Boolean, modifier: Modifier) {
    val use24 = Haven.settings.value.use24h
    Column(modifier, horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (g.state == GameState.Live) Box(Modifier.size(12.dp).clip(CircleShape).background(if (lit(focused)) C.Ink else C.Clay))
            Txt(safeStatus(g, use24, show), T.Label, color = if (g.state == GameState.Live && !lit(focused)) C.Clay else ink(focused))
        }
        Txt(
            listOfNotNull(g.league.label, g.broadcasts.firstOrNull()).joinToString(" · "), T.Label,
            color = inkMuted(focused), maxLines = 2, align = TextAlign.End,
        )
    }
}

@Composable
private fun GameCard(g: Game, cfg: SportsConfig, show: Boolean, focus: FocusRequester?, onClick: () -> Unit, onHold: () -> Unit) {
    val scored = g.state != GameState.Scheduled && show
    Focusable(
        onClick = onClick,
        onLongClick = onHold,
        modifier = Modifier.fillMaxWidth().height(144.dp),
        color = C.Surface,
        focusedScale = 1.03f,
        focusRequester = focus,
    ) { focused ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            TeamScore(g.first, cfg, scored, focused, 64.dp, 32, 48, 272.dp)
            TeamScore(g.second, cfg, scored, focused, 64.dp, 32, 48, 272.dp)
            StatusColumn(g, show, focused, Modifier.weight(1f))
        }
    }
}

/** One game per row, big enough to read across the room. */
@Composable
private fun BigGameCard(g: Game, cfg: SportsConfig, show: Boolean, focus: FocusRequester?, onClick: () -> Unit, onHold: () -> Unit) {
    val scored = g.state != GameState.Scheduled && show
    Focusable(
        onClick = onClick,
        onLongClick = onHold,
        modifier = Modifier.fillMaxWidth().height(232.dp),
        color = C.Surface,
        focusedScale = 1.02f,
        focusRequester = focus,
    ) { focused ->
        Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                TeamScore(g.first, cfg, scored, focused, 96.dp, 44, 72, 400.dp)
                TeamScore(g.second, cfg, scored, focused, 96.dp, 44, 72, 400.dp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val use24 = Haven.settings.value.use24h
                Txt(safeStatus(g, use24, show), T.Body, Modifier.weight(1f), if (g.state == GameState.Live && !lit(focused)) C.Clay else ink(focused))
                Txt(listOfNotNull(g.league.label, g.broadcasts.firstOrNull()).joinToString(" · "), T.Label, color = inkMuted(focused))
            }
        }
    }
}

@Composable
private fun CompactGame(g: Game, cfg: SportsConfig, show: Boolean, focus: FocusRequester?, onClick: () -> Unit, onHold: () -> Unit) {
    val use24 = Haven.settings.value.use24h
    val scored = g.state != GameState.Scheduled && show
    Focusable(
        onClick = onClick,
        onLongClick = onHold,
        modifier = Modifier.fillMaxWidth().height(88.dp),
        color = C.Surface,
        focusedScale = 1.03f,
        focusRequester = focus,
    ) { focused ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            for (side in listOf(g.first, g.second)) {
                if (cfg.teamColors) Box(Modifier.size(6.dp, 40.dp).clip(RoundedCornerShape(3.dp)).background(teamColor(side.team) ?: C.Line))
                if (cfg.logos) TeamLogo(side.team, 40.dp)
                Txt(
                    if (scored) "${side.team.abbr} ${side.score ?: ""}" else side.team.abbr,
                    T.Numeric, Modifier.width(120.dp), ink(focused),
                )
            }
            Txt(
                safeStatus(g, use24, show), T.Label, Modifier.weight(1f), if (g.state == GameState.Live && !lit(focused)) C.Clay else inkMuted(focused),
                align = TextAlign.End,
            )
        }
    }
}
