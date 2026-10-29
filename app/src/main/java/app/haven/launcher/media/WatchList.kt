package app.haven.launcher.media

import org.json.JSONArray
import org.json.JSONObject

/**
 * Something watched in an app on this TV, as the app described it while it played: Continue watching. [art] is a
 * picture file in Haven's storage or a web address; [at] is when it was last seen playing (wall clock, ms).
 */
data class Watched(
    val pkg: String,
    val title: String,
    val subtitle: String,
    val art: String,
    val positionMs: Long,
    val durationMs: Long,
    val at: Long,
    val profile: String,
) {
    val key: String get() = "$pkg|$title"

    /** How far through it is, 0 to 1, or null when the app didn't say how long it is. */
    val progress: Float? get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else null

    fun toJson(): JSONObject = JSONObject().put("pkg", pkg).put("title", title).put("subtitle", subtitle).put("art", art)
        .put("pos", positionMs).put("dur", durationMs).put("at", at).put("profile", profile)

    companion object {
        fun fromJson(o: JSONObject) = Watched(
            o.optString("pkg"), o.optString("title"), o.optString("subtitle"), o.optString("art"),
            o.optLong("pos"), o.optLong("dur"), o.optLong("at"), o.optString("profile"),
        )
    }
}

/** The Continue watching list: newest first, one entry per title and profile. Pure, so it's tested. */
object WatchList {
    const val MAX = 24
    private const val MONTH = 30L * 24 * 3600_000L

    /** [list] with [w] seen now: it moves to the front, keeping the picture it already had when it has none. */
    fun record(list: List<Watched>, w: Watched): List<Watched> {
        val old = list.firstOrNull { it.key == w.key && it.profile == w.profile }
        val item = if (w.art.isBlank() && old != null) w.copy(art = old.art) else w
        return (listOf(item) + list.filter { it !== old }).take(MAX)
    }

    /** Finished: past 95%, or within the last two minutes (the credits). */
    fun finished(w: Watched): Boolean =
        w.durationMs > 0 && (w.positionMs >= w.durationMs * 0.95 || w.durationMs - w.positionMs < 120_000L)

    /** What [profile]'s row shows at [now]: not finished, watched in the last month, a few seconds in at least. */
    fun shown(list: List<Watched>, profile: String, now: Long): List<Watched> =
        list.filter { it.profile == profile && !finished(it) && now - it.at < MONTH && (it.durationMs == 0L || it.positionMs > 30_000L) }

    fun remove(list: List<Watched>, w: Watched): List<Watched> = list.filter { !(it.key == w.key && it.profile == w.profile) }

    fun toJson(list: List<Watched>): String = JSONArray(list.map { it.toJson() }).toString()

    fun fromJson(text: String): List<Watched> = runCatching {
        val a = JSONArray(text)
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let(Watched::fromJson) }
    }.getOrDefault(emptyList())
}
