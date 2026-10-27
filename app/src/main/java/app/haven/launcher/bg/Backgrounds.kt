package app.haven.launcher.bg

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import app.haven.launcher.Haven
import app.haven.launcher.art.ArtPiece
import app.haven.launcher.art.ArtRepository
import app.haven.launcher.data.ArtSource
import app.haven.launcher.data.BackgroundConfig
import app.haven.launcher.data.BgBlur
import app.haven.launcher.data.BgKind
import app.haven.launcher.data.Settings
import app.haven.launcher.ha.HomeAssistant
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/** A movie or show from TMDB: a wide backdrop for one big picture, a poster for the wall. */
data class Poster(val title: String, val backdrop: String?, val poster: String?)

/** Bing's photo of the day: the picture and its credit line. */
data class DailyPhoto(val url: String, val title: String, val credit: String)

/**
 * Where background pictures come from: photos uploaded from the phone, Bing's photo of the day, public-domain
 * paintings (the art mode source), movie and show art from TMDB with the person's own key, and the cover art of
 * whatever Home Assistant says is playing in the room.
 */
class Backgrounds(private val context: Context, private val art: ArtRepository) {
    private val http = OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    val dir = File(context.filesDir, "backgrounds").apply { mkdirs() }

    private var daily: Pair<Long, DailyPhoto>? = null
    private var posterCache: Triple<String, Long, List<Poster>>? = null
    private val _status = MutableStateFlow<String?>(null)

    /** A short note about the last online source (a rejected TMDB key, today's photo), for Settings. */
    val status: StateFlow<String?> = _status

    fun files(): List<File> =
        dir.listFiles()?.filter { it.isFile && it.extension.lowercase() in ArtRepository.IMAGE_EXT }?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    /** What to show next for [cfg]: a file or a URL, or null when the source has nothing now. */
    suspend fun pick(cfg: BackgroundConfig, s: Settings, turn: Int): Any? = withContext(Dispatchers.IO) {
        when (cfg.kind) {
            BgKind.Photo -> {
                val all = files()
                if (cfg.photo.isNotBlank()) all.firstOrNull { it.name == cfg.photo } ?: all.firstOrNull()
                else all.getOrNull(Math.floorMod(turn, maxOf(all.size, 1)))
            }
            BgKind.Daily -> daily()?.url
            BgKind.Art -> (art.next(ArtSource.Paintings) as? ArtPiece.Painting)?.url(1686)
            BgKind.Posters -> posters(cfg).filter { it.backdrop != null }.let { list ->
                if (list.isEmpty()) null else list[Math.floorMod(turn, list.size)].backdrop
            }
            BgKind.NowPlaying -> nowPlaying(s)
            else -> null
        }
    }

    /** Decodes a background, softened when the profile asks: a small copy, box-blurred, drawn large. */
    suspend fun load(data: Any, blur: BgBlur): ImageBitmap? {
        val (w, h) = when (blur) {
            BgBlur.None -> 1920 to 1080
            BgBlur.Soft -> 480 to 270
            BgBlur.Strong -> 240 to 135
        }
        val req = ImageRequest.Builder(context).data(data).size(w, h).scale(Scale.FILL).allowHardware(false).build()
        val result = context.imageLoader.execute(req) as? SuccessResult ?: return null
        val bmp = result.drawable.toBitmap()
        if (blur == BgBlur.None) return bmp.asImageBitmap()
        return withContext(Dispatchers.Default) { blurred(bmp, if (blur == BgBlur.Soft) 4 else 3).asImageBitmap() }
    }

    private fun blurred(src: Bitmap, radius: Int): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        BoxBlur.blur(px, w, h, radius, passes = 3)
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    // ---- Bing photo of the day ----------------------------------------------------------------

    suspend fun daily(): DailyPhoto? = withContext(Dispatchers.IO) {
        daily?.let { (at, d) -> if (System.currentTimeMillis() - at < 3 * 3600_000L) return@withContext d }
        val mkt = Locale.getDefault().let { "${it.language}-${it.country}" }.takeIf { it.length == 5 } ?: "en-US"
        val found = runCatching {
            get("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=$mkt")?.let(BackgroundFeeds::parseBing)
        }.getOrNull()
        if (found != null) {
            daily = System.currentTimeMillis() to found
            _status.value = found.title.ifBlank { found.credit }
        }
        found ?: daily?.second
    }

    // ---- TMDB -----------------------------------------------------------------------------------

    /** This week's trending movies or shows, with the person's own TMDB key. Cached for a few hours. */
    suspend fun posters(cfg: BackgroundConfig): List<Poster> = withContext(Dispatchers.IO) {
        val key = cfg.tmdbKey.trim()
        if (key.isBlank()) {
            _status.value = "Needs a TMDB key"
            return@withContext emptyList()
        }
        val cacheKey = "${cfg.posters.path}:$key"
        posterCache?.let { (k, at, list) -> if (k == cacheKey && System.currentTimeMillis() - at < 3 * 3600_000L) return@withContext list }
        val out = ArrayList<Poster>()
        var rejected = false
        for (page in 1..2) {
            // A v3 key goes in the address; the long v4 "read access token" goes in a header.
            val v4 = key.length > 40
            val url = "https://api.themoviedb.org/3/trending/${cfg.posters.path}/week?page=$page" + if (v4) "" else "&api_key=$key"
            val req = Request.Builder().url(url).apply { if (v4) header("Authorization", "Bearer $key") }.build()
            runCatching {
                http.newCall(req).execute().use { r ->
                    if (r.code == 401) rejected = true
                    if (r.isSuccessful) out += BackgroundFeeds.parseTmdb(JSONObject(r.body!!.string()))
                }
            }
        }
        _status.value = when {
            rejected -> "TMDB didn't accept the key"
            out.isEmpty() -> "Can't reach TMDB right now"
            else -> null
        }
        if (out.isNotEmpty()) posterCache = Triple(cacheKey, System.currentTimeMillis(), out)
        out.ifEmpty { posterCache?.third.orEmpty() }
    }

    // ---- Home Assistant ---------------------------------------------------------------------------

    /** Cover art of what's playing in the room, if Home Assistant has it. */
    fun nowPlaying(s: Settings): String? {
        val playing = Haven.ha.roomSpeakers(s).filter { it.state == "playing" }
        val pic = playing.firstNotNullOfOrNull { e -> e.attrs.optString("entity_picture").takeIf { it.isNotBlank() && it != "null" } }
            ?: return null
        return if (pic.startsWith("http")) pic else HomeAssistant.normalizeUrl(s.haUrl) + pic
    }

    private fun get(url: String): JSONObject? =
        http.newCall(Request.Builder().url(url).header("User-Agent", "Haven TV").build()).execute().use { r ->
            if (r.isSuccessful) JSONObject(r.body!!.string()) else null
        }
}

/** Reads Bing's and TMDB's answers. Pure, so they can be unit tested. */
object BackgroundFeeds {
    fun parseBing(root: JSONObject): DailyPhoto? {
        val img = root.optJSONArray("images")?.optJSONObject(0) ?: return null
        val path = img.optString("url").ifBlank { return null }
        val url = if (path.startsWith("http")) path else "https://www.bing.com$path"
        return DailyPhoto(url, img.optString("title"), img.optString("copyright"))
    }

    fun parseTmdb(root: JSONObject): List<Poster> {
        val arr = root.optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            if (o.optBoolean("adult", false)) return@mapNotNull null
            fun img(key: String, size: String) =
                o.optString(key).takeIf { it.startsWith("/") }?.let { "https://image.tmdb.org/t/p/$size$it" }
            Poster(o.optString("title").ifBlank { o.optString("name") }, img("backdrop_path", "w1280"), img("poster_path", "w342"))
        }
    }
}

/** A three-pass box blur (close to a Gaussian) on ARGB pixels, in place. Pure, so it can be unit tested. */
object BoxBlur {
    fun blur(px: IntArray, w: Int, h: Int, radius: Int, passes: Int = 3) {
        if (radius < 1 || w < 2 || h < 2) return
        val tmp = IntArray(px.size)
        repeat(passes) {
            pass(px, tmp, w, h, radius, horizontal = true)
            pass(tmp, px, w, h, radius, horizontal = false)
        }
    }

    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val div = 2 * r + 1
        for (line in 0 until lines) {
            fun at(i: Int): Int {
                val k = i.coerceIn(0, len - 1)
                return if (horizontal) src[line * w + k] else src[k * w + line]
            }
            var sr = 0
            var sg = 0
            var sb = 0
            for (i in -r..r) {
                val p = at(i)
                sr += p shr 16 and 0xFF
                sg += p shr 8 and 0xFF
                sb += p and 0xFF
            }
            for (i in 0 until len) {
                val out = (0xFF shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                if (horizontal) dst[line * w + i] = out else dst[i * w + line] = out
                val add = at(i + r + 1)
                val sub = at(i - r)
                sr += (add shr 16 and 0xFF) - (sub shr 16 and 0xFF)
                sg += (add shr 8 and 0xFF) - (sub shr 8 and 0xFF)
                sb += (add and 0xFF) - (sub and 0xFF)
            }
        }
    }
}
