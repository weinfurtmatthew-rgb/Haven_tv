package app.haven.launcher.ui

import android.text.format.DateFormat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val LONG_PRESS_MS = 600L

/** Tracks one OK press so a key-up only clicks the element that saw the key-down. */
private class PressState {
    var down = false
    var longFired = false
}

fun KeyEvent.isSelectKey(): Boolean = key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter

/**
 * A D-pad focusable surface: grows a little and gets the accent outline when focused,
 * clicks on OK, long-clicks when OK is held. [onKey] sees every other key while focused.
 */
@Composable
fun Focusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    color: Color = C.Card,
    focusedColor: Color = C.CardHi,
    focusedScale: Float = 1.04f,
    outline: Boolean = true,
    restingBorder: Color = Color.Transparent,
    focusRequester: FocusRequester? = null,
    onFocus: ((Boolean) -> Unit)? = null,
    onKey: ((KeyEvent) -> Boolean)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val press = remember { PressState() }
    val scale by animateFloatAsState(if (focused) focusedScale else 1f, tween(140), label = "scale")
    val bg by animateColorAsState(if (focused) focusedColor else color, tween(140), label = "bg")
    val borderColor = if (focused && outline) C.Accent else restingBorder
    Box(
        modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused) press.down = false
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
                            } else if (press.down && onLongClick != null && !press.longFired &&
                                (native.isLongPress || native.eventTime - native.downTime >= LONG_PRESS_MS)
                            ) {
                                press.longFired = true
                                onLongClick()
                            }
                            true
                        }
                        KeyEventType.KeyUp -> {
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
            .clip(shape)
            .background(bg),
    ) {
        content(focused)
        // Drawn last so banners and images never cover the focus outline.
        if (borderColor != Color.Transparent) Box(Modifier.matchParentSize().border(2.dp, borderColor, shape))
    }
}

@Composable
fun Icon(icon: ImageVector, size: Dp, color: Color = C.Text, modifier: Modifier = Modifier) {
    Image(
        imageVector = icon,
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = modifier.size(size),
    )
}

/** Round icon button from the top bar (56px). */
@Composable
fun CircleButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = C.Card,
    size: Dp = 56.dp,
    focusRequester: FocusRequester? = null,
) {
    Focusable(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = CircleShape,
        color = color,
        focusedColor = C.CardHi,
        focusedScale = 1.1f,
        focusRequester = focusRequester,
    ) { focused ->
        Icon(icon, size / 2, if (focused) C.Accent else C.Text, Modifier.align(Alignment.Center))
    }
}

/** Pill button used for scenes, presets and dialog actions. */
@Composable
fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    icon: ImageVector? = null,
    height: Dp = 56.dp,
    focusRequester: FocusRequester? = null,
) {
    Focusable(
        onClick = onClick,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(height / 2),
        color = if (selected) C.Accent else C.CardHi,
        focusedColor = if (selected) C.Accent else C.Line,
        focusedScale = 1.06f,
        focusRequester = focusRequester,
    ) { focused ->
        val ink = if (selected) C.Bg else C.Text
        Row(
            Modifier.fillMaxHeight().padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) Icon(icon, 26.dp, if (selected) C.Bg else if (focused) C.Accent else C.Text)
            Txt(label, 22, weight = if (selected) FontWeight.Bold else FontWeight.SemiBold, color = ink)
        }
    }
}

/** The on/off switch from the lights mockup (72x40). */
@Composable
fun Toggle(on: Boolean, modifier: Modifier = Modifier) {
    val knob by animateFloatAsState(if (on) 1f else 0f, tween(160), label = "knob")
    Box(
        modifier
            .size(72.dp, 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (on) C.Accent else C.Track),
    ) {
        Box(
            Modifier
                .offset(x = (4 + 32 * knob).dp, y = 4.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(if (on) C.Bg else C.Text),
        )
    }
}

/** Level bar (brightness, volume). [fraction] in 0..1. */
@Composable
fun LevelBar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 12.dp, color: Color = C.Accent) {
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(C.Track),
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                    .clip(RoundedCornerShape(height / 2))
                    .background(color),
            )
        }
    }
}

/** Round checkbox from the speakers mockup. */
@Composable
fun CheckDot(checked: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (checked) C.Accent else Color.Transparent)
            .border(3.dp, if (checked) C.Accent else C.Ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Check, 22.dp, C.Bg)
    }
}

/** Screen header from the lights mockup: back button, title, subtitle, clock on the right. */
@Composable
fun ScreenHeader(title: String, subtitle: String?, onBack: () -> Unit, backFocus: FocusRequester? = null) {
    val now by rememberNow()
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().height(80.dp), verticalAlignment = Alignment.CenterVertically) {
        CircleButton(Icons.ChevronLeft, onBack, focusRequester = backFocus)
        Txt(title, 48, Modifier.padding(start = 24.dp), weight = FontWeight.ExtraBold)
        if (subtitle != null) Txt(subtitle, 28, Modifier.padding(start = 24.dp, top = 8.dp), FontWeight.Medium, C.Muted)
        Box(Modifier.weight(1f))
        Txt(formatTime(now, DateFormat.is24HourFormat(ctx), withAmPm = true), 30, weight = FontWeight.Bold)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Txt(text, 28, modifier, FontWeight.Bold)
}

/** A full-screen dimmed overlay panel used instead of system dialogs. Content is centered. */
@Composable
fun Overlay(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(C.Scrim), contentAlignment = Alignment.Center, content = content)
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(C.Card)
            .padding(40.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        content = content,
    )
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

@Composable
fun Spacer(width: Dp = 0.dp, height: Dp = 0.dp) {
    Box(Modifier.width(width).height(height))
}
