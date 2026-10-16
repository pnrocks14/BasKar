package app.pratyahara.core.reels

import org.junit.Assert.assertEquals
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
        assertEquals(0, c.onScroll(4, 0, 0, 1_000)) // first report only sets the baseline
        assertEquals(0, c.onScroll(4, 0, 0, 1_100))
        assertEquals(1, c.onScroll(5, 0, 0, 2_000))
        assertEquals(1, c.onScroll(6, 0, 0, 3_000))
        assertEquals(3, c.count)
    }

    @Test fun `a fast flick through several reels counts each one`() {
        val c = ReelCounter()
        c.onEnter(0)
        c.onScroll(10, 0, 0, 1_000)
        assertEquals(1, c.onScroll(11, 0, 0, 1_150))
        assertEquals(1, c.onScroll(12, 0, 0, 1_300))
        assertEquals(2, c.onScroll(14, 0, 0, 1_420)) // skipped an event in between
        assertEquals(5, c.count)
    }

    @Test fun `going back to rewatch adds nothing`() {
        val c = ReelCounter()
        c.onEnter(0)
        c.onScroll(0, 0, 0, 1_000)
        c.onScroll(1, 0, 0, 2_000)
        c.onScroll(2, 0, 0, 3_000)
        assertEquals(0, c.onScroll(1, 0, 0, 4_000))
        assertEquals(0, c.onScroll(2, 0, 0, 5_000))
        assertEquals(1, c.onScroll(3, 0, 0, 6_000))
        assertEquals(4, c.count)
    }

    @Test fun `scroll distance counts pages when there's no index`() {
        val c = ReelCounter()
        c.onEnter(0)
        val page = 2_000
        // One quick fling travels three pages in a burst of events.
        var added = 0
        for (i in 1..12) added += c.onScroll(-1, page / 4, page, 1_000L + i * 16)
        assertEquals(3, added)
        // A swipe that springs back adds nothing.
        assertEquals(0, c.onScroll(-1, page / 2, page, 3_000))
        assertEquals(0, c.onScroll(-1, -page / 2, page, 3_200))
        assertEquals(4, c.count)
    }

    @Test fun `a stuck index doesn't hide the scroll distance`() {
        val c = ReelCounter()
        c.onEnter(0)
        val page = 1_000
        for (i in 1..4) c.onScroll(7, page, page, 1_000L + i * 400)
        assertEquals(5, c.count)
    }

    @Test fun `without any signal each swipe gesture counts once`() {
        val c = ReelCounter()
        c.onEnter(0)
        assertEquals(1, c.onScroll(-1, 0, 0, 2_000))
        assertEquals(0, c.onScroll(-1, 0, 0, 2_050))
        assertEquals(0, c.onScroll(-1, 0, 0, 2_120))
        assertEquals(1, c.onScroll(-1, 0, 0, 2_600))
        assertEquals(3, c.count)
    }

    @Test fun `coming back to the same visit adds nothing`() {
        val c = ReelCounter()
        assertEquals(1, c.onEnter(0))
        c.onScroll(3, 0, 0, 1_000)
        c.onScroll(4, 0, 0, 2_000)
        c.onLeave(3_000)
        assertEquals(0, c.onEnter(20_000))
        assertEquals(0, c.onScroll(3, 0, 0, 21_000)) // back on an older reel
        assertEquals(1, c.onScroll(5, 0, 0, 22_000))
        assertEquals(3, c.count)
        c.onLeave(30_000)
        assertEquals(1, c.onEnter(30_000 + 10 * 60_000)) // a real new visit
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
