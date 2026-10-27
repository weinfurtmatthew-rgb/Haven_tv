package app.haven.launcher.art

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.exifinterface.media.ExifInterface
import app.haven.launcher.data.ArtSource
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.random.Random

sealed interface ArtPiece {
    val key: String
    val title: String
    val subtitle: String
    val credit: String

    data class Painting(
        val imageId: String,
        val iiif: String,
        override val title: String,
        override val subtitle: String,
    ) : ArtPiece {
        override val key get() = "aic:$imageId"
        override val credit get() = "Art Institute of Chicago · Public domain"
        fun url(width: Int) = "$iiif/$imageId/full/$width,/0/default.jpg"
    }

    data class Photo(val file: File, override val title: String, override val subtitle: String) : ArtPiece {
        override val key get() = "photo:${file.name}"
        override val credit get() = "From your photos"
    }

    data class Generated(val seed: Long) : ArtPiece {
        override val key get() = "gen:$seed"
        override val title get() = "Haven original"
        override val subtitle get() = "No. ${(seed and 0xFFFF).toString().padStart(5, '0')}"
        override val credit get() = "Made on this TV"
    }
}

/** A piece ready to show: the decoded image, or null for generated art (drawn live). */
data class LoadedArt(val piece: ArtPiece, val image: ImageBitmap?)

/**
 * Art sources: public-domain paintings from the Art Institute of Chicago's open API, photos uploaded
 * from the phone setup page, and generated compositions that work offline.
 */
class ArtRepository(private val context: Context) {
    private val http = OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    val photosDir = File(context.filesDir, "photos").apply { mkdirs() }
    private val cacheFile = File(context.filesDir, "paintings.json")
    private var paintings: List<ArtPiece.Painting> = emptyList()
    private var paintingsAt = 0L
    private val recent = ArrayDeque<String>()

    fun photos(): List<File> =
        photosDir.listFiles()?.filter { it.isFile && it.extension.lowercase() in IMAGE_EXT }?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    suspend fun next(source: ArtSource): ArtPiece = withContext(Dispatchers.IO) {
        val piece = when (source) {
            ArtSource.Paintings -> nextPainting()
            ArtSource.Photos -> nextPhoto() ?: nextPainting()
            ArtSource.Mixed -> if (Random.nextBoolean()) nextPhoto() ?: nextPainting() else nextPainting() ?: nextPhoto()
            ArtSource.Generated -> null
        } ?: ArtPiece.Generated(Random.nextLong())
        recent.addLast(piece.key)
        while (recent.size > 30) recent.removeFirst()
        piece
    }

    /** Decodes the image for [piece]. Returns null when it can't be loaded (the caller moves on). */
    suspend fun load(piece: ArtPiece): LoadedArt? {
        if (piece is ArtPiece.Generated) return LoadedArt(piece, null)
        val sources: List<Any> = when (piece) {
            is ArtPiece.Painting -> listOf(piece.url(1686), piece.url(843))
            is ArtPiece.Photo -> listOf(piece.file)
            is ArtPiece.Generated -> emptyList()
        }
        for (data in sources) {
            val req = ImageRequest.Builder(context).data(data).size(1920, 1080).allowHardware(false).build()
            val result = context.imageLoader.execute(req)
            if (result is SuccessResult) return LoadedArt(piece, result.drawable.toBitmap().asImageBitmap())
        }
        return null
    }

    private fun nextPhoto(): ArtPiece.Photo? {
        val files = photos()
        if (files.isEmpty()) return null
        val fresh = files.filter { "photo:${it.name}" !in recent }.ifEmpty { files }
        val f = fresh.random()
        return ArtPiece.Photo(f, photoTitle(f), photoDate(f))
    }

    private fun nextPainting(): ArtPiece.Painting? {
        if (paintings.isEmpty() || System.currentTimeMillis() - paintingsAt > 12 * 3600_000L) {
            val fetched = runCatching { fetchPaintings() }.getOrDefault(emptyList())
            if (fetched.isNotEmpty()) {
                paintings = fetched
                paintingsAt = System.currentTimeMillis()
                runCatching { cacheFile.writeText(JSONArray(fetched.map { it.toJson() }).toString()) }
            } else if (paintings.isEmpty()) {
                paintings = readCache()
            }
        }
        if (paintings.isEmpty()) return null
        return paintings.filter { it.key !in recent }.ifEmpty { paintings }.random()
    }

    private fun fetchPaintings(): List<ArtPiece.Painting> {
        val out = LinkedHashMap<String, ArtPiece.Painting>()
        for (topic in TOPICS.shuffled().take(4)) {
            val url = "https://api.artic.edu/api/v1/artworks/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", topic)
                .addQueryParameter("query[term][is_public_domain]", "true")
                .addQueryParameter("fields", "id,title,artist_title,date_display,image_id,thumbnail")
                .addQueryParameter("limit", "60")
                .build()
            val req = Request.Builder().url(url).header("AIC-User-Agent", "Haven TV launcher (personal art mode)").build()
            runCatching {
                http.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) return@use
                    val root = JSONObject(r.body!!.string())
                    val iiif = root.optJSONObject("config")?.optString("iiif_url")?.ifBlank { null } ?: "https://www.artic.edu/iiif/2"
                    val data = root.optJSONArray("data") ?: return@use
                    for (i in 0 until data.length()) {
                        val o = data.getJSONObject(i)
                        val imageId = o.optString("image_id").takeIf { it.isNotBlank() && it != "null" } ?: continue
                        val thumb = o.optJSONObject("thumbnail")
                        val w = thumb?.optDouble("width", 0.0) ?: 0.0
                        val h = thumb?.optDouble("height", 0.0) ?: 0.0
                        // Landscape works suit a TV; skip portraits and odd panoramas.
                        if (h <= 0 || w / h < 1.3 || w / h > 2.1) continue
                        val artist = o.optString("artist_title").takeIf { it.isNotBlank() && it != "null" }
                        val date = o.optString("date_display").takeIf { it.isNotBlank() && it != "null" }
                        out[imageId] = ArtPiece.Painting(
                            imageId, iiif, o.optString("title").ifBlank { "Untitled" },
                            listOfNotNull(artist, date).joinToString(", "),
                        )
                    }
                }
            }
        }
        return out.values.toList()
    }

    private fun readCache(): List<ArtPiece.Painting> = runCatching {
        val arr = JSONArray(cacheFile.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ArtPiece.Painting(o.getString("imageId"), o.getString("iiif"), o.getString("title"), o.getString("subtitle"))
        }
    }.getOrDefault(emptyList())

    private fun ArtPiece.Painting.toJson() =
        JSONObject().put("imageId", imageId).put("iiif", iiif).put("title", title).put("subtitle", subtitle)

    private fun photoTitle(f: File): String {
        val base = f.nameWithoutExtension.replace('_', ' ').replace('-', ' ').trim()
        // Camera names like IMG_2041 or PXL_20250714 say nothing; leave the title off.
        return if (CAMERA_NAME.matches(base) || base.none { it.isLetter() }) "" else base
    }

    private fun photoDate(f: File): String = runCatching {
        val raw = ExifInterface(f).getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: ExifInterface(f).getAttribute(ExifInterface.TAG_DATETIME) ?: return ""
        val d = LocalDate.parse(raw.take(10), DateTimeFormatter.ofPattern("yyyy:MM:dd"))
        d.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
    }.getOrDefault("")

    companion object {
        val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "heic", "heif")
        private val CAMERA_NAME = Regex("^(img|dsc|dscn|pxl|photo|image|mvimg|screenshot)?\\s*[0-9 ]+.*$", RegexOption.IGNORE_CASE)
        private val TOPICS = listOf(
            "landscape", "impressionism", "seascape", "garden", "river", "harbor", "mountains",
            "japanese woodblock", "snow", "sunset", "countryside", "still life",
        )
    }
}
