package app.haven.launcher

import android.content.pm.ApplicationInfo
import app.haven.launcher.bg.DepthMask
import app.haven.launcher.data.AppKind
import app.haven.launcher.data.AppKinds
import app.haven.launcher.data.FollowedShow
import app.haven.launcher.data.HeroKind
import app.haven.launcher.data.HomeLayout
import app.haven.launcher.data.HomeRow
import app.haven.launcher.data.HomeScope
import app.haven.launcher.data.HomeSection
import app.haven.launcher.data.KidsConfig
import app.haven.launcher.data.Layouts
import app.haven.launcher.data.LookConfig
import app.haven.launcher.data.NavStyle
import app.haven.launcher.data.Options
import app.haven.launcher.data.Settings
import app.haven.launcher.data.Surface
import app.haven.launcher.data.ThemePreset
import app.haven.launcher.media.MediaKind
import app.haven.launcher.media.MediaParse
import app.haven.launcher.ui.spotlightScrollBy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LayoutsTest {
    @Test
    fun everyLayoutIsItsOwnRecipe() {
        for (l in HomeLayout.values()) {
            val s = Layouts.apply(Settings(), l)
            assertEquals(l, s.look.layout)
            assertTrue("$l should match its recipe", Layouts.matches(s.look))
            assertEquals(l.label, Layouts.label(s.look))
            assertTrue("$l shows at least one row", s.look.rows.isNotEmpty())
            // Saved and loaded, it's the same layout.
            assertEquals(s.look, Settings.fromJson(JSONObject(s.toJson().toString())).look)
        }
    }

    @Test
    fun changingAPartIsAMix() {
        val s = Layouts.apply(Settings(), HomeLayout.GameDay)
        val mixed = Options["home.hero"]!!.with(s, HeroKind.Spotlight.name)
        assertEquals(HomeLayout.GameDay, mixed.look.layout)
        assertEquals("Game day, changed", Layouts.label(mixed.look))
        assertEquals(s.look.rows, mixed.look.rows)
    }

    @Test
    fun keepingYourOwnLook() {
        val mine = Settings(look = LookConfig(theme = ThemePreset.Forest, layoutLook = false))
        val s = Layouts.apply(mine, HomeLayout.Cinema)
        assertEquals(ThemePreset.Forest, s.look.theme)
        assertEquals(HeroKind.Movie, s.look.hero)
        assertEquals(mine.background, s.background)
        // With layouts bringing their look, Cinema is black.
        assertEquals(ThemePreset.Black, Layouts.apply(Settings(), HomeLayout.Cinema).look.theme)
        // The profile picture colour never changes with a layout.
        assertEquals(mine.look.avatar, Layouts.apply(mine.copy(look = mine.look.copy(layoutLook = true)), HomeLayout.Kids).look.avatar)
    }

    @Test
    fun layoutsDifferWhereTheyShould() {
        assertEquals(NavStyle.Menu, Layouts.recipe(HomeLayout.BigTiles).nav)
        assertEquals(NavStyle.Tabs, Layouts.recipe(HomeLayout.Shows).nav)
        assertEquals(Surface.Glass, Layouts.recipe(HomeLayout.Spotlight).surface)
        assertEquals(HeroKind.Scoreboard, Layouts.recipe(HomeLayout.GameDay).hero)
        assertEquals(listOf(HomeRow.KidsApps), Layouts.recipe(HomeLayout.Kids).rows)
        val (hero, rows) = Layouts.page(HomeSection.Sports, LookConfig())
        assertEquals(HeroKind.Scoreboard, hero)
        assertTrue(HomeRow.YourTeams in rows)
        assertTrue(Layouts.isAction(HomeSection.LiveTv))
        assertFalse(Layouts.isAction(HomeSection.Movies))
    }

    @Test
    fun kidsShowsAndRegionRoundTrip() {
        val s = Settings(
            kids = KidsConfig(pin = "2468", apps = listOf("org.pbskids.video"), bedtime = 19 * 60 + 30),
            shows = listOf(FollowedShow(82, "Harbor Street", "https://example.com/p.jpg")),
            region = "GB",
            homeScope = HomeScope.House,
        )
        val back = Settings.fromJson(JSONObject(s.toJson().toString()))
        assertEquals(s.kids, back.kids)
        assertEquals(s.shows, back.shows)
        assertEquals("GB", back.region)
        assertEquals(HomeScope.House, back.homeScope)
        assertTrue(back.kids.locked)
        // Only digits make a PIN; a bedtime outside the day is none.
        assertEquals("12", KidsConfig.fromJson(JSONObject("""{"pin":"1a2","bedtime":5000}""")).pin)
        assertEquals(-1, KidsConfig.fromJson(JSONObject("""{"bedtime":5000}""")).bedtime)
        // The PIN shows as on or off, never as itself.
        assertEquals("On", Options["kids.pin"]!!.label(s))
        assertEquals("Off", Options["kids.pin"]!!.label(Settings()))
    }

    @Test
    fun appKinds() {
        assertTrue(AppKind.Movies in AppKinds.of("com.netflix.ninja", "Netflix", -1))
        assertTrue(AppKind.Sports in AppKinds.of("com.example.watchespn", "ESPN", -1))
        assertTrue(AppKind.Music in AppKinds.of("com.example.player", "Spotify", -1))
        val kids = AppKinds.of("com.example.cartoons", "Cartoon Kids", ApplicationInfo.CATEGORY_VIDEO)
        assertTrue(AppKind.Kids in kids)
        assertFalse(AppKind.Movies in kids)
        assertTrue(AppKind.Games in AppKinds.of("com.example.racer", "Racer", ApplicationInfo.CATEGORY_GAME))
        // Short words only match whole words: "inflate" isn't the NFL.
        assertFalse(AppKind.Sports in AppKinds.of("com.example.inflate", "Balloon inflater", -1))
    }

    @Test
    fun tvMazeSchedules() {
        val tonight = JSONArray(
            """[{"id":1,"name":"Pilot","season":3,"number":7,"type":"regular","airstamp":"2026-10-28T01:00:00+00:00",
            "show":{"id":82,"name":"Harbor Street","weight":97,"genres":["Drama"],"network":{"name":"Channel 4"},
            "image":{"medium":"https://static.tvmaze.com/m.jpg","original":"https://static.tvmaze.com/o.jpg"},"summary":"<p>New <b>neighbours</b>.</p>"}}]""",
        )
        val a = MediaParse.schedule(tonight).single()
        assertEquals(82, a.showId)
        assertEquals("Harbor Street", a.show)
        assertEquals("Channel 4", a.network)
        assertFalse(a.web)
        assertEquals(java.time.Instant.parse("2026-10-28T01:00:00Z"), a.at)
        assertEquals("New neighbours.", a.summary)
        assertEquals("https://static.tvmaze.com/m.jpg", a.poster)
        val web = JSONArray(
            """[{"id":2,"name":"Ep","season":1,"number":1,"airstamp":"2026-10-27T07:00:00+00:00",
            "_embedded":{"show":{"id":90,"name":"Lantern Bay","network":null,"webChannel":{"name":"Streamline"},"image":null}}}]""",
        )
        val w = MediaParse.schedule(web).single()
        assertEquals("Streamline", w.network)
        assertTrue(w.web)
        assertNull(w.poster)
    }

    @Test
    fun tvMazeShowsAndPictures() {
        val show = MediaParse.show(
            JSONObject(
                """{"id":82,"name":"Harbor Street","status":"Running","premiered":"2024-01-01","genres":["Drama"],
                "rating":{"average":8.1},"schedule":{"time":"21:00","days":["Thursday"]},"network":{"name":"Channel 4"},
                "_embedded":{"nextepisode":{"season":3,"number":8,"name":"Tide","airstamp":"2026-11-05T02:00:00+00:00"}}}""",
            ),
        )!!
        assertEquals("Channel 4", show.network)
        assertEquals(8.1, show.rating!!, 0.001)
        assertEquals(8, show.next!!.number)
        assertNull(show.previous)
        val found = MediaParse.search(JSONArray("""[{"score":0.9,"show":{"id":5,"name":"Back Roads"}}]"""))
        assertEquals("Back Roads", found.single().name)
        val bg = MediaParse.background(
            JSONArray(
                """[{"type":"poster","resolutions":{"original":{"url":"p"}}},
                {"type":"background","main":false,"resolutions":{"original":{"url":"b1"}}},
                {"type":"background","main":true,"resolutions":{"original":{"url":"b2"}}}]""",
            ),
        )
        assertEquals("b2", bg)
    }

    @Test
    fun tmdbListsAndDetails() {
        val list = MediaParse.tmdbList(
            JSONObject(
                """{"results":[{"id":7,"title":"The Long Harbor","release_date":"2026-09-01","vote_average":8.1,"genre_ids":[18,9648],
                "poster_path":"/p.jpg","backdrop_path":"/b.jpg","overview":"A ferry captain."},
                {"id":8,"title":"Hidden","adult":true}]}""",
            ),
            MediaKind.Movie,
        )
        val m = list.single()
        assertEquals("2026", m.year)
        assertEquals(listOf("Drama", "Mystery"), m.genres)
        assertEquals("https://image.tmdb.org/t/p/w1280/b.jpg", m.backdrop)
        assertEquals(7, m.tmdbId)
        val d = MediaParse.details(
            JSONObject(
                """{"runtime":134,"genres":[{"id":18,"name":"Drama"}],
                "videos":{"results":[{"site":"Vimeo","key":"v","type":"Trailer"},{"site":"YouTube","key":"abc","type":"Trailer","official":true}]},
                "watch/providers":{"results":{"US":{"flatrate":[{"provider_name":"Netflix"}],"rent":[{"provider_name":"Apple TV"}]}}}}""",
            ),
            "US",
        )
        assertEquals(134, d.runtimeMin)
        assertEquals(listOf("Netflix", "Apple TV"), d.providers)
        assertEquals("abc", d.trailer)
        assertTrue(MediaParse.sameService("Amazon Prime Video", "Prime Video"))
        assertTrue(MediaParse.sameService("Disney Plus", "Disney+"))
        assertTrue(MediaParse.sameService("Peacock Premium", "Peacock"))
        assertFalse(MediaParse.sameService("Netflix", "Hulu"))
        assertEquals(8, MediaParse.providerFor("Netflix"))
    }

    // ---- the depth clock ------------------------------------------------------------------------------

    private fun sky(y: Int, h: Int): Int {
        val t = y.toFloat() / h
        val r = (40 + 180 * t).toInt()
        val g = (70 + 110 * t).toInt()
        val b = (140 - 40 * t).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** A sky with a mountain rising from the bottom: its peak at [peak] rows, at the middle column. */
    private fun mountains(w: Int, h: Int, peak: Int): IntArray = IntArray(w * h) { i ->
        val x = i % w
        val y = i / w
        val ridge = peak + abs(x - w / 2) * 2 / 3
        if (y >= ridge) 0xFF1E2636.toInt() else sky(y, h)
    }

    @Test
    fun depthFindsAMountain() {
        val w = 480
        val h = 215
        val scene = DepthMask.scene(mountains(w, h, 83), w, h)
        assertNotNull(scene)
        // The skyline is where the mountain is, and the far corners are all sky.
        assertEquals(83.0, scene!!.top[w / 2].toDouble(), 2.0)
        assertEquals(h, scene.top[0])
        // At full size the edge lines up with the photo, and the clock sits a little behind the peak.
        val fw = 1920
        val fh = 860
        val full = mountains(fw, fh, 332)
        val edge = DepthMask.edge(scene, full, fw, fh)
        assertEquals(332.0, edge[fw / 2].toDouble(), 3.0)
        val p = DepthMask.place(edge, fw / 2, 600, 190, 380, 530)
        assertTrue(p.depth)
        val cut = DepthMask.cutout(full, fw, fh, scene, edge, fw / 2 - 300, p.baseline - 190, 600, 190)
        assertTrue("the peak covers part of the clock", cut.any { it ushr 24 > 0 })
        assertTrue("the sky stays clear", cut.any { it == 0 })
    }

    @Test
    fun depthLeavesFlatHorizonsAndBusyPhotosAlone() {
        val w = 480
        val h = 215
        // A flat sea: there's scenery, but nothing rises into the clock, so it sits in front.
        val sea = IntArray(w * h) { i -> if (i / w >= 140) 0xFF1E5078.toInt() else sky(i / w, h) }
        val scene = DepthMask.scene(sea, w, h)!!
        val fw = 1920
        val fh = 860
        val full = IntArray(fw * fh) { i -> if (i / fw >= 560) 0xFF1E5078.toInt() else sky(i / fw * h / fh, h) }
        assertFalse(DepthMask.place(DepthMask.edge(scene, full, fw, fh), fw / 2, 600, 190, 380, 530).depth)
        // Leaves everywhere: no sky to fill, no depth.
        val rnd = java.util.Random(3)
        val leaves = IntArray(w * h) { (0xFF shl 24) or (rnd.nextInt(60) shl 16) or ((60 + rnd.nextInt(120)) shl 8) or rnd.nextInt(50) }
        assertNull(DepthMask.scene(leaves, w, h))
    }

    @Test
    fun theSunFloatsBehindTheClock() {
        val w = 480
        val h = 215
        val px = mountains(w, h, 150)
        // A bright disc high in the sky, touching nothing.
        for (y in 30 until 70) for (x in 300 until 340) if ((x - 320) * (x - 320) + (y - 50) * (y - 50) < 400) px[y * w + x] = 0xFFF6D28B.toInt()
        val scene = DepthMask.scene(px, w, h)!!
        assertFalse(scene.fg[50 * w + 320])
        assertTrue(scene.fg[(h - 1) * w + 320])
    }

    @Test
    fun spotlightKeepsThePhotoWholeOrGone() {
        // 1080 tall, the photo 860, row names 100; things are 160 tall at a place on the page.
        fun by(now: Float, top: Float) = spotlightScrollBy(now, top - now, 160f, 1080f, 860f, 100f)
        // On the photo (the dock, the top bar): back to the top.
        assertEquals(0f, by(0f, 620f), 0.01f)
        assertEquals(-860f, by(860f, 620f), 0.01f)
        // The first row below it: the photo slides all the way away.
        assertEquals(860f, by(0f, 940f), 0.01f)
        // Further down: as little as it can.
        assertEquals(320f, by(860f, 2100f), 0.01f)
        // Back up, on screen with its row's name: no move. The first row again: the first screen below the photo.
        assertEquals(0f, by(1180f, 1500f), 0.01f)
        assertEquals(-320f, by(1180f, 940f), 0.01f)
    }
}
