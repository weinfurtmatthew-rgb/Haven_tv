package app.haven.launcher.data

import org.json.JSONArray
import org.json.JSONObject

enum class ArtStyle(val label: String) {
    FullScreen("Full screen"),
    ThinFrame("Thin frame"),
    Gallery("Gallery frame and mat"),
}

enum class TvPanel(val label: String) {
    Unknown("Not set"),
    Oled("OLED"),
    Lcd("LCD / LED"),
}

enum class QuietMode(val label: String) {
    Dim("Dim the art"),
    Black("Go black"),
}

/** How art mode looks and how it protects the screen. */
data class ArtCare(
    val style: ArtStyle = ArtStyle.ThinFrame,
    val panel: TvPanel = TvPanel.Unknown,
    /** Extra darkening of the art, 0..60 percent. */
    val dimPct: Int = 0,
    /** Quiet hours as hours of the day (0..23); equal start and end means off. */
    val quietStart: Int = 23,
    val quietEnd: Int = 23,
    val quietMode: QuietMode = QuietMode.Black,
    /** Home Assistant motion/occupancy entity; empty = always show art. */
    val presenceEntity: String = "",
    /** Minutes without presence before art goes black. */
    val presenceDelayMin: Int = 10,
    /** Hours of continuous art before going black; 0 = no limit. */
    val maxHours: Int = 4,
) {
    val quietEnabled: Boolean get() = quietStart != quietEnd

    /** OLED (or unknown, to be safe) panels get stronger burn-in protection. */
    val strongProtection: Boolean get() = panel != TvPanel.Lcd

    fun toJson(): JSONObject = JSONObject()
        .put("style", style.name)
        .put("panel", panel.name)
        .put("dimPct", dimPct)
        .put("quietStart", quietStart)
        .put("quietEnd", quietEnd)
        .put("quietMode", quietMode.name)
        .put("presenceEntity", presenceEntity)
        .put("presenceDelayMin", presenceDelayMin)
        .put("maxHours", maxHours)

    companion object {
        fun fromJson(o: JSONObject?): ArtCare {
            val d = ArtCare()
            if (o == null) return d
            return ArtCare(
                style = enumOr(o.optString("style"), d.style),
                panel = enumOr(o.optString("panel"), d.panel),
                dimPct = o.optInt("dimPct", d.dimPct).coerceIn(0, 60),
                quietStart = o.optInt("quietStart", d.quietStart).coerceIn(0, 23),
                quietEnd = o.optInt("quietEnd", d.quietEnd).coerceIn(0, 23),
                quietMode = enumOr(o.optString("quietMode"), d.quietMode),
                presenceEntity = o.optString("presenceEntity", d.presenceEntity),
                presenceDelayMin = o.optInt("presenceDelayMin", d.presenceDelayMin).coerceIn(1, 120),
                maxHours = o.optInt("maxHours", d.maxHours).coerceIn(0, 24),
            )
        }
    }
}

/** How a greeting reads on the alarm and the briefing. */
enum class GreetingStyle(val label: String) {
    WithName("Good morning, and your name"),
    Plain("Good morning"),
    Custom("Your own words"),
    None("No greeting"),
}

enum class BriefSection(val label: String) {
    Weather("Weather"),
    Hourly("Hourly forecast"),
    Headlines("Headlines"),
    Calendar("Today's calendar"),
    Commute("Commute"),
    Home("Home status"),
    Teams("Your teams"),
}

enum class BriefLayout(val label: String) {
    Columns("Two columns"),
    Single("One column"),
    Minimal("Just the essentials, large"),
}

enum class BriefBackground(val label: String) {
    Theme("The theme's colour"),
    Background("Your home screen background"),
    Sunrise("Sunrise glow"),
    Art("A painting"),
    Black("Black"),
}

enum class BriefWhen(val label: String) {
    AfterAlarm("After the alarm"),
    Mornings("Every morning, the first time Haven opens"),
    Manual("Only when I open it"),
}

enum class BriefClose(val label: String) {
    AnyButton("Any button closes it"),
    Back("Only Back closes it"),
}

/** What the morning briefing shows and how. Empty selections mean "pick sensible defaults". */
data class BriefConfig(
    val calendars: Set<String> = emptySet(),
    val commuteEntity: String = "",
    val commuteLabel: String = "work",
    val statusEntities: Set<String> = emptySet(),
    val headlinesFeed: String = DEFAULT_FEED,
    /** Minutes before the briefing closes by itself; 0 = stays until closed. */
    val autoCloseMin: Int = 10,
    val order: List<BriefSection> = BriefSection.values().toList(),
    val shown: Set<BriefSection> = BriefSection.values().toSet(),
    val layout: BriefLayout = BriefLayout.Columns,
    val greeting: GreetingStyle = GreetingStyle.WithName,
    val message: String = "",
    val background: BriefBackground = BriefBackground.Theme,
    val showWhen: BriefWhen = BriefWhen.AfterAlarm,
    val closeWith: BriefClose = BriefClose.AnyButton,
    val readAloud: Boolean = false,
    val headlines: Int = 3,
) {
    val sections: List<BriefSection> get() = order.filter { it in shown }

    fun toJson(): JSONObject = JSONObject()
        .put("calendars", JSONArray(calendars.toList()))
        .put("commuteEntity", commuteEntity)
        .put("commuteLabel", commuteLabel)
        .put("statusEntities", JSONArray(statusEntities.toList()))
        .put("headlinesFeed", headlinesFeed)
        .put("autoCloseMin", autoCloseMin)
        .put("order", JSONArray(order.map { it.name }))
        .put("shown", JSONArray(shown.map { it.name }))
        .put("layout", layout.name)
        .put("greeting", greeting.name)
        .put("message", message)
        .put("background", background.name)
        .put("showWhen", showWhen.name)
        .put("closeWith", closeWith.name)
        .put("readAloud", readAloud)
        .put("headlines", headlines)

    companion object {
        const val DEFAULT_FEED = "https://feeds.npr.org/1001/rss.xml"

        fun fromJson(o: JSONObject?): BriefConfig {
            val d = BriefConfig()
            if (o == null) return d
            return BriefConfig(
                calendars = o.optJSONArray("calendars").strings().toSet(),
                commuteEntity = o.optString("commuteEntity", d.commuteEntity),
                commuteLabel = o.optString("commuteLabel", d.commuteLabel).ifBlank { d.commuteLabel },
                statusEntities = o.optJSONArray("statusEntities").strings().toSet(),
                headlinesFeed = o.optString("headlinesFeed", d.headlinesFeed),
                autoCloseMin = o.optInt("autoCloseMin", d.autoCloseMin).coerceIn(0, 120),
                order = completeOrder(enumList<BriefSection>(o.optJSONArray("order"))),
                shown = if (o.has("shown")) enumList<BriefSection>(o.optJSONArray("shown")).toSet() else d.shown,
                layout = enumOr(o.optString("layout"), d.layout),
                greeting = enumOr(o.optString("greeting"), d.greeting),
                message = o.optString("message", d.message).trim(),
                background = enumOr(o.optString("background"), d.background),
                showWhen = enumOr(o.optString("showWhen"), d.showWhen),
                closeWith = enumOr(o.optString("closeWith"), d.closeWith),
                readAloud = o.optBoolean("readAloud", d.readAloud),
                headlines = o.optInt("headlines", d.headlines).coerceIn(0, 6),
            )
        }
    }
}

/** A followed team. Name, colors and logo are kept so the UI can draw it before the network answers. */
data class FavoriteTeam(
    val league: String,
    val id: String,
    val name: String,
    val abbr: String,
    val color: String = "",
    val logo: String = "",
) {
    val key: String get() = "$league:$id"

    fun toJson(): JSONObject = JSONObject()
        .put("league", league).put("id", id).put("name", name).put("abbr", abbr).put("color", color).put("logo", logo)

    companion object {
        fun fromJson(o: JSONObject): FavoriteTeam = FavoriteTeam(
            o.optString("league"), o.optString("id"), o.optString("name"), o.optString("abbr"),
            o.optString("color"), o.optString("logo"),
        )
    }
}

enum class SportsLayout(val label: String) {
    Cards("Cards"),
    Compact("Compact list"),
    Big("Big scoreboard"),
}

/** What OK on a game does. */
enum class GameAction(val label: String) {
    Auto("Open the app that shows it"),
    Directv("Always open DIRECTV"),
    Ask("Ask each time"),
}

enum class AlertCorner(val label: String) {
    TopRight("Top right"),
    TopLeft("Top left"),
    BottomRight("Bottom right"),
    BottomLeft("Bottom left"),
}

enum class AlertSize(val label: String, val factor: Float) {
    Normal("Normal", 1f),
    Large("Large", 1.35f),
}

data class SportsConfig(
    val teams: List<FavoriteTeam> = emptyList(),
    val alerts: Boolean = true,
    val alertStart: Boolean = true,
    val alertScore: Boolean = true,
    val alertFinal: Boolean = true,
    val layout: SportsLayout = SportsLayout.Cards,
    /** Hide scores everywhere (home, briefing, alerts) until a game is opened with a long press. */
    val noSpoilers: Boolean = false,
    val logos: Boolean = true,
    val teamColors: Boolean = false,
    /** Show today's games for any league next to your teams. */
    val board: Boolean = true,
    val onSelect: GameAction = GameAction.Auto,
    val homeTile: Boolean = true,
    val alertCorner: AlertCorner = AlertCorner.TopRight,
    val alertSeconds: Int = 8,
    val alertSize: AlertSize = AlertSize.Normal,
    val alertSound: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("teams", JSONArray(teams.map { it.toJson() }))
        .put("alerts", alerts)
        .put("alertStart", alertStart)
        .put("alertScore", alertScore)
        .put("alertFinal", alertFinal)
        .put("layout", layout.name)
        .put("noSpoilers", noSpoilers)
        .put("logos", logos)
        .put("teamColors", teamColors)
        .put("board", board)
        .put("onSelect", onSelect.name)
        .put("homeTile", homeTile)
        .put("alertCorner", alertCorner.name)
        .put("alertSeconds", alertSeconds)
        .put("alertSize", alertSize.name)
        .put("alertSound", alertSound)

    companion object {
        fun fromJson(o: JSONObject?): SportsConfig {
            val d = SportsConfig()
            if (o == null) return d
            val arr = o.optJSONArray("teams")
            return SportsConfig(
                teams = if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let(FavoriteTeam::fromJson)?.takeIf { it.id.isNotBlank() }
                },
                alerts = o.optBoolean("alerts", d.alerts),
                alertStart = o.optBoolean("alertStart", d.alertStart),
                alertScore = o.optBoolean("alertScore", d.alertScore),
                alertFinal = o.optBoolean("alertFinal", d.alertFinal),
                layout = enumOr(o.optString("layout"), d.layout),
                noSpoilers = o.optBoolean("noSpoilers", d.noSpoilers),
                logos = o.optBoolean("logos", d.logos),
                teamColors = o.optBoolean("teamColors", d.teamColors),
                board = o.optBoolean("board", d.board),
                onSelect = enumOr(o.optString("onSelect"), d.onSelect),
                homeTile = o.optBoolean("homeTile", d.homeTile),
                alertCorner = enumOr(o.optString("alertCorner"), d.alertCorner),
                alertSeconds = o.optInt("alertSeconds", d.alertSeconds).coerceIn(3, 60),
                alertSize = enumOr(o.optString("alertSize"), d.alertSize),
                alertSound = o.optBoolean("alertSound", d.alertSound),
            )
        }
    }
}

inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: fallback
