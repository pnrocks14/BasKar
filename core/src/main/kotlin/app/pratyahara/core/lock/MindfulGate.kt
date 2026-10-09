package app.pratyahara.core.lock

/**
 * The breathing pause in front of Reels/Shorts. It asks once per visit, before the first video, and again
 * after [checkEveryMinutes] of scrolling without a break. Pure logic, driven by the accessibility service.
 */
class MindfulGate(
    private val pauseSeconds: Int = 5,
    private val checkEveryMinutes: Int = 10,
    /** Coming back within this long counts as the same visit: no second pause. */
    private val sameVisitMs: Long = 60_000,
) {
    private var lastLeftMs = -1L
    private var openedAt = -1L

    /** Reels/Shorts just became visible. True when the pause should show before it plays. */
    fun onEnter(nowMs: Long): Boolean {
        val quickReturn = lastLeftMs >= 0 && nowMs - lastLeftMs < sameVisitMs
        if (quickReturn && openedAt >= 0) return false
        openedAt = -1
        return pauseSeconds > 0
    }

    /** The user chose to keep going after the pause, or no pause was needed. */
    fun onOpened(nowMs: Long) {
        openedAt = nowMs
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
