package app.pratyahara.core.time

/**
 * Two clocks, because a wall clock can be changed by the user.
 *
 * [nowMillis] is wall-clock time (epoch millis). [elapsedMillis] is monotonic time since boot,
 * which the user cannot change; [bootCount] tells us whether [elapsedMillis] values are comparable.
 */
interface Clock {
    fun nowMillis(): Long
    fun elapsedMillis(): Long
    fun bootCount(): Int
}

/** A moment captured from all three clock sources at once. */
data class Stamp(val wallMillis: Long, val elapsedMillis: Long, val bootCount: Int) {
    companion object {
        fun of(clock: Clock) = Stamp(clock.nowMillis(), clock.elapsedMillis(), clock.bootCount())
    }
}
