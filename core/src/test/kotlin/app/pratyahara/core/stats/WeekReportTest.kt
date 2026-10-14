package app.pratyahara.core.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WeekReportTest {
    private val today = LocalDate.of(2026, 10, 18)

    @Test fun `compares with the week before`() {
        val used = mapOf(
            today to 10 * 60L, today.minusDays(1) to 40 * 60L, // this week: 50
            today.minusDays(8) to 90 * 60L, // last week: 90
        )
        val r = WeekReport.build(used, mapOf(today to 3), mapOf(today.minusDays(1) to 2), today, today.minusDays(30), budgetMinutes = 30)
        assertEquals(50, r.thisWeekMinutes)
        assertEquals(90, r.lastWeekMinutes)
        assertEquals(40, r.minutesLess)
        assertEquals(6, r.daysWithinLimit)
        assertEquals(7, r.daysCounted)
        assertEquals(3, r.visits)
        assertEquals(2, r.blocks)
    }

    @Test fun `days before the start don't count`() {
        val r = WeekReport.build(mapOf(today to 5 * 60L), emptyMap(), emptyMap(), today, today.minusDays(1), budgetMinutes = 30)
        assertEquals(2, r.daysCounted)
        assertEquals(today.minusDays(1), r.bestDay)
        assertEquals(0, r.lastWeekMinutes)
    }

    @Test fun `no comparison with nothing to compare`() {
        assertNull(WeekReport.build(emptyMap(), emptyMap(), emptyMap(), today, today, 30).minutesLess)
    }
}
