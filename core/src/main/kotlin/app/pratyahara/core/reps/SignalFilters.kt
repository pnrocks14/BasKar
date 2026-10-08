package app.pratyahara.core.reps

import kotlin.math.exp

/** First-order low-pass filter defined by a time constant, so it behaves the same at any sample rate. */
class LowPass(private val tauSeconds: Double) {
    private var value = 0.0
    private var initialized = false

    fun reset() {
        initialized = false
    }

    fun update(sample: Double, dtSeconds: Double): Double {
        if (!initialized) {
            value = sample
            initialized = true
            return value
        }
        val alpha = 1.0 - exp(-dtSeconds / tauSeconds)
        value += alpha * (sample - value)
        return value
    }
}
