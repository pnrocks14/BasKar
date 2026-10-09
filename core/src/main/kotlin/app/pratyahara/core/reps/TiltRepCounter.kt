package app.pratyahara.core.reps

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.exp
import kotlin.math.sqrt

data class TiltConfig(
    /** The thigh has to tilt this far from standing (degrees) for the bottom of a squat. Parallel is about 80°, a half squat 50-60°. */
    val downDegrees: Double = 45.0,
    /** ...and come back within this many degrees of standing to finish the rep. */
    val upDegrees: Double = 25.0,
    /** Reps faster than this are not squats. */
    val minRepIntervalMs: Long = 800,
    /** The bottom has to last at least this long, so a stumble doesn't count. */
    val minDownMs: Long = 250,
    /** High-frequency energy (RMS, m/s²) above which the phone is being shaken. */
    val shakeRms: Double = 4.0,
    val shakeCooldownMs: Long = 1_500,
    /**
     * During a rep the body has to actually go down and up: the phone's acceleration (beyond gravity) must
     * swing at least this far (m/s²). Tilting a phone in your hand turns it without moving it, so it fails this.
     * A slow 4-second squat still swings about 0.5 m/s².
     */
    val minMotion: Double = 0.3,
)

/**
 * Counts squats with the phone in a front trouser pocket. The thigh goes from upright to nearly level in a
 * squat, so the phone turns through 50-90°. That turn is far easier to see than the small up-and-down
 * acceleration of a phone held at the chest, and it doesn't care how the phone sits in the pocket.
 *
 * 1. A fast low-pass on the raw samples gives the phone's orientation (the gravity direction).
 * 2. The first second, standing still, sets the "standing" orientation. It keeps adapting slowly
 *    while standing still, so a phone that shifts in the pocket doesn't drift into false reps.
 * 3. A rep is the tilt from standing going past [TiltConfig.downDegrees] and back under [TiltConfig.upDegrees].
 *    Walking swings the thigh about 30°, which never reaches the bottom.
 * 4. The body has to move too: a squat drops the hips, so the phone accelerates down and up while it
 *    turns. Turning the phone in your hand doesn't count.
 * 5. Shaking (lots of high-frequency energy) pauses counting, like the chest counter.
 */
class TiltRepCounter(private val config: TiltConfig = TiltConfig()) : SquatCounter {

    override var reps: Int = 0
        private set
    override var shaking: Boolean = false
        private set
    override var depth: Double = 0.0
        private set

    /** Tilt from standing, in degrees, of the latest sample. */
    var tiltDegrees: Double = 0.0
        private set

    private val ox = LowPass(ORIENTATION_TAU)
    private val oy = LowPass(ORIENTATION_TAU)
    private val oz = LowPass(ORIENTATION_TAU)
    /** Standing orientation, as a unit-ish vector. */
    private var bx = 0.0
    private var by = 0.0
    private var bz = 0.0
    private val magnitude = LowPass(ORIENTATION_TAU)
    private val hfEnergy = LowPass(HF_TAU)
    private val motion = LowPass(MOTION_TAU)
    private val gravityLevel = LowPass(GRAVITY_TAU)
    private var motionPeak = 0.0

    private var previousTilt = 0.0
    private var startNanos = -1L
    private var lastTimeNanos = -1L
    private var down = false
    private var downSinceMs = 0L
    private var lastRepMs = -1L
    private var shakeUntilMs = -1L

    override fun reset() {
        reps = 0
        shaking = false
        depth = 0.0
        tiltDegrees = 0.0
        previousTilt = 0.0
        listOf(ox, oy, oz, magnitude, hfEnergy, motion, gravityLevel).forEach { it.reset() }
        motionPeak = 0.0
        bx = 0.0; by = 0.0; bz = 0.0
        startNanos = -1L
        lastTimeNanos = -1L
        down = false
        lastRepMs = -1L
        shakeUntilMs = -1L
    }

    override fun onSample(timestampNanos: Long, ax: Double, ay: Double, az: Double): Boolean {
        if (startNanos < 0) startNanos = timestampNanos
        val dt = if (lastTimeNanos < 0) 0.02 else (timestampNanos - lastTimeNanos) / 1e9
        lastTimeNanos = timestampNanos
        val step = if (dt <= 0 || dt > 0.5) 0.02 else dt
        val nowMs = timestampNanos / 1_000_000

        val x = ox.update(ax, step)
        val y = oy.update(ay, step)
        val z = oz.update(az, step)
        val len = sqrt(x * x + y * y + z * z)
        if (len < 1e-3) return false

        // Shaking: the raw magnitude jumps around far faster than any squat.
        val raw = sqrt(ax * ax + ay * ay + az * az)
        val hf = raw - magnitude.update(raw, step)
        val rms = sqrt(hfEnergy.update(hf * hf, step))
        if (rms > config.shakeRms) shakeUntilMs = nowMs + config.shakeCooldownMs
        shaking = nowMs < shakeUntilMs

        // Acceleration beyond gravity, whatever way the phone faces: |a| minus its long-run level.
        val moving = abs(motion.update(raw, step) - gravityLevel.update(raw, step))

        val calibrating = (timestampNanos - startNanos) < CALIBRATION_NANOS
        // While calibrating (quickly), or standing still between reps (slowly), the baseline follows the current orientation.
        val steady = abs(tiltDegrees - previousTilt) / step < STEADY_DEGREES_PER_SECOND
        previousTilt = tiltDegrees
        if (calibrating || (!down && steady && tiltDegrees < config.upDegrees)) {
            val alpha = if (calibrating) 0.1 else 1.0 - exp(-step / BASELINE_TAU)
            bx += alpha * (x / len - bx)
            by += alpha * (y / len - by)
            bz += alpha * (z / len - bz)
        }
        val bLen = sqrt(bx * bx + by * by + bz * bz)
        if (bLen < 1e-3 || calibrating) {
            tiltDegrees = 0.0
            depth = 0.0
            return false
        }
        val cos = ((x * bx + y * by + z * bz) / (len * bLen)).coerceIn(-1.0, 1.0)
        tiltDegrees = Math.toDegrees(acos(cos))
        depth = (tiltDegrees / config.downDegrees).coerceIn(0.0, 1.0)

        if (!down && tiltDegrees < config.upDegrees) motionPeak = 0.0
        if (tiltDegrees >= config.upDegrees || down) motionPeak = maxOf(motionPeak, moving)

        if (!down) {
            if (tiltDegrees > config.downDegrees) {
                down = true
                downSinceMs = nowMs
            }
            return false
        }
        if (tiltDegrees < config.upDegrees) {
            down = false
            val longEnough = nowMs - downSinceMs >= config.minDownMs
            val tooSoon = lastRepMs >= 0 && nowMs - lastRepMs < config.minRepIntervalMs
            val moved = motionPeak >= config.minMotion
            motionPeak = 0.0
            if (shaking || !longEnough || tooSoon || !moved) return false
            lastRepMs = nowMs
            reps++
            return true
        }
        return false
    }

    private companion object {
        const val ORIENTATION_TAU = 0.2
        const val BASELINE_TAU = 8.0
        const val STEADY_DEGREES_PER_SECOND = 8.0
        const val HF_TAU = 0.5
        const val MOTION_TAU = 0.15
        const val GRAVITY_TAU = 3.0
        const val CALIBRATION_NANOS = 1_000_000_000L
    }
}
