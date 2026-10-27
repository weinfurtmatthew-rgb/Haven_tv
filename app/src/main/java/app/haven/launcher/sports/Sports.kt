package app.haven.launcher.sports

import app.haven.launcher.data.FavoriteTeam
import app.haven.launcher.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

enum class League(val id: String, val label: String, val path: String) {
    NFL("nfl", "NFL", "football/nfl"),
    MLB("mlb", "MLB", "baseball/mlb"),
    NHL("nhl", "NHL", "hockey/nhl"),
    NBA("nba", "NBA", "basketball/nba"),
    EPL("eng.1", "Premier League", "soccer/eng.1"),
    LALIGA("esp.1", "La Liga", "soccer/esp.1"),
    UCL("uefa.champions", "Champions League", "soccer/uefa.champions"),
    ;

    /** Football (soccer) lists the home team first ("ARS 2 – 1 LIV"); North American leagues the away team. */
    val homeFirst: Boolean get() = path.startsWith("soccer/")

    companion object {
        fun byId(id: String): League? = values().firstOrNull { it.id == id }
    }
}

data class Team(val league: League, val id: String, val name: String, val abbr: String, val color: String, val logo: String) {
    fun toFavorite() = FavoriteTeam(league.id, id, name, abbr, color, logo)

    /** ESPN's version of the logo drawn for dark backgrounds (navy logos vanish on Haven's dark cards). */
    val darkLogo: String get() = logo.replace("/500/", "/500-dark/")
}

enum class GameState { Scheduled, Live, Final, Postponed }

data class Side(val team: Team, val score: String?)

data class Game(
    val id: String,
    val league: League,
    val start: Instant,
    val state: GameState,
    /** Status line from the source: "Q3 4:12", "Top 7th", "Final", "Sun, Oct 26 1:00 PM EDT". */
    val detail: String,
    val home: Side,
    val away: Side,
    val broadcasts: List<String>,
) {
    fun involves(teamIds: Set<String>): Boolean = "${league.id}:${home.team.id}" in teamIds || "${league.id}:${away.team.id}" in teamIds

    /** The side written first: home for football (soccer), away elsewhere. */
    val first: Side get() = if (league.homeFirst) home else away
    val second: Side get() = if (league.homeFirst) away else home

    /** "BUF 17 – 21 KC", "ARS 2 – 1 LIV". */
    val scoreLine: String get() = "${first.team.abbr} ${first.score ?: ""} – ${second.score ?: ""} ${second.team.abbr}".replace("  ", " ")

    /** "BOS at NYY", "ARS v LIV". */
    val matchup: String get() = if (league.homeFirst) "${home.team.abbr} v ${away.team.abbr}" else "${away.team.abbr} at ${home.team.abbr}"
}

/** A source of teams and games. ESPN's public feed is the default; others can be dropped in later. */
interface SportsProvider {
    suspend fun teams(league: League): List<Team>
    suspend fun scoreboard(league: League, date: LocalDate? = null): List<Game>
    suspend fun schedule(league: League, teamId: String): List<Game>
}

/** ESPN's public (unofficial, keyless) site API. It can change without notice; parsing is defensive. */
class EspnProvider(private val http: OkHttpClient) : SportsProvider {
    private val base = "https://site.api.espn.com/apis/site/v2/sports"

    private suspend fun get(url: String): JSONObject? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (r.isSuccessful) JSONObject(r.body!!.string()) else null
            }
        }.getOrNull()
    }

    override suspend fun teams(league: League): List<Team> = parseTeams(get("$base/${league.path}/teams?limit=200"), league)

    override suspend fun scoreboard(league: League, date: LocalDate?): List<Game> {
        val q = if (date != null) "?dates=" + date.format(DateTimeFormatter.BASIC_ISO_DATE) else ""
        return parseEvents(get("$base/${league.path}/scoreboard$q"), league)
    }

    override suspend fun schedule(league: League, teamId: String): List<Game> =
        parseEvents(get("$base/${league.path}/teams/$teamId/schedule"), league)

    companion object {
        private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm[:ss][.SSS]X")

        fun parseTime(s: String): Instant? = runCatching { OffsetDateTime.parse(s, DATE).toInstant() }.getOrNull()

        fun parseTeams(root: JSONObject?, league: League): List<Team> {
            val arr = root?.optJSONArray("sports")?.optJSONObject(0)?.optJSONArray("leagues")?.optJSONObject(0)?.optJSONArray("teams")
                ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                val t = arr.optJSONObject(i)?.optJSONObject("team") ?: return@mapNotNull null
                team(t, league)
            }.sortedBy { it.name }
        }

        private fun team(t: JSONObject, league: League): Team? {
            val id = t.optString("id").ifBlank { return null }
            val logo = t.optString("logo").ifBlank { t.optJSONArray("logos")?.optJSONObject(0)?.optString("href").orEmpty() }
            return Team(
                league, id, t.optString("displayName").ifBlank { t.optString("name") },
                t.optString("abbreviation").ifBlank { t.optString("shortDisplayName").take(3).uppercase() },
                t.optString("color"), logo,
            )
        }

        /** Parses both /scoreboard and /teams/{id}/schedule: their events share a shape, scores differ. */
        fun parseEvents(root: JSONObject?, league: League): List<Game> {
            val events = root?.optJSONArray("events") ?: return emptyList()
            return (0 until events.length()).mapNotNull { i ->
                runCatching {
                    val e = events.getJSONObject(i)
                    val comp = e.optJSONArray("competitions")?.optJSONObject(0) ?: return@runCatching null
                    val competitors = comp.optJSONArray("competitors") ?: return@runCatching null
                    var home: Side? = null
                    var away: Side? = null
                    for (k in 0 until competitors.length()) {
                        val c = competitors.getJSONObject(k)
                        val t = team(c.optJSONObject("team") ?: continue, league) ?: continue
                        val score = c.optJSONObject("score")?.optString("displayValue") ?: c.optString("score").takeIf { it.isNotBlank() }
                        val side = Side(t, score)
                        if (c.optString("homeAway") == "home") home = side else away = side
                    }
                    val status = (e.optJSONObject("status") ?: comp.optJSONObject("status"))?.optJSONObject("type")
                    val state = when {
                        status?.optString("name")?.contains("POSTPONED") == true -> GameState.Postponed
                        status?.optString("state") == "in" -> GameState.Live
                        status?.optString("state") == "post" -> GameState.Final
                        else -> GameState.Scheduled
                    }
                    val broadcasts = mutableListOf<String>()
                    comp.optJSONArray("broadcasts")?.let { b ->
                        for (k in 0 until b.length()) {
                            val o = b.optJSONObject(k) ?: continue
                            val names = o.optJSONArray("names")
                            if (names != null) {
                                for (n in 0 until names.length()) broadcasts += names.optString(n)
                            }
                            o.optJSONObject("media")?.optString("shortName")?.takeIf { it.isNotBlank() }?.let { broadcasts += it }
                        }
                    }
                    comp.optJSONArray("geoBroadcasts")?.let { b ->
                        for (k in 0 until b.length()) b.optJSONObject(k)?.optJSONObject("media")?.optString("shortName")
                            ?.takeIf { it.isNotBlank() }?.let { broadcasts += it }
                    }
                    Game(
                        id = e.optString("id"),
                        league = league,
                        start = parseTime(e.optString("date")) ?: return@runCatching null,
                        state = state,
                        detail = status?.optString("shortDetail").orEmpty(),
                        home = home ?: return@runCatching null,
                        away = away ?: return@runCatching null,
                        broadcasts = broadcasts.filter { it.isNotBlank() }.distinct(),
                    )
                }.getOrNull()
            }
        }
    }
}

/**
 * Games for the followed teams: anything today, plus each team's next game. Also remembers the last
 * score of every game so [changes] can produce alerts.
 */
class SportsRepository(private val store: SettingsStore) {
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    val provider: SportsProvider = EspnProvider(http)

    private val _games = MutableStateFlow<List<Game>>(emptyList())

    /** Followed teams' games, live first, then upcoming, then today's finals. */
    val games: StateFlow<List<Game>> = _games
    private val scheduleCache = HashMap<String, Pair<Long, List<Game>>>()
    private var lastRefresh = 0L

    val followed: Set<String> get() = store.value.sports.teams.map { it.key }.toSet()

    suspend fun refresh(maxAgeMs: Long = 0): List<Game> {
        if (app.haven.launcher.demo.Demo.active) {
            _games.value = app.haven.launcher.demo.Demo.games()
            return _games.value
        }
        if (System.currentTimeMillis() - lastRefresh < maxAgeMs) return _games.value
        val favs = store.value.sports.teams
        if (favs.isEmpty()) {
            _games.value = emptyList()
            return emptyList()
        }
        val keys = favs.map { it.key }.toSet()
        val today = LocalDate.now()
        val found = LinkedHashMap<String, Game>()
        for (league in favs.mapNotNull { League.byId(it.league) }.distinct()) {
            // Yesterday too, for late games still running after midnight.
            for (day in listOf(today.minusDays(1), today)) {
                provider.scoreboard(league, day).filter { it.involves(keys) }.forEach { found[it.id] = it }
            }
        }
        val now = Instant.now()
        for (f in favs) {
            val league = League.byId(f.league) ?: continue
            val hasGame = found.values.any { it.involves(setOf(f.key)) && (it.state != GameState.Final || isToday(it)) }
            if (hasGame) continue
            val cached = scheduleCache[f.key]
            val list = if (cached != null && System.currentTimeMillis() - cached.first < 6 * 3600_000L) cached.second
            else provider.schedule(league, f.id).also { scheduleCache[f.key] = System.currentTimeMillis() to it }
            list.filter { it.start.isAfter(now) && it.state == GameState.Scheduled }.minByOrNull { it.start }?.let { found[it.id] = it }
        }
        val sorted = found.values
            .filter { it.state != GameState.Final || isToday(it) }
            .sortedWith(compareBy<Game> { order(it.state) }.thenBy { it.start })
        _games.value = sorted
        lastRefresh = System.currentTimeMillis()
        return sorted
    }

    private fun isToday(g: Game) = g.start.atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()

    private fun order(s: GameState) = when (s) {
        GameState.Live -> 0
        GameState.Scheduled -> 1
        GameState.Final -> 2
        GameState.Postponed -> 3
    }

    /** Followed teams' games that start today (for the morning briefing). */
    fun today(): List<Game> = _games.value.filter { isToday(it) }

    /** Every game in [league] today, for the scoreboard on the sports screen. */
    suspend fun board(league: League): List<Game> =
        if (app.haven.launcher.demo.Demo.active) app.haven.launcher.demo.Demo.games().filter { it.league == league }
        else provider.scoreboard(league, LocalDate.now())

    // ---- change detection for alerts ----------------------------------------------------------

    private val seen = HashMap<String, Triple<GameState, String?, String?>>()

    /** Compares with the previous poll and returns alert texts (title to line). The first poll only records. */
    fun changes(games: List<Game>): List<Pair<String, String>> {
        val cfg = store.value.sports
        val out = mutableListOf<Pair<String, String>>()
        for (g in games) {
            val now = Triple(g.state, g.home.score, g.away.score)
            val before = seen.put(g.id, now) ?: continue
            // Without spoilers: starts and ends are announced, scores never are.
            val hide = cfg.noSpoilers
            when {
                before.first == GameState.Scheduled && g.state == GameState.Live && cfg.alertStart ->
                    out += "${g.matchup} is starting" to g.broadcasts.firstOrNull().orEmpty()
                before.first != GameState.Final && g.state == GameState.Final && cfg.alertFinal ->
                    out += (if (hide) "${g.matchup} is over" else "Final: ${g.scoreLine}") to g.league.label
                g.state == GameState.Live && (before.second != now.second || before.third != now.third) && cfg.alertScore && !hide ->
                    out += g.scoreLine to g.detail
            }
        }
        return out
    }
}

fun Game.involvesFollowed(followed: Set<String>) = involves(followed)

fun FavoriteTeam.leagueLabel(): String = League.byId(league)?.label ?: league
