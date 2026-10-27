package app.haven.launcher.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.haven.launcher.R

/** Haven's colours (DESIGN.md): dark, warm and still. Lamp light is only for things that are on. */
object C {
    val Hearth = Color(0xFF14120F)
    val Surface = Color(0xFF28241F)
    val Raised = Color(0xFF35302A)
    val Line = Color(0xFF4A433B)
    val Track = Color(0xFF7E756B)
    val Linen = Color(0xFFF2EDE4)
    val Stone = Color(0xFFB9B0A3)
    val Dust = Color(0xFF8F877B)
    val Lamp = Color(0xFFF2B35E)
    val LampSoft = Color(0xFF3A2D1C)
    val Sage = Color(0xFFA8C49A)
    val Clay = Color(0xFFE8795E)
    val Scrim = Color(0xE614120F)

    // On a focused (linen) surface.
    val Ink = Hearth
    val InkMuted = Color(0xFF5E564C)
    val Ember = Color(0xFF8A4F0B)
    val TrackOnLinen = Color(0xFFA39A8C)

    // Art-mode materials.
    val Plaster = Color(0xFFD9D2C5)
    val Walnut = Color(0xFF3B2A1E)
    val Mat = Color(0xFFF4F0E8)
    val LabelInk = Color(0xFF3A332B)
}

/** Text colour on a control: hearth when the control is focused (linen), linen otherwise. */
fun ink(focused: Boolean): Color = if (focused) C.Ink else C.Linen

fun inkMuted(focused: Boolean): Color = if (focused) C.InkMuted else C.Stone

/** Fraunces 36pt Soft SemiBold: titles and greetings. */
val Fraunces = FontFamily(Font(R.font.fraunces_title, FontWeight.SemiBold))

/** Fraunces 72pt Soft SemiBold: the clocks and other display sizes. */
val FrauncesDisplay = FontFamily(Font(R.font.fraunces_display, FontWeight.SemiBold))

/** Atkinson Hyperlegible Next: everything you read or act on. */
val Atkinson = FontFamily(
    Font(R.font.atkinson_medium, FontWeight.Medium),
    Font(R.font.atkinson_semibold, FontWeight.SemiBold),
    Font(R.font.atkinson_bold, FontWeight.Bold),
)

private fun style(family: FontFamily, size: Int, weight: FontWeight, line: Float, tracking: Float = 0f, features: String? = null) =
    TextStyle(
        fontFamily = family,
        fontSize = size.sp,
        fontWeight = weight,
        lineHeight = line.em,
        letterSpacing = tracking.sp,
        fontFeatureSettings = features,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )

/** The type scale (DESIGN.md). Sizes are design pixels; nothing you need to read is under 24. */
object T {
    val Display = style(FrauncesDisplay, 240, FontWeight.SemiBold, 1.0f, -4f)
    val Clock = style(FrauncesDisplay, 88, FontWeight.SemiBold, 1.0f, -1f)
    val Headline = style(Fraunces, 56, FontWeight.SemiBold, 1.15f)
    val Title = style(Fraunces, 36, FontWeight.SemiBold, 1.2f)
    val CardTitle = style(Atkinson, 28, FontWeight.Bold, 1.25f)
    val Body = style(Atkinson, 26, FontWeight.Medium, 1.35f)
    val Label = style(Atkinson, 24, FontWeight.SemiBold, 1.3f)

    /** Tabular figures, for numbers that tick or line up: timers, scores, times in a column. */
    val Numeric = style(Atkinson, 26, FontWeight.Bold, 1.25f, features = "tnum")
}

/**
 * Lays everything out on the mockups' 1920x1080 canvas: inside, 1.dp (and 1.sp) is one design pixel,
 * whatever resolution or density the box reports.
 */
@Composable
fun DesignCanvas(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val scale = minOf(constraints.maxWidth / 1920f, constraints.maxHeight / 1080f)
        CompositionLocalProvider(LocalDensity provides Density(scale, 1f)) {
            content()
        }
    }
}

/** All text goes through here: a style from [T], optionally at another [size]. */
@Composable
fun Txt(
    text: String,
    style: TextStyle = T.Body,
    modifier: Modifier = Modifier,
    color: Color = C.Linen,
    maxLines: Int = 1,
    align: TextAlign = TextAlign.Start,
    size: Int? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, textAlign = align, fontSize = size?.sp ?: style.fontSize),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
