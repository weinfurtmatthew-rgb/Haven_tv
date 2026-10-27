package app.haven.launcher.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.haven.launcher.Haven
import app.haven.launcher.art.ArtPiece
import app.haven.launcher.art.LoadedArt
import app.haven.launcher.data.ArtSource
import app.haven.launcher.data.BackgroundConfig
import app.haven.launcher.data.BgBlur
import app.haven.launcher.data.BgKind
import app.haven.launcher.data.GradientPreset
import app.haven.launcher.data.PosterStyle
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlin.random.Random

/** A background being tried in Settings: shown instead of the saved one until it's kept or let go. */
object BgPreview {
    var value: BackgroundConfig? by mutableStateOf(null)
}

/**
 * The profile's background behind every screen: the theme's colour, a colour, a gradient, or a picture (photos,
 * photo of the day, paintings, posters, what's playing), darkened by the profile's amount so text stays readable.
 */
@Composable
fun HavenBackground(cfg: BackgroundConfig, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(C.Hearth)) {
        when (cfg.kind) {
            BgKind.Theme -> Unit
            BgKind.Color -> Box(Modifier.fillMaxSize().background(Color(cfg.color)))
            BgKind.Gradient -> GradientBackground(cfg.gradient)
            BgKind.Posters -> if (cfg.posterStyle == PosterStyle.Wall) PosterWall(cfg) else PictureBackground(cfg)
            else -> PictureBackground(cfg)
        }
    }
}

/** Dithered, so the dark gradients don't band on an 8-bit TV panel. */
@Composable
private fun GradientBackground(g: GradientPreset) {
    val paint = remember { android.graphics.Paint().apply { isDither = true } }
    Canvas(Modifier.fillMaxSize()) {
        paint.shader = android.graphics.LinearGradient(
            0f, 0f, size.width, size.height,
            g.colors.map { it.toInt() }.toIntArray(), null, android.graphics.Shader.TileMode.CLAMP,
        )
        drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint) }
    }
}

@Composable
private fun PictureBackground(cfg: BackgroundConfig) {
    val s by Haven.settings.state.collectAsState()
    // What's playing changes with Home Assistant, so this reads the entities itself (CLAUDE.md).
    val entities by Haven.ha.entities.collectAsState()
    val playing = if (cfg.kind == BgKind.NowPlaying) remember(entities, s.haArea) { Haven.backgrounds.nowPlaying(s) } else null
    val image by produceState<ImageBitmap?>(null, cfg, playing) {
        var turn = Random.nextInt(1000)
        while (true) {
            val src = runCatching { Haven.backgrounds.pick(cfg, s, turn) }.getOrNull()
            val img = src?.let { runCatching { Haven.backgrounds.load(it, cfg.blur) }.getOrNull() }
            // Keep the last picture while a download fails; clear it when the source has nothing at all.
            if (img != null || src == null) value = img
            turn++
            if (cfg.kind == BgKind.NowPlaying) break // runs again when the cover changes
            val wait = if (cfg.kind == BgKind.Daily) 3 * 3600_000L else cfg.everyMin * 60_000L
            delay(if (src != null && img == null) 60_000L else wait)
        }
    }
    Crossfade(targetState = image, animationSpec = motion(1500), label = "background") { img ->
        if (img != null) {
            Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.High)
        }
    }
    Dim(cfg)
}

/** This week's posters, eight across and three down. */
@Composable
private fun PosterWall(cfg: BackgroundConfig) {
    val posters by produceState(emptyList<String>(), cfg.tmdbKey, cfg.posters, cfg.everyMin) {
        while (true) {
            val all = runCatching { Haven.backgrounds.posters(cfg) }.getOrDefault(emptyList()).mapNotNull { it.poster }
            if (all.isNotEmpty()) value = all.shuffled().take(24)
            delay(if (all.isEmpty()) 5 * 60_000L else cfg.everyMin * 60_000L)
        }
    }
    val soft = when (cfg.blur) {
        BgBlur.None -> Modifier
        BgBlur.Soft -> Modifier.blur(8.dp)
        BgBlur.Strong -> Modifier.blur(20.dp)
    }
    Column(Modifier.fillMaxSize().then(soft)) {
        for (row in 0 until 3) {
            Row {
                for (col in 0 until 8) {
                    val url = posters.getOrNull(row * 8 + col)
                    Box(Modifier.size(240.dp, 360.dp).background(C.Surface)) {
                        if (url != null) AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
            }
        }
    }
    Dim(cfg)
}

@Composable
private fun Dim(cfg: BackgroundConfig) {
    if (cfg.dimPct > 0) Box(Modifier.fillMaxSize().background(C.Hearth.copy(alpha = cfg.dimPct / 100f)))
}

/** A public-domain painting, darkened so the clock reads on top. Generated art when offline. */
@Composable
fun PaintingBackdrop() {
    val art by produceState<LoadedArt?>(null) {
        value = runCatching { Haven.art.load(Haven.art.next(ArtSource.Paintings)) }.getOrNull()
            ?: LoadedArt(ArtPiece.Generated(Random.nextLong()), null)
    }
    val shown = art ?: return
    val img = shown.image
    if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    else if (shown.piece is ArtPiece.Generated) GeneratedArt(shown.piece.seed, Modifier.fillMaxSize())
    Box(Modifier.fillMaxSize().background(C.Hearth.copy(alpha = 0.55f)))
}

/** A low sun under the bottom edge: lamp light fading out over the dark room. Dithered so it doesn't band. */
@Composable
fun SunriseGlow(rise: Float, modifier: Modifier) {
    val paint = remember { android.graphics.Paint().apply { isDither = true } }
    val lamp = C.Sunrise
    Canvas(modifier) {
        val r = size.height * (0.6f + 0.3f * rise)
        val cx = size.width / 2
        val cy = size.height + r * 0.3f
        fun a(alpha: Float) = lamp.copy(alpha = alpha.coerceIn(0f, 1f)).toArgb()
        paint.shader = android.graphics.RadialGradient(
            cx, cy, r,
            intArrayOf(a(0.62f * rise), a(0.22f * rise), a(0f)),
            floatArrayOf(0f, 0.5f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        )
        drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint) }
    }
}
