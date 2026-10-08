package app.pratyahara.core.tasks

import java.time.LocalDate

data class StreakSummary(
    /** Consecutive days answered "yes, did it" on time. */
    val done: Int,
    /** Consecutive days answered on time, either way. Honesty keeps this alive. */
    val honest: Int,
)

object Streaks {

    /** Counts back from today (if already answered) or yesterday. A missing or late day ends a streak. */
    fun compute(tasks: List<DayTask>, today: LocalDate): StreakSummary {
        val byDay = tasks.associateBy { it.day }
        val start = if (byDay[today]?.checkIn != null) today else today.minusDays(1)

        fun run(predicate: (DayTask) -> Boolean): Int {
            var count = 0
            var day = start
            while (true) {
                val t = byDay[day] ?: break
                if (t.checkIn == null || t.late || !predicate(t)) break
                count++
                day = day.minusDays(1)
            }
            return count
        }

        return StreakSummary(
            done = run { it.checkIn == CheckIn.DONE },
            honest = run { true },
        )
    }
}
