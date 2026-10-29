package app.haven.launcher

import app.haven.launcher.media.WatchList
import app.haven.launcher.media.Watched
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchListTest {
    private fun w(title: String, pos: Long = 600_000, dur: Long = 3_600_000, art: String = "", at: Long = 1_000L, profile: String = "p1") =
        Watched("com.example.video", title, "", art, pos, dur, at, profile)

    @Test
    fun newestFirstOncePerTitleKeepingItsPicture() {
        var l = WatchList.record(emptyList(), w("A", art = "a.jpg"))
        l = WatchList.record(l, w("B"))
        l = WatchList.record(l, w("A", pos = 900_000))
        assertEquals(listOf("A", "B"), l.map { it.title })
        assertEquals("a.jpg", l[0].art)
        assertEquals(900_000L, l[0].positionMs)
        // Another profile's viewing is its own.
        l = WatchList.record(l, w("A", profile = "p2"))
        assertEquals(3, l.size)
        assertEquals(listOf("A", "B"), WatchList.shown(l, "p1", 2_000L).map { it.title })
    }

    @Test
    fun finishedOldAndBarelyStartedAreLeftOut() {
        assertTrue(WatchList.finished(w("Done", pos = 3_500_000)))
        assertFalse(WatchList.finished(w("Half")))
        val month = 31L * 24 * 3600_000L
        val l = listOf(w("Done", pos = 3_500_000), w("Old", at = 0L), w("Peek", pos = 10_000), w("Half", at = month))
        assertEquals(listOf("Half"), WatchList.shown(l, "p1", month).map { it.title })
        assertEquals(l.size, WatchList.fromJson(WatchList.toJson(l)).size)
        assertEquals(3, WatchList.remove(l, w("Old")).size)
    }

    @Test
    fun keepsAtMostTheLimit() {
        var l = emptyList<Watched>()
        repeat(WatchList.MAX + 5) { l = WatchList.record(l, w("T$it")) }
        assertEquals(WatchList.MAX, l.size)
        assertEquals("T${WatchList.MAX + 4}", l.first().title)
    }
}
