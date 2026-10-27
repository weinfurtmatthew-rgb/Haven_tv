package app.haven.launcher.ui

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings as AndroidSettings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.haven.launcher.Haven
import app.haven.launcher.Nav
import app.haven.launcher.Screen
import app.haven.launcher.bedroom.Bedroom
import app.haven.launcher.data.AppArt
import app.haven.launcher.data.AppLook
import app.haven.launcher.data.AppNames
import app.haven.launcher.data.AppStyle
import app.haven.launcher.data.ClockStyle
import app.haven.launcher.data.HomeRow
import app.haven.launcher.data.ProfileInfo
import app.haven.launcher.data.Settings
import app.haven.launcher.data.TileColor
import app.haven.launcher.data.TvApp
import app.haven.launcher.data.moved
import app.haven.launcher.ha.HaEntity
import app.haven.launcher.ha.HaStatus
import app.haven.launcher.sports.Game
import app.haven.launcher.sports.GameState
import app.haven.launcher.sports.ScoreAlerts
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Where focus is on the home screen. [lastKey] is restored when you come back from another screen; it only
 * records focus that followed a key press, because Android hands the window's first focus to whatever control
 * comes first (the room chip), and that must not be remembered as the user's choice.
 */
private object HomeFocus {
    var lastKey: String? = null
    var current: String? = null
    var userMoved = false
}

private const val SIDE = 116

@Composable
fun HomeScreen(nav: Nav) {
    val settings by Haven.settings.state.collectAsState()
    val apps by Haven.apps.apps.collectAsState()
    val lastUsed by Haven.apps.lastUsed.collectAsState()
    val haStatus by Haven.ha.status.collectAsState()
    Haven.ha.entities.collectAsState().value // recompose on Home Assistant updates
    Haven.ha.areas.collectAsState().value
    val sleepEndsAt by Bedroom.sleepEndsAt.collectAsState()
    val update by Haven.updater.available.collectAsState()
    val games by Haven.sports.games.collectAsState()
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val requesters = remember { HashMap<String, FocusRequester>() }
    fun req(key: String) = requesters.getOrPut(key) { FocusRequester() }
    fun track(key: String): (Boolean) -> Unit = { f ->
        if (f) {
            HomeFocus.current = key
            if (HomeFocus.userMoved) HomeFocus.lastKey = key
        }
    }
    val keyPressed = remember { BooleanArray(1) }
    DisposableEffect(Unit) { onDispose { HomeFocus.current = null } }

    val visibleApps = apps.filter { it.pkg !in settings.hiddenApps }
    val byPkg = apps.associateBy { it.pkg }
    val favorites = settings.favorites.mapNotNull { byPkg[it] }
    val used = lastUsed[Haven.settings.activeId].orEmpty()
    val recent = visibleApps.filter { it.pkg in used }.sortedByDescending { used[it.pkg] }.take(12)
    val rows = settings.look.rows
    var menuFor by remember { mutableStateOf<TvApp?>(null) }
    var renaming by remember { mutableStateOf<TvApp?>(null) }
    var picturing by remember { mutableStateOf<TvApp?>(null) }
    var coloring by remember { mutableStateOf<TvApp?>(null) }

    val favState = rememberLazyListState()
    val recentState = rememberLazyListState()
    val appState = rememberLazyListState()
    val roomState = rememberLazyListState()
    val scroll = rememberScrollState()

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

    // Focus the first thing in the first row (DIRECTV on the DIRECTV box), or whatever the user had focused
    // before leaving. "Home pressed while already home" starts over from the top.
    val fallback = rows.firstNotNullOfOrNull { r ->
        when (r) {
            HomeRow.Favorites -> "fav:0".takeIf { favorites.isNotEmpty() }
            HomeRow.Recent -> "recent:0".takeIf { recent.isNotEmpty() }
            HomeRow.Apps -> "app:0".takeIf { visibleApps.isNotEmpty() }
            HomeRow.Room -> null
        }
    } ?: "top:settings"
    LaunchedEffect(nav.homeResets, apps.isNotEmpty(), fallback, Haven.settings.activeId) {
        if (nav.homeResets != lastResetSeen) {
            lastResetSeen = nav.homeResets
            HomeFocus.lastKey = null
            favState.scrollToItem(0)
            recentState.scrollToItem(0)
            appState.scrollToItem(0)
            roomState.scrollToItem(0)
            scroll.scrollTo(0)
        }
        var target = HomeFocus.lastKey ?: fallback
        // Ask until it sticks: the window may give its first focus to the top bar after this runs.
        for (attempt in 0 until 30) {
            if (HomeFocus.current == target || (attempt > 0 && keyPressed[0])) break
            val ok = runCatching { req(target).requestFocus() }.isSuccess
            if (!ok && attempt >= 4) target = fallback
            delay(if (attempt == 0) 30L else 100L)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(top = 48.dp, bottom = 64.dp)
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown) {
                    HomeFocus.userMoved = true
                    keyPressed[0] = true
                }
                false
            },
    ) {
        TopBar(settings, haStatus, update != null, ::req, ::track, nav)
        for (row in rows) {
            when (row) {
                HomeRow.Favorites -> {
                    RowTitle("Favorites")
                    if (favorites.isEmpty()) {
                        Panel(Modifier.padding(start = SIDE.dp, top = 24.dp, bottom = 32.dp).size(L.fav(400), L.fav(225)), padding = 32.dp) {
                            Txt("Hold OK on an app below to pin it here.", T.Body, color = C.Stone, maxLines = 3)
                        }
                    } else {
                        AppRow(favorites, "fav", favState, settings, L.fav(400), L.fav(225), L.fav(96), L.gap(32), ::req, ::track) { menuFor = it }
                    }
                }
                HomeRow.Recent -> {
                    RowTitle("Recently used")
                    if (recent.isEmpty()) {
                        Txt(
                            "Apps you open from Haven show up here.", T.Body,
                            Modifier.padding(start = SIDE.dp, top = 16.dp, bottom = 32.dp), C.Stone,
                        )
                    } else {
                        AppRow(recent, "recent", recentState, settings, L.app(256), L.app(144), L.app(72), L.gap(24), ::req, ::track) { menuFor = it }
                    }
                }
                HomeRow.Apps -> {
                    RowTitle("Apps")
                    AppRow(visibleApps, "app", appState, settings, L.app(256), L.app(144), L.app(72), L.gap(24), ::req, ::track) { menuFor = it }
                }
                HomeRow.Room -> {
                    RowTitle(settings.roomName.ifBlank { "This room" })
                    RoomRow(settings, haStatus, sleepEndsAt, games, nav, roomState, ::req, ::track)
                }
            }
        }
    }

    menuFor?.let { app ->
        AppMenu(
            app, settings,
            onDismiss = { menuFor = null },
            onRename = { renaming = app },
            onPicture = { picturing = app },
        )
    }
    renaming?.let { app ->
        val look = settings.appLook(app.pkg)
        TextEditDialog(
            TextEdit("Rename ${app.label}", look.name.ifBlank { app.label }, app.label) { v ->
                val name = v.trim().takeIf { it != app.label }.orEmpty()
                Haven.settings.update { it.withAppLook(app.pkg, it.appLook(app.pkg).copy(name = name)) }
            },
        ) { renaming = null }
    }
    picturing?.let { app ->
        val look = settings.appLook(app.pkg)
        val choices = buildList {
            add(Choice(AppStyle.Auto.name, AppStyle.Auto.label, settings.look.appArt.label))
            if (app.banner != null) add(Choice(AppStyle.Banner.name, AppStyle.Banner.label))
            add(Choice(AppStyle.Icon.name, AppStyle.Icon.label, "Then pick the tile's colour"))
            add(
                Choice(
                    AppStyle.Picture.name, AppStyle.Picture.label,
                    if (look.image.isBlank()) "Upload one first: open Settings and scan the code with your phone" else "The picture from your phone",
                ),
            )
        }
        ChoiceDialog(
            "Picture for ${look.name.ifBlank { app.label }}", choices, look.style.name,
            onPick = { v ->
                picturing = null
                val style = AppStyle.values().firstOrNull { it.name == v } ?: AppStyle.Auto
                if (style == AppStyle.Picture && look.image.isBlank()) {
                    Toast.makeText(ctx, "Upload a picture for ${app.label} from your phone first: Settings, then scan the code.", Toast.LENGTH_LONG).show()
                } else {
                    Haven.settings.update { it.withAppLook(app.pkg, it.appLook(app.pkg).copy(style = style)) }
                    if (style == AppStyle.Icon) coloring = app
                }
            },
            onDismiss = { picturing = null },
        )
    }
    coloring?.let { app ->
        val look = settings.appLook(app.pkg)
        ChoiceDialog(
            "Tile colour for ${look.name.ifBlank { app.label }}",
            TileColor.values().map { Choice(it.argb.toString(), it.label, swatch = if (it.argb == 0L) C.Surface else Color(it.argb)) },
            look.color.toString(),
            onPick = { v ->
                coloring = null
                Haven.settings.update { it.withAppLook(app.pkg, it.appLook(app.pkg).copy(color = v.toLongOrNull() ?: 0L)) }
            },
            onDismiss = { coloring = null },
        )
    }
}

private var lastResetSeen = 0

@Composable
private fun RowTitle(title: String) {
    SectionTitle(title, Modifier.padding(start = SIDE.dp, top = L.gap(24)))
}

/** A row of apps: favorites, recently used or all apps. */
@Composable
private fun AppRow(
    apps: List<TvApp>,
    prefix: String,
    state: LazyListState,
    settings: Settings,
    width: Dp,
    height: Dp,
    iconSize: Dp,
    gap: Dp,
    req: (String) -> FocusRequester,
    track: (String) -> (Boolean) -> Unit,
    onMenu: (TvApp) -> Unit,
) {
    LazyRow(
        state = state,
        contentPadding = PaddingValues(start = SIDE.dp, end = SIDE.dp, top = max(16.dp, L.gap(24)), bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        itemsIndexed(apps, key = { _, a -> "$prefix:" + a.pkg }) { i, app ->
            AppCard(
                app = app,
                look = settings.appLook(app.pkg),
                art = settings.look.appArt,
                names = settings.look.appNames,
                width = width,
                height = height,
                iconSize = iconSize,
                onClick = { Haven.apps.launch(app) },
                onLongClick = { onMenu(app) },
                focusRequester = req("$prefix:$i"),
                onFocus = track("$prefix:$i"),
            )
        }
    }
}

@Composable
private fun TopBar(
    settings: Settings,
    haStatus: HaStatus,
    updateReady: Boolean,
    req: (String) -> FocusRequester,
    track: (String) -> (Boolean) -> Unit,
    nav: Nav,
) {
    val ctx = LocalContext.current
    val now by rememberNow()
    val use24 = settings.use24h || DateFormat.is24HourFormat(ctx)
    val profiles by Haven.settings.profiles.collectAsState()
    val me = profiles.firstOrNull { it.active }
    val look = settings.look
    Haven.ha.entities.collectAsState().value // weather
    // At least 96 px tall, and taller when a big text size makes the clock taller.
    Row(
        Modifier.fillMaxWidth().heightIn(min = 96.dp).padding(horizontal = SIDE.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (look.clock != ClockStyle.Hidden || look.showDate) {
            Row(verticalAlignment = Alignment.Bottom) {
                if (look.clock != ClockStyle.Hidden) {
                    val size = if (look.clock == ClockStyle.Small) 56 else 88
                    Txt(formatTime(now, use24), T.Clock, Modifier.alignByBaseline(), size = size)
                    if (!use24) Txt(amPm(now), T.Label, Modifier.alignByBaseline().padding(start = 14.dp), C.Stone)
                }
                if (look.showDate) {
                    val pad = if (look.clock == ClockStyle.Hidden) 0.dp else 36.dp
                    Txt(formatDate(now), if (look.clock == ClockStyle.Hidden) T.Title else T.Body, Modifier.alignByBaseline().padding(start = pad), C.Stone)
                }
            }
        }
        Box(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // Information, not a button: no surface, so it doesn't look focusable.
            if (look.showWeather) {
                Haven.ha.weather(settings)?.takeIf { it.available }?.let { WeatherNote(it, Modifier.padding(end = 16.dp)) }
            }
            if (updateReady) {
                Chip("Update ready", { nav.go(Screen.Settings) }, icon = Icons.Download, focusRequester = req("top:update"), onFocus = track("top:update"))
            }
            if (look.showRoomChip) {
                Chip(
                    settings.roomName.ifBlank { "This room" },
                    { nav.go(if (haStatus == HaStatus.NotConfigured) Screen.Settings else Screen.Lights) },
                    icon = Icons.House,
                    focusRequester = req("top:room"),
                    onFocus = track("top:room"),
                )
            }
            if (me != null) ProfileButton(me, req("top:profile"), track("top:profile")) { nav.go(Screen.Profiles) }
            IconButton(Icons.Settings, { nav.go(Screen.Settings) }, focusRequester = req("top:settings"), onFocus = track("top:settings"))
        }
    }
}

/** The active profile's picture; selecting it opens "Who's watching?". */
@Composable
private fun ProfileButton(me: ProfileInfo, focus: FocusRequester, onFocus: (Boolean) -> Unit, onClick: () -> Unit) {
    Focusable(
        onClick = onClick,
        modifier = Modifier.size(64.dp),
        shape = L.pill(64.dp),
        color = Color.Transparent,
        style = FocusStyle.Picture,
        focusedScale = 1.08f,
        focusRequester = focus,
        onFocus = onFocus,
    ) {
        Avatar(me.name, ACCENTS.getValue(me.avatar).bright, 64.dp)
    }
}

@Composable
private fun WeatherNote(w: HaEntity, modifier: Modifier = Modifier) {
    val temp = w.attrs.optDouble("temperature", Double.NaN)
    val unit = w.attrs.optString("temperature_unit", "°").let { if (it.startsWith("°")) "°" else it }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(weatherIcon(w.state), 32.dp, C.Stone)
        val t = if (temp.isNaN()) "" else "${Math.round(temp)}$unit "
        Txt(t + weatherLabel(w.state), T.Label, color = C.Stone)
    }
}

/**
 * An app as a picture: its TV banner, its icon on a tile, or the profile's own picture for it. The name shows
 * under the card while it has focus (or always, or never, as the profile chose).
 */
@Composable
private fun AppCard(
    app: TvApp,
    look: AppLook,
    art: AppArt,
    names: AppNames,
    width: Dp,
    height: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    focusRequester: FocusRequester,
    onFocus: (Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val labelAlpha by animateFloatAsState(if (focused || names == AppNames.Always) 1f else 0f, motion(), label = "label")
    val style = when (look.style) {
        AppStyle.Auto -> if (art == AppArt.Banner && app.banner != null) AppStyle.Banner else AppStyle.Icon
        AppStyle.Banner -> if (app.banner != null) AppStyle.Banner else AppStyle.Icon
        AppStyle.Picture -> if (look.image.isNotBlank()) AppStyle.Picture else AppStyle.Icon
        AppStyle.Icon -> AppStyle.Icon
    }
    val tile = if (style == AppStyle.Icon && look.color != 0L) Color(look.color) else C.Surface
    Column(Modifier.width(width)) {
        Focusable(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.size(width, height),
            color = tile,
            style = FocusStyle.Picture,
            focusedScale = 1.06f,
            focusRequester = focusRequester,
            onFocus = {
                focused = it
                onFocus(it)
            },
        ) {
            when (style) {
                AppStyle.Picture -> AsyncImage(
                    model = File(Haven.apps.imagesDir, look.image),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                AppStyle.Banner -> app.banner?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                else -> IconArt(app.icon, iconSize)
            }
        }
        if (names != AppNames.Never) {
            Txt(
                look.name.ifBlank { app.label }, T.Label, Modifier.padding(top = 24.dp).alpha(labelAlpha),
                color = if (focused || names != AppNames.Always) C.Linen else C.Stone,
            )
        }
    }
}

@Composable
private fun IconArt(icon: ImageBitmap?, size: Dp) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (icon != null) Image(icon, null, Modifier.size(size))
    }
}

private enum class Glow { None, On, Alert }

private data class RoomTile(
    val key: String,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val glow: Glow,
    val onClick: () -> Unit,
)

@Composable
private fun RoomRow(
    settings: Settings,
    haStatus: HaStatus,
    sleepEndsAt: Long,
    games: List<Game>,
    nav: Nav,
    state: LazyListState,
    req: (String) -> FocusRequester,
    track: (String) -> (Boolean) -> Unit,
) {
    val ha = Haven.ha
    ha.entities.collectAsState().value // this row must recompose on Home Assistant updates itself
    val haNote = when (haStatus) {
        HaStatus.NotConfigured -> "Connect Home Assistant"
        is HaStatus.Failed -> "Can't reach Home Assistant"
        HaStatus.Connecting -> "Connecting…"
        is HaStatus.Connected -> null
    }
    val haTarget = if (haStatus == HaStatus.NotConfigured) Screen.Settings else Screen.Lights
    val lights = ha.roomLights(settings)
    val speakers = ha.roomSpeakers(settings)
    val lightsTile = RoomTile(
        "lights", Icons.Lightbulb, "Lights",
        haNote ?: when {
            lights.isEmpty() -> "None in this room"
            lights.none { it.isOn } -> "All off"
            else -> "${lights.count { it.isOn }} of ${lights.size} on"
        },
        if (lights.any { it.isOn }) Glow.On else Glow.None,
    ) { nav.go(haTarget) }
    val playing = speakers.firstOrNull { it.state == "playing" }
    val speakersTile = RoomTile(
        "speakers", Icons.Speaker, "Speakers",
        haNote ?: when {
            speakers.isEmpty() -> "None in this room"
            playing != null -> "Playing on ${playing.name}"
            speakers.none { it.isOn } -> "All off"
            else -> "${speakers.count { it.isOn }} of ${speakers.size} on"
        },
        if (playing != null) Glow.On else Glow.None,
    ) { nav.go(haTarget) }

    val tiles = buildList {
        if (settings.bedroomExtras) {
            val a = settings.alarm
            add(
                RoomTile(
                    "alarm", Icons.AlarmClock, "Alarm",
                    if (a.enabled) "${Bedroom.timeLabel(a.hour, a.minute, settings.use24h)} · ${Bedroom.daysLabel(a.days)}" else "Off",
                    if (a.enabled) Glow.On else Glow.None,
                ) { nav.go(Screen.Alarm) },
            )
            val left = ((sleepEndsAt - System.currentTimeMillis()) / 60_000L).toInt()
            add(
                RoomTile(
                    "sleep", Icons.MoonStar, "Sleep timer", if (sleepEndsAt > 0) "${maxOf(left, 1)} min left" else "Off",
                    if (sleepEndsAt > 0) Glow.On else Glow.None,
                ) { nav.go(Screen.Sleep) },
            )
        }
        add(lightsTile)
        add(speakersTile)
        if (!settings.bedroomExtras) {
            val scenes = ha.roomScenes(settings)
            if (scenes.isNotEmpty()) {
                add(RoomTile("scenes", Icons.Palette, "Scenes", scenes.joinToString(", ") { it.name }, Glow.None) { nav.go(Screen.Lights) })
            }
        }
        add(RoomTile("art", Icons.Image, "Art mode", settings.artSource.label, Glow.None) { nav.go(Screen.Art) })
        if (settings.sports.teams.isNotEmpty() && settings.sports.homeTile) {
            val g = games.firstOrNull()
            val hide = settings.sports.noSpoilers
            val (title, sub) = when {
                g == null -> "Your teams" to "No games scheduled"
                g.state == GameState.Live -> (if (hide) g.matchup else g.scoreLine) to
                    listOf(if (hide) "Live" else g.detail, g.league.label).filter { it.isNotBlank() }.joinToString(" · ")
                g.state == GameState.Final -> (if (hide) g.matchup else g.scoreLine) to "Final"
                else -> g.matchup to gameStatus(g, settings.use24h)
            }
            add(
                RoomTile("sports", Icons.Trophy, title, sub, if (g?.state == GameState.Live) Glow.Alert else Glow.None) {
                    nav.go(Screen.Sports)
                },
            )
        }
    }
    // A list keeps its place by key, so tiles added in front (bedroom features turned on) would start
    // scrolled out of view. Start from the left again unless the row has focus.
    val tileKeys = tiles.joinToString(",") { it.key }
    LaunchedEffect(tileKeys) {
        if (HomeFocus.current?.startsWith("room:") != true) state.scrollToItem(0)
    }
    LazyRow(
        state = state,
        contentPadding = PaddingValues(start = SIDE.dp, end = SIDE.dp, top = max(16.dp, L.gap(24)), bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(L.gap(32)),
    ) {
        itemsIndexed(tiles, key = { _, t -> t.key }) { _, t ->
            Focusable(
                onClick = t.onClick,
                modifier = Modifier.size(400.dp, 112.dp),
                color = C.Surface,
                focusedScale = 1.05f,
                focusRequester = req("room:${t.key}"),
                onFocus = track("room:${t.key}"),
            ) { focused ->
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Icon(
                        t.icon, 40.dp,
                        when {
                            lit(focused) -> C.Ink
                            t.glow == Glow.On -> C.Lamp
                            t.glow == Glow.Alert -> C.Clay
                            else -> C.Stone
                        },
                    )
                    Column {
                        Txt(t.title, T.CardTitle, color = ink(focused))
                        Txt(t.subtitle, T.Label, color = inkMuted(focused))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppMenu(app: TvApp, settings: Settings, onDismiss: () -> Unit, onRename: () -> Unit, onPicture: () -> Unit) {
    val isFav = app.pkg in settings.favorites
    val look = settings.appLook(app.pkg)
    val first = remember { FocusRequester() }
    HavenDialog(onDismiss) {
        Panel(Modifier.width(640.dp)) {
            Txt(look.name.ifBlank { app.label }, T.Title)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MenuItem(if (isFav) "Remove from favorites" else "Add to favorites", if (isFav) Icons.X else Icons.Star, first) {
                    Haven.settings.update {
                        it.copy(favorites = if (isFav) it.favorites - app.pkg else it.favorites + app.pkg)
                    }
                    onDismiss()
                }
                if (isFav) {
                    MenuItem("Move left", Icons.ArrowLeft) {
                        Haven.settings.update { it.copy(favorites = it.favorites.moved(app.pkg, -1)) }
                        onDismiss()
                    }
                    MenuItem("Move right", Icons.ArrowRight) {
                        Haven.settings.update { it.copy(favorites = it.favorites.moved(app.pkg, 1)) }
                        onDismiss()
                    }
                }
                MenuItem("Rename", Icons.Pencil) {
                    onDismiss()
                    onRename()
                }
                MenuItem("Change its picture", Icons.Image) {
                    onDismiss()
                    onPicture()
                }
                if (!look.isDefault) {
                    MenuItem("Back to its own name and picture", Icons.RefreshCw) {
                        Haven.settings.update { it.withAppLook(app.pkg, AppLook()) }
                        onDismiss()
                    }
                }
                MenuItem("Hide from Haven", Icons.EyeOff) {
                    Haven.settings.update { it.copy(hiddenApps = it.hiddenApps + app.pkg, favorites = it.favorites - app.pkg) }
                    onDismiss()
                }
                MenuItem("App info", Icons.Info) {
                    Haven.apps.openAppInfo(app.pkg)
                    onDismiss()
                }
            }
        }
    }
    FocusOnOpen(first, 50)
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
