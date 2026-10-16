package app.pratyahara.core.lock

import kotlin.random.Random

/**
 * The breathing pause in front of Reels/Shorts. It always asks on the first visit of the day, then only
 * now and then: each later visit has a [chance] of a pause, never sooner than [minGapMs] after the last one,
 * so it stays a surprise instead of a chore. It also checks in after [checkEveryMinutes] of scrolling
 * without a break. Pure logic, driven by the accessibility service.
 */
class MindfulGate(
    private val pauseSeconds: Int = 5,
    private val checkEveryMinutes: Int = 10,
    /** Coming back within this long counts as the same visit: no second pause. */
    private val sameVisitMs: Long = 60_000,
    private val chance: Double = 0.25,
    private val minGapMs: Long = 45 * 60_000L,
    private val random: Random = Random.Default,
    /** Carried over when the gate is rebuilt, so a settings change doesn't bring the pause back. */
    var lastPauseDay: String? = null,
    var lastPauseAt: Long = -1L,
) {
    private var lastLeftMs = -1L
    private var openedAt = -1L
    /** A pause showed but wasn't passed (they left during it): ask again next time. */
    private var pending = false

    /** Reels/Shorts just became visible on [day]. True when the pause should show before it plays. */
    fun onEnter(nowMs: Long, day: String): Boolean {
        val quickReturn = lastLeftMs >= 0 && nowMs - lastLeftMs < sameVisitMs
        if (quickReturn && openedAt >= 0) return false
        openedAt = -1
        if (pauseSeconds <= 0) return false
        val due = when {
            pending -> true
            day != lastPauseDay -> true
            lastPauseAt >= 0 && nowMs - lastPauseAt < minGapMs -> false
            else -> random.nextDouble() < chance
        }
        if (due) {
            pending = true
            lastPauseDay = day
            lastPauseAt = nowMs
        }
        return due
    }


    /** The user chose to keep going after the pause, or no pause was needed. */
    fun onOpened(nowMs: Long) {
        openedAt = nowMs
        pending = false
    }

    fun onLeave(nowMs: Long) {
        lastLeftMs = nowMs
    }

    /** Reels/Shorts is open and the pause, if any, was passed. */
    val isOpen: Boolean get() = openedAt >= 0

    /** True when it's time to check in again after a long stretch of scrolling. */
    fun isCheckDue(nowMs: Long): Boolean =
        checkEveryMinutes > 0 && openedAt >= 0 && nowMs - openedAt >= checkEveryMinutes * 60_000L

    /** Minutes of scrolling since the pause was last passed, for the check-in's wording. */
    fun minutesSinceOpened(nowMs: Long): Long = if (openedAt < 0) 0 else (nowMs - openedAt) / 60_000
}
