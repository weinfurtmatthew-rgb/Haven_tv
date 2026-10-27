package app.haven.launcher.demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.haven.launcher.Haven
import app.haven.launcher.bedroom.AlarmActivity
import app.haven.launcher.data.AlarmConfig
import app.haven.launcher.data.BriefConfig
import app.haven.launcher.data.SportsConfig
import app.haven.launcher.data.TvPanel
import app.haven.launcher.data.enumOr
import app.haven.launcher.ha.CalEvent
import app.haven.launcher.ha.Forecast
import app.haven.launcher.ha.HaArea
import app.haven.launcher.ha.HaEntity
import app.haven.launcher.sports.Game
import app.haven.launcher.sports.GameState
import app.haven.launcher.sports.League
import app.haven.launcher.sports.ScorePopup
import app.haven.launcher.sports.Side
import app.haven.launcher.sports.Team
import app.haven.launcher.system.CrashActivity
import app.haven.launcher.system.CrashReport
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Demo household for screenshots and UI review: lights, speakers, weather, calendar, locks and games, with no
 * Home Assistant server. Only reachable through [DemoReceiver], which needs a permission only `adb shell` has.
 */
object Demo {
    const val URL = "demo.haven.invalid"

    val active: Boolean get() = Haven.settings.value.haUrl == URL

    fun enable() {
        Haven.settings.update {
            it.copy(
                roomName = "Living Room",
                haArea = "living_room",
                profileName = "Matt",
                haUrl = URL,
                haToken = "demo",
                bedroomExtras = true,
                alarm = AlarmConfig(enabled = true, hour = 6, minute = 30),
                brief = BriefConfig(commuteEntity = "sensor.commute_work", commuteLabel = "work"),
                sports = SportsConfig(teams = games().flatMap { g -> listOf(g.home.team, g.away.team) }.filter { t -> t.abbr in FOLLOWED }.map { t -> t.toFavorite() }),
                artCare = it.artCare.copy(panel = TvPanel.Oled),
            )
        }
        Haven.scope.launch {
            Haven.ha.refresh()
            Haven.sports.refresh()
        }
    }

    private val FOLLOWED = setOf("KC", "NYY", "ARS")

    private fun attrs(vararg pairs: Pair<String, Any?>): JSONObject {
        val o = JSONObject()
        for ((k, v) in pairs) {
            when (v) {
                is List<*> -> o.put(k, JSONArray(v))
                null -> o.put(k, JSONObject.NULL)
                else -> o.put(k, v)
            }
        }
        return o
    }

    fun entities(): Map<String, HaEntity> = listOf(
        HaEntity("light.floor_lamp", "on", attrs("friendly_name" to "Floor lamp", "brightness" to 51, "supported_color_modes" to listOf("brightness"))),
        HaEntity("light.ceiling", "on", attrs("friendly_name" to "Ceiling", "brightness" to 26, "supported_color_modes" to listOf("brightness"))),
        HaEntity("light.tv_backlight", "on", attrs("friendly_name" to "TV backlight", "brightness" to 153, "supported_color_modes" to listOf("hs"))),
        HaEntity("light.shelf", "off", attrs("friendly_name" to "Shelf lights", "supported_color_modes" to listOf("brightness"))),
        HaEntity("switch.string_lights", "off", attrs("friendly_name" to "String lights")),
        HaEntity(
            "media_player.soundbar", "playing",
            attrs("friendly_name" to "Soundbar", "volume_level" to 0.45, "media_title" to "Harbor Lights", "app_name" to "TV", "supported_features" to 152461),
        ),
        HaEntity("media_player.kitchen", "paused", attrs("friendly_name" to "Kitchen speaker", "volume_level" to 0.3, "supported_features" to 152461)),
        HaEntity("media_player.patio", "off", attrs("friendly_name" to "Patio speaker", "supported_features" to 152461)),
        HaEntity("scene.movie_night", "scening", attrs("friendly_name" to "Movie night")),
        HaEntity("scene.reading", "scening", attrs("friendly_name" to "Reading")),
        HaEntity("scene.dinner", "scening", attrs("friendly_name" to "Dinner")),
        HaEntity("weather.home", "sunny", attrs("friendly_name" to "Home", "temperature" to 64, "temperature_unit" to "°F")),
        HaEntity("lock.front_door", "locked", attrs("friendly_name" to "Front door")),
        HaEntity("cover.garage_door", "closed", attrs("friendly_name" to "Garage door", "device_class" to "garage")),
        HaEntity("binary_sensor.living_room_motion", "on", attrs("friendly_name" to "Living room motion", "device_class" to "motion")),
        HaEntity("sensor.commute_work", "18", attrs("friendly_name" to "Commute to work", "unit_of_measurement" to "min", "route" to "Lakeshore Dr")),
    ).associateBy { it.id }

    fun areas(): List<HaArea> = listOf(
        HaArea(
            "living_room", "Living Room",
            setOf(
                "light.floor_lamp", "light.ceiling", "light.tv_backlight", "light.shelf", "switch.string_lights",
                "media_player.soundbar", "media_player.kitchen", "media_player.patio",
                "scene.movie_night", "scene.reading", "scene.dinner", "binary_sensor.living_room_motion",
            ),
        ),
        HaArea("bedroom", "Bedroom", emptySet()),
    )

    fun forecast(type: String): List<Forecast> {
        val now = ZonedDateTime.now().withMinute(0).withSecond(0).withNano(0)
        return if (type == "daily") {
            listOf(Forecast(now.toOffsetDateTime(), 71.0, 52.0, "sunny"))
        } else {
            val temps = listOf(58, 60, 62, 64, 66, 67, 69, 70, 71, 71, 70, 68)
            val conditions = listOf("sunny", "sunny", "sunny", "partlycloudy", "partlycloudy", "sunny", "sunny", "sunny", "cloudy", "cloudy", "partlycloudy", "clear-night")
            temps.indices.map { i -> Forecast(now.plusHours(i + 1L).toOffsetDateTime(), temps[i].toDouble(), null, conditions[i]) }
        }
    }

    fun calendars(): List<Pair<String, String>> = listOf("calendar.family" to "Family", "calendar.work" to "Work")

    fun events(calendarId: String): List<CalEvent> {
        val today = LocalDate.now()
        fun at(h: Int, m: Int) = OffsetDateTime.of(today, LocalTime.of(h, m), ZoneId.systemDefault().rules.getOffset(java.time.Instant.now()))
        return when (calendarId) {
            "calendar.work" -> listOf(CalEvent("Design review", at(9, 30), false))
            else -> listOf(CalEvent("Lunch with Sam", at(12, 30), false), CalEvent("Lily's swim class", at(18, 0), false))
        }
    }

    private fun team(league: League, id: String, name: String, abbr: String, logo: String) =
        Team(league, id, name, abbr, "", logo)

    fun games(): List<Game> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val kc = team(League.NFL, "12", "Kansas City Chiefs", "KC", "https://a.espncdn.com/i/teamlogos/nfl/500/kc.png")
        val buf = team(League.NFL, "2", "Buffalo Bills", "BUF", "https://a.espncdn.com/i/teamlogos/nfl/500/buf.png")
        val nyy = team(League.MLB, "10", "New York Yankees", "NYY", "https://a.espncdn.com/i/teamlogos/mlb/500/nyy.png")
        val bos = team(League.MLB, "2", "Boston Red Sox", "BOS", "https://a.espncdn.com/i/teamlogos/mlb/500/bos.png")
        val ars = team(League.EPL, "359", "Arsenal", "ARS", "https://a.espncdn.com/i/teamlogos/soccer/500/359.png")
        val liv = team(League.EPL, "364", "Liverpool", "LIV", "https://a.espncdn.com/i/teamlogos/soccer/500/364.png")
        return listOf(
            Game("demo-nfl", League.NFL, today.atTime(19, 15).atZone(zone).toInstant(), GameState.Live, "Q3 4:12", Side(kc, "21"), Side(buf, "17"), listOf("ESPN")),
            Game("demo-mlb", League.MLB, today.atTime(19, 5).atZone(zone).toInstant(), GameState.Scheduled, "", Side(nyy, null), Side(bos, null), listOf("YES")),
            Game("demo-epl", League.EPL, today.atTime(7, 30).atZone(zone).toInstant(), GameState.Final, "FT", Side(ars, "2"), Side(liv, "1"), listOf("Peacock")),
        )
    }

}

/**
 * Shell-only switchboard for UI review (`adb shell am broadcast -n app.haven.launcher/.demo.DemoReceiver -a ...`).
 * Protected by android.permission.DUMP in the manifest, which third-party apps can't hold.
 */
class DemoReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action?.substringAfterLast('.')) {
            "ENABLE" -> Demo.enable()
            "PATCH" -> Haven.settings.update { s ->
                var n = s
                intent.getStringExtra("style")?.let { n = n.copy(artCare = n.artCare.copy(style = enumOr(it, n.artCare.style))) }
                intent.getStringExtra("source")?.let { n = n.copy(artSource = enumOr(it, n.artSource)) }
                intent.getStringExtra("panel")?.let { n = n.copy(artCare = n.artCare.copy(panel = enumOr(it, n.artCare.panel))) }
                if (intent.hasExtra("bedroom")) n = n.copy(bedroomExtras = intent.getBooleanExtra("bedroom", n.bedroomExtras))
                n
            }
            "ALARM" -> ctx.startActivity(Intent(ctx, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "CRASH" -> {
                CrashReport.record(ctx, IllegalStateException("Demo crash for the report screen"))
                CrashActivity.open(ctx)
            }
            "POPUP" -> ScorePopup.show(ctx, "KC 21 – 17 BUF", "Q3 4:12 · Touchdown, Kansas City")
        }
    }
}
