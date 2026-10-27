package app.haven.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.haven.launcher.Haven
import app.haven.launcher.art.ArtCareLogic
import app.haven.launcher.art.ArtPiece
import app.haven.launcher.art.LoadedArt
import app.haven.launcher.data.ArtCare
import app.haven.launcher.data.ArtStyle
import app.haven.launcher.data.QuietMode
import app.haven.launcher.data.TvPanel
import kotlinx.coroutines.delay
import java.time.LocalTime
import kotlin.random.Random

private val DarkWall = Color(0xFF0B0A08)

/**
 * Art mode: a picture in one of three styles (full screen, thin frame, gallery). Any button exits.
 * Screen care: pixel shift, captions that fade, optional dimming, quiet hours, presence and a time limit,
 * all of which end in a black screen (pixels off on OLED) with no image work going on.
 */
@Composable
fun ArtScreen(onExit: () -> Unit, interactive: Boolean = true) {
    val settings by Haven.settings.state.collectAsState()
    val care = settings.artCare
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // A clock that ticks once a minute: drives quiet hours, pixel shift and the time limit.
    val minute by produceState(System.currentTimeMillis() / 60_000L) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            value = System.currentTimeMillis() / 60_000L
        }
    }
    val startedAt = remember { System.currentTimeMillis() }
    var lastPresentAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(care.presenceEntity) {
        if (care.presenceEntity.isBlank()) return@LaunchedEffect
        while (true) {
            Haven.ha.refresh()
            if (ArtCareLogic.isPresent(Haven.ha.entities.value[care.presenceEntity]?.state)) {
                lastPresentAt = System.currentTimeMillis()
            }
            delay(30_000)
        }
    }
    val hour = remember(minute) { LocalTime.now().hour }
    val rest = ArtCareLogic.restReason(care, hour, minute * 60_000L, lastPresentAt, startedAt)
    val quietDim = rest == null && care.quietMode == QuietMode.Dim && ArtCareLogic.inQuietHours(care, hour)

    var shown by remember { mutableStateOf<LoadedArt?>(null) }
    LaunchedEffect(settings.artSource, settings.artIntervalMin, rest == null) {
        if (rest != null) return@LaunchedEffect // resting: no downloads, no decoding
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

    // Captions show for a while after each new picture, then fade so no text sits still on the panel.
    var captionOn by remember { mutableStateOf(true) }
    LaunchedEffect(shown) {
        captionOn = true
        delay(ArtCareLogic.captionSeconds(care.strongProtection) * 1000L)
        captionOn = false
    }

    val focus = remember { FocusRequester() }
    var hint by remember { mutableStateOf(interactive) }
    LaunchedEffect(Unit) {
        delay(80)
        if (interactive) runCatching { focus.requestFocus() }
        delay(5_000)
        hint = false
    }

    val (dx, dy) = ArtCareLogic.shift(care.strongProtection, minute)
    val dim by animateFloatAsState(
        ((care.dimPct + if (quietDim) 60 else 0).coerceAtMost(85)) / 100f, tween(3000), label = "dim",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(if (rest != null || care.style != ArtStyle.Gallery) Color.Black else C.Plaster)
            .focusRequester(focus)
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown) onExit()
                true
            }
            .focusable(),
    ) {
        if (rest == null) {
            Box(Modifier.fillMaxSize().offset(dx.dp, dy.dp)) {
                Crossfade(targetState = shown, animationSpec = tween(1500), label = "art") { art ->
                    if (art != null) {
                        when (care.style) {
                            ArtStyle.FullScreen -> FullScreenArt(art, captionOn)
                            ArtStyle.ThinFrame -> ThinFrameArt(art, captionOn)
                            ArtStyle.Gallery -> FramedArt(art, captionOn)
                        }
                    }
                }
            }
            if (dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
        }
        AnimatedVisibility(
            visible = hint,
            enter = fadeIn(),
            exit = fadeOut(tween(800)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
        ) {
            Row(
                Modifier.height(64.dp).clip(RoundedCornerShape(32.dp)).background(C.Hearth).padding(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Txt("Art mode · ${settings.artSource.label}", T.Label)
                Txt("Press any button to leave", T.Label, color = C.Stone)
            }
        }
    }

    if (interactive && care.panel == TvPanel.Unknown) TvTypeDialog()
}

/** Asked once: OLED panels get stronger burn-in protection. */
@Composable
private fun TvTypeDialog() {
    val first = remember { FocusRequester() }
    fun pick(p: TvPanel) = Haven.settings.update { it.copy(artCare = it.artCare.copy(panel = p)) }
    HavenDialog(onDismiss = { pick(TvPanel.Oled) }) {
        Panel(Modifier.width(1040.dp)) {
            Txt("What kind of TV is this?", T.Title)
            Txt(
                "OLED screens can keep a faint ghost of a still picture, so on them Haven moves the art more often and " +
                    "hides captions sooner. You can change this later in Settings, under Art mode.",
                T.Body, color = C.Stone, maxLines = 4,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Chip("OLED", { pick(TvPanel.Oled) }, focusRequester = first)
                Chip("LCD or LED", { pick(TvPanel.Lcd) })
                Chip("Not sure", { pick(TvPanel.Oled) })
            }
        }
    }
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { first.requestFocus() }
    }
}

@Composable
private fun ArtImage(art: LoadedArt, scale: ContentScale, modifier: Modifier) {
    val img = art.image
    if (img != null) {
        Image(img, null, modifier, contentScale = scale)
    } else if (art.piece is ArtPiece.Generated) {
        GeneratedArt(art.piece.seed, modifier)
    }
}

/** Small caption in the corner, used by the full-screen and thin-frame styles. */
@Composable
private fun CornerCaption(art: LoadedArt, visible: Boolean, modifier: Modifier) {
    val a by animateFloatAsState(if (visible) 1f else 0f, tween(1500), label = "caption")
    if (a <= 0f) return
    // A museum label: solid, not see-through, so it never fights with the picture behind it.
    Column(
        modifier.alpha(a).widthIn(max = 1100.dp).clip(RoundedCornerShape(12.dp)).background(C.Hearth)
            .padding(horizontal = 28.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (art.piece.title.isNotBlank()) Txt(art.piece.title, T.Title, size = 30, maxLines = 2)
        val line = listOf(art.piece.subtitle, art.piece.credit).filter { it.isNotBlank() }.joinToString(" · ")
        if (line.isNotBlank()) Txt(line, T.Label, color = C.Stone, maxLines = 2)
    }
}

/** Edge to edge. Slightly enlarged so pixel shift never shows a black sliver. */
@Composable
private fun FullScreenArt(art: LoadedArt, captionOn: Boolean) {
    Box(Modifier.fillMaxSize()) {
        ArtImage(
            art, ContentScale.Crop,
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = 1.03f
                scaleY = 1.03f
            },
        )
        CornerCaption(art, captionOn, Modifier.align(Alignment.BottomStart).padding(start = 116.dp, bottom = 64.dp))
    }
}

/** A slim dark frame around the whole picture, filling about 95% of the screen. */
@Composable
private fun ThinFrameArt(art: LoadedArt, captionOn: Boolean) {
    val frame = 10f
    val aspect = art.image?.let { it.width.toFloat() / it.height } ?: (16f / 9f)
    val maxW = 1824f - 2 * frame
    val maxH = 1026f - 2 * frame
    val (w, h) = if (aspect > maxW / maxH) maxW to maxW / aspect else maxH * aspect to maxH
    Box(Modifier.fillMaxSize().background(DarkWall), contentAlignment = Alignment.Center) {
        Box(Modifier.background(C.Walnut).padding(frame.dp)) {
            ArtImage(art, ContentScale.Crop, Modifier.size(w.dp, h.dp))
        }
        CornerCaption(art, captionOn, Modifier.align(Alignment.BottomStart).padding(start = 116.dp, bottom = 64.dp))
    }
}

@Composable
private fun FramedArt(art: LoadedArt, captionOn: Boolean) {
    val photo = art.piece is ArtPiece.Photo
    val frame: Dp = if (photo) 20.dp else 28.dp
    val frameColor = if (photo) Color(0xFFE9E4DA) else C.Walnut
    val matColor = C.Mat
    val side = 70f
    val top = 70f
    val bottom = 110f
    // Largest picture that fits the mockup's 1560x920 frame area, keeping its aspect ratio.
    val aspect = art.image?.let { it.width.toFloat() / it.height } ?: (16f / 9f)
    val maxW = 1560f - 2 * frame.value - 2 * side
    val maxH = 920f - 2 * frame.value - top - bottom
    val (w, h) = if (aspect > maxW / maxH) maxW to maxW / aspect else maxH * aspect to maxH
    val captionAlpha by animateFloatAsState(if (captionOn) 1f else 0f, tween(1500), label = "mat caption")

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .shadow(30.dp, RoundedCornerShape(2.dp), ambientColor = Color(0x59281E14), spotColor = Color(0x59281E14))
                .background(frameColor)
                .padding(frame),
        ) {
            Column(Modifier.background(matColor).padding(start = side.dp, end = side.dp, top = top.dp)) {
                ArtImage(art, ContentScale.Crop, Modifier.size(w.dp, h.dp))
                Row(Modifier.size(w.dp, bottom.dp).alpha(captionAlpha), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        if (art.piece.title.isNotBlank()) Txt(art.piece.title, T.Title, color = C.LabelInk, size = 28)
                        val line = listOf(art.piece.subtitle, art.piece.credit).filter { it.isNotBlank() }.joinToString(" · ")
                        if (line.isNotBlank()) Txt(line, T.Label, color = C.LabelInk)
                    }
                }
            }
        }
    }
}

private data class PrintColors(val sky: Color, val deep: Color, val a: Color, val b: Color, val c: Color, val sun: Color)

/** Mid-century prints: earth pigments, a low sun, nothing neon. */
private val PRINTS = listOf(
    PrintColors(Color(0xFF1F3A4A), Color(0xFF152A36), Color(0xFFC8553D), Color(0xFFE9D8B4), Color(0xFF7E9C7A), Color(0xFFE8A33D)),
    PrintColors(Color(0xFFE9DCC3), Color(0xFF6F8F9B), Color(0xFFB5533C), Color(0xFFD9A35B), Color(0xFF3E5C50), Color(0xFFC8553D)),
    PrintColors(Color(0xFF1B2233), Color(0xFF10151F), Color(0xFFA65E46), Color(0xFFD8C3A0), Color(0xFF4F6E66), Color(0xFFE8C07A)),
    PrintColors(Color(0xFFE3B88A), Color(0xFF8C4F33), Color(0xFF6B2F2A), Color(0xFFF2E3C6), Color(0xFF3E5C50), Color(0xFFF4E6CC)),
    PrintColors(Color(0xFFA9C2C4), Color(0xFF3F6470), Color(0xFF2F4F3F), Color(0xFF7E9C7A), Color(0xFFEFE5D2), Color(0xFFF2B35E)),
)

/** Mid-century style shapes (sun, hills, arches, water) like the ArtPainting mockup, varied by [seed]. */
@Composable
fun GeneratedArt(seed: Long, modifier: Modifier = Modifier) {
    // Clipped: a hill may run past the edge, but never over the frame.
    Canvas(modifier.clipToBounds()) { drawComposition(seed) }
}

private fun DrawScope.drawComposition(seed: Long) {
    val r = Random(seed)
    val p = PRINTS[r.nextInt(PRINTS.size)]
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
