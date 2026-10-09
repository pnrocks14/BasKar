package app.pratyahara.core.detection

/**
 * One loose clue that a Reels/Shorts player is on screen. Rules combine several weighted signals
 * so that no single view ID, which apps rename often, decides the outcome.
 */
sealed interface Signal {
    fun matches(s: UiSnapshot): Boolean

    /** A selected bottom-nav tab whose label contains one of [labels] (e.g. "Reels", "Shorts"). */
    data class SelectedTab(val labels: Set<String>) : Signal {
        override fun matches(s: UiSnapshot) = s.nodes.any { n ->
            n.selected && n.top > s.screenHeight * 0.7 && labels.any { n.label.contains(it.lowercase()) }
        }
    }

    /**
     * A scrollable pager or list starting at the very top of the screen: the vertical full-screen player.
     * A feed starts below the app's header, which is what tells the two apart. Height allows for a bottom
     * navigation bar that stays visible (Reels opened from Explore).
     */
    data class FullScreenPager(val widthCoverage: Double = 0.9, val heightCoverage: Double = 0.8) : Signal {
        override fun matches(s: UiSnapshot) = s.nodes.any { n ->
            n.scrollable &&
                n.width >= s.screenWidth * widthCoverage &&
                n.height >= s.screenHeight * heightCoverage &&
                n.top <= s.screenHeight * 0.04 &&
                (n.className.orEmpty().contains("Pager") || n.className.orEmpty().contains("RecyclerView"))
        }
    }

    /**
     * At least [minMatches] of the action labels (like, comment, share...) on small buttons stacked on the right edge.
     * Grid tiles (a profile's posts, whose descriptions mention likes and comments) are too wide to count.
     */
    data class RightActionStack(val labels: Set<String>, val minMatches: Int = 2) : Signal {
        override fun matches(s: UiSnapshot): Boolean {
            val rightEdge = s.nodes.filter {
                it.centerX > s.screenWidth * 0.75 &&
                    it.width in 1..(s.screenWidth * 0.2).toInt() &&
                    it.height in 1..(s.screenHeight * 0.12).toInt() &&
                    it.top > s.screenHeight * 0.25 &&
                    it.label.length <= 40
            }
            val hits = labels.mapNotNull { l -> rightEdge.firstOrNull { it.label.contains(l.lowercase()) } }.distinct()
            if (hits.size < minMatches) return false
            // Stacked top to bottom, not side by side like a message composer's buttons.
            return hits.maxOf { it.top } - hits.minOf { it.top } >= s.screenHeight * 0.04
        }
    }

    /**
     * At least [minMatches] action labels on small buttons side by side in the left part of the screen:
     * the like/comment/share row under a post.
     */
    data class ActionRow(val labels: Set<String>, val minMatches: Int = 2) : Signal {
        override fun matches(s: UiSnapshot): Boolean {
            val small = s.nodes.filter { it.centerX < s.screenWidth * 0.6 && it.width in 1..(s.screenWidth * 0.2).toInt() && it.label.length <= 40 }
            val hits = labels.mapNotNull { l -> small.firstOrNull { it.label.contains(l.lowercase()) } }.distinct()
            if (hits.size < minMatches) return false
            return hits.maxOf { it.top } - hits.minOf { it.top } <= s.screenHeight * 0.03
        }
    }

    /** Every one of [signals] matches. For clues that only mean something together. */
    data class AllOf(val signals: List<Signal>) : Signal {
        override fun matches(s: UiSnapshot) = signals.all { it.matches(s) }
    }

    /** Any node whose text or description contains one of [labels], e.g. "Original audio". */
    data class LabelPresent(val labels: Set<String>) : Signal {
        override fun matches(s: UiSnapshot) = s.nodes.any { n -> labels.any { n.label.contains(it.lowercase()) } }
    }

    /** Every one of [labels] is on screen at once, e.g. "posts", "followers", "following" on a profile. */
    data class AllLabelsPresent(val labels: Set<String>) : Signal {
        override fun matches(s: UiSnapshot) = labels.all { l -> s.nodes.any { it.label.contains(l.lowercase()) } }
    }

    /**
     * A view ID containing one of [fragments] (Instagram calls Reels "clips" internally), except [exclude],
     * on a view at least [minHeight] of the screen tall starting at the top: the full-screen player itself,
     * not a reel playing inside the home feed or a preview in a chat.
     */
    data class ViewIdContains(val fragments: Set<String>, val exclude: Set<String> = emptySet(), val minHeight: Double = 0.0) : Signal {
        override fun matches(s: UiSnapshot) = s.nodes.any { n ->
            val id = n.viewId ?: return@any false
            id !in exclude && n.height >= s.screenHeight * minHeight && (minHeight == 0.0 || n.top <= s.screenHeight * 0.04) &&
                fragments.any { id.contains(it) }
        }
    }

    /** A view ID known to belong to the player. A soft clue: apps rename these. */
    data class ViewIdPresent(val ids: Set<String>) : Signal {
        override fun matches(s: UiSnapshot) = s.nodes.any { it.viewId != null && it.viewId in ids }
    }
}

/** A negative [weight] marks a screen that is clearly not Reels/Shorts, such as a profile page. */
data class WeightedSignal(val signal: Signal, val weight: Int)

/** Detection rule for one app. The screen is Reels/Shorts when matched weights (negative ones included) reach [threshold]. */
data class DetectionRule(
    val packageName: String,
    val appName: String,
    val sectionName: String,
    val signals: List<WeightedSignal>,
    val threshold: Int,
)
