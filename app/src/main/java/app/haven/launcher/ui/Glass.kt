package app.haven.launcher.ui

import android.graphics.Bitmap
import androidx.compose.foundation.border
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.haven.launcher.bg.BoxBlur
import kotlin.math.max
import kotlin.math.roundToInt

/** A blurred picture for glass to show, where the picture sits on screen (root pixels), and how much it's darkened. */
class GlassBackdrop(val image: ImageBitmap, val bounds: Rect, val dim: Float)

/**
 * Frosted glass (DESIGN.md, "Glass"): a surface that shows a blurred copy of the picture behind it under a light
 * tint and a hairline edge. Compose can't blur what's behind a surface, but Haven draws that picture itself, so
 * each glass surface draws the blurred picture at its own position instead.
 */
object Glass {
    /** The background picture, blurred, while the profile's background is a picture. */
    var backdrop: GlassBackdrop? by mutableStateOf(null)

    /** A small, very soft copy of [img] for glass to show (a few hundred pixels is plenty once it's this blurred). */
    fun frost(img: ImageBitmap): ImageBitmap {
        val src = img.asAndroidBitmap()
        val w = 160
        val h = max(1, (w.toFloat() * src.height / max(1, src.width)).roundToInt())
        val small = Bitmap.createScaledBitmap(src.copy(Bitmap.Config.ARGB_8888, false), w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        BoxBlur.blur(px, w, h, 4, passes = 3)
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}

/** What glass inside shows through it; null means the background picture ([Glass.backdrop]). The Spotlight photo provides its own. */
val LocalGlass = compositionLocalOf<GlassBackdrop?> { null }

/** The tint glass lays over the picture: light glass on the light scheme, a breath of white on the dark ones. */
fun glassTint(): Color = if (Look.r.palette.light) Color.White.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.11f)

fun glassEdge(): Color = if (Look.r.palette.light) Color.White.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.22f)

/**
 * Draws glass behind this element's content (after its clip): the backdrop at this element's place, darkened as the
 * background is, then the tint. Use [glassSurface] for a whole surface with its edge.
 */
fun Modifier.glassFill(): Modifier = composed {
    val backdrop = LocalGlass.current ?: Glass.backdrop
    val tint = glassTint()
    val hearth = C.Hearth
    var origin by remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { origin = it.positionInRoot() }
        .drawBehind {
            if (backdrop != null) {
                val b = backdrop.bounds
                val iw = backdrop.image.width.toFloat()
                val ih = backdrop.image.height.toFloat()
                val scale = max(b.width / iw, b.height / ih)
                val dw = iw * scale
                val dh = ih * scale
                drawImage(
                    backdrop.image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(iw.toInt(), ih.toInt()),
                    dstOffset = IntOffset((b.left + (b.width - dw) / 2 - origin.x).roundToInt(), (b.top + (b.height - dh) / 2 - origin.y).roundToInt()),
                    dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
                    filterQuality = FilterQuality.Low,
                )
                if (backdrop.dim > 0f) drawRect(hearth.copy(alpha = backdrop.dim))
            }
            drawRect(tint)
        }
}

/** A glass surface in [shape]: the frosted fill and its hairline edge. */
fun Modifier.glassSurface(shape: Shape): Modifier = composed {
    this.border(1.dp, glassEdge(), shape).clip(shape).glassFill()
}
