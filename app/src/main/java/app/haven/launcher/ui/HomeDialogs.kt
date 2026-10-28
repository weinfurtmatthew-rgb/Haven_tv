package app.haven.launcher.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.haven.launcher.Haven
import app.haven.launcher.data.AppLook
import app.haven.launcher.data.AppStyle
import app.haven.launcher.data.BackgroundConfig
import app.haven.launcher.data.BgColor
import app.haven.launcher.data.BgKind
import app.haven.launcher.data.GradientPreset
import app.haven.launcher.data.Settings
import app.haven.launcher.data.TileColor
import app.haven.launcher.data.TvApp
import app.haven.launcher.data.moved
import app.haven.launcher.ha.HaEntity
import app.haven.launcher.media.Details
import app.haven.launcher.media.MediaItem
import app.haven.launcher.media.MediaKind
import app.haven.launcher.media.ShowInfo
import app.haven.launcher.sports.Watch
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Whatever is open over the home screen. [replace] swaps it for another one (the app menu opening "Rename"). */
@Composable
fun HomeDialogs(h: HomeCtx, d: HomeDialog, replace: (HomeDialog) -> Unit, close: () -> Unit) {
    val ctx = LocalContext.current
    val s = h.s
    when (d) {
        is HomeDialog.AppMenu -> AppMenu(d.app, s, close, onRename = { replace(HomeDialog.Rename(d.app)) }, onPicture = { replace(HomeDialog.Picture(d.app)) })
        is HomeDialog.Rename -> {
            val look = s.appLook(d.app.pkg)
            TextEditDialog(
                TextEdit("Rename ${d.app.label}", look.name.ifBlank { d.app.label }, d.app.label) { v ->
                    val name = v.trim().takeIf { it != d.app.label }.orEmpty()
                    Haven.settings.update { it.withAppLook(d.app.pkg, it.appLook(d.app.pkg).copy(name = name)) }
                },
            ) { close() }
        }
        is HomeDialog.Picture -> {
            val app = d.app
            val look = s.appLook(app.pkg)
            val choices = buildList {
                add(Choice(AppStyle.Auto.name, AppStyle.Auto.label, s.look.appArt.label))
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
                    val style = AppStyle.values().firstOrNull { it.name == v } ?: AppStyle.Auto
                    if (style == AppStyle.Picture && look.image.isBlank()) {
                        close()
                        Toast.makeText(ctx, "Upload a picture for ${app.label} from your phone first: Settings, then scan the code.", Toast.LENGTH_LONG).show()
                    } else {
                        Haven.settings.update { it.withAppLook(app.pkg, it.appLook(app.pkg).copy(style = style)) }
                        if (style == AppStyle.Icon) replace(HomeDialog.TileColour(app)) else close()
                    }
                },
                onDismiss = close,
            )
        }
        is HomeDialog.TileColour -> {
            val app = d.app
            val look = s.appLook(app.pkg)
            ChoiceDialog(
                "Tile colour for ${look.name.ifBlank { app.label }}",
                TileColor.values().map { Choice(it.argb.toString(), it.label, swatch = if (it.argb == 0L) C.Surface else Color(it.argb)) },
                look.color.toString(),
                onPick = { v ->
                    close()
                    Haven.settings.update { it.withAppLook(app.pkg, it.appLook(app.pkg).copy(color = v.toLongOrNull() ?: 0L)) }
                },
                onDismiss = close,
            )
        }
        is HomeDialog.Title -> TitleDialog(h, d.item, close)
        is HomeDialog.Camera -> CameraDialog(h, d.id, close)
        is HomeDialog.Device -> DeviceDialog(h, d.id, close)
        is HomeDialog.WatchGame -> ChoiceDialog(
            "Watch ${d.game.matchup} on",
            d.ways.mapIndexed { i, w -> Choice(i.toString(), w.label) },
            "",
            onPick = { v ->
                close()
                d.ways.getOrNull(v.toIntOrNull() ?: -1)?.let { Watch.go(ctx, it) }
            },
            onDismiss = close,
        )
        is HomeDialog.Note -> NoteDialog(d.title, d.body, close)
        is HomeDialog.Confirm -> ConfirmDialog(d.title, d.body, d.yes, d.onYes, close)
        HomeDialog.Background -> BackgroundStrip(s, close)
        is HomeDialog.Pin -> PinDialog(
            s.kids.pin,
            onOk = {
                GrownUp.unlock()
                close()
                d.then()
            },
            onDismiss = close,
        )
    }
}

@Composable
private fun AppMenu(app: TvApp, s: app.haven.launcher.data.Settings, onDismiss: () -> Unit, onRename: () -> Unit, onPicture: () -> Unit) {
    val isFav = app.pkg in s.favorites
    val look = s.appLook(app.pkg)
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
                MenuItem("Rename", Icons.Pencil) { onRename() }
                MenuItem("Change its picture", Icons.Image) { onPicture() }
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

@Composable
fun NoteDialog(title: String, body: String, onDismiss: () -> Unit) {
    val ok = remember { FocusRequester() }
    HavenDialog(onDismiss) {
        Panel(Modifier.width(1100.dp)) {
            Txt(title, T.Title, maxLines = 3)
            Txt(body, T.Body, color = C.Stone, maxLines = 10)
            Chip("Close", onDismiss, focusRequester = ok)
        }
    }
    FocusOnOpen(ok)
}

// ---- movies and shows -----------------------------------------------------------------------------------

/** A movie's or show's details: what it is, where to watch it on this TV, its trailer, and following a show. */
@Composable
private fun TitleDialog(h: HomeCtx, item: MediaItem, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val apps by Haven.apps.apps.collectAsState()
    val details by produceState<Details?>(null, item.key) { value = runCatching { Haven.media.details(item) }.getOrNull() }
    val show by produceState<ShowInfo?>(null, item.showId) {
        val id = item.showId ?: return@produceState
        value = withContext(Dispatchers.IO) { runCatching { Haven.media.show(id) }.getOrNull() }
    }
    val settings by Haven.settings.state.collectAsState()
    val following = item.showId != null && settings.shows.any { it.id == item.showId }
    val first = remember { FocusRequester() }
    val d = details
    val watchApps = when {
        d != null -> Haven.media.appsFor(d, apps)
        item.network.isNotBlank() -> apps.filter { app.haven.launcher.media.MediaParse.sameService(item.network, it.label) }
        else -> emptyList()
    }.take(3)
    HavenDialog(onDismiss) {
        Panel(Modifier.width(1500.dp), padding = 48.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                Box(Modifier.size(300.dp, 450.dp).clip(L.shape).background(C.Raised)) {
                    val pic = item.poster ?: show?.poster
                    if (pic != null) AsyncImage(pic, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Txt(item.title, T.Headline, size = 64, maxLines = 2)
                    val meta = buildList {
                        if (item.year.isNotBlank()) add(item.year)
                        val genres = d?.genres?.takeIf { it.isNotEmpty() } ?: item.genres.ifEmpty { show?.genres.orEmpty() }
                        if (genres.isNotEmpty()) add(genres.take(3).joinToString(", "))
                        d?.runtimeMin?.let { add(if (it >= 60) "${it / 60} h ${it % 60} min" else "$it min") }
                        (item.rating ?: show?.rating)?.let { add("★ %.1f".format(it)) }
                        show?.network?.takeIf { it.isNotBlank() }?.let { add(it) }
                    }
                    if (meta.isNotEmpty()) Txt(meta.joinToString(" · "), T.Body, color = C.Linen)
                    val info = show
                    if (info != null) Txt(Haven.media.nextLine(info).second, T.CardTitle, color = C.Lamp)
                    val about = item.overview.ifBlank { show?.summary.orEmpty() }
                    if (about.isNotBlank()) Txt(about, T.Body, color = C.Stone, maxLines = 6)
                    if (d != null && d.providers.isNotEmpty()) {
                        Txt("On " + d.providers.take(5).joinToString(", "), T.Label, color = C.Stone, maxLines = 2)
                    }
                    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        var firstUsed = false
                        fun req(): FocusRequester? = if (!firstUsed) {
                            firstUsed = true
                            first
                        } else null
                        for (a in watchApps) {
                            Chip("Watch on ${a.label}", {
                                onDismiss()
                                Haven.apps.launch(a)
                            }, icon = Icons.Play, focusRequester = req())
                        }
                        if (item.kind == MediaKind.Show && watchApps.isEmpty()) {
                            Chip("Watch", {
                                onDismiss()
                                watchShow(ctx, item)
                            }, icon = Icons.Play, focusRequester = req())
                        }
                        d?.trailer?.let { key -> Chip("Trailer", { openTrailer(ctx, key) }, icon = Icons.SquarePlay, focusRequester = req()) }
                        val sid = item.showId
                        if (sid != null) {
                            Chip(
                                if (following) "Following" else "Follow",
                                {
                                    scope.launch {
                                        if (following) {
                                            Haven.media.unfollow(sid)
                                        } else {
                                            val info2 = show ?: withContext(Dispatchers.IO) { Haven.media.show(sid) }
                                            if (info2 != null) Haven.media.follow(info2)
                                        }
                                        Haven.media.ensure(app.haven.launcher.media.MediaList.YourShows, force = true)
                                    }
                                },
                                selected = following, icon = Icons.Heart, focusRequester = req(),
                            )
                        }
                        Chip("Close", onDismiss, focusRequester = req())
                    }
                    if (item.tmdbId != null) Txt("Movie and show details from TMDB.", T.Label, color = C.Dust)
                    if (item.showId != null) Txt("TV schedules from TVmaze.", T.Label, color = C.Dust)
                }
            }
        }
    }
    FocusOnOpen(first)
}

// ---- cameras -------------------------------------------------------------------------------------------------

/** One camera, full screen, a new picture every two seconds. Back closes it. */
@Composable
private fun CameraDialog(h: HomeCtx, id: String, onDismiss: () -> Unit) {
    val entities by Haven.ha.entities.collectAsState()
    val e = entities[id] ?: return onDismiss()
    val close = remember { FocusRequester() }
    val tick by produceState(0) {
        while (true) {
            delay(2_000)
            value++
        }
    }
    HavenDialog(onDismiss) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Haven.ha.picture(e, h.s)?.let { url ->
                AsyncImage(cameraUrl(url, tick), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
            Row(Modifier.align(Alignment.TopStart).padding(start = 64.dp, top = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                IconButton(Icons.ChevronLeft, onDismiss, focusRequester = close)
                Txt(e.name, T.Title, color = Color.White)
            }
        }
    }
    FocusOnOpen(close)
}

// ---- devices -----------------------------------------------------------------------------------------------

/** Left and right adjust a level on a focused control; true when the key was used. */
private fun adjust(ev: KeyEvent, step: (Int) -> Unit): Boolean {
    if (ev.type != KeyEventType.KeyDown) return false
    return when (ev.key) {
        Key.DirectionLeft -> {
            step(-1)
            true
        }
        Key.DirectionRight -> {
            step(1)
            true
        }
        else -> false
    }
}

/** Everything one device can do: brightness, temperature and mode, playback and volume, locking and opening. */
@Composable
private fun DeviceDialog(h: HomeCtx, id: String, onDismiss: () -> Unit) {
    val entities by Haven.ha.entities.collectAsState()
    val e = entities[id] ?: return onDismiss()
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    val pending = remember { arrayOfNulls<Job>(1) }
    fun later(block: suspend () -> Unit) {
        pending[0]?.cancel()
        pending[0] = scope.launch {
            delay(350)
            block()
        }
    }
    HavenDialog(onDismiss) {
        Panel(Modifier.width(1100.dp), padding = 48.dp) {
            Txt(e.name, T.Headline, maxLines = 2)
            when (e.domain) {
                "light", "switch" -> {
                    val pct = if (e.isOn) e.brightnessPct ?: 100 else 0
                    Txt(if (!e.isOn) "Off" else if (e.dimmable) "On · $pct%" else "On", T.Title, color = if (e.isOn) C.Lamp else C.Stone)
                    LevelControl(
                        "Brightness", pct / 100f, first,
                        onToggle = { scope.launch { Haven.ha.setLight(e, !e.isOn, if (!e.isOn) e.brightnessPct else null) } },
                        enabled = e.dimmable,
                    ) { dir ->
                        val target = (pct + dir * 10).coerceIn(0, 100)
                        Haven.ha.assume(e.copyWith(if (target > 0) "on" else "off", mapOf("brightness" to target * 255 / 100)))
                        later { Haven.ha.setLight(e, target > 0, target) }
                    }
                }
                "climate" -> {
                    val target = e.attrs.optDouble("temperature")
                    val step = e.attrs.optDouble("target_temp_step", 1.0).takeIf { it > 0 } ?: 1.0
                    Txt("Inside ${degrees(e.attrs.optDouble("current_temperature"))} · ${climateLine(e)}", T.Title, color = C.Stone)
                    Focusable(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        color = C.Raised,
                        focusedScale = 1.02f,
                        focusRequester = first,
                        onKey = { ev ->
                            adjust(ev) { dir ->
                                if (!target.isNaN()) {
                                    val min = e.attrs.optDouble("min_temp", 5.0)
                                    val max = e.attrs.optDouble("max_temp", 35.0)
                                    val t = (target + dir * step).coerceIn(min, max)
                                    Haven.ha.assume(e.copyWith(attrs = mapOf("temperature" to t)))
                                    later { Haven.ha.setTemperature(e, t) }
                                }
                            }
                        },
                    ) { focused ->
                        Row(Modifier.fillMaxSize().padding(horizontal = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Minus, 48.dp, inkIcon(focused))
                            Txt(degrees(target), T.Display, Modifier.weight(1f), ink(focused), align = TextAlign.Center, size = 140)
                            Icon(Icons.Plus, 48.dp, inkIcon(focused))
                        }
                    }
                    val modes = e.attrs.optJSONArray("hvac_modes")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
                    if (modes.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            for (m in modes.take(6)) {
                                Chip(m.replace('_', ' ').replaceFirstChar { it.uppercase() }, { scope.launch { Haven.ha.setHvacMode(e, m) } }, selected = e.state == m)
                            }
                        }
                    }
                    KeyHint("temperature", icon = Icons.ChevronsLeftRight)
                }
                "media_player" -> {
                    val title = e.attrs.optString("media_title")
                    if (title.isNotBlank()) Txt(listOf(title, e.attrs.optString("media_artist")).filter { it.isNotBlank() }.joinToString(" · "), T.Title, color = C.Stone, maxLines = 2)
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(Icons.SkipBack, { scope.launch { Haven.ha.media(e, "media_previous_track") } }, size = 88.dp)
                        IconButton(if (e.state == "playing") Icons.Pause else Icons.Play, { scope.launch { Haven.ha.media(e, "media_play_pause") } }, size = 112.dp, focusRequester = first)
                        IconButton(Icons.SkipForward, { scope.launch { Haven.ha.media(e, "media_next_track") } }, size = 88.dp)
                        Spacer(width = 32.dp)
                        Chip(if (e.isOn) "Turn off" else "Turn on", { scope.launch { Haven.ha.setPower(e, !e.isOn) } }, icon = Icons.Power)
                    }
                    if (e.canSetVolume) {
                        LevelControl("Volume", e.volume ?: 0f, null, onToggle = {}) { dir ->
                            val v = ((e.volume ?: 0f) + dir * 0.05f).coerceIn(0f, 1f)
                            Haven.ha.assume(e.copyWith(attrs = mapOf("volume_level" to v.toDouble())))
                            later { Haven.ha.setVolume(e, v) }
                        }
                    }
                }
                "lock" -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Txt(lockLook(e).second, T.Title, Modifier.padding(end = 24.dp), C.Stone)
                    Chip("Lock", { scope.launch { Haven.ha.setLocked(e, true) } }, icon = Icons.Lock, focusRequester = first)
                    Chip("Unlock", { scope.launch { Haven.ha.setLocked(e, false) } }, icon = Icons.LockOpen)
                }
                "cover" -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Txt(lockLook(e).second, T.Title, Modifier.padding(end = 24.dp), C.Stone)
                    Chip("Open", { scope.launch { Haven.ha.setOpen(e, true) } }, icon = Icons.ChevronUp, focusRequester = first)
                    Chip("Stop", { scope.launch { Haven.ha.stopCover(e) } }, icon = Icons.X)
                    Chip("Close", { scope.launch { Haven.ha.setOpen(e, false) } }, icon = Icons.ChevronDown)
                }
                else -> Txt(e.state, T.Title, color = C.Stone)
            }
            Chip("Done", onDismiss, focusRequester = if (e.domain in setOf("light", "switch", "climate", "media_player", "lock", "cover")) null else first)
        }
    }
    FocusOnOpen(first)
}

/** A level you set with left and right (brightness, volume); OK switches the device on or off. */
@Composable
private fun LevelControl(label: String, fraction: Float, focus: FocusRequester?, onToggle: () -> Unit, enabled: Boolean = true, onStep: (Int) -> Unit) {
    Focusable(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth().height(120.dp),
        color = C.Raised,
        focusedScale = 1.02f,
        focusRequester = focus,
        onKey = { ev -> enabled && adjust(ev, onStep) },
    ) { focused ->
        Row(Modifier.fillMaxSize().padding(horizontal = 36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Txt(label, T.CardTitle, Modifier.width(220.dp), ink(focused))
            LevelBar(fraction, Modifier.weight(1f), focused)
            Txt("${(fraction * 100).toInt()}%", T.Numeric, Modifier.width(100.dp), ink(focused), align = TextAlign.End)
        }
    }
}

// ---- grown-up PIN ------------------------------------------------------------------------------------------

/**
 * The grown-up lock: type the PIN with the remote's number keys or the keypad on screen. A wrong PIN clears
 * and says so; Back gives up.
 */
@Composable
fun PinDialog(pin: String, onOk: () -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    var wrong by remember { mutableIntStateOf(0) }
    val first = remember { FocusRequester() }
    fun type(c: Char) {
        if (typed.length >= pin.length) return
        typed += c
        if (typed.length == pin.length) {
            if (typed == pin) onOk() else {
                wrong++
                typed = ""
            }
        }
    }
    HavenDialog(onDismiss) {
        Panel(
            Modifier.width(760.dp).onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val digit = when (ev.key) {
                    Key.Zero, Key.NumPad0 -> '0'
                    Key.One, Key.NumPad1 -> '1'
                    Key.Two, Key.NumPad2 -> '2'
                    Key.Three, Key.NumPad3 -> '3'
                    Key.Four, Key.NumPad4 -> '4'
                    Key.Five, Key.NumPad5 -> '5'
                    Key.Six, Key.NumPad6 -> '6'
                    Key.Seven, Key.NumPad7 -> '7'
                    Key.Eight, Key.NumPad8 -> '8'
                    Key.Nine, Key.NumPad9 -> '9'
                    else -> null
                }
                if (digit != null) type(digit)
                digit != null
            },
            padding = 48.dp,
        ) {
            Txt("Grown-ups only", T.Title)
            Txt(if (wrong > 0) "That's not it. Try again." else "Enter the PIN to go on.", T.Body, color = if (wrong > 0) C.Clay else C.Stone)
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally)) {
                for (i in 0 until pin.length) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(if (i < typed.length) C.Lamp else Color.Transparent).border(3.dp, C.Line, CircleShape),
                    )
                }
            }
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "back", "0", "")
            Column(Modifier.align(Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                for (r in keys.chunked(3)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        for (k in r) {
                            when (k) {
                                "" -> Box(Modifier.size(112.dp, 88.dp))
                                "back" -> Focusable(
                                    onClick = { typed = typed.dropLast(1) },
                                    modifier = Modifier.size(112.dp, 88.dp),
                                    focusedScale = 1.05f,
                                ) { focused -> Icon(Icons.ArrowLeft, 36.dp, inkIcon(focused), Modifier.align(Alignment.Center)) }
                                else -> Focusable(
                                    onClick = { type(k[0]) },
                                    modifier = Modifier.size(112.dp, 88.dp),
                                    focusedScale = 1.05f,
                                    focusRequester = if (k == "1") first else null,
                                ) { focused -> Txt(k, T.Title, Modifier.align(Alignment.Center), ink(focused)) }
                            }
                        }
                    }
                }
            }
        }
    }
    FocusOnOpen(first)
}

/** An uploaded app picture's file, for the phone page's previews. */
fun appImageFile(name: String): File = File(Haven.apps.imagesDir, name)

/** Backgrounds that take turns, so "Next picture" has somewhere to go. */
private val TURNS = setOf(BgKind.Photo, BgKind.Art, BgKind.Posters)

/**
 * The background, changed from the home screen: a strip along the bottom, with the background showing behind it.
 * Moving along the strip tries each choice; OK keeps it. Back closes, keeping what was kept.
 */
@Composable
fun BackgroundStrip(s: Settings, onClose: () -> Unit) {
    val bg = s.background
    val first = remember { FocusRequester() }
    val photos = remember { Haven.backgrounds.files().map { it.name } }
    val kinds = buildList {
        add(BgKind.Daily)
        if (photos.isNotEmpty()) add(BgKind.Photo)
        add(BgKind.Art)
        if (bg.tmdbKey.isNotBlank()) add(BgKind.Posters)
        if (s.haConfigured) add(BgKind.NowPlaying)
        add(BgKind.Color)
        add(BgKind.Gradient)
        add(BgKind.Theme)
    }
    fun keep(b: BackgroundConfig) {
        Haven.settings.update { it.copy(background = b) }
        BgPreview.value = null
    }
    fun tryOn(b: BackgroundConfig) {
        BgPreview.value = if (b == Haven.settings.value.background) null else b
    }
    // Closing any way at all never leaves a background on trial.
    DisposableEffect(Unit) { onDispose { BgPreview.value = null } }
    val shown = BgPreview.value ?: bg
    HavenDialog(onClose, alignment = Alignment.BottomCenter, dim = false) {
        Panel(Modifier.fillMaxWidth().padding(start = 116.dp, end = 116.dp, bottom = 56.dp), padding = 36.dp) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Txt("Background", T.Title)
                Txt(shown.kind.label, T.Body, color = C.Stone)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (bg.kind in TURNS) {
                    Chip("Next picture", { BgPreview.next++ }, icon = Icons.RefreshCw, focusRequester = first, glass = Look.glass)
                }
                for (k in kinds) {
                    Chip(
                        k.label, { keep(bg.copy(kind = k)) },
                        selected = bg.kind == k,
                        focusRequester = if (bg.kind !in TURNS && k == bg.kind) first else null,
                        onFocus = { if (it) tryOn(bg.copy(kind = k)) },
                        glass = Look.glass,
                    )
                }
            }
            // The choices inside the background being tried or kept: which colour, gradient or photo.
            when (shown.kind) {
                BgKind.Color -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    for (c in BgColor.values()) {
                        val b = bg.copy(kind = BgKind.Color, color = c.argb)
                        Chip(c.label, { keep(b) }, selected = bg.kind == BgKind.Color && bg.color == c.argb, onFocus = { if (it) tryOn(b) }, glass = Look.glass)
                    }
                }
                BgKind.Gradient -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    for (g in GradientPreset.values()) {
                        val b = bg.copy(kind = BgKind.Gradient, gradient = g)
                        Chip(g.label, { keep(b) }, selected = bg.kind == BgKind.Gradient && bg.gradient == g, onFocus = { if (it) tryOn(b) }, glass = Look.glass)
                    }
                }
                BgKind.Photo -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    for (name in listOf("") + photos.take(12)) {
                        val b = bg.copy(kind = BgKind.Photo, photo = name)
                        Chip(
                            if (name.isBlank()) "Take turns" else name.substringBeforeLast('.'), { keep(b) },
                            selected = bg.kind == BgKind.Photo && bg.photo == name, onFocus = { if (it) tryOn(b) }, glass = Look.glass,
                        )
                    }
                }
                else -> Unit
            }
        }
    }
    FocusOnOpen(first)
}
