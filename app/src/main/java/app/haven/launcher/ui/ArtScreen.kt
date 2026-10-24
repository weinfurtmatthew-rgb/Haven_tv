package app.haven.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.haven.launcher.Haven
import app.haven.launcher.art.ArtPiece
import app.haven.launcher.art.LoadedArt
import kotlinx.coroutines.delay
import kotlin.random.Random

private val Wall = Color(0xFFD8D2C6)

/** Art mode from the mockups: a framed picture on a warm wall. Any button exits. */
@Composable
fun ArtScreen(onExit: () -> Unit, interactive: Boolean = true) {
    val settings by Haven.settings.state.collectAsState()
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    var shown by remember { mutableStateOf<LoadedArt?>(null) }
    LaunchedEffect(settings.artSource, settings.artIntervalMin) {
        var failures = 0
        while (true) {
            val piece = Haven.art.next(settings.artSource)
            val loaded = runCatching { Haven.art.load(piece) }.getOrNull()
            if (loaded != null) {
                shown = loaded
                failures = 0
                delay(settings.artIntervalMin.coerceAtLeast(1) * 60_000L)
            } else {
                // Couldn't download that one; try another soon, and fall back to generated art if the network is down.
                failures++
                if (failures >= 3 && shown == null) shown = LoadedArt(ArtPiece.Generated(Random.nextLong()), null)
                delay(if (failures < 3) 500L else 60_000L)
            }
        }
    }

    val focus = remember { FocusRequester() }
    var hint by remember { mutableStateOf(interactive) }
    LaunchedEffect(Unit) {
        delay(80)
        if (interactive) runCatching { focus.requestFocus() }
        delay(5_000)
        hint = false
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Wall)
            .focusRequester(focus)
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown) onExit()
                true
            }
            .focusable(),
    ) {
        Crossfade(targetState = shown, animationSpec = tween(1500), label = "art") { art ->
            if (art != null) FramedArt(art)
        }
        AnimatedVisibility(
            visible = hint,
            enter = fadeIn(),
            exit = fadeOut(tween(800)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
        ) {
            Row(
                Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xDB0D1014)).padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Txt("Art mode · ${settings.artSource.label}", 18, weight = FontWeight.SemiBold)
                Txt("Press any button to exit", 18, weight = FontWeight.Medium, color = C.Muted)
            }
        }
    }
}

@Composable
private fun FramedArt(art: LoadedArt) {
    val photo = art.piece is ArtPiece.Photo
    val frame: Dp = if (photo) 20.dp else 28.dp
    val frameColor = if (photo) Color(0xFFE9E4DA) else Color(0xFF2E2219)
    val matColor = if (photo) Color(0xFFFBFAF7) else Color(0xFFF3EFE8)
    val side = 70f
    val top = 70f
    val bottom = 110f
    // Largest picture that fits the mockup's 1560x920 frame area, keeping its aspect ratio.
    val aspect = art.image?.let { it.width.toFloat() / it.height } ?: (16f / 9f)
    val maxW = 1560f - 2 * frame.value - 2 * side
    val maxH = 920f - 2 * frame.value - top - bottom
    val (w, h) = if (aspect > maxW / maxH) maxW to maxW / aspect else maxH * aspect to maxH

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .shadow(30.dp, RoundedCornerShape(2.dp), ambientColor = Color(0x59281E14), spotColor = Color(0x59281E14))
                .background(frameColor)
                .padding(frame),
        ) {
            Column(Modifier.background(matColor).padding(start = side.dp, end = side.dp, top = top.dp)) {
                Box(Modifier.size(w.dp, h.dp)) {
                    val img = art.image
                    if (img != null) {
                        Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else if (art.piece is ArtPiece.Generated) {
                        GeneratedArt(art.piece.seed, Modifier.fillMaxSize())
                    }
                }
                Row(Modifier.size(w.dp, bottom.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (art.piece.title.isNotBlank()) {
                            Txt(art.piece.title, 24, Modifier.weight(1f, fill = false), FontWeight.Bold, Color(0xFF5B5348))
                        }
                        if (art.piece.subtitle.isNotBlank()) {
                            Txt(art.piece.subtitle, 22, weight = FontWeight.Medium, color = Color(0xFF5B5348))
                        }
                    }
                    Txt(art.piece.credit, 18, Modifier.padding(start = 24.dp), FontWeight.Medium, Color(0xFF6A6256))
                }
            }
        }
    }
}

private data class Palette(val sky: Color, val deep: Color, val a: Color, val b: Color, val c: Color, val sun: Color)

private val PALETTES = listOf(
    Palette(Color(0xFF214E5F), Color(0xFF183A4C), Color(0xFFC8553D), Color(0xFFF2E3C6), Color(0xFF8FB9A8), Color(0xFFE8A33D)),
    Palette(Color(0xFFA9CCE6), Color(0xFF4F86A8), Color(0xFF5E8C6A), Color(0xFF7BA37F), Color(0xFFF3EFE8), Color(0xFFFBE7B0)),
    Palette(Color(0xFF2A2340), Color(0xFF1B1730), Color(0xFFB9A2FF), Color(0xFFFF8A7A), Color(0xFFF4E9D8), Color(0xFFF7B955)),
    Palette(Color(0xFFE9D8C4), Color(0xFFB98B6A), Color(0xFF6B2F3C), Color(0xFFD9A066), Color(0xFF3E5C50), Color(0xFFC8553D)),
    Palette(Color(0xFF12304A), Color(0xFF0B2236), Color(0xFF2F6B57), Color(0xFF86EFAC), Color(0xFFF3EFE8), Color(0xFF7DD3FC)),
)

/** Mid-century style shapes (sun, hills, arches, water) like the ArtPainting mockup, varied by [seed]. */
@Composable
fun GeneratedArt(seed: Long, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawComposition(seed) }
}

private fun DrawScope.drawComposition(seed: Long) {
    val r = Random(seed)
    val p = PALETTES[r.nextInt(PALETTES.size)]
    val w = size.width
    val h = size.height
    val horizon = h * (0.6f + r.nextFloat() * 0.18f)
    drawRect(p.sky)
    drawRect(p.deep, topLeft = Offset(0f, horizon), size = Size(w, h - horizon))
    // sun
    val sunR = h * (0.14f + r.nextFloat() * 0.1f)
    val sunX = w * (0.15f + r.nextFloat() * 0.7f)
    val sunY = horizon - h * (0.25f + r.nextFloat() * 0.25f)
    drawCircle(p.sun, sunR, Offset(sunX, sunY))
    // hills / arches sitting on the horizon
    val shapes = 3 + r.nextInt(2)
    val colors = listOf(p.a, p.b, p.c).shuffled(r)
    for (i in 0 until shapes) {
        val sw = w * (0.18f + r.nextFloat() * 0.3f)
        val sx = w * r.nextFloat() * 0.9f - sw * 0.2f
        val sh = h * (0.18f + r.nextFloat() * 0.35f)
        val color = colors[i % colors.size]
        when (r.nextInt(3)) {
            0 -> { // half circle
                drawArc(color, 180f, 180f, true, topLeft = Offset(sx, horizon - sw / 2), size = Size(sw, sw))
            }
            1 -> { // arch: rounded top, straight sides
                val path = Path().apply {
                    moveTo(sx, horizon)
                    lineTo(sx, horizon - sh + sw / 2)
                    arcTo(androidx.compose.ui.geometry.Rect(sx, horizon - sh, sx + sw, horizon - sh + sw), 180f, 180f, false)
                    lineTo(sx + sw, horizon)
                    close()
                }
                drawPath(path, color)
            }
            else -> { // long rounded hill
                drawRoundRect(
                    color, topLeft = Offset(sx, horizon - sh * 0.6f), size = Size(sw * 1.4f, sh * 0.6f + 2f),
                    cornerRadius = CornerRadius(sh * 0.6f, sh * 0.6f),
                )
            }
        }
    }
    // the band under the horizon covers the shapes' lower halves, plus a few light lines on the water
    drawRect(p.deep, topLeft = Offset(0f, horizon + 2f), size = Size(w, h - horizon))
    drawRect(Color(0xFFF3EFE8), topLeft = Offset(0f, horizon), size = Size(w, 4f))
    repeat(2 + r.nextInt(2)) { i ->
        val lw = w * (0.12f + r.nextFloat() * 0.18f)
        val lx = w * (0.2f + r.nextFloat() * 0.5f)
        val ly = horizon + (h - horizon) * (0.3f + i * 0.22f)
        drawRoundRect(
            Color.White.copy(alpha = 0.35f), topLeft = Offset(lx, ly), size = Size(lw, 8f), cornerRadius = CornerRadius(4f, 4f),
        )
    }
}
