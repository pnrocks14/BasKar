package app.pratyahara.core.reps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class TiltRepCounterTest {

    /**
     * A phone in a front pocket: standing, gravity runs along the phone's long axis (tilted a little by
     * [mountDegrees], however it sits in the pocket). [thighDegrees] gives the thigh's tilt over time.
     */
    private fun pocket(seconds: Double, mountDegrees: Double = 15.0, noise: Double = 0.3, seed: Int = 3, extra: (Double) -> Double = { 0.0 }, thighDegrees: (Double) -> Double): List<Signals.Sample> {
        val rnd = Random(seed)
        val mount = Math.toRadians(mountDegrees)
        return (0 until (seconds * 50).toInt()).map { i ->
            val t = i / 50.0
            val a = Math.toRadians(thighDegrees(t))
            val g = Signals.G + extra(t)
            // Thigh tilt rotates gravity from the phone's y axis towards its z axis; the mount angle moves part of it into x.
            val y = g * cos(a)
            val z = g * sin(a)
            Signals.Sample(
                (t * 1e9).toLong(),
                y * sin(mount) + rnd.nextDouble(-noise, noise),
                y * cos(mount) + rnd.nextDouble(-noise, noise),
                z + rnd.nextDouble(-noise, noise),
            )
        }
    }

    private fun squats(count: Int, period: Double, depthDegrees: Double = 85.0, rest: Double = 2.0): (Double) -> Double = { t ->
        val local = t - rest
        if (local < 0 || local > count * period) 0.0 else depthDegrees * (1 - cos(2 * PI * local / period)) / 2
    }

    private fun TiltRepCounter.feed(samples: List<Signals.Sample>) = apply { samples.forEach { onSample(it.tNanos, it.x, it.y, it.z) } }

    @Test fun `counts twenty pocket squats`() {
        assertEquals(20, TiltRepCounter().feed(pocket(2.0 + 20 * 2.0 + 2.0, thighDegrees = squats(20, 2.0))).reps)
    }

    @Test fun `counts half squats, slow squats and quick ones`() {
        assertEquals(10, TiltRepCounter().feed(pocket(2.0 + 10 * 2.0 + 2.0, thighDegrees = squats(10, 2.0, depthDegrees = 60.0))).reps)
        assertEquals(8, TiltRepCounter().feed(pocket(2.0 + 8 * 4.0 + 2.0, thighDegrees = squats(8, 4.0))).reps)
        assertEquals(12, TiltRepCounter().feed(pocket(2.0 + 12 * 1.2 + 2.0, thighDegrees = squats(12, 1.2))).reps)
    }

    @Test fun `works however the phone sits in the pocket`() {
        for (mount in listOf(0.0, 40.0, 80.0, 170.0)) {
            val c = TiltRepCounter().feed(pocket(2.0 + 10 * 2.0 + 2.0, mountDegrees = mount, thighDegrees = squats(10, 2.0)))
            assertEquals("mounted at $mount°", 10, c.reps)
        }
    }

    @Test fun `walking does not count`() {
        val c = TiltRepCounter().feed(pocket(30.0, extra = { t -> 3.0 * sin(2 * PI * t / 0.55) }) { t -> 30.0 * sin(2 * PI * t / 1.1) })
        assertEquals(0, c.reps)
    }

    @Test fun `standing still does not count`() {
        assertEquals(0, TiltRepCounter().feed(pocket(30.0, noise = 0.6) { 0.0 }).reps)
    }

    @Test fun `shaking the phone does not count`() {
        val c = TiltRepCounter().feed(pocket(20.0, extra = { t -> 14.0 * sin(2 * PI * 6.0 * t) }) { t -> 45.0 * (1 - cos(2 * PI * t / 0.4)) })
        assertTrue("counted ${c.reps}", c.reps == 0)
    }

    @Test fun `depth follows the squat`() {
        val c = TiltRepCounter()
        val samples = pocket(4.0) { t -> if (t < 2.0) 0.0 else 60.0 }
        c.feed(samples)
        assertEquals(1.0, c.depth, 0.01)
    }
}
