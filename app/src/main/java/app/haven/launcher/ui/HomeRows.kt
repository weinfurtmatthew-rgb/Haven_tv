package app.haven.launcher.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import app.haven.launcher.Haven
import app.haven.launcher.Screen
import app.haven.launcher.brief.Briefing
import app.haven.launcher.data.AppKind
import app.haven.launcher.data.HomeRow
import app.haven.launcher.data.TvApp
import app.haven.launcher.ha.CalEvent
import app.haven.launcher.ha.Forecast
import app.haven.launcher.ha.HaEntity
import app.haven.launcher.ha.HaStatus
import app.haven.launcher.media.MediaItem
import app.haven.launcher.media.MediaList
import app.haven.launcher.sports.Game
import app.haven.launcher.sports.GameState
import app.haven.launcher.sports.NewsItem
import app.haven.launcher.sports.Watch
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the rows show from the internet, collected once by the home screen. */
class RowData(
    val games: List<Game>,
    val boards: List<Game>,
    val news: List<NewsItem>,
    val media: Map<MediaList, List<MediaItem>>,
)

private val KIND_ROWS = mapOf(
    HomeRow.TvApps to AppKind.Tv,
    HomeRow.MovieApps to AppKind.Movies,
    HomeRow.SportsApps to AppKind.Sports,
    HomeRow.MusicApps to AppKind.Music,
    HomeRow.Games to AppKind.Games,
)

private val MEDIA_ROWS = mapOf(
    HomeRow.TrendingMovies to MediaList.TrendingMovies,
    HomeRow.InTheaters to MediaList.InTheaters,
    HomeRow.ComingSoon to MediaList.ComingSoon,
    HomeRow.TopMovies to MediaList.TopMovies,
    HomeRow.TrendingShows to MediaList.TrendingShows,
    HomeRow.YourShows to MediaList.YourShows,
    HomeRow.Tonight to MediaList.Tonight,
    HomeRow.NewStreaming to MediaList.NewStreaming,
)

/** The apps of one kind; the Kids' apps row shows the apps a grown-up picked, when they picked any. */
fun appsOf(h: HomeCtx, row: HomeRow): List<TvApp> = when (row) {
    HomeRow.Favorites -> h.favorites
    HomeRow.Recent -> h.recent
    HomeRow.Apps -> h.visible
    HomeRow.KidsApps -> {
        val picked = h.s.kids.apps
        if (picked.isNotEmpty()) picked.mapNotNull { p -> h.visible.firstOrNull { it.pkg == p } }
        else h.visible.filter { AppKind.Kids in it.kinds }
    }
    else -> KIND_ROWS[row]?.let { k -> h.visible.filter { k in it.kinds } }.orEmpty()
}

/** How many things a row offers to focus (an empty-state card counts as one), for where focus starts. */
fun rowCount(h: HomeCtx, row: HomeRow, d: RowData): Int {
    val s = h.s
    val ha = Haven.ha
    return when (row) {
        HomeRow.Favorites, HomeRow.Recent, HomeRow.Apps, HomeRow.KidsApps, HomeRow.TvApps, HomeRow.MovieApps,
        HomeRow.SportsApps, HomeRow.MusicApps, HomeRow.Games -> appsOf(h, row).size
        HomeRow.Room -> 3
        HomeRow.YourTeams -> maxOf(1, d.games.size)
        HomeRow.LiveScores -> d.boards.size
        HomeRow.SportsNews -> d.news.size
        HomeRow.Scenes -> ha.scenesFor(s).size
        HomeRow.Lights -> ha.lightsFor(s).size
        HomeRow.Climate -> ha.devices(s, "climate").size
        HomeRow.Cameras -> ha.devices(s, "camera").size
        HomeRow.Locks -> ha.devices(s, "lock", "cover").size
        HomeRow.Speakers -> ha.devices(s, "media_player").size
        HomeRow.Sensors -> ha.sensorsFor(s).size
        HomeRow.Automations -> ha.devices(s, "automation").size
        HomeRow.NowPlaying -> if (leadSpeaker(h) != null) 1 else 0
        HomeRow.Weather, HomeRow.Calendar, HomeRow.Headlines -> 0
        else -> MEDIA_ROWS[row]?.let { maxOf(1, d.media[it].orEmpty().size) } ?: 0
    }
}

/**
 * One row of the home screen. [onPhoto] is the first row sitting on Spotlight's photo: no title, light names.
 * [page] is every row on the page, for rows that leave out what another row already shows.
 */
@Composable
fun HomeRowView(h: HomeCtx, row: HomeRow, onPhoto: Boolean = false, page: List<HomeRow> = emptyList()) {
    if (!onPhoto && row != HomeRow.Room) RowTitle(h, if (row == HomeRow.LiveScores && HomeRow.YourTeams in page) "More games today" else row.label)
    when (row) {
        HomeRow.Favorites -> if (h.favorites.isEmpty()) {
            Panel(Modifier.padding(start = h.start, top = 24.dp, bottom = 32.dp).size(L.fav(400), L.fav(225)), padding = 32.dp) {
                Txt("Hold OK on an app below to pin it here.", T.Body, color = C.Stone, maxLines = 3)
            }
        } else {
            AppsRow(h, row, h.favorites, onPhoto)
        }
        HomeRow.Recent -> if (h.recent.isEmpty()) {
            Txt("Apps you open from Haven show up here.", T.Body, Modifier.padding(start = h.start, top = 16.dp, bottom = 32.dp), C.Stone)
        } else {
            AppsRow(h, row, h.recent, onPhoto)
        }
        HomeRow.Room -> {
            RowTitle(h, h.s.roomName.ifBlank { "This room" })
            RoomRow(h)
        }
        HomeRow.Apps, HomeRow.KidsApps, HomeRow.TvApps, HomeRow.MovieApps, HomeRow.SportsApps, HomeRow.MusicApps, HomeRow.Games -> {
            val apps = appsOf(h, row)
            if (apps.isEmpty()) {
                Txt(
                    if (row == HomeRow.KidsApps) "No kids' apps yet. A grown-up can pick them in Settings, under Profiles."
                    else "None of these on this TV yet.",
                    T.Body, Modifier.padding(start = h.start, top = 16.dp, bottom = 32.dp), C.Stone, maxLines = 2,
                )
            } else {
                AppsRow(h, row, apps, onPhoto)
            }
        }
        HomeRow.YourTeams -> TeamsRow(h, row)
        HomeRow.LiveScores -> BoardsRow(h, row, others = HomeRow.YourTeams in page)
        HomeRow.SportsNews -> NewsRow(h, row)
        HomeRow.Scenes -> ScenesRow(h, row)
        HomeRow.Lights -> LightsRow(h, row)
        HomeRow.Climate -> ClimateRow(h, row)
        HomeRow.Cameras -> CamerasRow(h, row)
        HomeRow.Locks -> LocksRow(h, row)
        HomeRow.Speakers -> SpeakersRow(h, row)
        HomeRow.Sensors -> SensorsRow(h, row)
        HomeRow.Automations -> AutomationsRow(h, row)
        HomeRow.NowPlaying -> NowPlayingRow(h, row)
        HomeRow.Weather -> WeatherRow(h, row)
        HomeRow.Calendar -> CalendarRow(h, row)
        HomeRow.Headlines -> HeadlinesRow(h, row)
        else -> MEDIA_ROWS[row]?.let { MediaRow(h, row, it) }
    }
}

// ---- shared pieces -----------------------------------------------------------------------------------

/** A horizontal row of cards; each card gets its focus key ("Row:index"). */
@Composable
fun <T> CardRow(h: HomeCtx, row: HomeRow, items: List<T>, key: (T) -> String, gap: Dp = L.gap(28), card: @Composable (T, String) -> Unit) {
    val state = rememberLazyListState()
    SideEffect { h.lists[row.name] = state }
    LazyRow(
        state = state,
        contentPadding = PaddingValues(start = h.start, end = h.end, top = max(16.dp, L.gap(24)), bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        itemsIndexed(items, key = { _, t -> key(t) }) { i, t -> card(t, "${row.name}:$i") }
    }
}

/** A card that explains what a row needs or why it's empty, and where to fix it. */
@Composable
fun InfoCard(h: HomeCtx, row: HomeRow, icon: ImageVector, title: String, body: String, onClick: () -> Unit = {}) {
    val k = "${row.name}:0"
    Box(Modifier.padding(start = h.start, end = h.end, top = max(16.dp, L.gap(24)), bottom = 16.dp)) {
        Focusable(
            onClick = onClick,
            modifier = Modifier.width(880.dp),
            color = C.Surface,
            focusedScale = 1.03f,
            focusRequester = h.req(k),
            onFocus = h.track(k),
            glass = Look.glass,
        ) { focused ->
            Row(Modifier.padding(horizontal = 32.dp, vertical = 28.dp), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, 48.dp, inkIcon(focused))
                Column {
                    Txt(title, T.CardTitle, color = ink(focused), maxLines = 2)
                    Txt(body, T.Label, color = inkMuted(focused), maxLines = 3)
                }
            }
        }
    }
}

/** A tile for the smart home and sports rows: solid or glass, focus key and click from the row. */
@Composable
fun Tile(
    h: HomeCtx,
    k: String,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.(Boolean) -> Unit,
) {
    Focusable(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier.size(width, height),
        color = C.Surface,
        focusedScale = 1.05f,
        focusRequester = h.req(k),
        onFocus = h.track(k),
        glass = Look.glass,
        content = content,
    )
}

/** Opens Settings at a group, through the grown-up lock. */
fun openSettings(h: HomeCtx, group: String) = h.guard {
    h.nav.settingsStart = group
    h.nav.go(Screen.Settings)
}

@Composable
private fun NeedsHa(h: HomeCtx, row: HomeRow): Boolean {
    val status by Haven.ha.status.collectAsState()
    if (status == HaStatus.NotConfigured) {
        InfoCard(h, row, Icons.House, "Connect Home Assistant", "Scan the code in Settings with your phone to add your Home Assistant") {
            openSettings(h, "SmartHome")
        }
        return true
    }
    return false
}

// ---- sports ----------------------------------------------------------------------------------------------

@Composable
private fun TeamsRow(h: HomeCtx, row: HomeRow) {
    val games by Haven.sports.games.collectAsState()
    if (h.s.sports.teams.isEmpty()) {
        InfoCard(h, row, Icons.Trophy, "Follow your teams", "Pick them on your phone: Settings, then scan the code") { openSettings(h, "Sports") }
        return
    }
    if (games.isEmpty()) {
        InfoCard(h, row, Icons.Trophy, "No games coming up", "Your teams' next games show here") { h.nav.go(Screen.Sports) }
        return
    }
    CardRow(h, row, games, { it.id }) { g, k -> GameTile(h, g, k) }
}

/** Today's games in the followed teams' leagues; beside a "Your teams" row, the others (theirs are already there). */
@Composable
private fun BoardsRow(h: HomeCtx, row: HomeRow, others: Boolean) {
    val boards by Haven.sports.boards.collectAsState()
    val games by Haven.sports.games.collectAsState()
    val mine = if (others) games.map { it.id }.toSet() else emptySet()
    val shown = boards.filter { it.id !in mine }
    if (shown.isEmpty()) {
        Txt(if (others) "No other games today in your leagues." else "No games today in your leagues.", T.Body, Modifier.padding(start = h.start, top = 16.dp, bottom = 24.dp), C.Stone)
        return
    }
    CardRow(h, row, shown, { it.id }) { g, k -> GameTile(h, g, k) }
}

/** One game: the score (or the matchup, without spoilers), its state and where it's on. Hold OK shows a hidden score. */
@Composable
fun GameTile(h: HomeCtx, g: Game, k: String) {
    val ctx = LocalContext.current
    val cfg = h.s.sports
    var reveal by remember { mutableStateOf(false) }
    val show = !cfg.noSpoilers || reveal
    val scored = g.state != GameState.Scheduled && show
    Tile(
        h, k, 460.dp, 156.dp,
        onClick = { Watch.open(ctx, g)?.let { ways -> h.show(HomeDialog.WatchGame(g, ways)) } },
        onLongClick = { reveal = !reveal },
    ) { focused ->
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (cfg.teamColors) {
                Box(Modifier.width(10.dp).fillMaxHeight().background(teamColor(g.first.team) ?: C.Line))
            }
            Column(Modifier.padding(horizontal = 28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Txt(if (scored) g.scoreLine else g.matchup, T.Title, color = ink(focused), size = 40)
                val live = g.state == GameState.Live
                Txt(
                    listOf(safeStatus(g, h.s.use24h, show), g.broadcasts.firstOrNull().orEmpty()).filter { it.isNotBlank() }.joinToString(" · "),
                    T.Label, color = if (live && !lit(focused)) C.Clay else inkMuted(focused),
                )
            }
        }
    }
}

@Composable
private fun NewsRow(h: HomeCtx, row: HomeRow) {
    val news by Haven.sports.news.collectAsState()
    if (news.isEmpty()) {
        Txt("Headlines from your teams' leagues show here.", T.Body, Modifier.padding(start = h.start, top = 16.dp, bottom = 24.dp), C.Stone)
        return
    }
    CardRow(h, row, news, { it.headline }) { n, k ->
        Tile(h, k, 640.dp, 180.dp, onClick = { h.show(HomeDialog.Note(n.headline, n.description.ifBlank { n.headline })) }) { focused ->
            Row(Modifier.fillMaxSize()) {
                if (n.image != null) {
                    AsyncImage(n.image, null, Modifier.size(180.dp), contentScale = ContentScale.Crop)
                }
                Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Txt(n.headline, T.CardTitle, color = ink(focused), maxLines = 3)
                    Txt(listOfNotNull(n.league.label, n.published?.let { ago(it) }).joinToString(" · "), T.Label, color = inkMuted(focused))
                }
            }
        }
    }
}

/** "5 min ago", "3 h ago", "2 days ago". */
fun ago(t: Instant): String {
    val m = Duration.between(t, Instant.now()).toMinutes().coerceAtLeast(0)
    return when {
        m < 60 -> "$m min ago"
        m < 48 * 60 -> "${m / 60} h ago"
        else -> "${m / 1440} days ago"
    }
}

// ---- movies and shows -------------------------------------------------------------------------------------

@Composable
private fun MediaRow(h: HomeCtx, row: HomeRow, list: MediaList) {
    val lists by Haven.media.lists.collectAsState()
    val status by Haven.media.status.collectAsState()
    val items = lists[list]
    val tmdb = list in setOf(MediaList.TrendingMovies, MediaList.InTheaters, MediaList.ComingSoon, MediaList.TopMovies, MediaList.TrendingShows)
    when {
        items == null && tmdb && h.s.background.tmdbKey.isBlank() -> InfoCard(
            h, row, Icons.Film, "Add your free TMDB key",
            "Movie lists come from TMDB. Paste your key on the phone page: Settings, then scan the code",
        ) { openSettings(h, "HomeScreen") }
        items == null -> Txt(
            if (tmdb && status != null) status!! else "Loading…", T.Body,
            Modifier.padding(start = h.start, top = 16.dp, bottom = 24.dp), C.Stone,
        )
        items.isEmpty() && list == MediaList.YourShows -> InfoCard(
            h, row, Icons.Clapperboard, "Follow your shows",
            "Press OK on any show and choose Follow, or search for shows on the phone page",
        )
        items.isEmpty() -> Txt("Nothing here right now.", T.Body, Modifier.padding(start = h.start, top = 16.dp, bottom = 24.dp), C.Stone)
        else -> CardRow(h, row, items, { it.key }, gap = L.gap(24)) { item, k -> PosterCard(h, item, k) }
    }
}

/** A poster with its title and line ("Tonight 9:00 PM · NBC") under it, and a badge on it ("NEW TONIGHT"). */
@Composable
fun PosterCard(h: HomeCtx, item: MediaItem, k: String) {
    val w = L.app(220)
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(w)) {
        Focusable(
            onClick = { h.show(HomeDialog.Title(item)) },
            modifier = Modifier.size(w, w * 1.5f),
            color = C.Surface,
            style = FocusStyle.Picture,
            focusedScale = 1.06f,
            focusRequester = h.req(k),
            onFocus = {
                focused = it
                h.track(k)(it)
            },
        ) {
            if (item.poster != null) {
                AsyncImage(item.poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.BottomStart) {
                    Txt(item.title, T.CardTitle, maxLines = 4)
                }
            }
            if (item.badge.isNotBlank()) {
                Box(Modifier.align(Alignment.TopStart).padding(12.dp).clip(L.pill(40.dp)).background(C.Lamp).padding(horizontal = 14.dp, vertical = 4.dp)) {
                    Txt(item.badge, T.Label, color = C.Hearth)
                }
            }
        }
        Txt(item.title, T.Label, Modifier.padding(top = 16.dp), if (focused) C.Linen else C.Stone)
        val line = item.note.ifBlank { listOfNotNull(item.year.ifBlank { null }, item.rating?.let { "★ %.1f".format(it) }).joinToString(" · ") }
        if (line.isNotBlank()) Txt(line, T.Label, color = C.Dust)
    }
}

// ---- smart home ------------------------------------------------------------------------------------------

@Composable
private fun ScenesRow(h: HomeCtx, row: HomeRow) {
    Haven.ha.entities.collectAsState().value
    if (NeedsHa(h, row)) return
    val scope = rememberCoroutineScope()
    var active by remember { mutableStateOf<String?>(null) }
    val scenes = Haven.ha.scenesFor(h.s)
    if (scenes.isEmpty()) {
        Txt("No scenes in Home Assistant.", T.Body, Modifier.padding(start = h.start, top = 16.dp, bottom = 24.dp), C.Stone)
        return
    }
    CardRow(h, row, scenes, { it.id }, gap = 16.dp) { scene, k ->
        Chip(
            scene.name,
            {
                active = scene.id
                scope.launch {
                    Haven.ha.activate(scene)
                    delay(800)
                    Haven.ha.refresh()
                }
            },
            selected = active == scene.id,
            icon = Icons.Sparkles,
            height = 80.dp,
            focusRequester = h.req(k),
            onFocus = h.track(k),
            glass = Look.glass,
        )
    }
}

@Composable
private fun LightsRow(h: HomeCtx, row: HomeRow) {
    Haven.ha.entities.collectAsState().value
    if (NeedsHa(h, row)) return
    val scope = rememberCoroutineScope()
    val lights = Haven.ha.lightsFor(h.s)
    if (lights.isEmpty()) {
        Txt("No lights here. Pick the room's area in Settings, or show the whole house.", T.Body, Modifier.padding(start = h.start, top = 16.dp, bottom = 24.dp), C.Stone, maxLines = 2)
        return
    }
    CardRow(h, row, lights, { it.id }) { e, k ->
        val pct = if (e.isOn) e.brightnessPct ?: 100 else 0
        Tile(
            h, k, 340.dp, 156.dp,
            onClick = { scope.launch { Haven.ha.setLight(e, !e.isOn, if (!e.isOn) e.brightnessPct else null) } },
            onLongClick = { h.show(HomeDialog.Device(e.id)) },
        ) { focused ->
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Lightbulb, 32.dp, if (e.isOn && !lit(focused)) C.Lamp else inkIcon(focused))
                    Txt(e.name, T.CardTitle, color = ink(focused))
                }
                Txt(if (!e.isOn) "Off" else if (e.dimmable) "On · $pct%" else "On", T.Label, color = if (e.isOn && !lit(focused)) C.Lamp else inkMuted(focused))
                if (e.dimmable) LevelBar(pct / 100f, Modifier.fillMaxWidth(), focused) else Box(Modifier.size(1.dp, 12.dp))
            }
        }
    }
}

/** "68°" from a thermostat's or sensor's temperature, in its own unit. */
fun degrees(v: Double?): String = if (v == null || v.isNaN()) "–" else "${Math.round(v)}°"

@Composable
private fun ClimateRow(h: HomeCtx, row: HomeRow) {
    Haven.ha.entities.collectAsState().value
    if (NeedsHa(h, row)) return
    val list = Haven.ha.devices(h.s, "climate")
    if (list.isEmpty()) {
        Txt("No thermostats in Home Assistant.", T.Body, Modifier.padding(start = h.start, top = 16.dp, bottom = 24.dp), C.Stone)
        return
    }
    CardRow(h, row, list, { it.id }) { e, k ->
        Tile(h, k, 440.dp, 230.dp, onClick = { h.show(HomeDialog.Device(e.id)) }) { focused ->
            Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Txt(e.name, T.CardTitle, color = ink(focused))
                Txt(degrees(e.attrs.optDouble("current_temperature")), T.Headline, color = ink(focused), size = 88)
                Txt(climateLine(e), T.Label, color = inkMuted(focused))
            }
        }
    }
}

/** "Heating to 70°", "Cooling to 72°", "Off". */
fun climateLine(e: HaEntity): String {
    val target = e.attrs.optDouble("temperature")
    val action = e.attrs.optString("hvac_action")
    return when {
        e.state == "off" -> "Off"
        action == "heating" -> "Heating to ${degrees(target)}"
        action == "cooling" -> "Cooling to ${degrees(target)}"
        !target.isNaN() -> "Set to ${degrees(target)}"
        else -> e.state.replaceFirstChar { it.uppercase() }
    }
}

@Composable
private fun CamerasRow(h: HomeCtx, row: HomeRow) {
    Haven.ha.entities.collectAsState().value
    if (NeedsHa(h, row)) return
    val cams = Haven.ha.devices(h.s, "camera")
    if (cams.isEmpty()) {
        Txt("No cameras in Home Assistant.", T.Body, Modifier.padding(start = h.start, top = 16.dp, bottom = 24.dp), C.Stone)
        return
    }
    // A fresh picture every ten seconds while the row is on screen.
    val tick by produceState(0) {
        while (true) {
            delay(10_000)
            value++
        }
    }
    CardRow(h, row, cams, { it.id }) { e, k ->
        Tile(h, k, 480.dp, 270.dp, onClick = { h.show(HomeDialog.Camera(e.id)) }) { _ ->
            Haven.ha.picture(e, h.s)?.let { url ->
                AsyncImage(cameraUrl(url, tick), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Box(
                Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)), startY = 140f)),
            )
            Txt(e.name, T.Label, Modifier.align(Alignment.BottomStart).padding(20.dp), Color.White)
        }
    }
}

/** A camera picture's address that changes every [tick], so the image loader asks for a new one. */
fun cameraUrl(url: String, tick: Int): String = if (url.startsWith("http")) url + (if (url.contains('?')) "&" else "?") + "t=$tick" else url

@Composable
private fun LocksRow(h: HomeCtx, row: HomeRow) {
    Haven.ha.entities.collectAsState().value
    if (NeedsHa(h, row)) return
    val scope = rememberCoroutineScope()
    val list = Haven.ha.devices(h.s, "lock", "cover")
    if (list.isEmpty()) {
        Txt("No locks, garage doors or blinds in Home Assistant.", T.Body, Modifier.padding(start = h.start, top = 16.dp, bottom = 24.dp), C.Stone)
        return
    }
    CardRow(h, row, list, { it.id }) { e, k ->
        val (icon, words, alert) = lockLook(e)
        Tile(h, k, 380.dp, 150.dp, onClick = { lockAction(h, e, scope) }) { focused ->
            Row(Modifier.fillMaxSize().padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Icon(icon, 40.dp, if (alert && !lit(focused)) C.Clay else inkIcon(focused))
                Column {
                    Txt(e.name, T.CardTitle, color = ink(focused))
                    Txt(words, T.Label, color = if (alert && !lit(focused)) C.Clay else inkMuted(focused))
                }
            }
        }
    }
}

/** A lock's or door's icon, its state in words, and whether it's open (worth a look). */
fun lockLook(e: HaEntity): Triple<ImageVector, String, Boolean> = when (e.domain) {
    "lock" -> when (e.state) {
        "locked" -> Triple(Icons.Lock, "Locked", false)
        "unlocked" -> Triple(Icons.LockOpen, "Unlocked", true)
        "jammed" -> Triple(Icons.CircleAlert, "Jammed", true)
        else -> Triple(Icons.Lock, e.state.replaceFirstChar { it.uppercase() }, false)
    }
    else -> {
        val garage = e.attrs.optString("device_class") in setOf("garage", "gate")
        val isOpen = e.state == "open" || e.state == "opening"
        Triple(
            if (garage) Icons.Warehouse else if (e.attrs.optString("device_class") == "door") (if (isOpen) Icons.DoorOpen else Icons.DoorClosed) else Icons.Blinds,
            e.state.replaceFirstChar { it.uppercase() },
            isOpen && garage,
        )
    }
}

/** Locking and closing happen at once; unlocking and opening ask first. */
fun lockAction(h: HomeCtx, e: HaEntity, scope: kotlinx.coroutines.CoroutineScope) {
    when {
        e.domain == "lock" && e.state == "locked" -> h.show(
            HomeDialog.Confirm("Unlock ${e.name}?", "Anyone at the door can come in.", "Unlock") { scope.launch { Haven.ha.setLocked(e, false) } },
        )
        e.domain == "lock" -> scope.launch { Haven.ha.setLocked(e, true) }
        e.state == "closed" -> h.show(
            HomeDialog.Confirm("Open ${e.name}?", "Make sure nothing is in the way.", "Open") { scope.launch { Haven.ha.setOpen(e, true) } },
        )
        else -> scope.launch { Haven.ha.setOpen(e, false) }
    }
}

/** The speaker that leads: one playing in this room, then anywhere, then one paused. */
fun leadSpeaker(h: HomeCtx): HaEntity? {
    val room = Haven.ha.roomSpeakers(h.s)
    val all = Haven.ha.devices(h.s, "media_player")
    return room.firstOrNull { it.state == "playing" } ?: all.firstOrNull { it.state == "playing" }
        ?: room.firstOrNull { it.state == "paused" } ?: all.firstOrNull { it.state == "paused" }
}

@Composable
private fun SpeakersRow(h: HomeCtx, row: HomeRow) {
    Haven.ha.entities.collectAsState().value
    if (NeedsHa(h, row)) return
    val scope = rememberCoroutineScope()
    val list = Haven.ha.devices(h.s, "media_player")
    if (list.isEmpty()) {
        Txt("No speakers in Home Assistant.", T.Body, Modifier.padding(start = h.start, top = 16.dp, bottom = 24.dp), C.Stone)
        return
    }
    CardRow(h, row, list, { it.id }) { e, k ->
        val sub = when (e.state) {
            "playing" -> e.attrs.optString("media_title").ifBlank { "Playing" }
            "paused" -> "Paused"
            "idle", "on" -> "On"
            "standby", "off" -> "Off"
            else -> e.state.replaceFirstChar { it.uppercase() }
        }
        Tile(
            h, k, 400.dp, 156.dp,
            onClick = {
                scope.launch {
                    if (e.state == "playing" || e.state == "paused") Haven.ha.media(e, "media_play_pause") else Haven.ha.setPower(e, !e.isOn)
                }
            },
            onLongClick = { h.show(HomeDialog.Device(e.id)) },
        ) { focused ->
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Speaker, 32.dp, if (e.state == "playing" && !lit(focused)) C.Lamp else inkIcon(focused))
                    Txt(e.name, T.CardTitle, color = ink(focused))
                }
                Txt(sub, T.Label, color = if (e.state == "playing" && !lit(focused)) C.Lamp else inkMuted(focused))
                if (e.canSetVolume) LevelBar(if (e.isOn) e.volume ?: 0f else 0f, Modifier.fillMaxWidth(), focused) else Box(Modifier.size(1.dp, 12.dp))
            }
        }
    }
}

@Composable
private fun SensorsRow(h: HomeCtx, row: HomeRow) {
    Haven.ha.entities.collectAsState().value
    if (NeedsHa(h, row)) return
    val list = Haven.ha.sensorsFor(h.s)
    if (list.isEmpty()) {
        Txt("No temperature, humidity, door or motion sensors here.", T.Body, Modifier.padding(start = h.start, top = 16.dp, bottom = 24.dp), C.Stone)
        return
    }
    CardRow(h, row, list, { it.id }) { e, k ->
        val (icon, value, alert) = sensorLook(e)
        Tile(h, k, 320.dp, 150.dp, onClick = {}) { focused ->
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(icon, 28.dp, if (alert && !lit(focused)) C.Clay else inkIcon(focused))
                    Txt(e.name, T.Label, color = inkMuted(focused))
                }
                Txt(value, T.Title, color = if (alert && !lit(focused)) C.Clay else ink(focused))
            }
        }
    }
}

/** A sensor's icon, its reading in words, and whether it wants attention (a door open, motion). */
fun sensorLook(e: HaEntity): Triple<ImageVector, String, Boolean> {
    val cls = e.attrs.optString("device_class")
    val unit = e.attrs.optString("unit_of_measurement")
    val on = e.state == "on"
    return when (cls) {
        "temperature" -> Triple(Icons.Thermometer, e.state.toDoubleOrNull()?.let { "${Math.round(it)}${unit.ifBlank { "°" }}" } ?: e.state, false)
        "humidity" -> Triple(Icons.Droplets, e.state.toDoubleOrNull()?.let { "${Math.round(it)}%" } ?: e.state, false)
        "motion", "occupancy" -> Triple(Icons.Activity, if (on) "Motion" else "Clear", on)
        "moisture" -> Triple(Icons.Droplets, if (on) "Wet" else "Dry", on)
        "smoke" -> Triple(Icons.CircleAlert, if (on) "Smoke" else "Clear", on)
        else -> Triple(if (on) Icons.DoorOpen else Icons.DoorClosed, if (on) "Open" else "Closed", on)
    }
}

@Composable
private fun AutomationsRow(h: HomeCtx, row: HomeRow) {
    Haven.ha.entities.collectAsState().value
    if (NeedsHa(h, row)) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val list = Haven.ha.devices(h.s, "automation")
    if (list.isEmpty()) {
        Txt("No automations in Home Assistant.", T.Body, Modifier.padding(start = h.start, top = 16.dp, bottom = 24.dp), C.Stone)
        return
    }
    CardRow(h, row, list, { it.id }) { e, k ->
        Tile(
            h, k, 440.dp, 140.dp,
            onClick = { scope.launch { Haven.ha.setAutomation(e, !e.isOn) } },
            onLongClick = {
                scope.launch { Haven.ha.runAutomation(e) }
                Toast.makeText(ctx, "Running ${e.name}", Toast.LENGTH_SHORT).show()
            },
        ) { focused ->
            Row(Modifier.fillMaxSize().padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Icon(Icons.Zap, 36.dp, if (e.isOn && !lit(focused)) C.Lamp else inkIcon(focused))
                Column(Modifier.weight(1f)) {
                    Txt(e.name, T.CardTitle, color = ink(focused), maxLines = 2)
                    Txt(if (e.isOn) "On · hold OK to run now" else "Off", T.Label, color = inkMuted(focused))
                }
                Toggle(e.isOn, focused)
            }
        }
    }
}

@Composable
private fun NowPlayingRow(h: HomeCtx, row: HomeRow) {
    Haven.ha.entities.collectAsState().value
    if (NeedsHa(h, row)) return
    val scope = rememberCoroutineScope()
    val e = leadSpeaker(h)
    if (e == null) {
        Txt("Nothing's playing.", T.Body, Modifier.padding(start = h.start, top = 16.dp, bottom = 24.dp), C.Stone)
        return
    }
    CardRow(h, row, listOf(e), { it.id }) { sp, k ->
        Tile(h, k, 960.dp, 220.dp, onClick = { scope.launch { Haven.ha.media(sp, "media_play_pause") } }, onLongClick = { h.show(HomeDialog.Device(sp.id)) }) { focused ->
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(220.dp).background(C.Raised)) {
                    Haven.ha.picture(sp, h.s)?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
                Column(Modifier.padding(horizontal = 32.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Txt(sp.attrs.optString("media_title").ifBlank { "Playing" }, T.Title, color = ink(focused), maxLines = 2)
                    Txt(sp.attrs.optString("media_artist").ifBlank { sp.attrs.optString("app_name") }, T.Body, color = inkMuted(focused))
                    Txt((if (sp.state == "playing") "Playing on " else "Paused on ") + sp.name, T.Label, color = if (!lit(focused)) C.Lamp else inkMuted(focused))
                }
                Icon(if (sp.state == "playing") Icons.Pause else Icons.Play, 56.dp, inkIcon(focused), Modifier.padding(end = 40.dp))
            }
        }
    }
}

// ---- your day ----------------------------------------------------------------------------------------------

@Composable
private fun WeatherRow(h: HomeCtx, row: HomeRow) {
    val weather = Haven.ha.weather(h.s)
    val hours by produceState<List<Forecast>?>(null, weather?.id) {
        value = weather?.let { runCatching { Haven.ha.forecast(it.id, "hourly") }.getOrNull() }.orEmpty().take(12)
    }
    val list = hours
    if (weather == null || list.isNullOrEmpty()) {
        Txt(if (weather == null) "Weather comes from Home Assistant." else "Loading…", T.Body, Modifier.padding(start = h.start, top = 16.dp, bottom = 24.dp), C.Stone)
        return
    }
    val fmt = DateTimeFormatter.ofPattern(if (h.s.use24h) "H:mm" else "h a", Locale.getDefault())
    CardRow(h, row, list, { it.time.toString() }, gap = 16.dp) { f, k ->
        Tile(h, k, 170.dp, 210.dp, onClick = {}) { focused ->
            Column(Modifier.fillMaxSize().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                Txt(f.time?.atZoneSameInstant(ZoneId.systemDefault())?.format(fmt).orEmpty(), T.Label, color = inkMuted(focused))
                Icon(weatherIcon(f.condition), 48.dp, inkIcon(focused))
                Txt(degrees(f.temperature), T.Title, color = ink(focused))
            }
        }
    }
}

@Composable
private fun CalendarRow(h: HomeCtx, row: HomeRow) {
    val events by produceState<List<CalEvent>?>(null, h.s.brief.calendars) {
        val ids = h.s.brief.calendars.ifEmpty { runCatching { Haven.ha.calendars().map { it.first }.toSet() }.getOrDefault(emptySet()) }
        val day = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val start = day.atStartOfDay(zone).toOffsetDateTime().toString()
        val end = day.plusDays(1).atStartOfDay(zone).toOffsetDateTime().toString()
        value = ids.flatMap { runCatching { Haven.ha.events(it, start, end) }.getOrDefault(emptyList()) }.sortedBy { it.start }
    }
    val list = events
    if (list.isNullOrEmpty()) {
        Txt(if (list == null) "Loading…" else "Nothing on the calendar today.", T.Body, Modifier.padding(start = h.start, top = 16.dp, bottom = 24.dp), C.Stone)
        return
    }
    val fmt = DateTimeFormatter.ofPattern(if (h.s.use24h) "H:mm" else "h:mm a", Locale.getDefault())
    CardRow(h, row, list, { "${it.summary}:${it.start}" }) { ev, k ->
        Tile(h, k, 460.dp, 150.dp, onClick = {}) { focused ->
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Txt(if (ev.allDay) "All day" else ev.start?.atZoneSameInstant(ZoneId.systemDefault())?.format(fmt).orEmpty(), T.Label, color = if (!lit(focused)) C.Lamp else inkMuted(focused))
                Txt(ev.summary, T.CardTitle, color = ink(focused), maxLines = 2)
            }
        }
    }
}

@Composable
private fun HeadlinesRow(h: HomeCtx, row: HomeRow) {
    val feed = h.s.brief.headlinesFeed
    val titles by produceState<List<String>?>(null, feed) {
        value = runCatching { Briefing.headlines(feed, 8) }.getOrDefault(emptyList())
    }
    val list = titles
    if (list.isNullOrEmpty()) {
        Txt(if (list == null) "Loading…" else "No headlines right now.", T.Body, Modifier.padding(start = h.start, top = 16.dp, bottom = 24.dp), C.Stone)
        return
    }
    CardRow(h, row, list, { it }) { t, k ->
        Tile(h, k, 600.dp, 170.dp, onClick = { h.show(HomeDialog.Note("Headline", t)) }) { focused ->
            Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Icon(Icons.Newspaper, 32.dp, inkIcon(focused))
                Txt(t, T.CardTitle, color = ink(focused), maxLines = 3)
            }
        }
    }
}
