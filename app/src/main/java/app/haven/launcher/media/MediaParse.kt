package app.haven.launcher.media

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class MediaKind { Movie, Show }

/** The lists the movie and TV rows show. */
enum class MediaList { TrendingMovies, InTheaters, ComingSoon, TopMovies, TrendingShows, Tonight, NewStreaming, YourShows }

/**
 * A movie or show for a poster, a wide card or the featured spot at the top. [note] is the line under it
 * ("Tonight 9:00 PM · NBC"), [badge] a short flag ("NEW TONIGHT").
 */
data class MediaItem(
    val key: String,
    val kind: MediaKind,
    val title: String,
    val year: String = "",
    val rating: Double? = null,
    val overview: String = "",
    val poster: String? = null,
    val backdrop: String? = null,
    val genres: List<String> = emptyList(),
    val note: String = "",
    val badge: String = "",
    val tmdbId: Int? = null,
    val showId: Int? = null,
    val network: String = "",
    val at: Instant? = null,
)

/** One episode on the schedule (TVmaze). */
data class Airing(
    val showId: Int,
    val show: String,
    val season: Int,
    val number: Int,
    val episode: String,
    val at: Instant?,
    val network: String,
    val web: Boolean,
    val poster: String?,
    val summary: String,
    val weight: Int,
    val genres: List<String>,
    val type: String,
)

data class Episode(val season: Int, val number: Int, val name: String, val at: Instant?)

/** A show from TVmaze, with its next and last episodes when asked for. */
data class ShowInfo(
    val id: Int,
    val name: String,
    val poster: String?,
    val network: String,
    val status: String,
    val summary: String,
    val genres: List<String>,
    val rating: Double?,
    val premiered: String,
    val days: List<String>,
    val time: String,
    val next: Episode? = null,
    val previous: Episode? = null,
)

/** Where to watch something and its trailer, for the details dialog. */
data class Details(val runtimeMin: Int?, val genres: List<String>, val providers: List<String>, val trailer: String?)

/** Reads TVmaze's and TMDB's answers. Pure, so they can be unit tested. */
object MediaParse {
    private val TAGS = Regex("<[^>]+>")

    fun text(html: String?): String = (html ?: "").replace(TAGS, "").replace("&amp;", "&").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&nbsp;", " ").trim()

    private fun JSONObject.str(k: String): String = if (isNull(k)) "" else optString(k)

    private fun JSONArray?.strs(): List<String> = if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }

    private fun image(o: JSONObject?): String? = o?.optJSONObject("image")?.let { it.str("medium").ifBlank { it.str("original") } }?.ifBlank { null }

    private fun network(show: JSONObject): Pair<String, Boolean> {
        show.optJSONObject("network")?.str("name")?.takeIf { it.isNotBlank() }?.let { return it to false }
        return (show.optJSONObject("webChannel")?.str("name") ?: "") to true
    }

    /** /schedule and /schedule/web: episodes with their show inline or embedded. */
    fun schedule(arr: JSONArray?): List<Airing> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val e = arr.optJSONObject(i) ?: return@mapNotNull null
            val show = e.optJSONObject("show") ?: e.optJSONObject("_embedded")?.optJSONObject("show") ?: return@mapNotNull null
            val id = show.optInt("id", 0).takeIf { it > 0 } ?: return@mapNotNull null
            val (net, web) = network(show)
            Airing(
                showId = id,
                show = show.str("name"),
                season = e.optInt("season", 0),
                number = e.optInt("number", 0),
                episode = e.str("name"),
                at = runCatching { Instant.parse(e.str("airstamp").replace("+00:00", "Z")) }.getOrNull()
                    ?: runCatching { java.time.OffsetDateTime.parse(e.str("airstamp")).toInstant() }.getOrNull(),
                network = net,
                web = web,
                poster = image(show),
                summary = text(show.str("summary")),
                weight = show.optInt("weight", 0),
                genres = show.optJSONArray("genres").strs(),
                type = e.str("type"),
            )
        }
    }

    private fun episode(o: JSONObject?): Episode? {
        if (o == null) return null
        val at = runCatching { java.time.OffsetDateTime.parse(o.str("airstamp")).toInstant() }.getOrNull()
        return Episode(o.optInt("season", 0), o.optInt("number", 0), o.str("name"), at)
    }

    /** /shows/{id} (with embedded next and previous episodes) and the shows inside /search/shows. */
    fun show(o: JSONObject?): ShowInfo? {
        if (o == null) return null
        val id = o.optInt("id", 0).takeIf { it > 0 } ?: return null
        val sched = o.optJSONObject("schedule")
        val emb = o.optJSONObject("_embedded")
        return ShowInfo(
            id = id,
            name = o.str("name"),
            poster = image(o),
            network = network(o).first,
            status = o.str("status"),
            summary = text(o.str("summary")),
            genres = o.optJSONArray("genres").strs(),
            rating = o.optJSONObject("rating")?.optDouble("average")?.takeIf { !it.isNaN() },
            premiered = o.str("premiered"),
            days = sched?.optJSONArray("days").strs(),
            time = sched?.str("time").orEmpty(),
            next = episode(emb?.optJSONObject("nextepisode")),
            previous = episode(emb?.optJSONObject("previousepisode")),
        )
    }

    fun search(arr: JSONArray?): List<ShowInfo> =
        if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { show(arr.optJSONObject(it)?.optJSONObject("show")) }

    /** /shows/{id}/images: the main wide background, else any background. */
    fun background(arr: JSONArray?): String? {
        if (arr == null) return null
        val all = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.filter { it.str("type") == "background" }
        val pick = all.firstOrNull { it.optBoolean("main") } ?: all.firstOrNull() ?: return null
        val res = pick.optJSONObject("resolutions") ?: return null
        return (res.optJSONObject("original") ?: res.optJSONObject("medium"))?.str("url")?.ifBlank { null }
    }

    private val GENRES = mapOf(
        28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy", 80 to "Crime", 99 to "Documentary", 18 to "Drama",
        10751 to "Family", 14 to "Fantasy", 36 to "History", 27 to "Horror", 10402 to "Music", 9648 to "Mystery", 10749 to "Romance",
        878 to "Science fiction", 10770 to "TV movie", 53 to "Thriller", 10752 to "War", 37 to "Western", 10759 to "Action",
        10762 to "Kids", 10763 to "News", 10764 to "Reality", 10765 to "Sci-fi", 10766 to "Soap", 10767 to "Talk", 10768 to "Politics",
    )

    /** A TMDB list (/trending, /movie/now_playing, ...): movies or shows. */
    fun tmdbList(root: JSONObject?, kind: MediaKind?): List<MediaItem> {
        val arr = root?.optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            if (o.optBoolean("adult", false)) return@mapNotNull null
            val k = kind ?: when (o.str("media_type")) {
                "tv" -> MediaKind.Show
                "movie" -> MediaKind.Movie
                else -> return@mapNotNull null
            }
            val id = o.optInt("id", 0).takeIf { it > 0 } ?: return@mapNotNull null
            fun img(key: String, size: String) = o.str(key).takeIf { it.startsWith("/") }?.let { "https://image.tmdb.org/t/p/$size$it" }
            val date = o.str("release_date").ifBlank { o.str("first_air_date") }
            val ids = o.optJSONArray("genre_ids")
            MediaItem(
                key = "tmdb:${k.name}:$id",
                kind = k,
                title = o.str("title").ifBlank { o.str("name") },
                year = date.take(4),
                rating = o.optDouble("vote_average").takeIf { !it.isNaN() && it > 0 },
                overview = o.str("overview"),
                poster = img("poster_path", "w342"),
                backdrop = img("backdrop_path", "w1280"),
                genres = if (ids == null) emptyList() else (0 until ids.length()).mapNotNull { GENRES[ids.optInt(it)] }.distinct(),
                tmdbId = id,
                at = runCatching { LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant() }.getOrNull(),
            ).takeIf { it.title.isNotBlank() }
        }
    }

    /** /movie/{id} or /tv/{id} with videos and watch/providers appended. */
    fun details(root: JSONObject?, region: String): Details {
        if (root == null) return Details(null, emptyList(), emptyList(), null)
        val runtime = root.optInt("runtime", 0).takeIf { it > 0 }
            ?: root.optJSONArray("episode_run_time")?.optInt(0, 0)?.takeIf { it > 0 }
        val genres = root.optJSONArray("genres")?.let { g -> (0 until g.length()).mapNotNull { g.optJSONObject(it)?.str("name") } }.orEmpty()
        val prov = root.optJSONObject("watch/providers")?.optJSONObject("results")?.optJSONObject(region)
        val providers = buildList {
            for (key in listOf("flatrate", "free", "ads", "rent", "buy")) {
                val arr = prov?.optJSONArray(key) ?: continue
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.str("provider_name")?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }.distinct()
        val videos = root.optJSONObject("videos")?.optJSONArray("results")
        val trailer = videos?.let { v ->
            val all = (0 until v.length()).mapNotNull { v.optJSONObject(it) }.filter { it.str("site") == "YouTube" && it.str("key").isNotBlank() }
            (all.firstOrNull { it.str("type") == "Trailer" && it.optBoolean("official") } ?: all.firstOrNull { it.str("type") == "Trailer" } ?: all.firstOrNull())
                ?.str("key")
        }
        return Details(runtime, genres, providers, trailer)
    }

    private val DROP = listOf("with ads", "premium", "basic", "standard", "plus", "+", "amazon ", "channel", " tv", "tv ")

    /** A service or app name boiled down for matching: "Amazon Prime Video" and "Prime Video" both give "prime video". */
    fun norm(name: String): String {
        var n = " " + name.lowercase() + " "
        for (d in DROP) n = n.replace(d, " ")
        return n.replace(Regex("[^a-z0-9 ]"), "").replace(Regex("\\s+"), " ").trim()
    }

    /** True when a streaming service's name and an app's name are the same thing. */
    fun sameService(service: String, app: String): Boolean {
        val a = norm(service)
        val b = norm(app)
        if (a.length < 3 || b.length < 3) return a == b && a.isNotEmpty()
        return a == b || a.startsWith("$b ") || b.startsWith("$a ")
    }

    /** TMDB's ids for the services whose apps Haven can follow in Spotlight. */
    val PROVIDERS: List<Pair<String, Int>> = listOf(
        "Netflix" to 8, "Prime Video" to 9, "Disney" to 337, "Max" to 1899, "Hulu" to 15, "Apple" to 350, "Paramount" to 531,
        "Peacock" to 386, "Tubi" to 73, "Pluto" to 300,
    )

    fun providerFor(appLabel: String): Int? = PROVIDERS.firstOrNull { (name, _) -> sameService(name, appLabel) }?.second
}
