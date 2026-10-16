package app.pratyahara.core.reels

/**
 * Counts reels swiped past while the Reels/Shorts screen is open, including fast flicks through several.
 *
 * Two signals measure how far the player has moved forward, and the larger one wins:
 * - the pager's item index (an accessibility event's fromIndex), counted from where it was on opening;
 * - the scroll distance (scrollDeltaY), in pages of the player's height.
 * Each only counts new ground: going back to rewatch, or a swipe that springs back, adds nothing.
 * When an app reports neither, each new swipe gesture counts once.
 */
class ReelCounter(
    private val gestureGapMs: Long = 300,
    /** A page counts once it's this far on screen, before the swipe fully settles. */
    private val pageThreshold: Float = 0.7f,
    private val resumeMs: Long = 120_000,
) {
    var count = 0
        private set
    private var baseIndex: Int? = null
    private var maxIndex = 0
    private var scrolledPx = 0f
    private var maxPages = 0
    /** Reels counted from the index or distance signals since opening. */
    private var progress = 0
    private var sawSignal = false
    private var lastScrollAt = 0L

    private var leftAt = -1L

    /**
     * The section opened. A fresh visit counts the reel on screen; coming back within [resumeMs]
     * (from comments, a profile, or a brief blip in detection) is the same visit and adds nothing.
     * Returns how many reels this adds.
     */
    fun onEnter(nowMs: Long): Int {
        if (leftAt >= 0 && nowMs - leftAt < resumeMs) {
            leftAt = -1
            lastScrollAt = nowMs
            return 0
        }
        leftAt = -1
        count = 1
        baseIndex = null
        maxIndex = 0
        scrolledPx = 0f
        maxPages = 0
        progress = 0
        sawSignal = false
        lastScrollAt = nowMs
        return 1
    }

    fun onLeave(nowMs: Long) {
        leftAt = nowMs
    }

    /**
     * A scroll event from the full-screen player. [fromIndex] is -1 when unknown, [deltaY] is 0 when unknown,
     * and [pageHeight] is the player's height in pixels (0 when unknown). Returns how many new reels it brought.
     */
    fun onScroll(fromIndex: Int, deltaY: Int, pageHeight: Int, nowMs: Long): Int {
        val newGesture = nowMs - lastScrollAt >= gestureGapMs
        lastScrollAt = nowMs

        if (fromIndex >= 0) {
            sawSignal = true
            val base = baseIndex ?: fromIndex.also { baseIndex = it }
            maxIndex = maxOf(maxIndex, fromIndex - base)
        }
        if (deltaY != 0 && pageHeight > 0) {
            sawSignal = true
            scrolledPx += deltaY
            val pages = ((scrolledPx / pageHeight) + (1 - pageThreshold)).toInt()
            maxPages = maxOf(maxPages, pages)
        }

        val added = if (sawSignal) {
            val now = maxOf(maxIndex, maxPages)
            (now - progress).coerceAtLeast(0).also { progress = now }
        } else {
            if (newGesture) 1 else 0
        }
        count += added
        return added
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
