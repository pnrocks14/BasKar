package app.pratyahara.core.tasks

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreaksTest {
    private val today = LocalDate.of(2026, 10, 10)
    private fun t(daysAgo: Long, c: CheckIn?, late: Boolean = false) =
        DayTask(today.minusDays(daysAgo), "Finish chapter three", 0, c, if (c != null) 1 else null, late)

    @Test fun `counts consecutive yes days`() {
        val s = Streaks.compute(listOf(t(1, CheckIn.DONE), t(2, CheckIn.DONE), t(3, CheckIn.DONE)), today)
        assertEquals(StreakSummary(done = 3, honest = 3), s)
    }

    @Test fun `an honest no breaks the done streak but not the honest one`() {
        val s = Streaks.compute(listOf(t(1, CheckIn.DONE), t(2, CheckIn.NOT_DONE), t(3, CheckIn.DONE)), today)
        assertEquals(StreakSummary(done = 1, honest = 3), s)
    }

    @Test fun `silence breaks both`() {
        val s = Streaks.compute(listOf(t(1, null), t(2, CheckIn.DONE)), today)
        assertEquals(StreakSummary(0, 0), s)
    }

    @Test fun `a late answer breaks both`() {
        val s = Streaks.compute(listOf(t(1, CheckIn.DONE, late = true), t(2, CheckIn.DONE)), today)
        assertEquals(StreakSummary(0, 0), s)
    }

    @Test fun `today's answer counts once given`() {
        val s = Streaks.compute(listOf(t(0, CheckIn.DONE), t(1, CheckIn.DONE)), today)
        assertEquals(StreakSummary(2, 2), s)
    }

    @Test fun `a missing day ends the streak`() {
        val s = Streaks.compute(listOf(t(1, CheckIn.DONE), t(3, CheckIn.DONE)), today)
        assertEquals(StreakSummary(1, 1), s)
    }
}
