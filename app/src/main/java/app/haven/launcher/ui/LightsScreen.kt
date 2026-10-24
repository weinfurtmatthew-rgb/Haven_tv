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
import androidx.compose.ui.text.font.FontWeight
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

/** Lights and speakers for this room, from the LightsSpeakers mockup. */
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

    Column(Modifier.fillMaxSize().padding(horizontal = 96.dp, vertical = 56.dp), verticalArrangement = Arrangement.spacedBy(40.dp)) {
        ScreenHeader("Lights and speakers", settings.roomName, onBack = { nav.go(Screen.Home) }, backFocus = backFocus)
        when (val st = status) {
            HaStatus.NotConfigured -> EmptyState(
                "Connect Home Assistant",
                "Haven controls lights, scenes and speakers through Home Assistant. Open Settings and choose \"Set up from your phone\".",
                "Open settings", firstFocus,
            ) { nav.go(Screen.Settings) }
            is HaStatus.Failed -> EmptyState(
                "Can't reach Home Assistant", st.message, "Try again", firstFocus,
            ) { scope.launch { Haven.ha.refresh() } }
            else -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                LightsPanel(Modifier.weight(1f).fillMaxHeight(), scope, firstFocus)
                SpeakersPanel(Modifier.weight(1f).fillMaxHeight(), scope)
            }
        }
    }
}

@Composable
private fun LightsPanel(modifier: Modifier, scope: CoroutineScope, firstFocus: FocusRequester) {
    val s = Haven.settings.value
    val lights = Haven.ha.roomLights(s)
    val scenes = Haven.ha.roomScenes(s)
    var active by remember { mutableStateOf<String?>(null) }
    val pending = remember { HashMap<String, Job>() }

    Panel(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Lightbulb, 36.dp, C.Accent)
            Txt("Lights", 32, weight = FontWeight.ExtraBold)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            itemsIndexed(scenes, key = { _, e -> e.id }) { i, scene ->
                PillButton(scene.name, {
                    active = scene.id
                    scope.launch { Haven.ha.activate(scene); delay(800); Haven.ha.refresh() }
                }, selected = active == scene.id, focusRequester = if (i == 0) firstFocus else null)
            }
            item(key = "bright") {
                PillButton("Bright", {
                    active = "bright"
                    scope.launch { Haven.ha.setLights(lights, on = true, brightnessPct = 100); delay(600); Haven.ha.refresh() }
                }, selected = active == "bright", focusRequester = if (scenes.isEmpty()) firstFocus else null)
            }
            item(key = "off") {
                PillButton("All off", {
                    active = "off"
                    scope.launch { Haven.ha.setLights(lights.filter { it.isOn }, on = false); delay(600); Haven.ha.refresh() }
                }, selected = active == "off")
            }
        }
        if (lights.isEmpty()) {
            Txt(
                "No lights found for this room. Pick the room's Home Assistant area in Settings.",
                22, weight = FontWeight.Medium, color = C.Muted, maxLines = 3,
            )
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                items(lights, key = { it.id }) { light ->
                    LightRow(
                        light,
                        onToggle = {
                            active = null
                            scope.launch { Haven.ha.setLight(light, !light.isOn, if (!light.isOn) light.brightnessPct else null) }
                        },
                        onAdjust = { step ->
                            val current = if (light.isOn) light.brightnessPct ?: 100 else 0
                            val target = (current + step).coerceIn(0, 100)
                            if (target == current) {
                                false
                            } else {
                                active = null
                                Haven.ha.assume(
                                    light.copyWith(if (target > 0) "on" else "off", mapOf("brightness" to target * 255 / 100)),
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
            Txt("OK turns a light on or off.  ◀ ▶ changes brightness.", 18, weight = FontWeight.Medium, color = C.Muted)
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
        modifier = Modifier.fillMaxWidth().height(120.dp),
        color = C.CardHi,
        focusedColor = C.Line,
        focusedScale = 1.02f,
        onKey = { ev -> e.dimmable && adjustKey(ev, onAdjust, 10) },
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(Modifier.width(220.dp)) {
                Txt(e.name, 24, weight = FontWeight.Bold)
                Txt(status, 18, weight = FontWeight.Medium, color = C.Muted)
            }
            if (e.dimmable) LevelBar(pct / 100f, Modifier.weight(1f)) else Box(Modifier.weight(1f))
            Toggle(e.isOn)
        }
    }
}

@Composable
private fun SpeakersPanel(modifier: Modifier, scope: CoroutineScope) {
    val s = Haven.settings.value
    val speakers = Haven.ha.roomSpeakers(s)
    val pending = remember { HashMap<String, Job>() }
    Panel(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Speaker, 36.dp, C.Accent)
            Txt("Speakers", 32, weight = FontWeight.ExtraBold)
        }
        val playing = speakers.firstOrNull { it.state == "playing" }
        if (playing != null) {
            val title = playing.attrs.optString("media_title")
            val by = playing.attrs.optString("media_artist").ifBlank { playing.attrs.optString("app_name") }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.CardHi).padding(horizontal = 28.dp, vertical = 22.dp),
            ) {
                Txt("Playing on ${playing.name}", 26, weight = FontWeight.Bold)
                Txt(listOf(title, by).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Playing" }, 18, weight = FontWeight.Medium, color = C.Muted)
            }
        }
        if (speakers.isEmpty()) {
            Txt("No speakers found for this room.", 22, weight = FontWeight.Medium, color = C.Muted)
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                items(speakers, key = { it.id }) { sp ->
                    SpeakerRow(
                        sp,
                        onToggle = { scope.launch { Haven.ha.setPower(sp, !sp.isOn) } },
                        onAdjust = { step ->
                            val current = ((sp.volume ?: 0f) * 100).toInt()
                            val target = (current + step).coerceIn(0, 100)
                            if (target == current || !sp.canSetVolume) {
                                false
                            } else {
                                Haven.ha.assume(sp.copyWith(attrs = mapOf("volume_level" to target / 100.0)))
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
            Txt("OK turns a speaker on or off.  ◀ ▶ changes volume.", 18, weight = FontWeight.Medium, color = C.Muted)
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
        modifier = Modifier.fillMaxWidth().height(84.dp),
        color = if (e.isOn) C.CardHi else C.Card,
        focusedColor = C.Line,
        focusedScale = 1.02f,
        restingBorder = if (e.isOn) androidx.compose.ui.graphics.Color.Transparent else C.Line,
        onKey = { ev -> adjustKey(ev, onAdjust, 5) },
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            CheckDot(e.isOn)
            Column(Modifier.width(240.dp)) {
                Txt(e.name, 22, weight = FontWeight.Bold)
                Txt(sub, 17, weight = FontWeight.Medium, color = C.Muted)
            }
            if (e.canSetVolume) LevelBar(if (e.isOn) e.volume ?: 0f else 0f, Modifier.weight(1f), height = 10.dp)
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

@Composable
fun EmptyState(title: String, body: String, action: String, focus: FocusRequester, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Icon(Icons.House, 72.dp, C.Accent)
        Txt(title, 40, weight = FontWeight.ExtraBold)
        Txt(body, 24, Modifier.width(1000.dp), FontWeight.Medium, C.Muted, maxLines = 4, align = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(height = 12.dp)
        PillButton(action, onAction, selected = true, focusRequester = focus)
    }
}
