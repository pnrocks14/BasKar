package app.pratyahara.core.stats

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TimeSavedTest {
    private val today = LocalDate.of(2026, 10, 10)

    @Test fun `saved time is baseline minus use, never negative`() {
        val used = mapOf(today to 20 * 60L, today.minusDays(1) to 200 * 60L)
        // started yesterday: today saves 100, yesterday saves 0
        assertEquals(100, TimeSaved.weekMinutes(120, used, today, startDay = today.minusDays(1)))
    }

    @Test fun `counts at most seven days`() {
        assertEquals(7 * 60, TimeSaved.weekMinutes(60, emptyMap(), today, startDay = today.minusDays(30)))
    }
}
