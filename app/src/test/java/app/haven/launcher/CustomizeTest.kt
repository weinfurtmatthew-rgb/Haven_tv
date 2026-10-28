package app.haven.launcher

import app.haven.launcher.bedroom.Tones
import app.haven.launcher.bg.BackgroundFeeds
import app.haven.launcher.bg.BoxBlur
import app.haven.launcher.data.Accent
import app.haven.launcher.data.AlarmSound
import app.haven.launcher.data.AppLook
import app.haven.launcher.data.AppStyle
import app.haven.launcher.data.BackgroundConfig
import app.haven.launcher.data.BgKind
import app.haven.launcher.data.BriefSection
import app.haven.launcher.data.HomeRow
import app.haven.launcher.data.LookConfig
import app.haven.launcher.data.OptGroup
import app.haven.launcher.data.OptSection
import app.haven.launcher.data.Options
import app.haven.launcher.data.ProfilesMeta
import app.haven.launcher.data.Settings
import app.haven.launcher.data.ThemePreset
import app.haven.launcher.data.completeOrder
import app.haven.launcher.data.moved
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomizeTest {
    @Test
    fun optionIdsAreUnique() {
        val ids = Options.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    /** Every choice of every option sticks, and survives being saved and loaded. */
    @Test
    fun everyChoiceRoundTrips() {
        for (opt in Options.all) {
            if (opt.order != null || opt.text) continue
            for (c in opt.choices(Settings())) {
                val s = opt.with(Settings(), c.value)
                assertEquals("${opt.id} = ${c.value}", c.value, opt.value(s))
                val back = Settings.fromJson(JSONObject(s.toJson().toString()))
                assertEquals("${opt.id} = ${c.value} after saving", c.value, opt.value(back))
            }
        }
    }

    /** The six groups: every first option is a real one in its own group, and More is everything else. */
    @Test
    fun groupsAndBasics() {
        for ((group, ids) in Options.basics) {
            for (id in ids) {
                val opt = Options[id]
                assertTrue("$id is an option", opt != null)
                assertEquals("$id is in ${group.name}", group, opt!!.section.group)
            }
            assertEquals("${group.name} lists each option once", ids.size, ids.toSet().size)
        }
        for (group in OptGroup.values().filter { it != OptGroup.ThisTv }) {
            assertTrue("${group.name} starts with something", Options.basics.getValue(group).isNotEmpty())
        }
        val s = Settings()
        for (section in OptSection.values()) {
            val more = Options.moreOf(section, s)
            assertTrue(more.none { Options.isBasic(it) })
            assertEquals(Options.of(section).filter { !Options.isBasic(it) && Options.shown(it, s) }, more)
        }
        // The background's own choice (colour, gradient, photo) shows first only for the background it belongs to.
        val colour = Options["bg.kind"]!!.with(s, BgKind.Color.name)
        assertTrue(Options.basicsOf(OptGroup.HomeScreen, colour).any { it.id == "bg.color" })
        assertTrue(Options.basicsOf(OptGroup.HomeScreen, colour).none { it.id == "bg.gradient" })
    }

    @Test
    fun alarmOptionsNeedBedroomFeatures() {
        val sound = Options["alarm.sound"]!!
        assertTrue(Options.shown(sound, Settings(bedroomExtras = true)))
        assertTrue(!Options.shown(sound, Settings(bedroomExtras = false)))
        assertTrue(Options.shown(Options["artSource"]!!, Settings(bedroomExtras = false)))
    }

    @Test
    fun unknownValuesAreIgnored() {
        val s = Settings()
        assertEquals(s, Options["look.theme"]!!.with(s, "Neon"))
        assertEquals(s, Options["alarm.volume"]!!.with(s, "loud"))
    }

    @Test
    fun anyColourFromThePhone() {
        val s = Options["bg.color"]!!.with(Settings(), "4294902015")
        assertEquals(0xFFFF00FFL, s.background.color)
        assertEquals("#FF00FF", Options.hex(s.background.color))
    }

    @Test
    fun rowsKeepTheirOrderAndVisibility() {
        val rows = Options["home.rows"]!!.order!!
        val s = rows.set(Settings(), listOf("Recent", "Room"), setOf("Recent", "Room"))
        assertEquals(listOf(HomeRow.Recent, HomeRow.Room), s.look.rows)
        // Rows missing from a saved order come back at the end, hidden.
        assertEquals(HomeRow.values().toSet(), s.look.rowOrder.toSet())
        val back = Settings.fromJson(JSONObject(s.toJson().toString()))
        assertEquals(s.look, back.look)
        val sections = Options["brief.sections"]!!.order!!.set(Settings(), listOf("Teams", "Weather"), setOf("Teams"))
        assertEquals(listOf(BriefSection.Teams), sections.brief.sections)
    }

    @Test
    fun orderHelpers() {
        assertEquals(listOf(HomeRow.Apps) + HomeRow.values().filter { it != HomeRow.Apps }, completeOrder(listOf(HomeRow.Apps)))
        assertEquals(listOf("b", "a", "c"), listOf("a", "b", "c").moved("a", 1))
        assertEquals(listOf("a", "b", "c"), listOf("a", "b", "c").moved("a", -1))
    }

    @Test
    fun lookAndAppsRoundTrip() {
        val s = Settings(
            look = LookConfig(theme = ThemePreset.Daylight, accent = Accent.Mint, avatar = Accent.Rose),
            appLooks = mapOf("com.example.tv" to AppLook(name = "Movies", style = AppStyle.Icon, color = 0xFF14403F)),
        ).copy(background = Settings().background.copy(kind = BgKind.Posters, tmdbKey = "abc"))
        val back = Settings.fromJson(JSONObject(s.toJson().toString()))
        assertEquals(s.look, back.look)
        assertEquals(s.appLooks, back.appLooks)
        assertEquals(s.background, back.background)
        // A look reset to defaults isn't saved at all.
        assertTrue(s.withAppLook("com.example.tv", AppLook()).appLooks.isEmpty())
        // Settings from before this version have none of the new keys.
        assertEquals(LookConfig(), Settings.fromJson(JSONObject("{}")).look)
    }

    @Test
    fun profilesBookkeeping() {
        val m = ProfilesMeta(listOf("p1", "p2", "p4"), "p2", askOnStart = true)
        assertEquals(m, ProfilesMeta.fromJson(JSONObject(m.toJson().toString())))
        assertEquals("p3", ProfilesMeta.newId(m.order))
        assertNull(ProfilesMeta.fromJson(JSONObject("""{"order":[]}""")))
        // An active profile that no longer exists falls back to the first one.
        assertEquals("p1", ProfilesMeta.fromJson(JSONObject("""{"order":["p1","p2"],"active":"p9"}"""))!!.active)
    }

    /** Settings saved by 1.0.12, before profiles, become the first profile with nothing lost. */
    @Test
    fun settingsFromBeforeProfilesStillLoad() {
        val old = JSONObject(
            """{"roomName":"Bedroom","haArea":"bedroom","bedroomExtras":true,"profileName":"Alex",
            "haUrl":"http://homeassistant.local:8123","haToken":"abc.def","weatherEntity":"weather.home",
            "hiddenEntities":["light.closet"],"favorites":["com.att.tv","com.netflix.ninja"],"favoritesInitialized":true,
            "hiddenApps":["com.example.junk"],"use24h":true,"artSource":"Photos","artIntervalMin":7,"artIdleMin":15,
            "artCare":{"style":"Gallery","panel":"Oled","dimPct":20,"quietStart":23,"quietEnd":6,"quietMode":"Black",
              "presenceEntity":"","presenceDelayMin":10,"maxHours":4},
            "brief":{"calendars":["calendar.family"],"commuteEntity":"sensor.commute","commuteLabel":"work",
              "statusEntities":[],"headlinesFeed":"https://feeds.npr.org/1001/rss.xml","autoCloseMin":10},
            "sports":{"teams":[{"league":"nfl","id":"9","name":"Green Bay Packers","abbr":"GB","color":"204E32","logo":""}],
              "alerts":true,"alertStart":false,"alertScore":true,"alertFinal":true},
            "homeOverride":true,"alarm":{"enabled":true,"hour":6,"minute":45,"days":[1,2,3,4,5],"sunriseMin":15,"snoozeMin":9},
            "sleepLightsOff":true,"sleepBox":false}""",
        )
        val s = Settings.fromJson(old)
        assertEquals("Bedroom", s.roomName)
        assertEquals("bedroom", s.haArea)
        assertTrue(s.bedroomExtras)
        assertEquals("Alex", s.profileName)
        assertEquals("http://homeassistant.local:8123", s.haUrl)
        assertEquals("abc.def", s.haToken)
        assertEquals("weather.home", s.weatherEntity)
        assertEquals(setOf("light.closet"), s.hiddenEntities)
        assertEquals(listOf("com.att.tv", "com.netflix.ninja"), s.favorites)
        assertTrue(s.favoritesInitialized)
        assertEquals(setOf("com.example.junk"), s.hiddenApps)
        assertTrue(s.use24h)
        assertEquals(7, s.artIntervalMin)
        assertEquals(15, s.artIdleMin)
        assertEquals(20, s.artCare.dimPct)
        assertEquals(setOf("calendar.family"), s.brief.calendars)
        assertEquals("sensor.commute", s.brief.commuteEntity)
        assertEquals("Green Bay Packers", s.sports.teams.single().name)
        assertEquals(false, s.sports.alertStart)
        assertTrue(s.alarm.enabled)
        assertEquals(6 to 45, s.alarm.hour to s.alarm.minute)
        assertEquals(5, s.alarm.days.size)
        assertEquals(15, s.alarm.sunriseMin)
        assertEquals(false, s.sleepBox)
        // Everything new starts as Haven's own look and behaviour.
        assertEquals(LookConfig(), s.look)
        assertEquals(BackgroundConfig(), s.background)
        assertTrue(s.appLooks.isEmpty())
        assertEquals(AlarmSound.Chime, s.alarm.sound)
        assertEquals(BriefSection.values().toSet(), s.brief.shown)
        assertEquals(s, Settings.fromJson(JSONObject(s.toJson().toString())))
    }

    @Test
    fun alarmSoundsAreRealSound() {
        for (sound in AlarmSound.values()) {
            val pcm = Tones.pcm(sound)
            if (sound == AlarmSound.Silent) {
                assertEquals(0, pcm.size)
                continue
            }
            assertTrue("$sound is at least a second long", pcm.size >= Tones.RATE)
            val peak = pcm.maxOf { kotlin.math.abs(it.toInt()) }
            assertTrue("$sound is audible", peak > 2000)
            assertTrue("$sound doesn't clip", peak < 32767)
        }
        assertNotEquals(Tones.pcm(AlarmSound.Chime).toList(), Tones.pcm(AlarmSound.Bells).toList())
    }

    @Test
    fun blurSpreadsLightAndKeepsFlatAreasFlat() {
        val w = 32
        val h = 16
        val flat = IntArray(w * h) { 0xFF404040.toInt() }
        BoxBlur.blur(flat, w, h, 3)
        assertTrue(flat.all { it == 0xFF404040.toInt() })
        val dot = IntArray(w * h) { 0xFF000000.toInt() }
        dot[8 * w + 16] = 0xFFFFFFFF.toInt()
        BoxBlur.blur(dot, w, h, 2, passes = 1)
        val lit = dot.count { (it and 0xFF) > 0 }
        assertTrue("the bright pixel spread to its neighbours ($lit)", lit > 9)
    }

    @Test
    fun bingAndTmdbParse() {
        val bing = BackgroundFeeds.parseBing(
            JSONObject("""{"images":[{"url":"/th?id=OHR.Lake_1920x1080.jpg","title":"A quiet lake","copyright":"© Someone"}]}"""),
        )!!
        assertEquals("https://www.bing.com/th?id=OHR.Lake_1920x1080.jpg", bing.url)
        assertEquals("A quiet lake", bing.title)
        assertNull(BackgroundFeeds.parseBing(JSONObject("""{"images":[]}""")))
        val posters = BackgroundFeeds.parseTmdb(
            JSONObject(
                """{"results":[{"title":"A film","backdrop_path":"/b.jpg","poster_path":"/p.jpg"},
                   {"name":"A show","backdrop_path":null,"poster_path":"/s.jpg"},{"title":"Hidden","adult":true}]}""",
            ),
        )
        assertEquals(listOf("A film", "A show"), posters.map { it.title })
        assertEquals("https://image.tmdb.org/t/p/w1280/b.jpg", posters[0].backdrop)
        assertNull(posters[1].backdrop)
        assertEquals("https://image.tmdb.org/t/p/w342/s.jpg", posters[1].poster)
    }
}
