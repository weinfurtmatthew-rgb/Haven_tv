package app.haven.launcher

import app.haven.launcher.art.ArtCareLogic
import app.haven.launcher.brief.Rss
import app.haven.launcher.data.ArtCare
import app.haven.launcher.data.ArtStyle
import app.haven.launcher.data.FavoriteTeam
import app.haven.launcher.data.QuietMode
import app.haven.launcher.data.Settings
import app.haven.launcher.data.SportsConfig
import app.haven.launcher.data.TvPanel
import app.haven.launcher.sports.EspnProvider
import app.haven.launcher.sports.GameState
import app.haven.launcher.sports.League
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeaturesTest {
    @Test
    fun quietHoursWrapPastMidnight() {
        val care = ArtCare(quietStart = 23, quietEnd = 6)
        assertTrue(ArtCareLogic.inQuietHours(care, 23))
        assertTrue(ArtCareLogic.inQuietHours(care, 2))
        assertFalse(ArtCareLogic.inQuietHours(care, 6))
        assertFalse(ArtCareLogic.inQuietHours(care, 12))
        val day = ArtCare(quietStart = 9, quietEnd = 17)
        assertTrue(ArtCareLogic.inQuietHours(day, 9))
        assertFalse(ArtCareLogic.inQuietHours(day, 17))
        assertFalse(ArtCareLogic.inQuietHours(ArtCare(quietStart = 5, quietEnd = 5), 5))
    }

    @Test
    fun pixelShiftStaysSmallAndMoves() {
        val positions = (0L until 60L).map { ArtCareLogic.shift(strong = true, epochMinutes = it) }.toSet()
        assertTrue(positions.size > 4)
        assertTrue(positions.all { (x, y) -> x in -12..12 && y in -12..12 })
        assertTrue((0L until 120L).map { ArtCareLogic.shift(false, it) }.all { (x, y) -> x in -6..6 && y in -6..6 })
    }

    @Test
    fun restReasons() {
        val noon = 12
        val base = ArtCare(quietStart = 23, quietEnd = 6, maxHours = 4)
        assertNull(ArtCareLogic.restReason(base, noon, 1_000_000, 1_000_000, 1_000_000))
        assertEquals(ArtCareLogic.Rest.Quiet, ArtCareLogic.restReason(base, 1, 0, 0, 0))
        assertNull(ArtCareLogic.restReason(base.copy(quietMode = QuietMode.Dim), 1, 0, 0, 0))
        assertEquals(ArtCareLogic.Rest.Limit, ArtCareLogic.restReason(base, noon, 5 * 3_600_000L, 5 * 3_600_000L, 0))
        val away = base.copy(presenceEntity = "binary_sensor.motion", presenceDelayMin = 10)
        assertEquals(ArtCareLogic.Rest.Away, ArtCareLogic.restReason(away, noon, 11 * 60_000L, 0, 0))
        assertTrue(ArtCareLogic.isPresent("on"))
        assertFalse(ArtCareLogic.isPresent("off"))
    }

    @Test
    fun rssTitles() {
        val xml = """<rss><channel><title>Feed name</title>
            <item><title><![CDATA[First &amp; best story]]></title><link>x</link></item>
            <item><title>Second   story</title></item>
            <item><title>Third</title></item></channel></rss>"""
        assertEquals(listOf("First & best story", "Second story"), Rss.titles(xml, 2))
        val atom = """<feed><title>A</title><entry><title type="html">Atom one</title></entry></feed>"""
        assertEquals(listOf("Atom one"), Rss.titles(atom, 5))
    }

    @Test
    fun espnScoreboardParses() {
        val json = JSONObject(
            """{"events":[{"id":"401","date":"2026-10-27T00:15Z","status":{"type":{"state":"in","name":"STATUS_IN_PROGRESS","shortDetail":"Q3 4:12"}},
              "competitions":[{"competitors":[
                {"homeAway":"home","score":"21","team":{"id":"12","displayName":"Kansas City Chiefs","abbreviation":"KC","color":"e31837","logo":"https://x/kc.png"}},
                {"homeAway":"away","score":"17","team":{"id":"2","displayName":"Buffalo Bills","abbreviation":"BUF","logo":"https://x/buf.png"}}],
                "broadcasts":[{"market":"national","names":["ESPN","ABC"]}]}]}]}""",
        )
        val games = EspnProvider.parseEvents(json, League.NFL)
        assertEquals(1, games.size)
        val g = games[0]
        assertEquals(GameState.Live, g.state)
        assertEquals("KC", g.home.team.abbr)
        assertEquals("21", g.home.score)
        assertEquals("BUF 17 – 21 KC", g.scoreLine)
        assertEquals(listOf("ESPN", "ABC"), g.broadcasts)
        assertTrue(g.involves(setOf("nfl:12")))
    }

    @Test
    fun espnScheduleScoresAreObjects() {
        val json = JSONObject(
            """{"events":[{"id":"9","date":"2026-11-02T18:00:00Z","competitions":[{"status":{"type":{"state":"pre","shortDetail":"Sun 1:00 PM"}},
              "competitors":[{"homeAway":"home","score":{"value":0,"displayValue":"0"},"team":{"id":"359","displayName":"Arsenal","abbreviation":"ARS","logos":[{"href":"https://x/ars.png"}]}},
                             {"homeAway":"away","team":{"id":"364","displayName":"Liverpool","abbreviation":"LIV"}}]}]}]}""",
        )
        val g = EspnProvider.parseEvents(json, League.EPL).single()
        assertEquals(GameState.Scheduled, g.state)
        assertEquals("0", g.home.score)
        assertEquals("https://x/ars.png", g.home.team.logo)
    }

    @Test
    fun espnTeamsParse() {
        val json = JSONObject(
            """{"sports":[{"leagues":[{"teams":[{"team":{"id":"2","displayName":"Buffalo Bills","abbreviation":"BUF","logos":[{"href":"b"}]}},
              {"team":{"id":"12","displayName":"Kansas City Chiefs","abbreviation":"KC"}}]}]}]}""",
        )
        val teams = EspnProvider.parseTeams(json, League.NFL)
        assertEquals(listOf("Buffalo Bills", "Kansas City Chiefs"), teams.map { it.name })
        assertEquals("b", teams[0].logo)
    }

    @Test
    fun newSettingsRoundTrip() {
        val s = Settings(
            artCare = ArtCare(style = ArtStyle.FullScreen, panel = TvPanel.Oled, dimPct = 30, quietStart = 22, quietEnd = 6, presenceEntity = "binary_sensor.m"),
            sports = SportsConfig(teams = listOf(FavoriteTeam("nfl", "12", "Kansas City Chiefs", "KC")), alertScore = false),
        )
        val back = Settings.fromJson(JSONObject(s.toJson().toString()))
        assertEquals(s.artCare, back.artCare)
        assertEquals(s.sports, back.sports)
        assertEquals(s.brief, back.brief)
        // Settings saved by older versions have none of these keys.
        assertEquals(ArtCare(), Settings.fromJson(JSONObject("{}")).artCare)
    }
}
