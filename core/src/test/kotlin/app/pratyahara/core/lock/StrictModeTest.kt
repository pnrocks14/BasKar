package app.pratyahara.core.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictModeTest {
    private val hour = 60 * 60 * 1000L
    private val now = 1_000_000_000L

    @Test fun `active until the end time`() {
        assertTrue(StrictMode.isActive(now + 1, now))
        assertFalse(StrictMode.isActive(now, now))
    }

    @Test fun `starting again only extends`() {
        val threeDays = StrictMode.extend(0, now, 3)
        assertEquals(now + 72 * hour, threeDays)
        assertEquals(threeDays, StrictMode.extend(threeDays, now, 1))
        assertEquals(now + 168 * hour, StrictMode.extend(threeDays, now, 7))
    }

    @Test fun `remaining reads naturally`() {
        assertEquals("3 days", StrictMode.remaining(now + 72 * hour, now))
        assertEquals("5 hours", StrictMode.remaining(now + 5 * hour, now))
        assertEquals("an hour", StrictMode.remaining(now + 30 * 60 * 1000L, now))
        assertEquals("under an hour", StrictMode.remaining(now, now))
    }
}
