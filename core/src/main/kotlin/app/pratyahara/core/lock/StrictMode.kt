package app.pratyahara.core.lock

/**
 * A promise you make for a few days: while it runs, nothing that loosens Pratyahara can be done, not even
 * with a delay. No turning blocking off, no removing apps, no raising the limit, no squat passes, no
 * editing focus hours. Tightening still works.
 */
object StrictMode {
    val DURATIONS_DAYS = listOf(1, 3, 7)
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    fun isActive(untilMillis: Long, nowMillis: Long): Boolean = nowMillis < untilMillis

    /** Starting again while active can only extend the promise, never shorten it. */
    fun extend(currentUntil: Long, nowMillis: Long, days: Int): Long =
        maxOf(currentUntil, nowMillis + days.coerceIn(1, DURATIONS_DAYS.last()) * DAY_MS)

    /** "2 days", "5 hours" or "under an hour". */
    fun remaining(untilMillis: Long, nowMillis: Long): String {
        val hours = ((untilMillis - nowMillis).coerceAtLeast(0) + 59 * 60 * 1000L) / (60 * 60 * 1000L)
        return when {
            hours >= 48 -> "${(hours + 12) / 24} days"
            hours >= 2 -> "$hours hours"
            hours == 1L -> "an hour"
            else -> "under an hour"
        }
    }
}
