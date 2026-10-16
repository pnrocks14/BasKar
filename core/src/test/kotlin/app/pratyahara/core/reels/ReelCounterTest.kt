package app.pratyahara.core.reels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelCounterTest {
    @Test fun `opening counts the first reel`() {
        val c = ReelCounter()
        c.onEnter(0)
        assertEquals(1, c.count)
    }

    @Test fun `a changed pager index is a new reel`() {
        val c = ReelCounter()
        c.onEnter(0)
        assertFalse(c.onScroll(4, 1_000)) // first report only sets the baseline
        assertFalse(c.onScroll(4, 1_100)) // same reel, still settling
        assertTrue(c.onScroll(5, 2_000))
        assertTrue(c.onScroll(6, 3_000))
        assertTrue(c.onScroll(5, 4_000)) // going back still means watching one more
        assertEquals(4, c.count)
    }

    @Test fun `index flicker inside one swipe counts once`() {
        val c = ReelCounter()
        c.onEnter(0)
        c.onScroll(1, 1_000)
        assertTrue(c.onScroll(2, 2_000))
        assertFalse(c.onScroll(1, 2_100))
        assertEquals(2, c.count)
    }

    @Test fun `without an index each swipe gesture counts once`() {
        val c = ReelCounter()
        c.onEnter(0)
        assertTrue(c.onScroll(-1, 2_000))
        assertFalse(c.onScroll(-1, 2_050))
        assertFalse(c.onScroll(-1, 2_120))
        assertTrue(c.onScroll(-1, 4_000))
        assertEquals(3, c.count)
    }

    @Test fun `brain states and health`() {
        assertEquals(BrainState.FRESH, BrainState.of(0))
        assertEquals(BrainState.FOGGY, BrainState.of(10))
        assertEquals(BrainState.MELTING, BrainState.of(99))
        assertEquals(BrainState.ROTTEN, BrainState.of(400))
        assertEquals(100, BrainState.health(0))
        assertEquals(60, BrainState.health(50))
        assertEquals(1, BrainState.health(1_000))
    }
}
