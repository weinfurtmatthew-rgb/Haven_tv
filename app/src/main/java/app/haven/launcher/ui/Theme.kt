package app.haven.launcher.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.haven.launcher.R
import app.haven.launcher.data.Accent
import app.haven.launcher.data.Corners
import app.haven.launcher.data.FocusLook
import app.haven.launcher.data.HeadingFont
import app.haven.launcher.data.LookConfig
import app.haven.launcher.data.Motion
import app.haven.launcher.data.TextFont
import app.haven.launcher.data.ThemePreset

/**
 * One colour scheme: the roles every screen uses (DESIGN.md, "Colour"). On the light scheme the roles keep their
 * jobs, so linen is still the text and the focus fill and ink is still what sits on a focused control.
 */
class Palette(
    val hearth: Color,
    val surface: Color,
    val raised: Color,
    val line: Color,
    val track: Color,
    val linen: Color,
    val stone: Color,
    val dust: Color,
    val ink: Color,
    val inkMuted: Color,
    val trackOnLinen: Color,
    val sage: Color,
    val clay: Color,
    val light: Boolean = false,
)

private fun pal(vararg c: Long, light: Boolean = false) = Palette(
    Color(c[0]), Color(c[1]), Color(c[2]), Color(c[3]), Color(c[4]), Color(c[5]), Color(c[6]), Color(c[7]),
    Color(c[8]), Color(c[9]), Color(c[10]), Color(c[11]), Color(c[12]), light,
)

// hearth, surface, raised, line, track, linen, stone, dust, ink, ink muted, track on linen, sage, clay.
// Every scheme is checked for the contrast DESIGN.md asks for (text pairs meet WCAG AA at their size).
val PALETTES: Map<ThemePreset, Palette> = mapOf(
    ThemePreset.Hearth to pal(0xFF14120F, 0xFF28241F, 0xFF35302A, 0xFF4A433B, 0xFF7E756B, 0xFFF2EDE4, 0xFFB9B0A3, 0xFF8F877B, 0xFF14120F, 0xFF5E564C, 0xFFA39A8C, 0xFFA8C49A, 0xFFE8795E),
    ThemePreset.Midnight to pal(0xFF0E1218, 0xFF1C232D, 0xFF28313D, 0xFF3A4553, 0xFF717E8F, 0xFFEDF1F6, 0xFFAAB5C3, 0xFF7F8B9B, 0xFF0E1218, 0xFF4F5A68, 0xFF97A2B0, 0xFFA3C9A8, 0xFFEE8170),
    ThemePreset.Forest to pal(0xFF0F1411, 0xFF1D2621, 0xFF28332C, 0xFF39473D, 0xFF718173, 0xFFEDF2EA, 0xFFADB9AB, 0xFF83907F, 0xFF0F1411, 0xFF505C51, 0xFF98A495, 0xFFB3D19E, 0xFFE8836A),
    ThemePreset.Plum to pal(0xFF151017, 0xFF261E2A, 0xFF322837, 0xFF463A4C, 0xFF7B6F82, 0xFFF3EEF5, 0xFFBBAFC0, 0xFF8F8395, 0xFF151017, 0xFF5C5262, 0xFFA398A8, 0xFFA9C79D, 0xFFEE7F79),
    ThemePreset.Graphite to pal(0xFF121212, 0xFF222222, 0xFF2E2E2E, 0xFF434343, 0xFF787878, 0xFFF0F0F0, 0xFFB5B5B5, 0xFF8B8B8B, 0xFF121212, 0xFF575757, 0xFFA0A0A0, 0xFFA6C79A, 0xFFEC7A62),
    ThemePreset.Black to pal(0xFF000000, 0xFF161616, 0xFF222222, 0xFF383838, 0xFF727272, 0xFFF2F2F2, 0xFFB2B2B2, 0xFF888888, 0xFF000000, 0xFF565656, 0xFF9C9C9C, 0xFFA6C79A, 0xFFEC7A62),
    ThemePreset.Contrast to pal(0xFF000000, 0xFF1A1A1A, 0xFF262626, 0xFF5C5C5C, 0xFFA0A0A0, 0xFFFFFFFF, 0xFFE3E3E3, 0xFFC4C4C4, 0xFF000000, 0xFF2E2E2E, 0xFF7A7A7A, 0xFFB8E0A6, 0xFFFF9A80),
    ThemePreset.Daylight to pal(0xFFE8E1D5, 0xFFFBF8F3, 0xFFDDD4C6, 0xFFC9BFAF, 0xFF837A6F, 0xFF1E1A16, 0xFF544C43, 0xFF665D52, 0xFFF7F3EC, 0xFFC2B9AC, 0xFF6F665B, 0xFF3F6B33, 0xFFB23A1E, light = true),
)

/** An accent in two strengths: bright for dark surfaces, deep for light ones (and for a linen focus fill). */
class AccentPair(val bright: Color, val deep: Color)

val ACCENTS: Map<Accent, AccentPair> = mapOf(
    Accent.Lamp to AccentPair(Color(0xFFF2B35E), Color(0xFF8A4F0B)),
    Accent.Coral to AccentPair(Color(0xFFF4927A), Color(0xFF9C3A1F)),
    Accent.Rose to AccentPair(Color(0xFFF29BBA), Color(0xFF9B2C57)),
    Accent.Lilac to AccentPair(Color(0xFFBFA8F5), Color(0xFF5B3FA8)),
    Accent.Sky to AccentPair(Color(0xFF86C8F2), Color(0xFF1D5C8C)),
    Accent.Mint to AccentPair(Color(0xFF82DDBF), Color(0xFF1B6A52)),
    Accent.Sage to AccentPair(Color(0xFFAFCB9F), Color(0xFF43603A)),
    Accent.Gold to AccentPair(Color(0xFFE9CB5E), Color(0xFF755C00)),
    Accent.Red to AccentPair(Color(0xFFF5857B), Color(0xFFA6271E)),
    Accent.Mono to AccentPair(Color(0xFFFFFFFF), Color(0xFF2B2B2B)),
)

/** Haven's colours (DESIGN.md), from the active profile's scheme. Lamp light is only for things that are on. */
object C {
    val Hearth: Color get() = Look.r.palette.hearth
    val Surface: Color get() = Look.r.palette.surface
    val Raised: Color get() = Look.r.palette.raised
    val Line: Color get() = Look.r.palette.line
    val Track: Color get() = Look.r.palette.track
    val Linen: Color get() = Look.r.palette.linen
    val Stone: Color get() = Look.r.palette.stone
    val Dust: Color get() = Look.r.palette.dust
    val Lamp: Color get() = Look.r.lamp
    val LampSoft: Color get() = Look.r.lampSoft
    val Sage: Color get() = Look.r.palette.sage
    val Clay: Color get() = Look.r.palette.clay
    val Scrim: Color get() = Look.r.palette.hearth.copy(alpha = 0.9f)

    // On a focused (linen) surface.
    val Ink: Color get() = Look.r.palette.ink
    val InkMuted: Color get() = Look.r.palette.inkMuted
    val Ember: Color get() = Look.r.ember
    val TrackOnLinen: Color get() = Look.r.palette.trackOnLinen

    /** Text on a bright accent (profile pictures), whatever the scheme. */
    val OnBright = Color(0xFF14120F)

    /** The alarm's sunrise: lamp light, whatever accent the profile chose. */
    val Sunrise = Color(0xFFF2B35E)

    // Art-mode materials: real ones, so they don't change with the scheme.
    val Plaster = Color(0xFFD9D2C5)
    val Walnut = Color(0xFF3B2A1E)
    val Mat = Color(0xFFF4F0E8)
    val LabelInk = Color(0xFF3A332B)
}

/** True when a focused control is filled with linen, so its content must switch to ink. */
fun lit(focused: Boolean): Boolean = focused && Look.fill

/** Text colour on a control: ink when the control is focused and filled, linen otherwise. */
fun ink(focused: Boolean): Color = if (lit(focused)) C.Ink else C.Linen

fun inkMuted(focused: Boolean): Color = if (lit(focused)) C.InkMuted else C.Stone

/** Icon colour on a control: ink when focused and filled, stone otherwise. */
fun inkIcon(focused: Boolean): Color = if (lit(focused)) C.Ink else C.Stone

// ---- fonts ----------------------------------------------------------------------------------------

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

private fun three(medium: Int, semibold: Int, bold: Int) = FontFamily(
    Font(medium, FontWeight.Medium),
    Font(semibold, FontWeight.SemiBold),
    Font(bold, FontWeight.Bold),
)

// Heading fonts ship one cut each, declared SemiBold (the weight headings ask for) so Android never fakes a bold.
private val Literata = three(R.font.literata_medium, R.font.literata_semibold, R.font.literata_bold)
private val LiterataHeading = FontFamily(Font(R.font.literata_semibold, FontWeight.SemiBold))
private val Playfair = FontFamily(Font(R.font.playfair_bold, FontWeight.SemiBold))
private val Bebas = FontFamily(Font(R.font.bebas_regular, FontWeight.SemiBold))
private val Fredoka = FontFamily(Font(R.font.fredoka_semibold, FontWeight.SemiBold))
private val Outfit = FontFamily(Font(R.font.outfit_semibold, FontWeight.SemiBold))
private val Lexend = three(R.font.lexend_medium, R.font.lexend_semibold, R.font.lexend_bold)
private val Nunito = three(R.font.nunito_medium, R.font.nunito_semibold, R.font.nunito_bold)
private val Inter = three(R.font.inter_medium, R.font.inter_semibold, R.font.inter_bold)

private fun textFamily(f: TextFont): FontFamily = when (f) {
    TextFont.Atkinson -> Atkinson
    TextFont.Lexend -> Lexend
    TextFont.Nunito -> Nunito
    TextFont.Inter -> Inter
    TextFont.Literata -> Literata
    TextFont.System -> FontFamily.Default
}

/** A heading font, for showing each choice in its own font. */
fun headingFamilyOf(f: HeadingFont): FontFamily = TypeSet(LookConfig(headingFont = f)).heading

fun textFamilyOf(f: TextFont): FontFamily = textFamily(f)

/** Fonts differ in how big they look at the same size; these bring them close to Atkinson and Fraunces. */
private fun textAdjust(f: TextFont): Float = when (f) {
    TextFont.Lexend -> 0.94f
    TextFont.Inter -> 0.96f
    TextFont.Literata -> 0.96f
    TextFont.Nunito -> 1.02f
    else -> 1f
}

private fun headingAdjust(f: HeadingFont): Float = when (f) {
    HeadingFont.Bebas -> 1.18f
    HeadingFont.SameAsText -> 0.95f
    HeadingFont.Literata -> 0.98f
    else -> 1f
}

private fun style(family: FontFamily, size: Float, weight: FontWeight, line: Float, tracking: Float = 0f, features: String? = null) =
    TextStyle(
        fontFamily = family,
        fontSize = size.sp,
        fontWeight = weight,
        lineHeight = line.em,
        letterSpacing = tracking.sp,
        fontFeatureSettings = features,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )

/** The type scale for one profile: its fonts at its text size. */
class TypeSet(cfg: LookConfig) {
    private val text = textFamily(cfg.textFont)
    val heading: FontFamily = when (cfg.headingFont) {
        HeadingFont.Fraunces -> Fraunces
        HeadingFont.Literata -> LiterataHeading
        HeadingFont.Playfair -> Playfair
        HeadingFont.Bebas -> Bebas
        HeadingFont.Fredoka -> Fredoka
        HeadingFont.Outfit -> Outfit
        HeadingFont.SameAsText -> text
    }
    val display: FontFamily = if (cfg.headingFont == HeadingFont.Fraunces) FrauncesDisplay else heading
    val headingFactor = cfg.textScale.factor * headingAdjust(cfg.headingFont)
    val textFactor = cfg.textScale.factor * textAdjust(cfg.textFont)
    private val headingWeight = if (cfg.headingFont == HeadingFont.SameAsText) FontWeight.Bold else FontWeight.SemiBold
    private val tight = if (cfg.headingFont == HeadingFont.Fraunces) 1f else 0f

    val displayStyle = style(display, 240 * headingFactor, headingWeight, 1.0f, -4f * tight)
    val clock = style(display, 88 * headingFactor, headingWeight, 1.0f, -1f * tight)
    val headline = style(heading, 56 * headingFactor, headingWeight, 1.15f)
    val title = style(heading, 36 * headingFactor, headingWeight, 1.2f)
    val cardTitle = style(text, 28 * textFactor, FontWeight.Bold, 1.25f)
    val body = style(text, 26 * textFactor, FontWeight.Medium, 1.35f)
    val label = style(text, 24 * textFactor, FontWeight.SemiBold, 1.3f)
    val numeric = style(text, 26 * textFactor, FontWeight.Bold, 1.25f, features = "tnum")

    /** How much a size given in design pixels grows for this style's font and the profile's text size. */
    fun factorOf(s: TextStyle): Float = if (s.fontFamily == heading || s.fontFamily == display) headingFactor else textFactor
}

/** The type scale (DESIGN.md), in the active profile's fonts. Sizes are design pixels; nothing you read is under 24. */
object T {
    val Display: TextStyle get() = Look.r.type.displayStyle
    val Clock: TextStyle get() = Look.r.type.clock
    val Headline: TextStyle get() = Look.r.type.headline
    val Title: TextStyle get() = Look.r.type.title
    val CardTitle: TextStyle get() = Look.r.type.cardTitle
    val Body: TextStyle get() = Look.r.type.body
    val Label: TextStyle get() = Look.r.type.label

    /** Tabular figures, for numbers that tick or line up: timers, scores, times in a column. */
    val Numeric: TextStyle get() = Look.r.type.numeric
}

// ---- shape and space ------------------------------------------------------------------------------

/** Layout choices of the active profile: corners, spacing and tile sizes (DESIGN.md, "Layout" and "Shape"). */
object L {
    /** Tiles, rows, cards and app banners. */
    val corner: Dp get() = Look.r.cfg.corners.px.dp
    val shape: Shape get() = RoundedCornerShape(corner)

    /** Panels and dialogs: a little rounder than tiles. */
    val panelShape: Shape get() = RoundedCornerShape((Look.r.cfg.corners.px * 5 / 3).coerceAtMost(40).dp)

    /** Chips and buttons: pills, unless the profile wants square or slightly rounded corners. */
    fun pill(height: Dp): Shape = when (Look.r.cfg.corners) {
        Corners.Square -> RoundedCornerShape(0.dp)
        Corners.Slight -> RoundedCornerShape(8.dp)
        else -> RoundedCornerShape(height / 2)
    }

    /** A gap of [px] design pixels at the profile's spacing. */
    fun gap(px: Int): Dp = (px * Look.r.cfg.spacing.factor).dp

    fun fav(px: Int): Dp = (px * Look.r.cfg.favSize.factor).dp

    fun app(px: Int): Dp = (px * Look.r.cfg.appSize.factor).dp
}

// ---- the live look ----------------------------------------------------------------------------------

/** Everything resolved from one [LookConfig]: read through [C], [T] and [L]. */
class ResolvedLook(val cfg: LookConfig) {
    val palette: Palette = PALETTES.getValue(cfg.theme)
    private val accent = ACCENTS.getValue(cfg.accent)
    val lamp: Color = if (palette.light) accent.deep else accent.bright
    val ember: Color = if (palette.light) accent.bright else accent.deep
    val lampSoft: Color = when {
        cfg.theme == ThemePreset.Hearth && cfg.accent == Accent.Lamp -> Color(0xFF3A2D1C)
        palette.light -> lerp(palette.surface, accent.bright, 0.30f)
        else -> lerp(palette.surface, accent.bright, 0.15f)
    }
    val type = TypeSet(cfg)
}

/**
 * The active profile's look. It's snapshot state, so every composable that reads a colour, a text style or a size
 * redraws when the profile (or a preview in Settings) changes it.
 */
object Look {
    var r: ResolvedLook by mutableStateOf(ResolvedLook(LookConfig()))
        private set

    fun apply(cfg: LookConfig) {
        if (r.cfg != cfg) r = ResolvedLook(cfg)
    }

    val cfg: LookConfig get() = r.cfg

    /** Focused controls fill with linen (the default); otherwise they get a frame. */
    val fill: Boolean get() = r.cfg.focus == FocusLook.Fill

    val reduced: Boolean get() = r.cfg.motion == Motion.Reduced
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

/** All text goes through here: a style from [T], optionally at another [size] (design pixels at standard size). */
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
    val fontSize = if (size != null) (size * Look.r.type.factorOf(style)).sp else style.fontSize
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, textAlign = align, fontSize = fontSize),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
