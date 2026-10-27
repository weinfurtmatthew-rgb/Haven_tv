package app.haven.launcher.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import android.os.SystemClock
import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.haven.launcher.Haven
import app.haven.launcher.Nav
import app.haven.launcher.Screen
import app.haven.launcher.bedroom.Bedroom
import app.haven.launcher.data.Settings
import app.haven.launcher.data.TvApp
import app.haven.launcher.ha.HaEntity
import app.haven.launcher.ha.HaStatus
import app.haven.launcher.sports.Game
import app.haven.launcher.sports.GameState
import app.haven.launcher.sports.ScoreAlerts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val haStatus by Haven.ha.status.collectAsState()
    Haven.ha.entities.collectAsState().value // recompose on Home Assistant updates
    Haven.ha.areas.collectAsState().value
    val sleepEndsAt by Bedroom.sleepEndsAt.collectAsState()
    val update by Haven.updater.available.collectAsState()
    val games by Haven.sports.games.collectAsState()
    val appCtx = LocalContext.current.applicationContext
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
    var menuFor by remember { mutableStateOf<TvApp?>(null) }

    val favState = rememberLazyListState()
    val appState = rememberLazyListState()
    val roomState = rememberLazyListState()

    // First run: pin a few apps so the top row isn't empty.
    LaunchedEffect(apps.isNotEmpty()) {
        if (apps.isNotEmpty() && !Haven.settings.value.favoritesInitialized) {
            Haven.settings.update { it.copy(favorites = defaultFavorites(apps), favoritesInitialized = true) }
        }
    }

    // Poll Home Assistant while the home screen is on screen; start art mode after the idle time.
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
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

    // Focus the first favorite (DIRECTV on the DIRECTV box), or whatever the user had focused before leaving.
    // "Home pressed while already home" starts over from the first favorite.
    LaunchedEffect(nav.homeResets, apps.isNotEmpty(), favorites.isNotEmpty()) {
        if (nav.homeResets != lastResetSeen) {
            lastResetSeen = nav.homeResets
            HomeFocus.lastKey = null
            favState.scrollToItem(0)
            appState.scrollToItem(0)
            roomState.scrollToItem(0)
        }
        val fallback = when {
            favorites.isNotEmpty() -> "fav:0"
            visibleApps.isNotEmpty() -> "app:0"
            else -> "top:settings"
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
            .padding(top = 48.dp)
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown) {
                    HomeFocus.userMoved = true
                    keyPressed[0] = true
                }
                false
            },
    ) {
        TopBar(settings, haStatus, update != null, ::req, ::track, nav)

        // ---- Favorites
        SectionTitle("Favorites", Modifier.padding(start = SIDE.dp, top = 32.dp))
        if (favorites.isEmpty()) {
            Panel(Modifier.padding(start = SIDE.dp, top = 24.dp, bottom = 55.dp).size(400.dp, 225.dp), padding = 32.dp) {
                Txt("Hold OK on an app below to pin it here.", T.Body, color = C.Stone, maxLines = 3)
            }
        } else {
            LazyRow(
                state = favState,
                contentPadding = PaddingValues(start = SIDE.dp, end = SIDE.dp, top = 24.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                itemsIndexed(favorites, key = { _, a -> "fav:" + a.pkg }) { i, app ->
                    AppCard(
                        app = app,
                        width = 400.dp,
                        height = 225.dp,
                        iconSize = 96.dp,
                        onClick = { Haven.apps.launch(app) },
                        onLongClick = { menuFor = app },
                        focusRequester = req("fav:$i"),
                        onFocus = track("fav:$i"),
                    )
                }
            }
        }

        // ---- Apps
        SectionTitle("Apps", Modifier.padding(start = SIDE.dp, top = 8.dp))
        LazyRow(
            state = appState,
            contentPadding = PaddingValues(start = SIDE.dp, end = SIDE.dp, top = 24.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            itemsIndexed(visibleApps, key = { _, a -> "app:" + a.pkg }) { i, app ->
                AppCard(
                    app = app,
                    width = 256.dp,
                    height = 144.dp,
                    iconSize = 72.dp,
                    onClick = { Haven.apps.launch(app) },
                    onLongClick = { menuFor = app },
                    focusRequester = req("app:$i"),
                    onFocus = track("app:$i"),
                )
            }
        }

        // ---- This room
        SectionTitle(settings.roomName.ifBlank { "This room" }, Modifier.padding(start = SIDE.dp, top = 8.dp))
        RoomRow(settings, haStatus, sleepEndsAt, games, nav, roomState, ::req, ::track)
    }

    menuFor?.let { app ->
        AppMenu(app, settings, onDismiss = { menuFor = null })
    }
}

private var lastResetSeen = 0

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
    Haven.ha.entities.collectAsState().value // weather
    Row(
        Modifier.fillMaxWidth().height(96.dp).padding(horizontal = SIDE.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Txt(formatTime(now, use24), T.Clock, Modifier.alignByBaseline())
            if (!use24) Txt(amPm(now), T.Label, Modifier.alignByBaseline().padding(start = 14.dp), C.Stone)
            Txt(formatDate(now), T.Body, Modifier.alignByBaseline().padding(start = 36.dp), C.Stone)
        }
        Box(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // Information, not a button: no surface, so it doesn't look focusable.
            Haven.ha.weather(settings)?.takeIf { it.available }?.let { WeatherNote(it, Modifier.padding(end = 16.dp)) }
            if (updateReady) {
                Chip("Update ready", { nav.go(Screen.Settings) }, icon = Icons.Download, focusRequester = req("top:update"), onFocus = track("top:update"))
            }
            Chip(
                settings.roomName.ifBlank { "This room" },
                { nav.go(if (haStatus == HaStatus.NotConfigured) Screen.Settings else Screen.Lights) },
                icon = Icons.House,
                focusRequester = req("top:room"),
                onFocus = track("top:room"),
            )
            IconButton(Icons.Settings, { nav.go(Screen.Settings) }, focusRequester = req("top:settings"), onFocus = track("top:settings"))
        }
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
 * An app as a picture: its TV banner, or its icon on a plain tile. The name appears under the card only while
 * it has focus, so rows stay quiet and nothing is cut mid-word at the edge of the screen.
 */
@Composable
private fun AppCard(
    app: TvApp,
    width: Dp,
    height: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    focusRequester: FocusRequester,
    onFocus: (Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val labelAlpha by animateFloatAsState(if (focused) 1f else 0f, tween(160), label = "label")
    Column(Modifier.width(width)) {
        Focusable(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.size(width, height),
            color = C.Surface,
            style = FocusStyle.Picture,
            focusedScale = 1.06f,
            focusRequester = focusRequester,
            onFocus = {
                focused = it
                onFocus(it)
            },
        ) {
            if (app.banner != null) {
                Image(app.banner, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                IconArt(app.icon, iconSize)
            }
        }
        Txt(app.label, T.Label, Modifier.padding(top = 24.dp).alpha(labelAlpha))
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
    state: androidx.compose.foundation.lazy.LazyListState,
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
        if (settings.sports.teams.isNotEmpty()) {
            val g = games.firstOrNull()
            val (title, sub) = when {
                g == null -> "Your teams" to "No games scheduled"
                g.state == GameState.Live -> g.scoreLine to listOf(g.detail, g.league.label).filter { it.isNotBlank() }.joinToString(" · ")
                g.state == GameState.Final -> g.scoreLine to "Final"
                else -> g.matchup to gameStatus(g, settings.use24h)
            }
            add(
                RoomTile("sports", Icons.Trophy, title, sub, if (g?.state == GameState.Live) Glow.Alert else Glow.None) {
                    nav.go(Screen.Sports)
                },
            )
        }
    }
    LazyRow(
        state = state,
        contentPadding = PaddingValues(start = SIDE.dp, end = SIDE.dp, top = 24.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
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
                            focused -> C.Ink
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
private fun AppMenu(app: TvApp, settings: Settings, onDismiss: () -> Unit) {
    val isFav = app.pkg in settings.favorites
    val first = remember { FocusRequester() }
    HavenDialog(onDismiss) {
        Panel(Modifier.width(640.dp)) {
            Txt(app.label, T.Title)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MenuItem(if (isFav) "Remove from favorites" else "Add to favorites", if (isFav) Icons.X else Icons.Star, first) {
                    Haven.settings.update {
                        it.copy(favorites = if (isFav) it.favorites - app.pkg else it.favorites + app.pkg)
                    }
                    onDismiss()
                }
                if (isFav) {
                    MenuItem("Move left", Icons.ArrowLeft) {
                        Haven.settings.update { it.copy(favorites = it.favorites.move(app.pkg, -1)) }
                        onDismiss()
                    }
                    MenuItem("Move right", Icons.ArrowRight) {
                        Haven.settings.update { it.copy(favorites = it.favorites.move(app.pkg, 1)) }
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
    LaunchedEffect(Unit) {
        delay(50)
        runCatching { first.requestFocus() }
    }
}

@Composable
fun MenuItem(label: String, icon: ImageVector, focusRequester: FocusRequester? = null, onClick: () -> Unit) {
    Focusable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(80.dp),
        focusedScale = 1.03f,
        focusRequester = focusRequester,
    ) { focused ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Icon(icon, 32.dp, if (focused) C.Ink else C.Stone)
            Txt(label, T.Body, color = ink(focused))
        }
    }
}

/** A dialog window (so D-pad focus stays inside it) drawn on the 1920x1080 canvas over a dimmed screen. */
@Composable
fun HavenDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DesignCanvas {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
        }
    }
}

private fun List<String>.move(item: String, by: Int): List<String> {
    val i = indexOf(item)
    if (i < 0) return this
    val j = (i + by).coerceIn(0, size - 1)
    if (i == j) return this
    val m = toMutableList()
    m.removeAt(i)
    m.add(j, item)
    return m
}

/** First run: DIRECTV (live TV on the DIRECTV box) first, then the first few apps with banners. */
private fun defaultFavorites(apps: List<TvApp>): List<String> {
    val provider = apps.filter { it.pkg.contains("directv", true) || it.label.contains("directv", true) }
        .sortedByDescending { it.launchIntent.hasCategory(Intent.CATEGORY_HOME) }
        .take(1)
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
