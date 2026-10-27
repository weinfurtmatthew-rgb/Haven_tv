package app.haven.launcher.art

import app.haven.launcher.data.ArtCare

/** Screen-care rules for art mode, kept free of Android so they can be unit tested. */
object ArtCareLogic {
    /** True when [hour] (0..23) falls in the quiet window; windows may wrap past midnight (23 to 6). */
    fun inQuietHours(care: ArtCare, hour: Int): Boolean {
        if (!care.quietEnabled) return false
        val s = care.quietStart
        val e = care.quietEnd
        return if (s < e) hour in s until e else hour >= s || hour < e
    }

    private val ORBIT = listOf(0 to 0, 1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1)

    /**
     * Pixel shift: the picture walks around a small square so no edge or caption sits on the same pixels for
     * hours. OLED (strong) moves further and more often than LCD.
     */
    fun shift(strong: Boolean, epochMinutes: Long): Pair<Int, Int> {
        val period = if (strong) 3 else 10
        val amp = if (strong) 12 else 6
        val p = ORBIT[((epochMinutes / period) % ORBIT.size).toInt()]
        return p.first * amp to p.second * amp
    }

    /** States that mean "someone is here" for motion, occupancy and presence entities. */
    fun isPresent(state: String?): Boolean =
        state == null || state in setOf("on", "home", "detected", "occupied", "true", "open", "unavailable", "unknown")

    /** How long a caption stays before fading, in seconds. */
    fun captionSeconds(strong: Boolean): Long = if (strong) 20 else 60

    enum class Rest { Quiet, Away, Limit }

    /** Why art should go black right now, or null to keep showing it. */
    fun restReason(care: ArtCare, hour: Int, nowMs: Long, lastPresentMs: Long, startedMs: Long): Rest? = when {
        inQuietHours(care, hour) && care.quietMode == app.haven.launcher.data.QuietMode.Black -> Rest.Quiet
        care.presenceEntity.isNotBlank() && nowMs - lastPresentMs > care.presenceDelayMin * 60_000L -> Rest.Away
        care.maxHours > 0 && nowMs - startedMs > care.maxHours * 3_600_000L -> Rest.Limit
        else -> null
    }
}
