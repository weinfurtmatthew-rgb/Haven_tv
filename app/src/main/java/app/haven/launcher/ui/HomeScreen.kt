package app.haven.launcher.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import android.os.SystemClock
import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Remembers which home tile had focus, so coming back from a screen lands on the same tile. */
private object HomeFocus {
    var lastKey: String? = null
}

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
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val requesters = remember { HashMap<String, FocusRequester>() }
    fun req(key: String) = requesters.getOrPut(key) { FocusRequester() }
    fun track(key: String): (Boolean) -> Unit = { f -> if (f) HomeFocus.lastKey = key }

    val visibleApps = apps.filter { it.pkg !in settings.hiddenApps }
    val byPkg = apps.associateBy { it.pkg }
    val favorites = settings.favorites.mapNotNull { byPkg[it] }
    var menuFor by remember { mutableStateOf<TvApp?>(null) }

    val favState = rememberLazyListState()
    val appState = rememberLazyListState()

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
            while (true) {
                delay(10_000)
                val idleMin = Haven.settings.value.artIdleMin
                if (idleMin > 0 && SystemClock.uptimeMillis() - Haven.lastInput > idleMin * 60_000L) {
                    nav.go(Screen.Art)
                }
            }
        }
    }

    // Initial focus, and "Home pressed while already home" -> back to the start of the rows.
    LaunchedEffect(nav.homeResets, apps.isNotEmpty(), favorites.isNotEmpty()) {
        if (nav.homeResets != lastResetSeen) {
            lastResetSeen = nav.homeResets
            HomeFocus.lastKey = null
            favState.scrollToItem(0)
            appState.scrollToItem(0)
        }
        delay(30)
        val key = HomeFocus.lastKey
        val ok = key != null && runCatching { requesters[key]?.requestFocus() != null }.getOrDefault(false)
        if (!ok) {
            runCatching { req("fav:0").requestFocus() }
                .recoverCatching { req("app:0").requestFocus() }
                .recoverCatching { req("top:settings").requestFocus() }
        }
    }

    Column(Modifier.fillMaxSize().padding(top = 56.dp)) {
        TopBar(settings, haStatus, update != null, ::req, ::track, nav)

        // ---- Favorites
        SectionTitle("Favorites", Modifier.padding(start = 96.dp, top = 30.dp, bottom = 8.dp))
        if (favorites.isEmpty()) {
            Box(
                Modifier.padding(start = 96.dp, top = 12.dp).size(520.dp, 292.dp)
                    .clip(RoundedCornerShape(16.dp)).background(C.Card),
                contentAlignment = Alignment.Center,
            ) {
                Txt("Hold OK on an app below to pin it here", 24, color = C.Muted, weight = FontWeight.Medium)
            }
        } else {
            LazyRow(
                state = favState,
                contentPadding = PaddingValues(start = 96.dp, end = 96.dp, top = 12.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.Top,
            ) {
                itemsIndexed(favorites, key = { _, a -> "fav:" + a.pkg }) { i, app ->
                    FavoriteCard(
                        app = app,
                        big = i == 0,
                        index = i,
                        subtitle = lastOpened(lastUsed[app.pkg]),
                        onClick = { Haven.apps.launch(app) },
                        onLongClick = { menuFor = app },
                        focusRequester = req("fav:$i"),
                        onFocus = track("fav:$i"),
                    )
                }
            }
        }

        // ---- Apps
        SectionTitle("Apps", Modifier.padding(start = 96.dp, top = 18.dp, bottom = 8.dp))
        LazyRow(
            state = appState,
            contentPadding = PaddingValues(start = 96.dp, end = 96.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            itemsIndexed(visibleApps, key = { _, a -> "app:" + a.pkg }) { i, app ->
                AppTile(app, i, { Haven.apps.launch(app) }, { menuFor = app }, req("app:$i"), track("app:$i"))
            }
        }

        // ---- This room
        SectionTitle(settings.roomName.ifBlank { "This room" }, Modifier.padding(start = 96.dp, top = 12.dp, bottom = 8.dp))
        RoomRow(settings, haStatus, sleepEndsAt, nav, ::req, ::track)
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
    rememberFocus: (String) -> (Boolean) -> Unit,
    nav: Nav,
) {
    val ctx = LocalContext.current
    val now by rememberNow()
    val use24 = settings.use24h || DateFormat.is24HourFormat(ctx)
    Row(
        Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 96.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Txt(formatTime(now, use24), 56, Modifier.alignByBaseline(), FontWeight.ExtraBold, spacing = -1f)
            if (!use24) Txt(amPm(now), 24, Modifier.alignByBaseline().padding(start = 20.dp), FontWeight.SemiBold, C.Muted)
            Txt(formatDate(now), 26, Modifier.alignByBaseline().padding(start = 32.dp), FontWeight.Medium, C.Muted)
        }
        Box(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (updateReady) {
                PillButton(
                    "Update ready", { nav.go(Screen.Settings) }, icon = Icons.Download,
                    focusRequester = req("top:update"),
                )
            }
            Focusable(
                onClick = { nav.go(Screen.Lights) },
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(28.dp),
                color = C.CardHi,
                focusedColor = C.Line,
                focusedScale = 1.06f,
                focusRequester = req("top:room"),
                onFocus = rememberFocus("top:room"),
            ) {
                Row(
                    Modifier.padding(horizontal = 24.dp).height(56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.House, 28.dp, C.Accent)
                    Txt(settings.roomName.ifBlank { "This room" }, 22, weight = FontWeight.Bold)
                }
            }
            Haven.ha.weather(settings)?.takeIf { it.available }?.let { WeatherChip(it) }
            CircleButton(Icons.Wifi, { openSystem(ctx, AndroidSettings.ACTION_WIFI_SETTINGS) }, focusRequester = req("top:wifi"))
            CircleButton(Icons.SlidersHorizontal, { openSystem(ctx, AndroidSettings.ACTION_SETTINGS) }, focusRequester = req("top:system"))
            Focusable(
                onClick = { nav.go(Screen.Settings) },
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = C.Avatar,
                focusedColor = C.Avatar,
                focusedScale = 1.1f,
                focusRequester = req("top:settings"),
                onFocus = rememberFocus("top:settings"),
            ) {
                val initial = settings.profileName.trim().firstOrNull()?.uppercase()
                if (initial != null) {
                    Txt(initial, 24, Modifier.align(Alignment.Center), FontWeight.Bold)
                } else {
                    Icon(Icons.Settings, 28.dp, C.Text, Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

@Composable
private fun WeatherChip(w: HaEntity) {
    val temp = w.attrs.optDouble("temperature", Double.NaN)
    val unit = w.attrs.optString("temperature_unit", "°").let { if (it.startsWith("°")) "°" else it }
    Row(
        Modifier.height(56.dp).clip(RoundedCornerShape(28.dp)).background(C.Card).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(weatherIcon(w.state), 28.dp)
        val t = if (temp.isNaN()) "" else "${Math.round(temp)}$unit "
        Txt(t + weatherLabel(w.state), 22)
    }
}

@Composable
private fun FavoriteCard(
    app: TvApp,
    big: Boolean,
    index: Int,
    subtitle: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    focusRequester: FocusRequester,
    onFocus: (Boolean) -> Unit,
) {
    val w = if (big) 520.dp else 360.dp
    val h = if (big) 292.dp else 203.dp
    Column(Modifier.width(w)) {
        Focusable(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.size(w, h),
            focusedScale = 1.05f,
            focusRequester = focusRequester,
            onFocus = onFocus,
        ) {
            if (app.banner != null) {
                Image(app.banner, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                PlaceholderArt(index, app.icon, big)
            }
        }
        Txt(app.label, if (big) 28 else 22, Modifier.padding(top = if (big) 14.dp else 12.dp), FontWeight.Bold)
        Txt(subtitle, if (big) 20 else 18, Modifier.padding(top = 2.dp), FontWeight.Medium, C.Muted)
    }
}

/** The mockup's abstract card art, with the app icon in the corner square. */
@Composable
private fun PlaceholderArt(index: Int, icon: ImageBitmap?, big: Boolean) {
    val base = C.CardPalette[index % C.CardPalette.size]
    val circle = if (big) 320.dp else 220.dp
    val square = if (big) 120.dp else 84.dp
    Box(Modifier.fillMaxSize().background(base)) {
        Box(
            Modifier.align(Alignment.TopEnd).offset(x = circle * 0.19f, y = -circle * 0.24f).size(circle)
                .clip(CircleShape).background(Color.White.copy(alpha = 0.14f)),
        )
        Box(
            Modifier.align(Alignment.BottomStart).padding(start = if (big) 36.dp else 24.dp, bottom = if (big) 44.dp else 32.dp)
                .size(square).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) Image(icon, null, Modifier.size(square * 0.72f))
        }
    }
}

@Composable
private fun AppTile(
    app: TvApp,
    index: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    focusRequester: FocusRequester,
    onFocus: (Boolean) -> Unit,
) {
    Focusable(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier.size(220.dp, 124.dp),
        focusedScale = 1.08f,
        focusRequester = focusRequester,
        onFocus = onFocus,
    ) { focused ->
        if (app.banner != null) {
            Image(app.banner, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (app.icon != null) Image(app.icon, null, Modifier.size(48.dp))
                Txt(app.label, 20, color = if (focused) C.Accent else C.Text)
            }
        }
    }
}

private data class RoomTile(
    val key: String,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit,
)

@Composable
private fun RoomRow(
    settings: Settings,
    haStatus: HaStatus,
    sleepEndsAt: Long,
    nav: Nav,
    req: (String) -> FocusRequester,
    rememberFocus: (String) -> (Boolean) -> Unit,
) {
    val ha = Haven.ha
    val tiles = buildList {
        if (settings.bedroomExtras) {
            val a = settings.alarm
            add(
                RoomTile(
                    "alarm", Icons.AlarmClock, "Alarm",
                    if (a.enabled) "${Bedroom.timeLabel(a.hour, a.minute, settings.use24h)} · ${Bedroom.daysLabel(a.days)}" else "Off",
                ) { nav.go(Screen.Alarm) },
            )
            val left = ((sleepEndsAt - System.currentTimeMillis()) / 60_000L).toInt()
            add(RoomTile("sleep", Icons.MoonStar, "Sleep timer", if (sleepEndsAt > 0) "${maxOf(left, 1)} min left" else "Off") { nav.go(Screen.Sleep) })
        }
        add(RoomTile("art", Icons.Image, "Art mode", settings.artSource.label) { nav.go(Screen.Art) })
        val lights = ha.roomLights(settings)
        val speakers = ha.roomSpeakers(settings)
        val haNote = when (haStatus) {
            HaStatus.NotConfigured -> "Connect Home Assistant"
            is HaStatus.Failed -> "Can't reach Home Assistant"
            HaStatus.Connecting -> "Connecting…"
            is HaStatus.Connected -> null
        }
        add(
            RoomTile(
                "lights", Icons.Lightbulb, "Lights",
                haNote ?: if (lights.isEmpty()) "None in this room" else "${lights.count { it.isOn }} of ${lights.size} on",
            ) { nav.go(if (haStatus == HaStatus.NotConfigured) Screen.Settings else Screen.Lights) },
        )
        val playing = speakers.firstOrNull { it.state == "playing" }
        add(
            RoomTile(
                "speakers", Icons.Speaker, "Speakers",
                haNote ?: when {
                    speakers.isEmpty() -> "None in this room"
                    playing != null -> "Playing on ${playing.name}"
                    else -> "${speakers.count { it.isOn }} of ${speakers.size} on"
                },
            ) { nav.go(if (haStatus == HaStatus.NotConfigured) Screen.Settings else Screen.Lights) },
        )
        if (!settings.bedroomExtras) {
            val scenes = ha.roomScenes(settings)
            if (scenes.isNotEmpty()) {
                add(RoomTile("scenes", Icons.Palette, "Scenes", scenes.joinToString(", ") { it.name }) { nav.go(Screen.Lights) })
            }
        }
    }
    val gap = 24
    val width = ((1728 - gap * (tiles.size - 1)) / tiles.size).coerceAtMost(340)
    Row(
        Modifier.padding(start = 96.dp, top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(gap.dp),
    ) {
        tiles.forEach { t ->
            Focusable(
                onClick = t.onClick,
                modifier = Modifier.size(width.dp, 96.dp),
                focusRequester = req("room:${t.key}"),
                onFocus = rememberFocus("room:${t.key}"),
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(t.icon, 36.dp, C.Accent)
                    Column {
                        Txt(t.title, 21, weight = FontWeight.Bold)
                        Txt(t.subtitle, 17, weight = FontWeight.Medium, color = C.Muted)
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
        Column(
            Modifier.width(560.dp).clip(RoundedCornerShape(24.dp)).background(C.Card).padding(36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Txt(app.label, 32, weight = FontWeight.ExtraBold)
            Spacer(height = 6.dp)
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
    LaunchedEffect(Unit) {
        delay(50)
        runCatching { first.requestFocus() }
    }
}

@Composable
fun MenuItem(label: String, icon: ImageVector, focusRequester: FocusRequester? = null, onClick: () -> Unit) {
    Focusable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(72.dp),
        color = C.CardHi,
        focusedColor = C.Line,
        focusedScale = 1.02f,
        focusRequester = focusRequester,
    ) { focused ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Icon(icon, 28.dp, if (focused) C.Accent else C.Text)
            Txt(label, 24)
        }
    }
}

/** A dialog window (so D-pad focus stays inside it) drawn on the 1920x1080 canvas. */
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

/** First run: DIRECTV (live TV on the DIRECTV box) as the big card, then the first few apps with banners. */
private fun defaultFavorites(apps: List<TvApp>): List<String> {
    val provider = apps.filter { it.pkg.contains("directv", true) || it.label.contains("directv", true) }
        .sortedByDescending { it.launchIntent.hasCategory(Intent.CATEGORY_HOME) }
        .take(1)
    val rest = apps.filter { it !in provider && it.banner != null }
    return (provider + rest).take(5).map { it.pkg }
}

private fun lastOpened(at: Long?): String {
    if (at == null || at == 0L) return "Not opened yet"
    val mins = (System.currentTimeMillis() - at) / 60_000L
    return when {
        mins < 1 -> "Opened just now"
        mins < 60 -> "Opened $mins min ago"
        mins < 24 * 60 -> "Opened ${mins / 60} hr ago"
        mins < 48 * 60 -> "Opened yesterday"
        else -> "Opened ${mins / (24 * 60)} days ago"
    }
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
