package app.pratyahara.core.reps

/** Where the phone is during squats. Each placement has its own counter. */
enum class PhonePlacement { POCKET, CHEST }

/** Counts squats from raw accelerometer samples (including gravity, m/s²). */
interface SquatCounter {
    val reps: Int

    /** True while the motion looks like shaking rather than squatting; nothing counts then. */
    val shaking: Boolean

    /** 0 to 1: how far the current movement has gone towards a counted rep. Drives the live meter. */
    val depth: Double

    /** Returns true if this sample completed a rep. */
    fun onSample(timestampNanos: Long, ax: Double, ay: Double, az: Double): Boolean

    fun reset()

    companion object {
        fun forPlacement(placement: PhonePlacement): SquatCounter = when (placement) {
            PhonePlacement.POCKET -> TiltRepCounter()
            PhonePlacement.CHEST -> RepCounter()
        }
    }
}
