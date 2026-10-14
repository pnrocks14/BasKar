package app.pratyahara.core.stats

import java.time.LocalDate

/** This week (the 7 days ending today) against the 7 days before it. */
data class WeekReport(
    val thisWeekMinutes: Int,
    val lastWeekMinutes: Int,
    /** Days this week that stayed within the daily limit; days before the start don't count. */
    val daysWithinLimit: Int,
    val daysCounted: Int,
    /** The lightest day this week, or null if there is no day to count. */
    val bestDay: LocalDate?,
    val visits: Int,
    val blocks: Int,
) {
    /** Positive means less scrolling than last week. Null when there's no last week to compare. */
    val minutesLess: Int? get() = if (lastWeekMinutes == 0 && thisWeekMinutes == 0) null else lastWeekMinutes - thisWeekMinutes

    companion object {
        fun build(
            usedSecondsByDay: Map<LocalDate, Long>,
            visitsByDay: Map<LocalDate, Int>,
            blocksByDay: Map<LocalDate, Int>,
            today: LocalDate,
            startDay: LocalDate,
            budgetMinutes: Int,
        ): WeekReport {
            val thisWeek = (0L until 7L).map { today.minusDays(it) }.filter { !it.isBefore(startDay) }
            val lastWeek = (7L until 14L).map { today.minusDays(it) }.filter { !it.isBefore(startDay) }
            fun mins(d: LocalDate) = ((usedSecondsByDay[d] ?: 0L) / 60).toInt()
            return WeekReport(
                thisWeekMinutes = thisWeek.sumOf { mins(it) },
                lastWeekMinutes = lastWeek.sumOf { mins(it) },
                daysWithinLimit = thisWeek.count { mins(it) <= budgetMinutes },
                daysCounted = thisWeek.size,
                bestDay = thisWeek.minByOrNull { mins(it) },
                visits = thisWeek.sumOf { visitsByDay[it] ?: 0 },
                blocks = thisWeek.sumOf { blocksByDay[it] ?: 0 },
            )
        }
    }
}
