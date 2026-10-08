package app.pratyahara.core.stats

import java.time.LocalDate

object TimeSaved {
    /**
     * Minutes saved over the 7 days ending [today], against the daily [baselineMinutes] the user
     * estimated during onboarding. Days before [startDay] (when they began) don't count.
     */
    fun weekMinutes(
        baselineMinutes: Int,
        usedSecondsByDay: Map<LocalDate, Long>,
        today: LocalDate,
        startDay: LocalDate,
    ): Int = (0L until 7L)
        .map { today.minusDays(it) }
        .filter { !it.isBefore(startDay) }
        .sumOf { day -> (baselineMinutes - ((usedSecondsByDay[day] ?: 0L) / 60).toInt()).coerceAtLeast(0) }
}
