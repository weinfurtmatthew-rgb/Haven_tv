package app.haven.launcher.brief

import app.haven.launcher.Haven
import app.haven.launcher.data.BriefSection
import app.haven.launcher.ha.CalEvent
import app.haven.launcher.ha.Forecast
import app.haven.launcher.ha.HaEntity
import app.haven.launcher.sports.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

data class StatusItem(val name: String, val text: String, val ok: Boolean)

data class Commute(val minutes: String, val unit: String, val label: String, val route: String)

data class BriefData(
    val weather: HaEntity? = null,
    val hourly: List<Forecast> = emptyList(),
    val today: Forecast? = null,
    val events: List<CalEvent> = emptyList(),
    val commute: Commute? = null,
    val status: List<StatusItem> = emptyList(),
    val headlines: List<String> = emptyList(),
    val games: List<Game> = emptyList(),
    /** False when Home Assistant has no calendars at all, so "nothing on the calendar" would be misleading. */
    val hasCalendars: Boolean = false,
)

/** Gathers the morning briefing from Home Assistant, a news feed and the sports feed, all in parallel. */
object Briefing {
    private val http = OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()

    /** Loads only what the profile's briefing shows, all in parallel. */
    suspend fun load(): BriefData = coroutineScope {
        val s = Haven.settings.value
        val cfg = s.brief
        val want = cfg.sections.toSet()
        val ha = Haven.ha
        val news = async { if (BriefSection.Headlines in want && cfg.headlines > 0) headlines(cfg.headlinesFeed, cfg.headlines) else emptyList<String>() }
        val sports = async {
            if (BriefSection.Teams !in want) emptyList<Game>()
            else runCatching { Haven.sports.refresh(maxAgeMs = 5 * 60_000L); Haven.sports.today() }.getOrDefault(emptyList())
        }
        if (!s.haConfigured) {
            return@coroutineScope BriefData(headlines = news.await(), games = sports.await())
        }
        ha.refresh()
        val weather = ha.weather(s)
        val hourly = async { if (BriefSection.Hourly in want) weather?.let { ha.forecast(it.id, "hourly") }.orEmpty() else emptyList<Forecast>() }
        val daily = async { if (BriefSection.Weather in want) weather?.let { ha.forecast(it.id, "daily") }.orEmpty() else emptyList<Forecast>() }
        val calendarIds = async {
            if (BriefSection.Calendar !in want) emptySet<String>() else cfg.calendars.ifEmpty { Haven.ha.calendars().map { it.first }.toSet() }
        }
        val events = async { calendarToday(calendarIds.await()) }
        val now = ZonedDateTime.now()
        BriefData(
            weather = weather,
            hourly = hourly.await().filter { it.time == null || it.time.toInstant().isAfter(now.toInstant().minusSeconds(1800)) }
                .take(12).filterIndexed { i, _ -> i % 2 == 0 }.take(5),
            today = daily.await().firstOrNull(),
            events = events.await(),
            commute = if (BriefSection.Commute in want) commute(cfg.commuteEntity, cfg.commuteLabel) else null,
            status = if (BriefSection.Home in want) status(cfg.statusEntities) else emptyList(),
            headlines = news.await(),
            games = sports.await(),
            hasCalendars = calendarIds.await().isNotEmpty(),
        )
    }

    private suspend fun calendarToday(ids: Set<String>): List<CalEvent> {
        val zone = ZoneId.systemDefault()
        val start = ZonedDateTime.now(zone)
        val end = LocalDate.now(zone).plusDays(1).atStartOfDay(zone)
        val fmt = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        return ids.flatMap { Haven.ha.events(it, start.withHour(0).withMinute(0).format(fmt), end.format(fmt)) }
            .filter { it.allDay || it.start == null || !it.start.toInstant().isBefore(start.toInstant().minusSeconds(3600)) }
            .sortedWith(compareBy<CalEvent> { !it.allDay }.thenBy { it.start })
            .distinctBy { it.summary + it.start }
            .take(4)
    }

    private fun commute(entityId: String, label: String): Commute? {
        val e = Haven.ha.entities.value[entityId] ?: return null
        if (!e.available) return null
        return Commute(
            e.state.toDoubleOrNull()?.let { Math.round(it).toString() } ?: e.state,
            e.attrs.optString("unit_of_measurement", "min"),
            label,
            e.attrs.optString("route"),
        )
    }

    /** Chosen entities, or by default every lock plus garage doors. */
    private fun status(chosen: Set<String>): List<StatusItem> {
        val all = Haven.ha.entities.value
        val picked = if (chosen.isNotEmpty()) chosen.mapNotNull { all[it] }
        else all.values.filter { it.domain == "lock" || (it.domain == "cover" && it.attrs.optString("device_class") == "garage") }
        return picked.sortedBy { it.name }.take(4).map { statusOf(it) }
    }

    fun statusOf(e: HaEntity): StatusItem = when (e.domain) {
        "lock" -> StatusItem(e.name, if (e.state == "locked") "Locked" else e.state.replaceFirstChar { it.uppercase() }, e.state == "locked")
        "cover" -> StatusItem(e.name, e.state.replaceFirstChar { it.uppercase() }, e.state == "closed")
        "binary_sensor" -> StatusItem(e.name, if (e.state == "on") "Open" else "Closed", e.state != "on")
        "alarm_control_panel" -> StatusItem(e.name, e.state.replace('_', ' ').replaceFirstChar { it.uppercase() }, e.state.startsWith("armed"))
        else -> StatusItem(e.name, e.state.replaceFirstChar { it.uppercase() }, true)
    }

    private suspend fun headlines(url: String, max: Int): List<String> = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext emptyList()
        runCatching {
            http.newCall(Request.Builder().url(url).header("User-Agent", "Haven TV").build()).execute().use { r ->
                if (r.isSuccessful) Rss.titles(r.body!!.string(), max) else emptyList()
            }
        }.getOrDefault(emptyList())
    }
}

/** Minimal RSS/Atom title reader: enough for headlines, with no XML library. */
object Rss {
    private val ITEM = Regex("<(item|entry)\\b[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE)
    private val TITLE = Regex("<title\\b[^>]*>([\\s\\S]*?)</title>", RegexOption.IGNORE_CASE)

    fun titles(xml: String, max: Int): List<String> =
        ITEM.findAll(xml).mapNotNull { item ->
            TITLE.find(item.value)?.groupValues?.get(1)?.let(::clean)?.takeIf { it.isNotBlank() }
        }.take(max).toList()

    private fun clean(raw: String): String = raw
        .replace(Regex("<!\\[CDATA\\[([\\s\\S]*?)]]>"), "$1")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&#8217;", "’").replace("&#8216;", "‘")
        .replace("&#8220;", "“").replace("&#8221;", "”").replace("&#8212;", "—").replace("&#8211;", "–")
        .replace(Regex("\\s+"), " ")
        .trim()
}
