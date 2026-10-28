package app.haven.launcher.ui

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.haven.launcher.Haven
import app.haven.launcher.Nav
import app.haven.launcher.Screen
import app.haven.launcher.data.HeroKind
import app.haven.launcher.data.HomeRow
import app.haven.launcher.data.HomeSection
import app.haven.launcher.data.Layouts
import app.haven.launcher.data.NavStyle
import app.haven.launcher.data.Settings
import app.haven.launcher.data.TvApp
import app.haven.launcher.media.MediaItem
import app.haven.launcher.media.MediaList
import app.haven.launcher.sports.Game
import app.haven.launcher.sports.GameState
import app.haven.launcher.sports.ScoreAlerts
import app.haven.launcher.sports.Watch
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Where focus is on the home screen. [lastKey] is restored when you come back from another screen; it only
 * records focus that followed a key press, because Android hands the window's first focus to whatever control
 * comes first (the room chip), and Haven's own placing of focus isn't the user's choice either.
 */
internal object HomeFocus {
    var lastKey: String? = null
    var current: String? = null

    /** The layout [lastKey] belongs to: another layout starts from its own first focus. */
    var layout: String? = null
}

/** The home screen's side margin (DESIGN.md, "Layout"). */
internal const val SIDE = 116

/** Something opened over the home screen. */
sealed interface HomeDialog {
    class AppMenu(val app: TvApp) : HomeDialog
    class Rename(val app: TvApp) : HomeDialog
    class Picture(val app: TvApp) : HomeDialog
    class TileColour(val app: TvApp) : HomeDialog
    class Title(val item: MediaItem) : HomeDialog
    class Camera(val id: String) : HomeDialog
    class Device(val id: String) : HomeDialog
    class WatchGame(val game: Game, val ways: List<Watch.Way>) : HomeDialog
    class Note(val title: String, val body: String) : HomeDialog
    class Confirm(val title: String, val body: String, val yes: String, val onYes: () -> Unit) : HomeDialog
    class Pin(val then: () -> Unit) : HomeDialog
}

/** After the grown-up PIN, the lock stays open for a few minutes, so one visit to Settings asks once. */
object GrownUp {
    private var until by mutableLongStateOf(0L)

    fun isOpen(s: Settings): Boolean = !s.kids.locked || SystemClock.elapsedRealtime() < until

    fun unlock() {
        until = SystemClock.elapsedRealtime() + 5 * 60_000L
    }
}

/**
 * What every part of the home screen needs: the profile, its apps, focus helpers and the way to show dialogs.
 * [start] and [end] are the page's margins and [width] its content width, which differ beside the side menu.
 */
class HomeCtx(
    val nav: Nav,
    val s: Settings,
    val visible: List<TvApp>,
    val favorites: List<TvApp>,
    val recent: List<TvApp>,
    val req: (String) -> FocusRequester,
    val track: (String) -> (Boolean) -> Unit,
    val show: (HomeDialog) -> Unit,
    val start: Dp,
    val end: Dp,
    val width: Dp,
    val lists: MutableMap<String, LazyListState>,
) {
    /** Runs [action] now, or after the grown-up PIN when this profile has one. */
    fun guard(action: () -> Unit) {
        if (GrownUp.isOpen(s)) action() else show(HomeDialog.Pin(action))
    }

    fun inside(start: Dp, end: Dp, width: Dp) = HomeCtx(nav, s, visible, favorites, recent, req, track, show, start, end, width, lists)
}

/** What a page needs loaded from the internet: today's games, sports news, movie and TV lists. */
private sealed interface Need {
    data object Boards : Need
    data object News : Need
    data class Media(val list: MediaList) : Need
}

private fun needsOf(hero: HeroKind, rows: List<HomeRow>): Set<Need> = buildSet {
    for (r in rows) {
        when (r) {
            HomeRow.LiveScores -> add(Need.Boards)
            HomeRow.SportsNews -> add(Need.News)
            HomeRow.TrendingMovies -> add(Need.Media(MediaList.TrendingMovies))
            HomeRow.InTheaters -> add(Need.Media(MediaList.InTheaters))
            HomeRow.ComingSoon -> add(Need.Media(MediaList.ComingSoon))
            HomeRow.TopMovies -> add(Need.Media(MediaList.TopMovies))
            HomeRow.TrendingShows -> add(Need.Media(MediaList.TrendingShows))
            HomeRow.YourShows -> add(Need.Media(MediaList.YourShows))
            HomeRow.Tonight -> add(Need.Media(MediaList.Tonight))
            HomeRow.NewStreaming -> add(Need.Media(MediaList.NewStreaming))
            else -> Unit
        }
    }
    when (hero) {
        HeroKind.Movie -> add(Need.Media(MediaList.TrendingMovies))
        HeroKind.Show -> {
            add(Need.Media(MediaList.YourShows))
            add(Need.Media(MediaList.Tonight))
        }
        else -> Unit
    }
}

@Composable
fun HomeScreen(nav: Nav) {
    val settings by Haven.settings.state.collectAsState()
    val apps by Haven.apps.apps.collectAsState()
    val lastUsed by Haven.apps.lastUsed.collectAsState()
    Haven.ha.entities.collectAsState().value // recompose on Home Assistant updates
    Haven.ha.areas.collectAsState().value
    val games by Haven.sports.games.collectAsState()
    val boards by Haven.sports.boards.collectAsState()
    val news by Haven.sports.news.collectAsState()
    val media by Haven.media.lists.collectAsState()
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val requesters = remember { HashMap<String, FocusRequester>() }
    fun req(key: String) = requesters.getOrPut(key) { FocusRequester() }
    // A key pressed since Haven last placed focus: from then on, where focus goes is the user's doing.
    val keyPressed = remember { BooleanArray(1) }
    fun track(key: String): (Boolean) -> Unit = { f ->
        if (f) {
            HomeFocus.current = key
            if (keyPressed[0]) HomeFocus.lastKey = key
        } else if (HomeFocus.current == key) {
            HomeFocus.current = null
        }
    }
    DisposableEffect(Unit) { onDispose { HomeFocus.current = null } }

    val visible = apps.filter { it.pkg !in settings.hiddenApps }
    val byPkg = apps.associateBy { it.pkg }
    val favorites = settings.favorites.mapNotNull { byPkg[it] }
    val used = lastUsed[Haven.settings.activeId].orEmpty()
    val recent = visible.filter { it.pkg in used }.sortedByDescending { used[it.pkg] }.take(12)
    var dialog by remember { mutableStateOf<HomeDialog?>(null) }
    val listStates = remember { HashMap<String, LazyListState>() }
    val look = settings.look
    var section by remember(Haven.settings.activeId) { mutableStateOf(HomeSection.Home) }
    if (look.nav == NavStyle.Rows || section !in look.sections) section = HomeSection.Home
    val (hero, rows) = if (look.nav == NavStyle.Rows) look.hero to look.rows else Layouts.page(section, look)
    // The parts the home page is made of: another layout, or a change to this one, starts at the top.
    val layoutKey = "${look.nav} ${look.hero} ${look.rows.joinToString(",")}"
    val scroll = remember(layoutKey) { ScrollState(0) }

    val h = HomeCtx(nav, settings, visible, favorites, recent, ::req, ::track, { dialog = it }, SIDE.dp, SIDE.dp, (1920 - 2 * SIDE).dp, listStates)

    // First run: pin a few apps so the top row isn't empty.
    LaunchedEffect(apps.isNotEmpty(), Haven.settings.activeId) {
        if (apps.isNotEmpty() && !Haven.settings.value.favoritesInitialized) {
            Haven.settings.update { it.copy(favorites = defaultFavorites(apps), favoritesInitialized = true) }
        }
    }

    // Poll Home Assistant while the home screen is on screen; start art mode after the idle time.
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (BriefingTrigger.due(appCtx)) nav.go(Screen.Briefing)
            launch {
                while (true) {
                    Haven.ha.refresh()
                    delay(30_000)
                }
            }
            launch { Haven.updater.checkDaily() }
            launch {
                while (true) {
                    val g = runCatching { Haven.sports.refresh(maxAgeMs = 60_000L) }.getOrDefault(emptyList())
                    ScoreAlerts.sync(appCtx, g)
                    delay(if (g.any { it.state == GameState.Live }) 60_000L else 10 * 60_000L)
                }
            }
            while (true) {
                delay(10_000)
                val idleMin = Haven.settings.value.artIdleMin
                if (idleMin > 0 && SystemClock.uptimeMillis() - Haven.lastInput > idleMin * 60_000L) {
                    nav.go(Screen.Art)
                }
            }
        }
    }

    // What this page shows from the internet, kept fresh while it's on screen (each source keeps its own cache).
    val needs = needsOf(hero, rows)
    LaunchedEffect(lifecycle, needs) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                for (n in needs) {
                    launch {
                        runCatching {
                            when (n) {
                                Need.Boards -> Haven.sports.refreshBoards()
                                Need.News -> Haven.sports.refreshNews()
                                is Need.Media -> Haven.media.ensure(n.list)
                            }
                        }
                    }
                }
                delay(60_000)
            }
        }
    }

    // Where focus starts: the hero's main button when the hero is the point of the page, else the first thing in
    // the first row with something in it, else the top of the page. Whatever the user had focused before leaving
    // comes first, on the same layout. "Home pressed while already home" starts over from the top.
    val data = RowData(games, boards, news, media)
    val firstFocus = listOfNotNull(
        heroStart(hero),
        rows.firstNotNullOfOrNull { r -> "${r.name}:0".takeIf { rowCount(h, r, data) > 0 } },
        heroKey(hero),
        when (look.nav) {
            NavStyle.Rows -> "top:settings"
            NavStyle.Menu -> "menu:${section.name}"
            NavStyle.Tabs -> "tab:${section.name}"
        },
    ).distinct()
    LaunchedEffect(nav.homeResets, apps.isNotEmpty(), firstFocus, Haven.settings.activeId, layoutKey) {
        if (nav.homeResets != lastResetSeen) {
            lastResetSeen = nav.homeResets
            HomeFocus.lastKey = null
            section = HomeSection.Home
            for (state in listStates.values) runCatching { state.scrollToItem(0) }
            scroll.scrollTo(0)
        }
        if (HomeFocus.layout != layoutKey) {
            HomeFocus.layout = layoutKey
            HomeFocus.lastKey = null
        }
        keyPressed[0] = false
        val wanted = listOfNotNull(HomeFocus.lastKey) + firstFocus
        // Keep the most wanted one that's on the page focused while the page settles: the window may hand its
        // first focus to the top bar after this runs, a row that's still loading takes focus once it arrives (the
        // next one on the page has it till then), and a placeholder that gets replaced takes focus with it.
        // Asking the focused one again changes nothing. A key press ends it.
        for (attempt in 0 until 150) {
            if (attempt > 0 && keyPressed[0]) break
            for (k in wanted) if (runCatching { req(k).requestFocus() }.isSuccess) break
            delay(if (attempt == 0) 30L else 100L)
        }
    }

    Box(
        Modifier.fillMaxSize().onPreviewKeyEvent {
            if (it.type == KeyEventType.KeyDown) keyPressed[0] = true
            false
        },
    ) {
        when (look.nav) {
            NavStyle.Rows -> HomePage(h, hero, rows, scroll, PageChrome.TopBar)
            NavStyle.Menu -> MenuFrame(h, section, { section = it }, { focusFirst(firstFocus.dropLast(1), ::req) }) { inner ->
                key(section, layoutKey) { HomePage(inner, hero, rows, rememberScrollState(), PageChrome.Clock) }
            }
            NavStyle.Tabs -> TabsFrame(h, section, { section = it }) {
                key(section, layoutKey) { HomePage(h, hero, rows, rememberScrollState(), PageChrome.None) }
            }
        }
    }

    dialog?.let { d -> HomeDialogs(h, d, replace = { dialog = it }, close = { dialog = null }) }
}

private var lastResetSeen = 0

/** Focuses the first of [keys] that's on the page. */
private fun focusFirst(keys: List<String>, req: (String) -> FocusRequester) {
    for (k in keys) if (runCatching { req(k).requestFocus() }.isSuccess) return
}

/** What sits above the top of a page: the full top bar, just the clock (beside the side menu), or nothing (under tabs). */
enum class PageChrome { TopBar, Clock, None }

/**
 * One page of the home screen: its top and its rows, scrolling as one. A Spotlight page scrolls its own way (the
 * photo whole or gone, [spotlightScroll]); the rows inside keep the usual scrolling.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomePage(h: HomeCtx, hero: HeroKind, rows: List<HomeRow>, scroll: ScrollState, chrome: PageChrome) {
    val rowScroll = LocalBringIntoViewSpec.current
    val pageScroll = if (hero == HeroKind.Spotlight) spotlightScroll(scroll) else rowScroll
    CompositionLocalProvider(LocalBringIntoViewSpec provides pageScroll) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(bottom = 64.dp)) {
            CompositionLocalProvider(LocalBringIntoViewSpec provides rowScroll) {
                if (hero == HeroKind.Spotlight) {
                    val first = rows.firstOrNull()
                    val firstRow: (@Composable () -> Unit)? = first?.let { r -> @Composable { HomeRowView(h, r, onPhoto = true, page = rows) } }
                    SpotlightHero(h, chrome, firstRow)
                    // The rows below the photo get a whole screen, so moving down to them can slide the photo away.
                    if (rows.size > 1) {
                        Column(Modifier.heightIn(min = 1080.dp)) {
                            for (row in rows.drop(1)) HomeRowView(h, row, page = rows)
                        }
                    }
                } else {
                    when (chrome) {
                        PageChrome.TopBar -> Box(Modifier.padding(top = 48.dp)) { TopBar(h) }
                        PageChrome.Clock -> ClockLine(h)
                        PageChrome.None -> Spacer(height = 16.dp)
                    }
                    Hero(h, hero)
                    for (row in rows) HomeRowView(h, row, page = rows)
                }
            }
        }
    }
}

/** Opens a page's section: some sections are pages, some do something. */
fun openSection(h: HomeCtx, ctx: Context, s: HomeSection, show: (HomeSection) -> Unit) {
    when (s) {
        HomeSection.LiveTv -> Haven.apps.provider()?.let { Haven.apps.launch(it) }
            ?: android.widget.Toast.makeText(ctx, "No live TV app on this TV", android.widget.Toast.LENGTH_SHORT).show()
        HomeSection.Search -> openSearch(ctx)
        HomeSection.Settings -> h.guard { h.nav.go(Screen.Settings) }
        else -> show(s)
    }
}

/** The TV's own search (Google TV and Android TV both answer this), or the voice search when that's all there is. */
fun openSearch(ctx: Context) {
    val tries = listOf(
        Intent("android.search.action.GLOBAL_SEARCH"),
        Intent(Intent.ACTION_SEARCH_LONG_PRESS),
        Intent(Intent.ACTION_WEB_SEARCH),
    )
    for (i in tries) {
        val ok = runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (ok) return
    }
    android.widget.Toast.makeText(ctx, "This TV has no search to open", android.widget.Toast.LENGTH_SHORT).show()
}

fun sectionIcon(s: HomeSection): ImageVector = when (s) {
    HomeSection.Home -> Icons.House
    HomeSection.LiveTv -> Icons.Tv
    HomeSection.Sports -> Icons.Trophy
    HomeSection.Movies -> Icons.Film
    HomeSection.Shows -> Icons.Clapperboard
    HomeSection.Music -> Icons.Music
    HomeSection.SmartHome -> Icons.Lightbulb
    HomeSection.Kids -> Icons.Smile
    HomeSection.Apps -> Icons.LayoutGrid
    HomeSection.Search -> Icons.Search
    HomeSection.Settings -> Icons.Settings
}

/** First run: DIRECTV (live TV on the DIRECTV box) first, then the first few apps with banners. */
private fun defaultFavorites(apps: List<TvApp>): List<String> {
    val provider = listOfNotNull(Haven.apps.provider())
    val rest = apps.filter { it !in provider && it.banner != null }
    return (provider + rest).take(5).map { it.pkg }
}

fun openSystem(ctx: Context, action: String) {
    runCatching { ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .recoverCatching { ctx.startActivity(Intent(AndroidSettings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

fun weatherLabel(state: String): String = when (state) {
    "sunny", "clear-night" -> "Clear"
    "partlycloudy" -> "Partly cloudy"
    "cloudy" -> "Cloudy"
    "fog" -> "Fog"
    "rainy" -> "Rain"
    "pouring" -> "Heavy rain"
    "lightning", "lightning-rainy" -> "Storms"
    "snowy" -> "Snow"
    "snowy-rainy" -> "Sleet"
    "hail" -> "Hail"
    "windy", "windy-variant" -> "Windy"
    else -> state.replace('-', ' ').replaceFirstChar { it.uppercase() }
}

fun weatherIcon(state: String): ImageVector = when (state) {
    "sunny" -> Icons.Sun
    "clear-night" -> Icons.Moon
    "partlycloudy" -> Icons.CloudSun
    "cloudy" -> Icons.Cloud
    "fog" -> Icons.CloudFog
    "rainy" -> Icons.CloudDrizzle
    "pouring" -> Icons.CloudRain
    "lightning", "lightning-rainy" -> Icons.CloudLightning
    "snowy", "snowy-rainy", "hail" -> Icons.CloudSnow
    "windy", "windy-variant" -> Icons.Wind
    else -> Icons.Cloud
}
