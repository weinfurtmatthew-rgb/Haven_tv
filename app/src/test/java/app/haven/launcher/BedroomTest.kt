package app.haven.launcher

import app.haven.launcher.bedroom.Bedroom
import app.haven.launcher.data.AlarmConfig
import app.haven.launcher.ha.HomeAssistant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class BedroomTest {
    private val zone = ZoneId.of("America/Chicago")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone)
    private val weekdays630 = AlarmConfig(enabled = true, hour = 6, minute = 30)

    @Test
    fun ringsLaterTheSameDay() {
        // Wednesday, October 21 2026, 5:00 AM
        assertEquals(at(2026, 10, 21, 6, 30), Bedroom.nextAlarm(weekdays630, at(2026, 10, 21, 5, 0)))
    }

    @Test
    fun movesToTomorrowOncePassed() {
        assertEquals(at(2026, 10, 22, 6, 30), Bedroom.nextAlarm(weekdays630, at(2026, 10, 21, 6, 30)))
    }

    @Test
    fun skipsTheWeekend() {
        // Friday 7:00 AM -> Monday 6:30 AM
        assertEquals(at(2026, 10, 26, 6, 30), Bedroom.nextAlarm(weekdays630, at(2026, 10, 23, 7, 0)))
    }

    @Test
    fun singleDayWrapsAWholeWeek() {
        val sundays = weekdays630.copy(days = setOf(DayOfWeek.SUNDAY))
        // Sunday Oct 25 at 8:00 AM -> next Sunday
        assertEquals(at(2026, 11, 1, 6, 30), Bedroom.nextAlarm(sundays, at(2026, 10, 25, 8, 0)))
    }

    @Test
    fun offOrNoDaysNeverRings() {
        assertNull(Bedroom.nextAlarm(weekdays630.copy(enabled = false), at(2026, 10, 21, 5, 0)))
        assertNull(Bedroom.nextAlarm(weekdays630.copy(days = emptySet()), at(2026, 10, 21, 5, 0)))
    }

    @Test
    fun daysLabels() {
        assertEquals("Weekdays", Bedroom.daysLabel(AlarmConfig.WEEKDAYS))
        assertEquals("Every day", Bedroom.daysLabel(AlarmConfig.EVERY_DAY))
        assertEquals("Weekends", Bedroom.daysLabel(AlarmConfig.WEEKENDS))
    }

    @Test
    fun homeAssistantAddressesAreNormalized() {
        assertEquals("http://192.168.1.10:8123", HomeAssistant.normalizeUrl("192.168.1.10"))
        assertEquals("http://192.168.1.10:8123", HomeAssistant.normalizeUrl("192.168.1.10:8123/"))
        assertEquals("http://homeassistant.local:8123", HomeAssistant.normalizeUrl(" homeassistant.local "))
        assertEquals("https://example.ui.nabu.casa", HomeAssistant.normalizeUrl("https://example.ui.nabu.casa/"))
    }
}
