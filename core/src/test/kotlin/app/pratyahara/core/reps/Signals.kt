package app.pratyahara.core.reps

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Synthetic accelerometer traces for tests. Gravity points along a tilted axis, like a phone held at the chest. */
object Signals {
    const val G = 9.81
    const val RATE_HZ = 50

    data class Sample(val tNanos: Long, val x: Double, val y: Double, val z: Double)

    /** Phone held upright-ish: gravity mostly along y, a little along z. */
    private val up = doubleArrayOf(0.0, cos(0.4), sin(0.4))

    fun build(seconds: Double, verticalAccel: (Double) -> Double, noise: Double = 0.15, seed: Int = 1, extra: (Double) -> DoubleArray = { doubleArrayOf(0.0, 0.0, 0.0) }): List<Sample> {
        val rnd = Random(seed)
        val n = (seconds * RATE_HZ).toInt()
        return (0 until n).map { i ->
            val t = i.toDouble() / RATE_HZ
            val a = G + verticalAccel(t)
            val e = extra(t)
            Sample(
                (t * 1e9).toLong(),
                up[0] * a + e[0] + rnd.nextDouble(-noise, noise),
                up[1] * a + e[1] + rnd.nextDouble(-noise, noise),
                up[2] * a + e[2] + rnd.nextDouble(-noise, noise),
            )
        }
    }

    /**
     * A squat as vertical acceleration: dip as the body starts down, rise while braking and pushing up,
     * dip again when stopping at the top. Modelled as -A·sin(2πt/period) shifted so each rep is one full cycle.
     */
    fun squats(count: Int, periodSeconds: Double, amplitude: Double = 3.0, restSeconds: Double = 2.0): List<Sample> {
        val total = restSeconds + count * periodSeconds + restSeconds
        return build(total, { t ->
            val local = t - restSeconds
            if (local < 0 || local > count * periodSeconds) 0.0
            else -amplitude * sin(2 * PI * local / periodSeconds)
        })
    }

    fun RepCounter.feed(samples: List<Sample>): RepCounter {
        samples.forEach { onSample(it.tNanos, it.x, it.y, it.z) }
        return this
    }
}
