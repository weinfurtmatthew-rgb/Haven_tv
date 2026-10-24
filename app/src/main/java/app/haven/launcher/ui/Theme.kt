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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import app.haven.launcher.R

/** Colors from the Haven mockups (accent #7DD3FC on near-black). */
object C {
    val Bg = Color(0xFF0D1014)
    val Card = Color(0xFF171C23)
    val CardHi = Color(0xFF1F2630)
    val Line = Color(0xFF2F3946)
    val Track = Color(0xFF3A4553)
    val Ring = Color(0xFF5C6877)
    val Text = Color(0xFFEEF1F5)
    val Muted = Color(0xFFA3ADBA)
    val Accent = Color(0xFF7DD3FC)
    val Avatar = Color(0xFF2B4C7E)
    val Warm = Color(0xFFF7B955)
    val Danger = Color(0xFFFF7A70)
    val Scrim = Color(0xD90D1014)

    /** Card colors used when an app has no banner (from the "Continue watching" mockup row). */
    val CardPalette = listOf(
        Color(0xFF2B4C7E), Color(0xFF5B3F8C), Color(0xFF2F6B57), Color(0xFF8A5A2B), Color(0xFF6B2F3C), Color(0xFF566A82),
    )
}

val Manrope = FontFamily(
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

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

@Composable
fun Txt(
    text: String,
    size: Int,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.SemiBold,
    color: Color = C.Text,
    maxLines: Int = 1,
    align: TextAlign = TextAlign.Start,
    spacing: Float = 0f,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size.sp,
            fontWeight = weight,
            fontFamily = Manrope,
            textAlign = align,
            letterSpacing = spacing.sp,
            lineHeight = (size * 1.3f).sp,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
