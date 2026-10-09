package app.pratyahara.core.buddy

/** A small message shown at the top of the screen while scrolling. It never blocks anything. */
data class Chip(val kind: Kind, val title: String, val subtitle: String? = null) {
    enum class Kind { HELLO, AGAIN, WARNING, QUOTE }
}

/**
 * Decides when the in-scroll check-ins appear: a hello when Reels/Shorts opens, a heads-up when time
 * is nearly gone, and a research-backed quote every few minutes of continuous scrolling. Pure logic,
 * driven by the accessibility service once a second.
 */
class Buddy(
    private val quoteEverySeconds: Long = 180,
    /** Opening Reels again within this long counts as the same visit: no second hello. */
    private val sameVisitMs: Long = 60_000,
    private val seed: Long = System.nanoTime(),
) {
    private var lastLeftMs = -1L
    private var watchingSinceQuote = 0L
    private var quoteIndex = 0
    private var warnedDay: String? = null
    private var warned5 = false
    private var warned1 = false

    /**
     * Reels/Shorts just became visible. [visitsToday] counts earlier visits today, including this one.
     * Returns a greeting, or null when it's just a quick return.
     */
    fun onEnter(nowMs: Long, visitsToday: Int, minutesLeft: Long): Chip? {
        val quickReturn = lastLeftMs >= 0 && nowMs - lastLeftMs < sameVisitMs
        if (quickReturn) return null
        watchingSinceQuote = 0
        val left = leftLine(minutesLeft)
        return if (visitsToday <= 1) {
            Chip(Chip.Kind.HELLO, "hii 👀 i'm watching you", left)
        } else {
            Chip(Chip.Kind.AGAIN, AGAIN[Math.floorMod(seed + visitsToday, AGAIN.size.toLong()).toInt()], "visit #$visitsToday today · $left")
        }
    }

    fun onLeave(nowMs: Long) {
        lastLeftMs = nowMs
    }

    /** One second of watching. [secondsLeft] is today's remaining allowance. */
    fun onSecond(day: String, secondsLeft: Long): Chip? {
        if (warnedDay != day) {
            warnedDay = day
            warned5 = false
            warned1 = false
        }
        if (secondsLeft in 1..60 && !warned1) {
            warned1 = true
            warned5 = true
            return Chip(Chip.Kind.WARNING, "last minute ⏳", "make it count, then we're done for today")
        }
        if (secondsLeft in 61..300 && !warned5) {
            warned5 = true
            return Chip(Chip.Kind.WARNING, "${(secondsLeft + 59) / 60} min left ✌️", "start wrapping up")
        }
        watchingSinceQuote++
        if (quoteEverySeconds > 0 && watchingSinceQuote >= quoteEverySeconds) {
            watchingSinceQuote = 0
            val q = Quotes.pick(seed + quoteIndex++ * 5L)
            return Chip(Chip.Kind.QUOTE, q.text, q.source)
        }
        return null
    }

    private fun leftLine(minutesLeft: Long) = when {
        minutesLeft <= 0 -> "no reels time left today"
        minutesLeft == 1L -> "1 min of reels left today"
        else -> "$minutesLeft min of reels left today"
    }

    companion object {
        val AGAIN = listOf(
            "you again? 👀",
            "back so soon? 🤨",
            "oh hey, reels again 👀",
            "not you opening reels again 💀",
            "we meet again 👁️👄👁️",
        )
    }
}
