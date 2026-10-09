package app.pratyahara.core.lock

/**
 * A daily window when Reels/Shorts stay closed whatever time is left, like bedtime or study hours.
 * Minutes count from midnight; a window may cross midnight (22:30 to 07:00).
 */
data class FocusHours(val startMinute: Int, val endMinute: Int) {

    fun isActive(minuteOfDay: Int): Boolean = when {
        startMinute == endMinute -> false
        startMinute < endMinute -> minuteOfDay in startMinute until endMinute
        else -> minuteOfDay >= startMinute || minuteOfDay < endMinute
    }

    /** Minutes from [minuteOfDay] until the window ends. */
    fun minutesUntilEnd(minuteOfDay: Int): Int = Math.floorMod(endMinute - minuteOfDay, MINUTES_PER_DAY)

    companion object {
        const val MINUTES_PER_DAY = 24 * 60

        /** "07:00" style, for labels. */
        fun format(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
    }
}
