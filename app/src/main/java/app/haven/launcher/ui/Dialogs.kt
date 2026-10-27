package app.haven.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.haven.launcher.data.FocusLook
import app.haven.launcher.data.HeroKind
import app.haven.launcher.data.NavStyle
import kotlinx.coroutines.delay

/** A dialog window (so D-pad focus stays inside it) drawn on the 1920x1080 canvas over a dimmed screen. */
@Composable
fun HavenDialog(onDismiss: () -> Unit, alignment: Alignment = Alignment.Center, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DesignCanvas {
            Box(Modifier.fillMaxSize(), contentAlignment = alignment) { content() }
        }
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
            Icon(icon, 32.dp, inkIcon(focused))
            Txt(label, T.Body, color = ink(focused))
        }
    }
}

/** Asks focus for [r] once the dialog's window is up. */
@Composable
fun FocusOnOpen(r: FocusRequester, delayMs: Long = 80) {
    LaunchedEffect(Unit) {
        delay(delayMs)
        runCatching { r.requestFocus() }
    }
}

// ---- choices ------------------------------------------------------------------------------------

/** One choice in a [ChoiceDialog]. [family] shows the label in that font; [swatch] shows a colour. */
class Choice(
    val value: String,
    val label: String,
    val note: String? = null,
    val swatch: Color? = null,
    val family: FontFamily? = null,
)

/**
 * Pick one of a list. With [onPreview], moving focus over a choice shows it on the TV right away (fonts, colours,
 * sizes, backgrounds) and a small sample of the home screen sits at the top; OK keeps it ([onPick]), Back puts
 * back what was there ([onDismiss]).
 */
@Composable
fun ChoiceDialog(
    title: String,
    choices: List<Choice>,
    selected: String,
    note: String? = null,
    onPreview: ((String) -> Unit)? = null,
    sample: Boolean = onPreview != null,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val start = choices.indexOfFirst { it.value == selected }.coerceAtLeast(0)
    val requesters = remember(choices.size) { List(choices.size) { FocusRequester() } }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (start - 2).coerceAtLeast(0))
    HavenDialog(onDismiss, alignment = Alignment.CenterEnd) {
        Panel(Modifier.padding(end = 96.dp).width(780.dp).heightIn(max = 984.dp), padding = 36.dp) {
            Txt(title, T.Title, maxLines = 2)
            if (note != null) Txt(note, T.Body, color = C.Stone, maxLines = 3)
            if (sample) LookSample()
            LazyColumn(
                Modifier.weight(1f, fill = false).bleed(16.dp).fadeBottom(32.dp),
                state = list,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            ) {
                itemsIndexed(choices, key = { _, c -> c.value }) { i, c ->
                    ChoiceRow(c, c.value == selected, requesters[i], onFocus = { onPreview?.invoke(c.value) }) { onPick(c.value) }
                }
            }
        }
    }
    if (requesters.isNotEmpty()) FocusOnOpen(requesters[start])
}

@Composable
private fun ChoiceRow(c: Choice, isSelected: Boolean, requester: FocusRequester, onFocus: () -> Unit, onClick: () -> Unit) {
    Focusable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
        color = C.Raised,
        focusedScale = 1.03f,
        focusRequester = requester,
        onFocus = { if (it) onFocus() },
    ) { focused ->
        val l = lit(focused)
        Row(
            Modifier.align(Alignment.CenterStart).fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                Modifier.size(14.dp).clip(CircleShape).background(
                    when {
                        !isSelected -> Color.Transparent
                        l -> C.Ember
                        else -> C.Lamp
                    },
                ),
            )
            if (c.swatch != null) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(c.swatch).border(2.dp, C.Line, CircleShape))
            }
            Column(Modifier.weight(1f)) {
                val style = if (c.family != null) T.Body.copy(fontFamily = c.family) else T.Body
                Txt(
                    c.label, style, size = 28, maxLines = 2,
                    color = when {
                        l -> C.Ink
                        isSelected -> C.Lamp
                        else -> C.Linen
                    },
                )
                if (c.note != null) Txt(c.note, T.Label, color = inkMuted(focused), maxLines = 2)
            }
        }
    }
}

/**
 * A small home screen in the current look, so a preview shows what a choice does: the way around (a side menu or
 * tabs), the top of the screen, a row of tiles and two chips, in the colours, fonts, corners and focus chosen.
 */
@Composable
fun LookSample() {
    val cfg = Look.cfg
    val frame = if (cfg.focus == FocusLook.ColorFrame) C.Lamp else C.Linen
    Row(Modifier.fillMaxWidth().clip(L.shape).background(C.Hearth).border(2.dp, C.Line, L.shape)) {
        if (cfg.nav == NavStyle.Menu) {
            Column(Modifier.width(120.dp).background(C.Surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (i in 0 until 5) Box(Modifier.fillMaxWidth().height(20.dp).clip(L.pill(20.dp)).background(if (i == 0) C.Linen else C.Raised))
            }
        }
        Column(Modifier.weight(1f).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (cfg.nav == NavStyle.Tabs) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (i in 0 until 4) Box(Modifier.size(84.dp, 28.dp).clip(L.pill(28.dp)).background(if (i == 0) C.Linen else C.Raised))
                }
            }
            when (cfg.hero) {
                HeroKind.Spotlight -> Box(
                    Modifier.fillMaxWidth().height(120.dp).clip(L.shape)
                        .background(Brush.verticalGradient(listOf(Color(0xFF2B3F6B), Color(0xFFE8A060)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Txt("7:30", T.Display, color = Color.White, size = 72)
                }
                HeroKind.Scoreboard -> Txt("KC 21 – 17 BUF", T.Headline, size = 48)
                HeroKind.Movie, HeroKind.Show -> Column {
                    Txt(if (cfg.hero == HeroKind.Movie) "TRENDING THIS WEEK" else "NEW TONIGHT", T.Label, color = C.Lamp)
                    Txt(if (cfg.hero == HeroKind.Movie) "The featured movie" else "Your show", T.Headline, size = 48)
                }
                HeroKind.NowPlaying -> Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(84.dp).clip(L.shape).background(C.Raised), contentAlignment = Alignment.Center) { Icon(Icons.Music, 40.dp, C.Stone) }
                    Column {
                        Txt("Harbor Lights", T.Title)
                        Txt("Playing on the soundbar", T.Label, color = C.Stone)
                    }
                }
                HeroKind.HomeStatus -> Txt("Good evening", T.Headline, size = 48)
                HeroKind.Welcome -> Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(72.dp).clip(CircleShape).background(C.Lamp))
                    Txt("Hi there!", T.Headline, size = 56)
                }
                HeroKind.ClockBar -> Row(verticalAlignment = Alignment.Bottom) {
                    Txt("7:30", T.Clock, size = 64)
                    Txt("Saturday, May 3", T.Body, Modifier.padding(start = 20.dp, bottom = 6.dp), C.Stone)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(L.gap(20))) {
                for (i in 0 until 3) {
                    val w = L.fav(150)
                    val h = L.fav(84)
                    Box(
                        Modifier.size(w, h)
                            .then(if (i == 0 && cfg.focus != FocusLook.Lift) Modifier.border(4.dp, frame, L.shape) else Modifier)
                            .clip(L.shape)
                            .background(if (Look.glass) glassTint() else if (i == 0) C.Raised else C.Surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(listOf(Icons.Tv, Icons.Film, Icons.Music)[i], 36.dp, C.Stone)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                SampleChip("Movie night", focused = true)
                SampleChip("Reading", selected = true)
                Toggle(true)
            }
        }
    }
}

/** A chip drawn as if it were focused or selected, for [LookSample]. */
@Composable
private fun SampleChip(label: String, focused: Boolean = false, selected: Boolean = false) {
    val l = lit(focused)
    val frame = if (Look.cfg.focus == FocusLook.ColorFrame) C.Lamp else C.Linen
    Row(
        Modifier.height(56.dp)
            .then(if (focused && !l) Modifier.border(4.dp, frame, L.pill(56.dp)) else Modifier)
            .clip(L.pill(56.dp))
            .background(
                when {
                    l -> C.Linen
                    selected -> C.LampSoft
                    else -> C.Raised
                },
            )
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (selected) Box(Modifier.size(12.dp).clip(CircleShape).background(C.Lamp))
        Txt(
            label, T.Label,
            color = when {
                l -> C.Ink
                selected -> C.Lamp
                else -> C.Linen
            },
        )
    }
}

// ---- order and visibility -------------------------------------------------------------------------

class OrderItem(val key: String, val label: String, val note: String? = null)

/**
 * Turn things on or off and put them in order (home screen rows, menu sections, briefing sections, kids' apps).
 * What's on comes first, in its order, with arrows to move it; what's off is listed after, to add. Changes apply
 * as they're made, so the screen behind shows them.
 */
@Composable
fun OrderDialog(
    title: String,
    note: String?,
    items: List<OrderItem>,
    shown: Set<String>,
    onToggle: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    val on = items.filter { it.key in shown }
    val off = items.filter { it.key !in shown }
    val firstKey = remember { (on.firstOrNull() ?: off.firstOrNull())?.key }
    HavenDialog(onDismiss) {
        Panel(Modifier.width(1100.dp).heightIn(max = 1000.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(title, T.Title, Modifier.weight(1f))
                KeyHint("on or off", key = "OK")
            }
            if (note != null) Txt(note, T.Body, color = C.Stone, maxLines = 3)
            LazyColumn(
                Modifier.weight(1f, fill = false).bleed(16.dp).fadeBottom(48.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
            ) {
                itemsIndexed(on, key = { _, it -> "on:" + it.key }) { i, item ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        OrderRow(item, i + 1, true, if (item.key == firstKey) first else null, Modifier.weight(1f)) { onToggle(item.key) }
                        IconButton(Icons.ArrowUp, { onMove(item.key, -1) }, size = 72.dp)
                        IconButton(Icons.ArrowDown, { onMove(item.key, 1) }, size = 72.dp)
                    }
                }
                if (off.isNotEmpty()) {
                    item(key = "h:off") { SectionTitle("Not showing", Modifier.padding(top = 16.dp)) }
                    itemsIndexed(off, key = { _, it -> "off:" + it.key }) { _, item ->
                        OrderRow(item, null, false, if (item.key == firstKey) first else null, Modifier.fillMaxWidth()) { onToggle(item.key) }
                    }
                }
                item(key = "done") {
                    Box(Modifier.padding(top = 16.dp)) { Chip("Done", onDismiss) }
                }
            }
        }
    }
    FocusOnOpen(first)
}

@Composable
private fun OrderRow(item: OrderItem, number: Int?, on: Boolean, focus: FocusRequester?, modifier: Modifier, onClick: () -> Unit) {
    Focusable(
        onClick = onClick,
        modifier = modifier.heightIn(min = 88.dp),
        color = C.Raised,
        focusedScale = 1.03f,
        focusRequester = focus,
    ) { focused ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt(number?.toString() ?: "", T.Numeric, Modifier.width(48.dp), inkMuted(focused))
            Column(Modifier.weight(1f)) {
                Txt(item.label, T.CardTitle, color = ink(focused))
                if (!item.note.isNullOrBlank()) Txt(item.note, T.Label, color = inkMuted(focused), maxLines = 2)
            }
            Toggle(on, focused)
        }
    }
}

// ---- text -----------------------------------------------------------------------------------------

class TextEdit(val title: String, val initial: String, val hint: String, val onSave: (String) -> Unit)

/** Typing on the TV, for short things. Longer things (tokens, feeds) are easier from the phone page. */
@Composable
fun TextEditDialog(e: TextEdit, onClose: () -> Unit) {
    var value by remember { mutableStateOf(e.initial) }
    val field = remember { FocusRequester() }
    // At the top of the screen, so the on-screen keyboard doesn't cover the buttons.
    HavenDialog(onClose, alignment = Alignment.TopCenter) {
        Panel(Modifier.padding(top = 96.dp).width(1100.dp)) {
            Txt(e.title, T.Title)
            Box(
                Modifier.fillMaxWidth().height(88.dp).clip(L.shape).background(C.Raised).padding(horizontal = 28.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) Txt(e.hint, T.Body, color = C.Dust)
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(field),
                    singleLine = true,
                    textStyle = T.Body.copy(color = C.Linen, fontSize = T.CardTitle.fontSize),
                    cursorBrush = SolidColor(C.Lamp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        e.onSave(value)
                        onClose()
                    }),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Chip("Save", {
                    e.onSave(value)
                    onClose()
                })
                Chip("Cancel", onClose)
            }
        }
    }
    FocusOnOpen(field)
}

/** Yes or no, for things that can't be undone. */
@Composable
fun ConfirmDialog(title: String, body: String, yes: String, onYes: () -> Unit, onDismiss: () -> Unit) {
    val cancel = remember { FocusRequester() }
    HavenDialog(onDismiss) {
        Panel(Modifier.width(1000.dp)) {
            Txt(title, T.Title, maxLines = 2)
            Txt(body, T.Body, color = C.Stone, maxLines = 4)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Chip("Cancel", onDismiss, focusRequester = cancel)
                Chip(yes, {
                    onYes()
                    onDismiss()
                })
            }
        }
    }
    FocusOnOpen(cancel)
}
