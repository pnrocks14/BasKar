package app.pratyahara.core.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MindfulGateTest {
    private val hour = 3_600_000L

    /** A visit: enter, watch, leave a few minutes later. */
    private fun MindfulGate.visit(at: Long, day: String = "2026-10-16"): Boolean {
        val paused = onEnter(at, day)
        onOpened(at)
        onLeave(at + 5 * 60_000)
        return paused
    }

    @Test fun `the first visit of the day always pauses`() {
        val gate = MindfulGate(random = Random(1))
        assertTrue(gate.visit(9 * hour))
        assertTrue(gate.visit(9 * hour + 24 * hour, day = "2026-10-17"))
    }

    @Test fun `a never-lucky later visit doesn't pause`() {
        val gate = MindfulGate(chance = 0.0)
        assertTrue(gate.visit(9 * hour))
        for (i in 1..10) assertFalse(gate.visit(9 * hour + i * hour))
    }

    @Test fun `no random pause soon after the last one`() {
        val gate = MindfulGate(chance = 1.0)
        assertTrue(gate.visit(9 * hour))
        assertFalse(gate.visit(9 * hour + 20 * 60_000))
        assertTrue(gate.visit(9 * hour + 50 * 60_000))
    }

    @Test fun `later pauses are rare and random`() {
        val gate = MindfulGate(random = Random(42))
        gate.visit(0)
        // A visit every hour for a long stretch: roughly a quarter of them pause.
        val paused = (1..400).count { gate.visit(it * hour) }
        assertTrue("paused $paused times", paused in 60..140)
    }

    @Test fun `a rebuilt gate keeps the day's pause`() {
        val first = MindfulGate(chance = 0.0)
        assertTrue(first.visit(9 * hour))
        val rebuilt = MindfulGate(chance = 0.0, lastPauseDay = first.lastPauseDay, lastPauseAt = first.lastPauseAt)
        assertFalse(rebuilt.visit(11 * hour))
    }

    @Test fun `pause off means never`() {
        assertFalse(MindfulGate(pauseSeconds = 0).visit(0))
    }

    @Test fun `a quick return is the same visit`() {
        val gate = MindfulGate(chance = 1.0, minGapMs = 0)
        assertTrue(gate.onEnter(0, "d"))
        gate.onOpened(0)
        gate.onLeave(10_000)
        assertFalse(gate.onEnter(20_000, "d"))
        assertEquals(true, gate.isOpen)
    }

    @Test fun `checks in after a long stretch`() {
        val g = MindfulGate(pauseSeconds = 5, checkEveryMinutes = 10)
        assertTrue(g.onEnter(0, "d"))
        g.onOpened(5_000)
        assertFalse(g.isCheckDue(5_000 + 9 * 60_000))
        assertTrue(g.isCheckDue(5_000 + 10 * 60_000))
        g.onOpened(700_000)
        assertFalse(g.isCheckDue(700_001))
    }

    @Test fun `leaving during the pause asks again on return`() {
        val g = MindfulGate(chance = 0.0)
        assertTrue(g.onEnter(0, "d"))
        g.onLeave(2_000)
        assertTrue(g.onEnter(10_000, "d"))
        g.onOpened(15_000)
        g.onLeave(100_000)
        assertFalse(g.onEnter(200_000, "d"))
    }
}
