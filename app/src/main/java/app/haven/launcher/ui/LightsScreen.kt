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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.haven.launcher.Haven
import app.haven.launcher.Nav
import app.haven.launcher.Screen
import app.haven.launcher.ha.HaEntity
import app.haven.launcher.ha.HaStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Lights and speakers for this room: two columns of rows, no boxes around them. */
@Composable
fun LightsScreen(nav: Nav) {
    val settings by Haven.settings.state.collectAsState()
    val status by Haven.ha.status.collectAsState()
    Haven.ha.entities.collectAsState().value
    Haven.ha.areas.collectAsState().value
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val backFocus = remember { FocusRequester() }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                Haven.ha.refresh()
                delay(4_000)
            }
        }
    }
    LaunchedEffect(status is HaStatus.Connected) {
        delay(60)
        runCatching { firstFocus.requestFocus() }.recoverCatching { backFocus.requestFocus() }
    }

    Column(Modifier.fillMaxSize().padding(start = 116.dp, end = 116.dp, top = 48.dp), verticalArrangement = Arrangement.spacedBy(40.dp)) {
        ScreenHeader("Lights and speakers", settings.roomName, onBack = { nav.go(Screen.Home) }, backFocus = backFocus)
        when (val st = status) {
            HaStatus.NotConfigured -> EmptyState(
                "Connect Home Assistant",
                "Haven turns lights, scenes and speakers on and off through Home Assistant. Open Settings and scan the code with your phone to connect it.",
                "Open settings", firstFocus,
            ) { nav.go(Screen.Settings) }
            is HaStatus.Failed -> EmptyState(
                "Can't reach Home Assistant", "${st.message}. Check the address in Settings, or try again.", "Try again", firstFocus,
            ) { scope.launch { Haven.ha.refresh() } }
            else -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(64.dp)) {
                LightsColumn(Modifier.weight(1f).fillMaxHeight(), scope, firstFocus)
                SpeakersColumn(Modifier.weight(1f).fillMaxHeight(), scope)
            }
        }
    }
}

@Composable
private fun LightsColumn(modifier: Modifier, scope: CoroutineScope, firstFocus: FocusRequester) {
    // Read the states here, not only in the screen: otherwise Compose skips this column and toggles never show.
    val s by Haven.settings.state.collectAsState()
    Haven.ha.entities.collectAsState().value
    val lights = Haven.ha.roomLights(s)
    val scenes = Haven.ha.roomScenes(s)
    var active by remember { mutableStateOf<String?>(null) }
    val pending = remember { HashMap<String, Job>() }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        SectionTitle(
            "Lights",
            trailing = when {
                lights.isEmpty() -> null
                lights.none { it.isOn } -> "All off"
                else -> "${lights.count { it.isOn }} of ${lights.size} on"
            },
        )
        // Negative offset + padding: the row scrolls edge to edge, so a focused chip's lift is never clipped.
        LazyRow(
            Modifier.offset(x = (-16).dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            itemsIndexed(scenes, key = { _, e -> e.id }) { i, scene ->
                Chip(scene.name, {
                    active = scene.id
                    scope.launch { Haven.ha.activate(scene); delay(800); Haven.ha.refresh() }
                }, selected = active == scene.id, focusRequester = if (i == 0) firstFocus else null)
            }
            item(key = "bright") {
                Chip("Bright", {
                    active = "bright"
                    scope.launch { Haven.ha.setLights(lights, on = true, brightnessPct = 100); delay(600); Haven.ha.refresh() }
                }, selected = active == "bright", focusRequester = if (scenes.isEmpty()) firstFocus else null)
            }
            item(key = "off") {
                Chip("All off", {
                    active = "off"
                    scope.launch { Haven.ha.setLights(lights.filter { it.isOn }, on = false); delay(600); Haven.ha.refresh() }
                }, selected = active == "off")
            }
        }
        if (lights.isEmpty()) {
            Txt("No lights in this room. Pick the room's Home Assistant area in Settings.", T.Body, color = C.Stone, maxLines = 3)
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    Modifier.offset(x = (-16).dp).fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
                ) {
                    items(lights, key = { it.id }) { light ->
                        LightRow(
                            light,
                            onToggle = {
                                active = null
                                scope.launch { Haven.ha.setLight(light, !light.isOn, if (!light.isOn) light.brightnessPct else null) }
                            },
                            onAdjust = { step ->
                                val now = Haven.ha.entities.value[light.id] ?: light
                                val current = if (now.isOn) now.brightnessPct ?: 100 else 0
                                val target = (current + step).coerceIn(0, 100)
                                if (target == current) {
                                    false
                                } else {
                                    active = null
                                    Haven.ha.assume(
                                        now.copyWith(if (target > 0) "on" else "off", mapOf("brightness" to target * 255 / 100)),
                                    )
                                    pending.remove(light.id)?.cancel()
                                    pending[light.id] = scope.launch {
                                        delay(350)
                                        Haven.ha.setLight(light, target > 0, target)
                                    }
                                    true
                                }
                            },
                        )
                    }
                }
                BottomFade(48.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                KeyHint("on or off", key = "OK")
                KeyHint("brightness", icon = Icons.ChevronsLeftRight)
            }
        }
    }
}

@Composable
private fun LightRow(e: HaEntity, onToggle: () -> Unit, onAdjust: (Int) -> Boolean) {
    val pct = if (e.isOn) e.brightnessPct ?: 100 else 0
    val status = when {
        !e.available -> "Unavailable"
        e.isOn && e.dimmable -> "On · $pct%"
        e.isOn -> "On"
        else -> "Off"
    }
    Focusable(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth().height(112.dp),
        color = C.Surface,
        focusedScale = 1.03f,
        onKey = { ev -> e.dimmable && adjustKey(ev, onAdjust, 10) },
    ) { focused ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Column(Modifier.width(260.dp)) {
                Txt(e.name, T.CardTitle, color = ink(focused))
                Txt(status, T.Label, color = if (e.isOn && !focused) C.Lamp else inkMuted(focused))
            }
            if (e.dimmable) LevelBar(pct / 100f, Modifier.weight(1f), focused) else Box(Modifier.weight(1f))
            Toggle(e.isOn, focused)
        }
    }
}

@Composable
private fun SpeakersColumn(modifier: Modifier, scope: CoroutineScope) {
    val s by Haven.settings.state.collectAsState()
    Haven.ha.entities.collectAsState().value
    val speakers = Haven.ha.roomSpeakers(s)
    val pending = remember { HashMap<String, Job>() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        SectionTitle("Speakers", trailing = speakers.count { it.isOn }.takeIf { speakers.isNotEmpty() }?.let { "$it of ${speakers.size} on" })
        val playing = speakers.firstOrNull { it.state == "playing" }
        if (playing != null) {
            val title = playing.attrs.optString("media_title")
            val by = playing.attrs.optString("media_artist").ifBlank { playing.attrs.optString("app_name") }
            Row(
                Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(12.dp)).background(C.LampSoft).padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Icon(Icons.Music, 32.dp, C.Lamp)
                Txt(listOf(title, by).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Playing" }, T.Body, Modifier.weight(1f))
                Txt(playing.name, T.Label, color = C.Lamp)
            }
        }
        if (speakers.isEmpty()) {
            Txt("No speakers in this room.", T.Body, color = C.Stone)
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    Modifier.offset(x = (-16).dp).fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
                ) {
                    items(speakers, key = { it.id }) { sp ->
                        SpeakerRow(
                            sp,
                            onToggle = { scope.launch { Haven.ha.setPower(sp, !sp.isOn) } },
                            onAdjust = { step ->
                                val now = Haven.ha.entities.value[sp.id] ?: sp
                                val current = ((now.volume ?: 0f) * 100).toInt()
                                val target = (current + step).coerceIn(0, 100)
                                if (target == current || !now.canSetVolume) {
                                    false
                                } else {
                                    Haven.ha.assume(now.copyWith(attrs = mapOf("volume_level" to target / 100.0)))
                                    pending.remove(sp.id)?.cancel()
                                    pending[sp.id] = scope.launch {
                                        delay(300)
                                        Haven.ha.setVolume(sp, target / 100f)
                                    }
                                    true
                                }
                            },
                        )
                    }
                }
                BottomFade(48.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                KeyHint("on or off", key = "OK")
                KeyHint("volume", icon = Icons.ChevronsLeftRight)
            }
        }
    }
}

@Composable
private fun SpeakerRow(e: HaEntity, onToggle: () -> Unit, onAdjust: (Int) -> Boolean) {
    val sub = when (e.state) {
        "playing" -> e.attrs.optString("media_title").ifBlank { "Playing" }
        "paused" -> "Paused"
        "idle", "on" -> "On"
        "standby", "off" -> "Off"
        else -> e.state.replaceFirstChar { it.uppercase() }
    }
    Focusable(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth().height(112.dp),
        color = C.Surface,
        focusedScale = 1.03f,
        onKey = { ev -> adjustKey(ev, onAdjust, 5) },
    ) { focused ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Column(Modifier.width(260.dp)) {
                Txt(e.name, T.CardTitle, color = ink(focused))
                Txt(sub, T.Label, color = if (e.state == "playing" && !focused) C.Lamp else inkMuted(focused))
            }
            if (e.canSetVolume) LevelBar(if (e.isOn) e.volume ?: 0f else 0f, Modifier.weight(1f), focused) else Box(Modifier.weight(1f))
            Toggle(e.isOn, focused)
        }
    }
}

private fun adjustKey(ev: KeyEvent, onAdjust: (Int) -> Boolean, step: Int): Boolean {
    if (ev.type != KeyEventType.KeyDown) return false
    return when (ev.key) {
        Key.DirectionLeft -> onAdjust(-step)
        Key.DirectionRight -> onAdjust(step)
        else -> false
    }
}

/** Something isn't set up yet: the doorway mark, what's missing, and the one thing to do about it. */
@Composable
fun EmptyState(title: String, body: String, action: String, focus: FocusRequester, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        HavenMark(Modifier.width(76.dp).height(92.dp))
        Txt(title, T.Headline)
        Txt(body, T.Body, Modifier.width(1000.dp), C.Stone, maxLines = 4, align = TextAlign.Center)
        Spacer(height = 16.dp)
        Chip(action, onAction, focusRequester = focus)
    }
}
