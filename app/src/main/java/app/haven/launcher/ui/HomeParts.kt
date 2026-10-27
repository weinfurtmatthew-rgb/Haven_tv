package app.haven.launcher.ui

import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import app.haven.launcher.Haven
import app.haven.launcher.Screen
import app.haven.launcher.bedroom.Bedroom
import app.haven.launcher.data.AppArt
import app.haven.launcher.data.AppLook
import app.haven.launcher.data.AppNames
import app.haven.launcher.data.AppStyle
import app.haven.launcher.data.ClockStyle
import app.haven.launcher.data.HomeRow
import app.haven.launcher.data.ProfileInfo
import app.haven.launcher.data.TvApp
import app.haven.launcher.ha.HaEntity
import app.haven.launcher.ha.HaStatus
import app.haven.launcher.sports.GameState
import coil.compose.AsyncImage
import java.io.File

/** A row's title, at the page's margin. */
@Composable
fun RowTitle(h: HomeCtx, title: String, trailing: String? = null) {
    SectionTitle(title, Modifier.padding(start = h.start, end = h.end, top = L.gap(24)), trailing = trailing)
}

/**
 * The clock, the date, the weather and the buttons (room, profile, settings) in one line. Over Spotlight's photo
 * only the buttons show, on glass, because the photo carries the clock.
 */
@Composable
fun TopBar(h: HomeCtx, overPhoto: Boolean = false) {
    val ctx = LocalContext.current
    val s = h.s
    val now by rememberNow()
    val use24 = s.use24h || DateFormat.is24HourFormat(ctx)
    val profiles by Haven.settings.profiles.collectAsState()
    val haStatus by Haven.ha.status.collectAsState()
    val update by Haven.updater.available.collectAsState()
    val me = profiles.firstOrNull { it.active }
    val look = s.look
    Haven.ha.entities.collectAsState().value // weather
    // At least 96 px tall, and taller when a big text size makes the clock taller.
    Row(
        Modifier.fillMaxWidth().heightIn(min = 96.dp).padding(start = h.start, end = h.end),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!overPhoto && (look.clock != ClockStyle.Hidden || look.showDate)) {
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
                Haven.ha.weather(s)?.takeIf { it.available }?.let { WeatherNote(it, Modifier.padding(end = 16.dp), onPhoto = overPhoto) }
            }
            if (update != null) {
                Chip("Update ready", { h.guard { h.nav.go(Screen.Settings) } }, icon = Icons.Download, focusRequester = h.req("top:update"), onFocus = h.track("top:update"), glass = Look.glass)
            }
            if (look.showRoomChip) {
                Chip(
                    s.roomName.ifBlank { "This room" },
                    { h.nav.go(if (haStatus == HaStatus.NotConfigured) Screen.Settings else Screen.Lights) },
                    icon = Icons.House,
                    focusRequester = h.req("top:room"),
                    onFocus = h.track("top:room"),
                    glass = Look.glass,
                )
            }
            if (me != null) ProfileButton(me, h.req("top:profile"), h.track("top:profile")) { h.guard { h.nav.go(Screen.Profiles) } }
            IconButton(Icons.Settings, { h.guard { h.nav.go(Screen.Settings) } }, focusRequester = h.req("top:settings"), onFocus = h.track("top:settings"), glass = Look.glass)
        }
    }
}

/** Beside the side menu: just the time and date, on the right. */
@Composable
fun ClockLine(h: HomeCtx) {
    val ctx = LocalContext.current
    val now by rememberNow()
    val look = h.s.look
    val use24 = h.s.use24h || DateFormat.is24HourFormat(ctx)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 112.dp).padding(start = h.start, end = h.end, top = 32.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (look.showWeather) Haven.ha.weather(h.s)?.takeIf { it.available }?.let { WeatherNote(it, Modifier.padding(end = 40.dp)) }
        if (look.clock != ClockStyle.Hidden) Txt(formatTime(now, use24, withAmPm = !use24), T.Title, color = C.Linen)
        if (look.showDate) Txt(formatDate(now), T.Body, Modifier.padding(start = 28.dp), C.Stone)
    }
}

/** The active profile's picture; selecting it opens "Who's watching?". */
@Composable
fun ProfileButton(me: ProfileInfo, focus: FocusRequester, onFocus: (Boolean) -> Unit, size: Dp = 64.dp, onClick: () -> Unit) {
    Focusable(
        onClick = onClick,
        modifier = Modifier.size(size),
        shape = L.pill(size),
        color = Color.Transparent,
        style = FocusStyle.Picture,
        focusedScale = 1.08f,
        focusRequester = focus,
        onFocus = onFocus,
    ) {
        Avatar(me.name, ACCENTS.getValue(me.avatar).bright, size)
    }
}

@Composable
fun WeatherNote(w: HaEntity, modifier: Modifier = Modifier, onPhoto: Boolean = false) {
    val temp = w.attrs.optDouble("temperature", Double.NaN)
    val unit = w.attrs.optString("temperature_unit", "°").let { if (it.startsWith("°")) "°" else it }
    val color = if (onPhoto) Color.White else C.Stone
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(weatherIcon(w.state), 32.dp, color)
        val t = if (temp.isNaN()) "" else "${Math.round(temp)}$unit "
        Txt(t + weatherLabel(w.state), T.Label, color = color)
    }
}

// ---- apps ---------------------------------------------------------------------------------------------

/** Tile size for an app row: favorites and kids' apps are big, the rest are the app size. */
private fun bigRow(row: HomeRow) = row == HomeRow.Favorites || row == HomeRow.KidsApps

/** A row of apps that scrolls, or a grid of them when the profile wants boxes. */
@Composable
fun AppsRow(h: HomeCtx, row: HomeRow, apps: List<TvApp>, onPhoto: Boolean = false) {
    val big = bigRow(row)
    val columns = h.s.look.appGrid.columns
    if (columns > 0 && row != HomeRow.Recent) {
        AppGrid(h, row, apps, columns)
        return
    }
    val state = rememberLazyListState()
    SideEffect { h.lists[row.name] = state }
    val w = if (big) L.fav(400) else L.app(256)
    val ht = if (big) L.fav(225) else L.app(144)
    LazyRow(
        state = state,
        contentPadding = PaddingValues(start = h.start, end = h.end, top = max(16.dp, L.gap(24)), bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(if (big) L.gap(32) else L.gap(24)),
    ) {
        itemsIndexed(apps, key = { _, a -> "${row.name}:" + a.pkg }) { i, app ->
            AppCard(
                app = app,
                look = h.s.appLook(app.pkg),
                art = h.s.look.appArt,
                names = h.s.look.appNames,
                width = w,
                height = ht,
                iconSize = if (big) L.fav(96) else L.app(72),
                onPhoto = onPhoto,
                onClick = { Haven.apps.launch(app) },
                onLongClick = { h.guard { h.show(HomeDialog.AppMenu(app)) } },
                focusRequester = h.req("${row.name}:$i"),
                onFocus = { f ->
                    h.track("${row.name}:$i")(f)
                    // Spotlight's photo follows the app you're on in the row along its bottom.
                    if (onPhoto) {
                        if (f) SpotFocus.app = app else if (SpotFocus.app == app) SpotFocus.app = null
                    }
                },
            )
        }
    }
}

/** Apps as a grid of boxes, [columns] across the page (Big tiles, Kids). */
@Composable
private fun AppGrid(h: HomeCtx, row: HomeRow, apps: List<TvApp>, columns: Int) {
    val gap = L.gap(36)
    val w = (h.width - gap * (columns - 1)) / columns
    val ht = w * 9 / 16
    Column(Modifier.padding(start = h.start, end = h.end, top = max(16.dp, L.gap(24))), verticalArrangement = Arrangement.spacedBy(gap)) {
        for ((r, chunk) in apps.chunked(columns).withIndex()) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                for ((c, app) in chunk.withIndex()) {
                    val i = r * columns + c
                    AppCard(
                        app = app,
                        look = h.s.appLook(app.pkg),
                        art = h.s.look.appArt,
                        names = h.s.look.appNames,
                        width = w,
                        height = ht,
                        iconSize = ht * 0.45f,
                        onClick = { Haven.apps.launch(app) },
                        onLongClick = { h.guard { h.show(HomeDialog.AppMenu(app)) } },
                        focusRequester = h.req("${row.name}:$i"),
                        onFocus = h.track("${row.name}:$i"),
                    )
                }
            }
        }
    }
}

/**
 * An app as a picture: its TV banner, its icon on a tile, or the profile's own picture for it. The name shows
 * under the card while it has focus (or always, or never, as the profile chose).
 */
@Composable
fun AppCard(
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
    onPhoto: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val labelAlpha by animateFloatAsState(if (focused || names == AppNames.Always) 1f else 0f, motion(), label = "label")
    val style = when (look.style) {
        AppStyle.Auto -> if (art == AppArt.Banner && app.banner != null) AppStyle.Banner else AppStyle.Icon
        AppStyle.Banner -> if (app.banner != null) AppStyle.Banner else AppStyle.Icon
        AppStyle.Picture -> if (look.image.isNotBlank()) AppStyle.Picture else AppStyle.Icon
        AppStyle.Icon -> AppStyle.Icon
    }
    val ownColor = style == AppStyle.Icon && look.color != 0L
    val tile = if (ownColor) Color(look.color) else C.Surface
    Column(Modifier.width(width)) {
        Focusable(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.size(width, height),
            color = tile,
            style = FocusStyle.Picture,
            focusedScale = 1.06f,
            focusRequester = focusRequester,
            glass = Look.glass && style == AppStyle.Icon && !ownColor,
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
                color = when {
                    onPhoto -> Color.White
                    focused || names != AppNames.Always -> C.Linen
                    else -> C.Stone
                },
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

// ---- this room ----------------------------------------------------------------------------------------

private enum class Glow { None, On, Alert }

private data class RoomTile(
    val key: String,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val glow: Glow,
    val onClick: () -> Unit,
)

/** Lights, speakers, the alarm, the sleep timer, art mode and the next game: this room at a glance. */
@Composable
fun RoomRow(h: HomeCtx) {
    val ha = Haven.ha
    val s = h.s
    val nav = h.nav
    ha.entities.collectAsState().value // this row must recompose on Home Assistant updates itself
    val haStatus by ha.status.collectAsState()
    val sleepEndsAt by Bedroom.sleepEndsAt.collectAsState()
    val games by Haven.sports.games.collectAsState()
    val haNote = when (haStatus) {
        HaStatus.NotConfigured -> "Connect Home Assistant"
        is HaStatus.Failed -> "Can't reach Home Assistant"
        HaStatus.Connecting -> "Connecting…"
        is HaStatus.Connected -> null
    }
    val haTarget = if (haStatus == HaStatus.NotConfigured) Screen.Settings else Screen.Lights
    val lights = ha.roomLights(s)
    val speakers = ha.roomSpeakers(s)
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
        if (s.bedroomExtras) {
            val a = s.alarm
            add(
                RoomTile(
                    "alarm", Icons.AlarmClock, "Alarm",
                    if (a.enabled) "${Bedroom.timeLabel(a.hour, a.minute, s.use24h)} · ${Bedroom.daysLabel(a.days)}" else "Off",
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
        if (!s.bedroomExtras) {
            val scenes = ha.roomScenes(s)
            if (scenes.isNotEmpty()) {
                add(RoomTile("scenes", Icons.Palette, "Scenes", scenes.joinToString(", ") { it.name }, Glow.None) { nav.go(Screen.Lights) })
            }
        }
        add(RoomTile("art", Icons.Image, "Art mode", s.artSource.label, Glow.None) { nav.go(Screen.Art) })
        if (s.sports.teams.isNotEmpty() && s.sports.homeTile) {
            val g = games.firstOrNull()
            val hide = s.sports.noSpoilers
            val (title, sub) = when {
                g == null -> "Your teams" to "No games scheduled"
                g.state == GameState.Live -> (if (hide) g.matchup else g.scoreLine) to
                    listOf(if (hide) "Live" else g.detail, g.league.label).filter { it.isNotBlank() }.joinToString(" · ")
                g.state == GameState.Final -> (if (hide) g.matchup else g.scoreLine) to "Final"
                else -> g.matchup to gameStatus(g, s.use24h)
            }
            add(
                RoomTile("sports", Icons.Trophy, title, sub, if (g?.state == GameState.Live) Glow.Alert else Glow.None) {
                    nav.go(Screen.Sports)
                },
            )
        }
    }
    val state = rememberLazyListState()
    SideEffect { h.lists[HomeRow.Room.name] = state }
    // A list keeps its place by key, so tiles added in front (bedroom features turned on) would start
    // scrolled out of view. Start from the left again unless the row has focus.
    val tileKeys = tiles.joinToString(",") { it.key }
    LaunchedEffect(tileKeys) {
        if (HomeFocus.current?.startsWith("${HomeRow.Room.name}:") != true) state.scrollToItem(0)
    }
    LazyRow(
        state = state,
        contentPadding = PaddingValues(start = h.start, end = h.end, top = max(16.dp, L.gap(24)), bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(L.gap(32)),
    ) {
        itemsIndexed(tiles, key = { _, t -> t.key }) { i, t ->
            Focusable(
                onClick = t.onClick,
                modifier = Modifier.size(400.dp, 112.dp),
                color = C.Surface,
                focusedScale = 1.05f,
                focusRequester = h.req("${HomeRow.Room.name}:$i"),
                onFocus = h.track("${HomeRow.Room.name}:$i"),
                glass = Look.glass,
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
