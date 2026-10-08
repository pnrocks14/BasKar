package app.pratyahara.core.budget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class DelayTableTest {
    @Test fun `delay grows with the size of the increase`() {
        assertEquals(Duration.ZERO, DelayTable.increaseDelay(0))
        assertEquals(Duration.ofHours(4), DelayTable.increaseDelay(1))
        assertEquals(Duration.ofHours(4), DelayTable.increaseDelay(10))
        assertEquals(Duration.ofHours(12), DelayTable.increaseDelay(11))
        assertEquals(Duration.ofHours(12), DelayTable.increaseDelay(20))
        assertEquals(Duration.ofHours(24), DelayTable.increaseDelay(30))
        assertEquals(Duration.ofDays(2), DelayTable.increaseDelay(31))
        assertEquals(Duration.ofDays(2), DelayTable.increaseDelay(60))
        assertEquals(Duration.ofDays(3), DelayTable.increaseDelay(61))
        assertEquals(Duration.ofDays(3), DelayTable.increaseDelay(115))
    }
}
