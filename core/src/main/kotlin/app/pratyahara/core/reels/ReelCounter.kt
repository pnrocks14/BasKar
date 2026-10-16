package app.pratyahara.core.reels

/**
 * Counts reels swiped past while the Reels/Shorts screen is open. Pagers usually report which item is
 * on screen (an accessibility event's fromIndex), so a changed index is a new reel. Without an index,
 * each new swipe gesture counts once: scroll events in one burst belong to one swipe.
 */
class ReelCounter(
    private val minGapMs: Long = 700,
    private val gestureGapMs: Long = 450,
) {
    var count = 0
        private set
    private var lastIndex: Int? = null
    private var lastCountAt = 0L
    private var lastScrollAt = 0L

    /** The section opened: the reel on screen is the first one. */
    fun onEnter(nowMs: Long) {
        count = 1
        lastIndex = null
        lastCountAt = nowMs
        lastScrollAt = 0L
    }

    /** Returns true when this scroll brought a new reel on screen. */
    fun onScroll(fromIndex: Int, nowMs: Long): Boolean {
        val newGesture = nowMs - lastScrollAt >= gestureGapMs
        lastScrollAt = nowMs
        val counted = if (fromIndex >= 0) {
            val previous = lastIndex
            lastIndex = fromIndex
            previous != null && previous != fromIndex && nowMs - lastCountAt >= minGapMs
        } else {
            newGesture && nowMs - lastCountAt >= minGapMs
        }
        if (counted) {
            count++
            lastCountAt = nowMs
        }
        return counted
    }
}

/** How fried the brain looks after [reelsToday] reels. The meter is a joke with a point. */
enum class BrainState(val fromReels: Int, val label: String) {
    FRESH(0, "Fresh"),
    FOGGY(10, "Foggy"),
    MUSHY(25, "Mushy"),
    MELTING(50, "Melting"),
    ROTTEN(100, "Brain rot");

    companion object {
        fun of(reelsToday: Int): BrainState = entries.last { reelsToday >= it.fromReels }

        /** 100 is a fresh brain; each reel costs a little, and it never quite hits zero. */
        fun health(reelsToday: Int): Int = (100 - reelsToday * 0.8).toInt().coerceIn(1, 100)
    }
}
