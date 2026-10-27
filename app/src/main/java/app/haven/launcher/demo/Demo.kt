package app.haven.launcher.demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import app.haven.launcher.Haven
import app.haven.launcher.bedroom.AlarmActivity
import app.haven.launcher.bedroom.Bedroom
import app.haven.launcher.data.Accent
import app.haven.launcher.data.AlarmConfig
import app.haven.launcher.data.FollowedShow
import app.haven.launcher.data.HomeLayout
import app.haven.launcher.data.KidsConfig
import app.haven.launcher.data.Layouts
import app.haven.launcher.data.Settings
import app.haven.launcher.data.AppLook
import app.haven.launcher.data.AppStyle
import app.haven.launcher.data.BriefConfig
import app.haven.launcher.data.HeadingFont
import app.haven.launcher.data.Options
import app.haven.launcher.data.SportsConfig
import app.haven.launcher.data.TextFont
import app.haven.launcher.data.ThemePreset
import app.haven.launcher.data.TvPanel
import app.haven.launcher.data.enumOr
import java.io.File
import app.haven.launcher.ha.CalEvent
import app.haven.launcher.ha.Forecast
import app.haven.launcher.ha.HaArea
import app.haven.launcher.ha.HaEntity
import app.haven.launcher.media.MediaItem
import app.haven.launcher.media.MediaKind
import app.haven.launcher.media.MediaList
import app.haven.launcher.sports.Game
import app.haven.launcher.sports.GameState
import app.haven.launcher.sports.League
import app.haven.launcher.sports.NewsItem
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
                shows = SHOWS.take(4).mapIndexed { i, name -> FollowedShow(i + 1, name) },
            )
        }
        // A second person with a different look, for the profile picker and theme screenshots.
        if (Haven.settings.profiles.value.none { it.name == "Sam" }) {
            val sam = Haven.settings.addProfile("Sam", copy = true)
            Haven.settings.updateProfile(sam) {
                it.copy(
                    look = it.look.copy(theme = ThemePreset.Midnight, accent = Accent.Sky, headingFont = HeadingFont.Outfit, textFont = TextFont.Inter, avatar = Accent.Sky),
                    alarm = it.alarm.copy(enabled = true, hour = 7, minute = 15),
                )
            }
        }
        // A child with the Kids layout, a grown-up PIN (1234) and a bedtime, for the kids' screenshots.
        if (Haven.settings.profiles.value.none { it.name == "Lily" }) {
            val lily = Haven.settings.addProfile("Lily", copy = false)
            Haven.settings.updateProfile(lily) {
                val kid = Layouts.apply(it, HomeLayout.Kids)
                kid.copy(
                    profileName = "Lily",
                    kids = KidsConfig(pin = "1234", apps = kidApps(), bedtime = 19 * 60 + 30),
                    look = kid.look.copy(avatar = Accent.Coral),
                )
            }
        }
        Haven.scope.launch {
            Haven.ha.refresh()
            Haven.sports.refresh()
        }
    }

    /** A few of the emulator's apps stand in for kids' apps. */
    private fun kidApps(): List<String> =
        Haven.apps.apps.value.map { it.pkg }.filter { it.contains("youtube") || it.contains("games") || it.contains("tv") }.take(3)

    /** "id=value;id=value" from the registry, applied to the profile on the TV. */
    fun setOptions(spec: String) {
        for (pair in spec.split(';').map { it.trim() }.filter { it.contains('=') }) {
            val opt = Options[pair.substringBefore('=')] ?: continue
            val value = pair.substringAfter('=')
            Haven.settings.update { s ->
                val order = opt.order
                if (order != null) {
                    // Rows or sections: "Recent,Favorites,Room" in that order, all shown.
                    val keys = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    order.set(s, keys, keys.toSet())
                } else {
                    opt.with(s, value)
                }
            }
        }
    }

    /** A painted landscape saved as an uploaded background photo, so the emulator has one to show. */
    fun backgroundPhoto(): String {
        val f = File(Haven.backgrounds.dir, "Demo hills.jpg")
        if (!f.exists()) {
            val bmp = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
            p.shader = LinearGradient(0f, 0f, 0f, 760f, intArrayOf(0xFF2B3F6B.toInt(), 0xFFE8A060.toInt()), null, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, 1920f, 1080f, p)
            p.shader = null
            p.color = 0xFFF6D28B.toInt()
            c.drawCircle(1340f, 430f, 110f, p)
            p.color = 0xFF2E4A3B.toInt()
            c.drawCircle(420f, 1300f, 700f, p)
            p.color = 0xFF1F3329.toInt()
            c.drawCircle(1500f, 1420f, 820f, p)
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        }
        return f.name
    }

    /** Gives the first favorites their own names, an icon on a colour and a painted picture. */
    fun appLooks() {
        val favs = Haven.settings.value.favorites
        val first = favs.getOrNull(0) ?: return
        Haven.settings.update { it.withAppLook(first, AppLook(name = "Live TV", style = AppStyle.Icon, color = 0xFFF2B35E)) }
        val second = favs.getOrNull(1) ?: return
        val f = File(Haven.apps.imagesDir, "demo-picture.jpg")
        if (!f.exists()) {
            val bmp = Bitmap.createBitmap(800, 450, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = LinearGradient(0f, 0f, 800f, 450f, intArrayOf(0xFF3A2348.toInt(), 0xFFE8795E.toInt()), null, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, 800f, 450f, p)
            p.shader = null
            p.color = 0xFFF2EDE4.toInt()
            p.textSize = 96f
            c.drawText("Movies", 60f, 270f, p)
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        }
        Haven.settings.update { it.withAppLook(second, AppLook(name = "Movie night", style = AppStyle.Picture, image = f.name)) }
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
            attrs(
                "friendly_name" to "Soundbar", "volume_level" to 0.45, "media_title" to "Harbor Lights", "media_artist" to "The Night Ferries",
                "media_album_name" to "Low Tide", "media_duration" to 238, "media_position" to 102,
                "media_position_updated_at" to java.time.Instant.now().toString(), "entity_picture" to picture("cover", 600, 600, 0xFF9B2C57, 0xFFF4927A),
                "app_name" to "TV", "supported_features" to 152461,
            ),
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
        HaEntity(
            "climate.living_room", "heat",
            attrs(
                "friendly_name" to "Thermostat", "current_temperature" to 68, "temperature" to 70, "hvac_action" to "heating",
                "hvac_modes" to listOf("off", "heat", "cool", "auto"), "min_temp" to 50, "max_temp" to 90, "target_temp_step" to 1,
            ),
        ),
        HaEntity("camera.front_porch", "idle", attrs("friendly_name" to "Front porch", "entity_picture" to picture("porch", 960, 540, 0xFF55635A, 0xFF1C2420))),
        HaEntity("camera.driveway", "idle", attrs("friendly_name" to "Driveway", "entity_picture" to picture("driveway", 960, 540, 0xFF5D5A4A, 0xFF1F1D17))),
        HaEntity("sensor.living_room_temperature", "68.4", attrs("friendly_name" to "Living room", "device_class" to "temperature", "unit_of_measurement" to "°F")),
        HaEntity("sensor.living_room_humidity", "45", attrs("friendly_name" to "Humidity", "device_class" to "humidity", "unit_of_measurement" to "%")),
        HaEntity("binary_sensor.back_door", "off", attrs("friendly_name" to "Back door", "device_class" to "door")),
        HaEntity("automation.porch_lights_at_sunset", "on", attrs("friendly_name" to "Porch lights at sunset")),
        HaEntity("automation.blinds_in_the_morning", "off", attrs("friendly_name" to "Blinds in the morning")),
        HaEntity("person.matt", "home", attrs("friendly_name" to "Matt")),
        HaEntity("person.sam", "not_home", attrs("friendly_name" to "Sam")),
    ).associateBy { it.id }

    /** A painted stand-in picture (album cover, camera view) saved as a file, so the demo needs no network. */
    private fun picture(name: String, w: Int, h: Int, from: Long, to: Long): String {
        val f = File(Haven.app.filesDir, "demo/$name.jpg")
        if (!f.exists()) {
            f.parentFile?.mkdirs()
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
            p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), intArrayOf(from.toInt(), to.toInt()), null, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            p.shader = null
            p.color = 0x55FFFFFF
            c.drawCircle(w * 0.7f, h * 0.35f, h * 0.18f, p)
            p.color = 0x66000000
            c.drawRect(0f, h * 0.72f, w.toFloat(), h.toFloat(), p)
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        }
        return "file://" + f.absolutePath
    }

    // ---- movies, shows and sports news (invented titles, painted posters) -----------------------------

    private val MOVIES = listOf("The Long Harbor", "Night Shift", "Paper Moons", "Northern Line", "Glass Garden", "The Last Ferry", "Copper Hills", "Small Hours")
    private val SHOWS = listOf("Harbor Street", "Back Roads", "Quiet Valley", "The Kitchen Table", "Lantern Bay", "Field Notes", "Two Doors Down", "Tall Grass")
    private val COLORS = listOf(0xFFC44D34, 0xFF2F6F8F, 0xFFD6B25A, 0xFF6B4FA8, 0xFF3D8C62, 0xFFA33D6D, 0xFF888888, 0xFFC77D2A)

    private fun poster(title: String, i: Int, wide: Boolean): String {
        val w = if (wide) 1280 else 342
        val h = if (wide) 720 else 513
        val f = File(Haven.app.filesDir, "demo/${if (wide) "wide" else "poster"}-$i.jpg")
        if (!f.exists()) {
            f.parentFile?.mkdirs()
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
            val base = COLORS[i % COLORS.size].toInt()
            p.shader = LinearGradient(0f, 0f, w * 0.6f, h.toFloat(), intArrayOf(base, 0xFF0B0B10.toInt()), null, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            p.shader = null
            p.color = 0x40FFFFFF
            c.drawCircle(w * 0.72f, h * 0.3f, h * 0.2f, p)
            if (!wide) {
                p.color = 0xFFF2EDE4.toInt()
                p.textSize = 34f
                p.isFakeBoldText = true
                val words = title.split(" ")
                var y = h - 40f - (words.size - 1) * 40f
                for (wd in words) {
                    c.drawText(wd, 24f, y, p)
                    y += 40f
                }
            }
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        }
        return "file://" + f.absolutePath
    }

    fun media(list: MediaList, s: Settings): List<MediaItem> {
        val zone = ZoneId.systemDefault()
        val evening = LocalDate.now().atTime(20, 0).atZone(zone).toInstant()
        return when (list) {
            MediaList.TrendingMovies, MediaList.InTheaters, MediaList.ComingSoon, MediaList.TopMovies -> MOVIES.mapIndexed { i, t ->
                val j = (i + list.ordinal * 3) % MOVIES.size
                MediaItem(
                    "demo:movie:$j", MediaKind.Movie, MOVIES[j], year = "2026", rating = 6.8 + (j % 5) * 0.4,
                    overview = "A story about people, places and the hours between them. Invented for Haven's demo.",
                    poster = poster(MOVIES[j], j, false), backdrop = poster(MOVIES[j], j, true), genres = listOf("Drama", "Mystery").take(1 + j % 2),
                )
            }
            MediaList.TrendingShows -> SHOWS.mapIndexed { i, t ->
                MediaItem("demo:show:$i", MediaKind.Show, t, year = "2025", rating = 7.0 + (i % 4) * 0.3, poster = poster(t, i + 8, false), genres = listOf("Comedy"))
            }
            MediaList.YourShows -> s.shows.mapIndexed { i, f ->
                val (badge, note) = when (i) {
                    0 -> "NEW TONIGHT" to "Tonight 9:00 PM on Channel 4"
                    1 -> "" to "Thursday 8:00 PM on Channel 2"
                    2 -> "" to "Back March 4"
                    else -> "" to "Waiting on a new season"
                }
                MediaItem(
                    "maze:${f.id}", MediaKind.Show, f.name, overview = "New neighbours, old secrets. Invented for Haven's demo.",
                    poster = poster(f.name, i + 8, false), showId = f.id, network = if (i < 2) "Channel ${if (i == 0) 4 else 2}" else "",
                    at = if (i == 0) evening.plusSeconds(3600) else null, note = note, badge = badge,
                )
            }
            MediaList.Tonight -> SHOWS.mapIndexed { i, t ->
                val at = evening.plusSeconds(1800L * i)
                val time = at.atZone(zone).format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
                MediaItem("maze:${100 + i}", MediaKind.Show, t, poster = poster(t, i + 8, false), showId = 100 + i, network = "Channel ${2 + i % 4}", at = at, note = "$time · Channel ${2 + i % 4}")
            }
            MediaList.NewStreaming -> SHOWS.reversed().mapIndexed { i, t ->
                MediaItem("maze:${200 + i}", MediaKind.Show, t, poster = poster(t, 15 - i, false), showId = 200 + i, network = "Streamline", note = "New on Streamline")
            }
        }
    }

    fun news(): List<NewsItem> {
        val now = java.time.Instant.now()
        return listOf(
            NewsItem("Kansas City's late drive seals it", "A fourth-quarter march of 80 yards. Invented for Haven's demo.", null, now.minusSeconds(3600), League.NFL),
            NewsItem("Yankees set their rotation for the weekend", "Who starts and when. Invented for Haven's demo.", null, now.minusSeconds(7200), League.MLB),
            NewsItem("Arsenal's midfield, explained", "What changed since the spring. Invented for Haven's demo.", null, now.minusSeconds(14400), League.EPL),
        )
    }

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
            "ALARM" -> ctx.startActivity(
                Intent(ctx, AlarmActivity::class.java)
                    .putExtra(Bedroom.EXTRA_PROFILE, Haven.settings.activeId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            "OPT" -> Demo.setOptions(intent.getStringExtra("set").orEmpty())
            "BGPHOTO" -> {
                val name = Demo.backgroundPhoto()
                Haven.settings.update { it.copy(background = it.background.copy(photo = name)) }
            }
            "APPLOOKS" -> Demo.appLooks()
            "UNSNOOZE" -> Bedroom.stopped(ctx, Haven.settings.activeId)
            "SWITCH" -> Haven.settings.profiles.value.firstOrNull { it.name == intent.getStringExtra("name") }?.let { Haven.settings.switchTo(it.id) }
            "RESET" -> Haven.settings.update { it.copy(look = app.haven.launcher.data.LookConfig(avatar = it.look.avatar), background = app.haven.launcher.data.BackgroundConfig()) }
            "CRASH" -> {
                CrashReport.record(ctx, IllegalStateException("Demo crash for the report screen"))
                CrashActivity.open(ctx)
            }
            "POPUP" -> ScorePopup.show(ctx, Demo.games().first().scoreLine, "Q3 4:12 · Touchdown, Kansas City")
        }
    }
}
