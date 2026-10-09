package app.pratyahara.core.reps

import app.pratyahara.core.reps.Signals.feed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class RepCounterTest {

    @Test fun `counts twenty steady squats`() {
        val c = RepCounter().feed(Signals.squats(count = 20, periodSeconds = 2.0))
        assertEquals(20, c.reps)
    }

    @Test fun `counts slow and fast but human squats`() {
        assertEquals(10, RepCounter().feed(Signals.squats(10, periodSeconds = 3.5, amplitude = 2.2)).reps)
        assertEquals(15, RepCounter().feed(Signals.squats(15, periodSeconds = 1.3, amplitude = 4.0)).reps)
    }

    @Test fun `counts squats whose pace varies`() {
        val r = Random(7)
        val periods = List(12) { 1.6 + r.nextDouble() * 1.4 }
        val starts = periods.runningFold(2.0) { acc, p -> acc + p }
        val samples = Signals.build(starts.last() + 2.0, { t ->
            val i = starts.indexOfLast { it <= t }
            if (i < 0 || i >= periods.size) 0.0 else -3.0 * sin(2 * PI * (t - starts[i]) / periods[i])
        })
        assertEquals(12, RepCounter().feed(samples).reps)
    }

    /** A slow 3 s squat with the phone at the chest only peaks around 0.7 m/s². Reported missed on a real phone. */
    @Test fun `counts slow gentle squats held at the chest`() {
        assertEquals(10, RepCounter().feed(Signals.squats(10, periodSeconds = 3.0, amplitude = 0.75)).reps)
    }

    @Test fun `ignores small sways`() {
        assertEquals(0, RepCounter().feed(Signals.squats(10, periodSeconds = 2.0, amplitude = 0.3)).reps)
    }

    @Test fun `ignores a phone resting still`() {
        val c = RepCounter().feed(Signals.build(30.0, { 0.0 }, noise = 0.3))
        assertEquals(0, c.reps)
    }

    @Test fun `ignores fast shaking`() {
        for (hz in listOf(3.0, 5.0, 8.0)) {
            val c = RepCounter().feed(Signals.build(20.0, { t -> 12.0 * sin(2 * PI * hz * t) }))
            assertEquals("shaking at $hz Hz", 0, c.reps)
        }
    }

    @Test fun `ignores sideways shaking`() {
        val c = RepCounter().feed(Signals.build(20.0, { 0.0 }, extra = { t -> doubleArrayOf(15.0 * sin(2 * PI * 4.0 * t), 0.0, 0.0) }))
        assertEquals(0, c.reps)
    }

    @Test fun `ignores squat-speed swings mixed with shaking`() {
        val c = RepCounter().feed(Signals.build(20.0, { t -> -3.0 * sin(2 * PI * t / 2.0) + 10.0 * sin(2 * PI * 6.0 * t) }))
        assertEquals(0, c.reps)
        assertTrue(c.shaking || c.rejectedCycles > 0)
    }

    @Test fun `ignores cycles faster than a person can squat`() {
        val c = RepCounter().feed(Signals.squats(count = 30, periodSeconds = 0.6, amplitude = 4.0))
        assertTrue("counted ${c.reps}", c.reps <= 2)
    }

    @Test fun `reset starts again from zero`() {
        val c = RepCounter().feed(Signals.squats(5, 2.0))
        c.reset()
        assertEquals(0, c.reps)
        assertEquals(5, c.feed(Signals.squats(5, 2.0)).reps)
    }
}
