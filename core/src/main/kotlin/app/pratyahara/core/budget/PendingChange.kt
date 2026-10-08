package app.pratyahara.core.budget

import app.pratyahara.core.time.Stamp
import java.time.Duration

enum class ChangeType {
    /** value = new budget in minutes */
    RAISE_BUDGET,
    /** value = package name */
    REMOVE_APP,
    /** value unused */
    DISABLE_BLOCKING,
    /** value = new squat count */
    LOWER_SQUATS,
}

/**
 * A change that weakens protection and therefore waits before it applies.
 *
 * It is due once the wall clock has passed [effectiveAtMillis] AND, if the phone has not
 * rebooted since the request, the monotonic clock has advanced by the full delay too.
 * That stops someone from skipping the wait by moving the system clock forward.
 */
data class PendingChange(
    val id: String,
    val type: ChangeType,
    val value: String,
    val requested: Stamp,
    val delayMillis: Long,
) {
    val effectiveAtMillis: Long get() = requested.wallMillis + delayMillis

    fun isDue(now: Stamp): Boolean {
        if (now.wallMillis < effectiveAtMillis) return false
        if (now.bootCount != requested.bootCount) return true
        return now.elapsedMillis - requested.elapsedMillis >= delayMillis
    }

    fun remainingMillis(now: Stamp): Long {
        val byWall = effectiveAtMillis - now.wallMillis
        val byMonotonic = if (now.bootCount == requested.bootCount) {
            delayMillis - (now.elapsedMillis - requested.elapsedMillis)
        } else 0L
        return maxOf(byWall, byMonotonic, 0L)
    }

    companion object {
        fun create(id: String, type: ChangeType, value: String, now: Stamp, delay: Duration) =
            PendingChange(id, type, value, now, delay.toMillis())
    }
}

/**
 * Adds a pending change to [existing]. Only one change of each type and target may wait at a time:
 * a new request replaces the old one and restarts its clock.
 */
fun List<PendingChange>.withRequest(change: PendingChange): List<PendingChange> {
    val sameSlot: (PendingChange) -> Boolean = when (change.type) {
        ChangeType.REMOVE_APP -> { c -> c.type == change.type && c.value == change.value }
        else -> { c -> c.type == change.type }
    }
    return filterNot(sameSlot) + change
}
