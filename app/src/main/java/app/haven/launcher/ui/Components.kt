package app.haven.launcher.ui

import android.text.format.DateFormat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val LONG_PRESS_MS = 600L
private val FocusMotion = tween<Float>(160, easing = FastOutSlowInEasing)

/** Tracks one OK press so a key-up only clicks the element that saw the key-down. */
private class PressState {
    var down = false
    var longFired = false
}

fun KeyEvent.isSelectKey(): Boolean = key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter

/** How a focusable shows focus (DESIGN.md, "Focus and state"). */
enum class FocusStyle {
    /** Buttons, chips, rows and text tiles: the surface turns linen and the content turns hearth. */
    Control,

    /** App banners, posters and art: a linen frame just outside the picture. */
    Picture,
}

/**
 * Every interactive element. A D-pad focusable surface that lifts when focused and shows focus the Haven way
 * ([FocusStyle]), dips while OK is held, clicks on OK and long-clicks when OK is held. [onKey] sees every other
 * key while focused. The content gets `focused` so it can switch to [ink] colours.
 */
@Composable
fun Focusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    color: Color = C.Raised,
    style: FocusStyle = FocusStyle.Control,
    focusedScale: Float = 1.04f,
    focusRequester: FocusRequester? = null,
    onFocus: ((Boolean) -> Unit)? = null,
    onKey: ((KeyEvent) -> Boolean)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val press = remember { PressState() }
    val scale by animateFloatAsState(
        when {
            pressed -> 0.97f
            focused -> focusedScale
            else -> 1f
        },
        FocusMotion, label = "scale",
    )
    val bg by animateColorAsState(
        if (focused && style == FocusStyle.Control) C.Linen else color,
        tween(160, easing = FastOutSlowInEasing), label = "bg",
    )
    val frame by animateFloatAsState(if (focused && style == FocusStyle.Picture) 1f else 0f, FocusMotion, label = "frame")
    Box(
        modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused) {
                    press.down = false
                    pressed = false
                }
                onFocus?.invoke(it.isFocused)
            }
            .onPreviewKeyEvent { ev ->
                if (ev.isSelectKey()) {
                    val native = ev.nativeKeyEvent
                    when (ev.type) {
                        KeyEventType.KeyDown -> {
                            if (native.repeatCount == 0) {
                                press.down = true
                                press.longFired = false
                                pressed = true
                            } else if (press.down && onLongClick != null && !press.longFired &&
                                (native.isLongPress || native.eventTime - native.downTime >= LONG_PRESS_MS)
                            ) {
                                press.longFired = true
                                pressed = false
                                onLongClick()
                            }
                            true
                        }
                        KeyEventType.KeyUp -> {
                            pressed = false
                            if (press.down && !press.longFired) {
                                val held = native.eventTime - native.downTime
                                if (onLongClick != null && held >= LONG_PRESS_MS) onLongClick() else onClick()
                            }
                            press.down = false
                            true
                        }
                        else -> false
                    }
                } else {
                    onKey?.invoke(ev) ?: false
                }
            }
            .focusable()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawWithContent {
                drawContent()
                if (frame > 0f) {
                    // 4 px linen frame, 4 px outside the picture, following its corners. Drawn outside the clip.
                    val w = 4.dp.toPx()
                    val gap = 4.dp.toPx() + w / 2
                    val radius = 12.dp.toPx() + gap
                    drawRoundRect(
                        color = C.Linen.copy(alpha = frame),
                        topLeft = Offset(-gap, -gap),
                        size = Size(size.width + 2 * gap, size.height + 2 * gap),
                        cornerRadius = CornerRadius(radius, radius),
                        style = Stroke(width = w),
                    )
                }
            }
            .clip(shape)
            .background(bg),
    ) {
        content(focused)
    }
}

@Composable
fun Icon(icon: ImageVector, size: Dp, color: Color = C.Linen, modifier: Modifier = Modifier) {
    Image(
        imageVector = icon,
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = modifier.size(size),
    )
}

/** Round icon button (64 px). */
@Composable
fun IconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    focusRequester: FocusRequester? = null,
    onFocus: ((Boolean) -> Unit)? = null,
) {
    Focusable(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = CircleShape,
        color = C.Surface,
        focusedScale = 1.08f,
        focusRequester = focusRequester,
        onFocus = onFocus,
    ) { focused ->
        Icon(icon, size / 2, ink(focused), Modifier.align(Alignment.Center))
    }
}

/**
 * Pill for scenes, presets, choices and dialog actions. Selected (lamp) and focused (linen) are different
 * things and look different: a selected chip keeps its lamp dot when focused.
 */
@Composable
fun Chip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    icon: ImageVector? = null,
    height: Dp = 64.dp,
    focusRequester: FocusRequester? = null,
    onFocus: ((Boolean) -> Unit)? = null,
) {
    Focusable(
        onClick = onClick,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(height / 2),
        color = if (selected) C.LampSoft else C.Raised,
        focusedScale = 1.06f,
        focusRequester = focusRequester,
        onFocus = onFocus,
    ) { focused ->
        Row(
            Modifier.fillMaxHeight().padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selected) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(if (focused) C.Ember else C.Lamp))
            } else if (icon != null) {
                Icon(icon, 28.dp, ink(focused))
            }
            Txt(
                label, T.Label,
                color = when {
                    focused -> C.Ink
                    selected -> C.Lamp
                    else -> C.Linen
                },
            )
        }
    }
}

/** On/off switch (72x40). On is lamp light; on a focused row the track turns hearth and the knob keeps the lamp. */
@Composable
fun Toggle(on: Boolean, focused: Boolean = false, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val knob by animateFloatAsState(if (on) 1f else 0f, FocusMotion, label = "knob")
    val track = when {
        !enabled -> if (focused) C.TrackOnLinen else C.Line
        focused -> if (on) C.Ink else C.TrackOnLinen
        else -> if (on) C.Lamp else C.Track
    }
    val dot = when {
        !enabled -> if (focused) C.InkMuted else C.Dust
        focused -> if (on) C.Lamp else C.Ink
        else -> if (on) C.Ink else C.Linen
    }
    Box(
        modifier
            .size(72.dp, 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(track),
    ) {
        Box(
            Modifier
                .offset(x = (4 + 32 * knob).dp, y = 4.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(dot),
        )
    }
}

/** Level bar (brightness, volume). [fraction] in 0..1. */
@Composable
fun LevelBar(fraction: Float, modifier: Modifier = Modifier, focused: Boolean = false, height: Dp = 12.dp) {
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(if (focused) C.TrackOnLinen else C.Track),
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                    .clip(RoundedCornerShape(height / 2))
                    .background(if (focused) C.Ink else C.Lamp),
            )
        }
    }
}

/** A remote key drawn as a keycap, for hints: [KeyCap]("OK") turns a light on or off. */
@Composable
fun KeyCap(label: String? = null, icon: ImageVector? = null) {
    Box(
        Modifier.height(40.dp).clip(RoundedCornerShape(10.dp)).background(C.Raised).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(icon, 28.dp, C.Stone) else Txt(label.orEmpty(), T.Label, color = C.Stone)
    }
}

/** One hint line under a list: a keycap and what it does. */
@Composable
fun KeyHint(text: String, key: String? = null, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        KeyCap(key, icon)
        Txt(text, T.Label, color = C.Dust)
    }
}

/** Screen header: back button, title, subtitle, clock on the right. */
@Composable
fun ScreenHeader(title: String, subtitle: String?, onBack: () -> Unit, backFocus: FocusRequester? = null) {
    val now by rememberNow()
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().height(96.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(Icons.ChevronLeft, onBack, focusRequester = backFocus)
        Txt(title, T.Headline, Modifier.padding(start = 32.dp))
        if (subtitle != null) Txt(subtitle, T.Body, Modifier.padding(start = 24.dp, top = 10.dp), C.Stone)
        Box(Modifier.weight(1f))
        Txt(formatTime(now, DateFormat.is24HourFormat(ctx), withAmPm = true), T.Numeric, color = C.Stone, size = 30)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Txt(text, T.Title)
        if (trailing != null) Txt(trailing, T.Label, Modifier.padding(start = 20.dp, bottom = 4.dp), C.Stone)
    }
}

/** A raised group (dialogs, the phone setup card). Screens themselves don't box their content. */
@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 40.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(C.Surface)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        content = content,
    )
}

/**
 * Scrolls a list only as far as needed to show the focused item, keeping [margin] clear at both ends, instead
 * of the TV default that pins focus at a third of the row (which cut the first card off).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberKeepVisible(margin: Dp): BringIntoViewSpec {
    val px = with(LocalDensity.current) { margin.toPx() }
    return remember(px) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                val start = px.coerceAtMost((containerSize - size) / 2).coerceAtLeast(0f)
                val end = containerSize - start
                return when {
                    offset >= start && offset + size <= end -> 0f
                    offset < start -> offset - start
                    else -> offset + size - end
                }
            }
        }
    }
}

/**
 * Lets a list draw [x] past both side edges, so a focused item's lift isn't clipped by the list, while its
 * content still lines up with everything else. Pair it with `contentPadding` of the same [x].
 */
fun Modifier.bleed(x: Dp): Modifier = layout { measurable, constraints ->
    val px = x.roundToPx()
    if (!constraints.hasBoundedWidth) {
        val p = measurable.measure(constraints)
        return@layout layout(p.width, p.height) { p.place(0, 0) }
    }
    val p = measurable.measure(constraints.copy(minWidth = constraints.minWidth + 2 * px, maxWidth = constraints.maxWidth + 2 * px))
    layout(p.width - 2 * px, p.height) { p.place(-px, 0) }
}

/** Current local time, ticking on the second. */
@Composable
fun rememberNow(): State<LocalDateTime> = produceState(LocalDateTime.now()) {
    while (true) {
        value = LocalDateTime.now()
        delay(1000L - System.currentTimeMillis() % 1000L)
    }
}

fun formatTime(t: LocalDateTime, use24h: Boolean, withAmPm: Boolean = false): String =
    if (use24h) t.format(DateTimeFormatter.ofPattern("H:mm"))
    else t.format(DateTimeFormatter.ofPattern(if (withAmPm) "h:mm a" else "h:mm", Locale.getDefault()))

fun amPm(t: LocalDateTime): String = t.format(DateTimeFormatter.ofPattern("a", Locale.getDefault()))

fun formatDate(t: LocalDateTime): String = t.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault()))

/** "Good morning" before noon, "Good afternoon" until six, "Good evening" after. */
fun greeting(hour: Int): String = when (hour) {
    in 4..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

@Composable
fun Spacer(width: Dp = 0.dp, height: Dp = 0.dp) {
    Box(Modifier.width(width).height(height))
}

/** Fills the rest of a list's area with a fade, so a cut-off row reads as "more below", not a glitch. */
@Composable
fun BoxScope.BottomFade(height: Dp = 64.dp) {
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(height)
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(listOf(C.Hearth.copy(alpha = 0f), C.Hearth)),
            ),
    )
}

@Composable
fun FullScreen(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), content = content)
}
