package app.pratyahara.core.reps

import kotlin.math.abs
import kotlin.math.sqrt

data class RepConfig(
    /** Vertical acceleration (m/s²) the smoothed signal must swing past, both ways, for a rep. */
    val threshold: Double = 1.2,
    /** Reps faster than this are not squats. */
    val minRepIntervalMs: Long = 900,
    /** Reps slower than this still count, but a gap this long breaks the rhythm check. */
    val maxRepIntervalMs: Long = 8_000,
    /** High-frequency energy (RMS, m/s²) above which the phone is being shaken. */
    val shakeRms: Double = 3.0,
    /** After shaking stops, wait this long before counting again. */
    val shakeCooldownMs: Long = 1_500,
    /** A smoothed swing above this is a jolt or throw, not a squat. */
    val maxAmplitude: Double = 20.0,
)

/**
 * Counts squats from raw accelerometer samples while the phone is held.
 *
 * 1. Gravity direction is estimated with a slow low-pass filter (squat motion averages out).
 * 2. Each sample is projected onto that direction, giving vertical acceleration.
 * 3. A two-stage low-pass removes hand jitter. One squat makes the signal dip (start of descent),
 *    rise (braking at the bottom and pushing up), then dip again (stopping at the top).
 *    A rep is counted on each dip-then-rise cycle.
 * 4. Shaking shows up as high-frequency energy; while it is present nothing is counted.
 *    Cycles faster than a person can squat are also rejected.
 *
 * Pure Kotlin: feed it samples from SensorManager on the phone, or from CSV traces in tests.
 */
class RepCounter(private val config: RepConfig = RepConfig()) {

    var reps: Int = 0
        private set

    /** True while the signal looks like shaking rather than squatting. */
    var shaking: Boolean = false
        private set

    /** Cycles thrown out as too fast, too violent or during shaking. */
    var rejectedCycles: Int = 0
        private set

    private val gx = LowPass(GRAVITY_TAU)
    private val gy = LowPass(GRAVITY_TAU)
    private val gz = LowPass(GRAVITY_TAU)
    private val smooth1 = LowPass(SMOOTH_TAU)
    private val smooth2 = LowPass(SMOOTH_TAU)
    private val hfEnergy = LowPass(HF_TAU)

    private var lastTimeNanos = -1L
    private var armed = false
    private var swingPeak = 0.0
    private var lastRepMs = -1L
    private var shakeUntilMs = -1L

    fun reset() {
        reps = 0
        shaking = false
        rejectedCycles = 0
        listOf(gx, gy, gz, smooth1, smooth2, hfEnergy).forEach { it.reset() }
        lastTimeNanos = -1L
        armed = false
        swingPeak = 0.0
        lastRepMs = -1L
        shakeUntilMs = -1L
    }

    /**
     * Feeds one accelerometer sample (including gravity, m/s²). Returns true if this sample completed a rep.
     */
    fun onSample(timestampNanos: Long, ax: Double, ay: Double, az: Double): Boolean {
        val dt = if (lastTimeNanos < 0) 0.0 else (timestampNanos - lastTimeNanos) / 1e9
        lastTimeNanos = timestampNanos
        if (dt < 0 || dt > 0.5) {
            // Clock jump or a long gap: restart the filters rather than trust them.
            listOf(smooth1, smooth2, hfEnergy).forEach { it.reset() }
            armed = false
        }
        val step = if (dt <= 0 || dt > 0.5) 0.02 else dt
        val nowMs = timestampNanos / 1_000_000

        val gxv = gx.update(ax, step)
        val gyv = gy.update(ay, step)
        val gzv = gz.update(az, step)
        val gMag = sqrt(gxv * gxv + gyv * gyv + gzv * gzv)
        if (gMag < 1e-3) return false

        val vertical = (ax * gxv + ay * gyv + az * gzv) / gMag - gMag
        val s = smooth2.update(smooth1.update(vertical, step), step)

        val hf = vertical - s
        val rms = sqrt(hfEnergy.update(hf * hf, step))
        if (rms > config.shakeRms) {
            shakeUntilMs = nowMs + config.shakeCooldownMs
        }
        shaking = nowMs < shakeUntilMs

        swingPeak = maxOf(swingPeak, abs(s))

        if (!armed) {
            if (s < -config.threshold) {
                armed = true
            }
            return false
        }
        if (s > config.threshold) {
            armed = false
            val peak = swingPeak
            swingPeak = 0.0
            return considerRep(nowMs, peak)
        }
        return false
    }

    private fun considerRep(nowMs: Long, peak: Double): Boolean {
        val tooSoon = lastRepMs >= 0 && nowMs - lastRepMs < config.minRepIntervalMs
        if (shaking || tooSoon || peak > config.maxAmplitude) {
            rejectedCycles++
            if (tooSoon) lastRepMs = nowMs
            return false
        }
        lastRepMs = nowMs
        reps++
        return true
    }

    private companion object {
        const val GRAVITY_TAU = 3.0
        const val SMOOTH_TAU = 0.15
        const val HF_TAU = 0.5
    }
}
