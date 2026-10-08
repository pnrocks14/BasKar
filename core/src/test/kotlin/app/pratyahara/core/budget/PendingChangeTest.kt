package app.pratyahara.core.budget

import app.pratyahara.core.time.Stamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class PendingChangeTest {
    private val hour = 3_600_000L
    private val start = Stamp(wallMillis = 1_000_000_000L, elapsedMillis = 50_000L, bootCount = 3)
    private val change = PendingChange.create("a", ChangeType.DISABLE_BLOCKING, "", start, Duration.ofHours(24))

    @Test fun `not due before the delay`() {
        val later = start.copy(wallMillis = start.wallMillis + 23 * hour, elapsedMillis = start.elapsedMillis + 23 * hour)
        assertFalse(change.isDue(later))
        assertEquals(hour, change.remainingMillis(later))
    }

    @Test fun `due once both clocks pass the delay`() {
        val later = start.copy(wallMillis = start.wallMillis + 24 * hour, elapsedMillis = start.elapsedMillis + 24 * hour)
        assertTrue(change.isDue(later))
    }

    @Test fun `moving the wall clock forward does not skip the wait`() {
        val cheat = start.copy(wallMillis = start.wallMillis + 48 * hour, elapsedMillis = start.elapsedMillis + 2 * hour)
        assertFalse(change.isDue(cheat))
        assertEquals(22 * hour, change.remainingMillis(cheat))
    }

    @Test fun `after a reboot the wall clock decides`() {
        val rebooted = Stamp(wallMillis = start.wallMillis + 24 * hour, elapsedMillis = 10_000L, bootCount = 4)
        assertTrue(change.isDue(rebooted))
    }

    @Test fun `a new request replaces the waiting one of the same kind`() {
        val first = PendingChange.create("1", ChangeType.RAISE_BUDGET, "40", start, Duration.ofHours(4))
        val other = PendingChange.create("2", ChangeType.REMOVE_APP, "com.instagram.android", start, Duration.ofHours(24))
        val second = PendingChange.create("3", ChangeType.RAISE_BUDGET, "60", start, Duration.ofDays(2))
        val list = listOf(first, other).withRequest(second)
        assertEquals(listOf("2", "3"), list.map { it.id })
    }

    @Test fun `removing two different apps can wait side by side`() {
        val ig = PendingChange.create("1", ChangeType.REMOVE_APP, "com.instagram.android", start, Duration.ofHours(24))
        val yt = PendingChange.create("2", ChangeType.REMOVE_APP, "com.google.android.youtube", start, Duration.ofHours(24))
        assertEquals(2, listOf(ig).withRequest(yt).size)
    }
}
