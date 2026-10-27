package app.haven.launcher.media

import app.haven.launcher.data.FollowedShow
import app.haven.launcher.data.Settings
import app.haven.launcher.data.SettingsStore
import app.haven.launcher.data.TvApp
import app.haven.launcher.demo.Demo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Movie and TV content for the layouts: TVmaze for schedules and the shows people follow (free, no key), TMDB for
 * movie lists, details and trailers with the person's own key. Lists are cached; rows read [lists].
 */
class Media(private val store: SettingsStore) {
    private val http = OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    private val lock = Mutex()
    private val loadedAt = HashMap<String, Long>()
    private val showCache = HashMap<Int, Pair<Long, ShowInfo>>()
    private val backgroundCache = HashMap<Int, String?>()
    private val serviceCache = HashMap<Int, Pair<Long, List<MediaItem>>>()

    private val _lists = MutableStateFlow<Map<MediaList, List<MediaItem>>>(emptyMap())
    val lists: StateFlow<Map<MediaList, List<MediaItem>>> = _lists
    private val _status = MutableStateFlow<String?>(null)

    /** Why movie lists are empty ("Needs a TMDB key"), for the rows to say. */
    val status: StateFlow<String?> = _status

    private fun region(s: Settings): String = s.region.ifBlank { Locale.getDefault().country }.ifBlank { "US" }.uppercase()

    private fun get(url: String, bearer: String? = null): String? = runCatching {
        val req = Request.Builder().url(url).header("User-Agent", "Haven TV").apply { if (bearer != null) header("Authorization", "Bearer $bearer") }.build()
        http.newCall(req).execute().use { r ->
            if (r.code == 401) _status.value = "TMDB didn't accept the key"
            if (r.isSuccessful) r.body!!.string() else null
        }
    }.getOrNull()

    /** A TMDB call with the profile's key: v3 keys go in the address, long v4 tokens in a header. */
    private fun tmdb(path: String, s: Settings): JSONObject? {
        val key = s.background.tmdbKey.trim()
        if (key.isBlank()) {
            _status.value = "Needs a TMDB key"
            return null
        }
        val v4 = key.length > 40
        val sep = if (path.contains('?')) "&" else "?"
        val url = "https://api.themoviedb.org/3/$path" + if (v4) "" else "${sep}api_key=$key"
        return get(url, if (v4) key else null)?.let { runCatching { JSONObject(it) }.getOrNull() }
    }

    private fun maze(path: String): String? = get("https://api.tvmaze.com/$path")

    private fun ttl(list: MediaList): Long = when (list) {
        MediaList.Tonight, MediaList.NewStreaming, MediaList.YourShows -> 60 * 60_000L
        else -> 3 * 3600_000L
    }

    /** Loads [list] unless a fresh copy is cached. Safe to call often (every time a row shows). */
    suspend fun ensure(list: MediaList, force: Boolean = false) = withContext(Dispatchers.IO) {
        val s = store.value
        val cacheKey = "$list:${region(s)}:${s.background.tmdbKey.hashCode()}:${s.shows.hashCode()}"
        lock.withLock {
            val at = loadedAt[cacheKey]
            if (!force && at != null && System.currentTimeMillis() - at < ttl(list) && _lists.value[list] != null) return@withLock
            val items = if (Demo.active) Demo.media(list, s) else load(list, s)
            if (items != null) {
                _lists.value = _lists.value + (list to items)
                loadedAt[cacheKey] = System.currentTimeMillis()
            }
        }
    }

    private fun load(list: MediaList, s: Settings): List<MediaItem>? {
        val r = region(s)
        return when (list) {
            MediaList.TrendingMovies -> tmdbList("trending/movie/week", s, MediaKind.Movie)
            MediaList.InTheaters -> tmdbList("movie/now_playing?region=$r", s, MediaKind.Movie)
            MediaList.ComingSoon -> tmdbList("movie/upcoming?region=$r", s, MediaKind.Movie)?.let { l ->
                val now = Instant.now()
                l.filter { it.at == null || it.at.isAfter(now) }.sortedBy { it.at }
            }
            MediaList.TopMovies -> tmdbList("movie/top_rated", s, MediaKind.Movie)
            MediaList.TrendingShows -> tmdbList("trending/tv/week", s, MediaKind.Show)
            MediaList.Tonight -> tonight(r)
            MediaList.NewStreaming -> streaming(r)
            MediaList.YourShows -> yourShows(s.shows)
        }
    }

    private fun tmdbList(path: String, s: Settings, kind: MediaKind): List<MediaItem>? {
        val root = tmdb(path, s) ?: return null
        _status.value = null
        return MediaParse.tmdbList(root, kind)
    }

    private val zone get() = ZoneId.systemDefault()

    private fun timeOf(at: Instant?): String =
        at?.atZone(zone)?.format(DateTimeFormatter.ofPattern(if (store.value.use24h) "H:mm" else "h:mm a", Locale.getDefault())).orEmpty()

    /** This evening's broadcast schedule: one card per show, by time, from 5 PM. */
    private fun tonight(region: String): List<MediaItem>? {
        val today = LocalDate.now(zone)
        val body = maze("schedule?country=$region&date=$today") ?: return null
        val evening = today.atTime(17, 0).atZone(zone).toInstant()
        return MediaParse.schedule(runCatching { JSONArray(body) }.getOrNull())
            .filter { it.at != null && !it.at.isBefore(evening) && it.type != "insignificant_special" }
            .sortedWith(compareBy<Airing> { it.at }.thenByDescending { it.weight })
            .distinctBy { it.showId }
            .take(40)
            .map { a ->
                MediaItem(
                    key = "maze:${a.showId}", kind = MediaKind.Show, title = a.show, overview = a.summary, poster = a.poster,
                    genres = a.genres, showId = a.showId, network = a.network, at = a.at,
                    note = listOf(timeOf(a.at), a.network).filter { it.isNotBlank() }.joinToString(" · "),
                )
            }
    }

    /** Today's new episodes on streaming services, worldwide ones and the country's own, most watched first. */
    private fun streaming(region: String): List<MediaItem>? {
        val today = LocalDate.now(zone)
        val global = maze("schedule/web?date=$today")
        val local = maze("schedule/web?date=$today&country=$region")
        if (global == null && local == null) return null
        val all = listOfNotNull(global, local).flatMap { MediaParse.schedule(runCatching { JSONArray(it) }.getOrNull()) }
        return all.sortedByDescending { it.weight }.distinctBy { it.showId }.take(40).map { a ->
            MediaItem(
                key = "maze:${a.showId}", kind = MediaKind.Show, title = a.show, overview = a.summary, poster = a.poster,
                genres = a.genres, showId = a.showId, network = a.network, at = a.at,
                note = if (a.network.isNotBlank()) "New on ${a.network}" else "New today",
            )
        }
    }

    /** A show with its next and previous episodes, cached for a few hours. */
    fun show(id: Int): ShowInfo? {
        showCache[id]?.let { (at, info) -> if (System.currentTimeMillis() - at < 3 * 3600_000L) return info }
        val body = maze("shows/$id?embed[]=nextepisode&embed[]=previousepisode") ?: return showCache[id]?.second
        val info = MediaParse.show(runCatching { JSONObject(body) }.getOrNull()) ?: return showCache[id]?.second
        showCache[id] = System.currentTimeMillis() to info
        return info
    }

    /** "Tonight 9:00 PM on NBC", "Thursday 8:00 PM", "Back March 4", "Ended". */
    fun nextLine(info: ShowInfo, now: ZonedDateTime = ZonedDateTime.now(zone)): Pair<String, String> {
        val next = info.next?.at?.atZone(zone)
        if (next != null) {
            val days = java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), next.toLocalDate())
            val time = timeOf(info.next?.at)
            val on = if (info.network.isNotBlank()) " on ${info.network}" else ""
            return when {
                days <= 0L -> "NEW TONIGHT" to "Tonight $time$on"
                days == 1L -> "TOMORROW" to "Tomorrow $time$on"
                days < 7L -> "" to next.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())) + " $time$on"
                else -> "" to "Back " + next.format(DateTimeFormatter.ofPattern("MMMM d", Locale.getDefault()))
            }
        }
        return "" to when (info.status) {
            "Ended" -> "Ended"
            "To Be Determined" -> "Waiting on a new season"
            "In Development" -> "Coming"
            else -> "No date yet"
        }
    }

    private fun yourShows(followed: List<FollowedShow>): List<MediaItem> {
        val now = ZonedDateTime.now(zone)
        return followed.map { f ->
            val info = show(f.id)
            if (info == null) {
                MediaItem("maze:${f.id}", MediaKind.Show, f.name, poster = f.image.ifBlank { null }, showId = f.id)
            } else {
                val (badge, line) = nextLine(info, now)
                MediaItem(
                    key = "maze:${f.id}", kind = MediaKind.Show, title = info.name, overview = info.summary, poster = info.poster,
                    genres = info.genres, showId = f.id, network = info.network, at = info.next?.at, rating = info.rating,
                    note = line, badge = badge,
                )
            }
        }.sortedWith(compareBy<MediaItem> { it.at == null }.thenBy { it.at })
    }

    /** A wide picture for a show (the featured spot), from TVmaze. */
    suspend fun showBackground(id: Int): String? = withContext(Dispatchers.IO) {
        if (Demo.active) return@withContext null
        if (backgroundCache.containsKey(id)) return@withContext backgroundCache[id]
        val url = maze("shows/$id/images")?.let { MediaParse.background(runCatching { JSONArray(it) }.getOrNull()) }
        backgroundCache[id] = url
        url
    }

    suspend fun search(query: String): List<ShowInfo> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = java.net.URLEncoder.encode(query.trim(), "UTF-8")
        MediaParse.search(maze("search/shows?q=$q")?.let { runCatching { JSONArray(it) }.getOrNull() })
    }

    /** Runtime, genres, where to watch and the trailer, for the details dialog. */
    suspend fun details(item: MediaItem): Details? = withContext(Dispatchers.IO) {
        val s = store.value
        val id = item.tmdbId ?: return@withContext null
        val path = (if (item.kind == MediaKind.Movie) "movie" else "tv") + "/$id?append_to_response=videos,watch/providers"
        tmdb(path, s)?.let { MediaParse.details(it, region(s)) }
    }

    /** The apps on this TV that have [d]'s services. */
    fun appsFor(d: Details, apps: List<TvApp>): List<TvApp> =
        d.providers.flatMap { p -> apps.filter { MediaParse.sameService(p, it.label) } }.distinct()

    /** What's popular on the service behind [app] (Spotlight following the app you're on). */
    suspend fun onService(app: TvApp): List<MediaItem> = withContext(Dispatchers.IO) {
        val s = store.value
        val provider = MediaParse.providerFor(app.label) ?: return@withContext emptyList()
        serviceCache[provider]?.let { (at, l) -> if (System.currentTimeMillis() - at < 6 * 3600_000L) return@withContext l }
        val list = tmdb("discover/movie?with_watch_providers=$provider&watch_region=${region(s)}&sort_by=popularity.desc", s)
            ?.let { MediaParse.tmdbList(it, MediaKind.Movie) }.orEmpty().filter { it.backdrop != null }
        if (list.isNotEmpty()) serviceCache[provider] = System.currentTimeMillis() to list
        list
    }

    fun follow(info: ShowInfo) {
        store.update { s ->
            if (s.shows.any { it.id == info.id }) s else s.copy(shows = s.shows + FollowedShow(info.id, info.name, info.poster.orEmpty()))
        }
        loadedAt.keys.removeAll { it.startsWith(MediaList.YourShows.name) }
    }

    fun unfollow(id: Int) {
        store.update { s -> s.copy(shows = s.shows.filter { it.id != id }) }
        loadedAt.keys.removeAll { it.startsWith(MediaList.YourShows.name) }
    }
}
