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

    /** A scrollable container covering most of the screen: the vertical full-screen pager. */
    data class FullScreenPager(val coverage: Double = 0.85) : Signal {
        override fun matches(s: UiSnapshot) = s.nodes.any { n ->
            n.scrollable &&
                n.width >= s.screenWidth * coverage &&
                n.height >= s.screenHeight * coverage &&
                (n.className.orEmpty().contains("Pager") || n.className.orEmpty().contains("RecyclerView"))
        }
    }

    /** At least [minMatches] of the action labels (like, comment, share...) stacked on the right edge. */
    data class RightActionStack(val labels: Set<String>, val minMatches: Int = 2) : Signal {
        override fun matches(s: UiSnapshot): Boolean {
            val rightEdge = s.nodes.filter { it.centerX > s.screenWidth * 0.75 }
            val found = labels.count { l -> rightEdge.any { it.label.contains(l.lowercase()) } }
            return found >= minMatches
        }
    }

    /** Any node whose text or description contains one of [labels], e.g. "Original audio". */
    data class LabelPresent(val labels: Set<String>) : Signal {
        override fun matches(s: UiSnapshot) = s.nodes.any { n -> labels.any { n.label.contains(it.lowercase()) } }
    }

    /** A view ID known to belong to the player. A soft clue: apps rename these. */
    data class ViewIdPresent(val ids: Set<String>) : Signal {
        override fun matches(s: UiSnapshot) = s.nodes.any { it.viewId != null && it.viewId in ids }
    }
}

data class WeightedSignal(val signal: Signal, val weight: Int)

/** Detection rule for one app. The screen is Reels/Shorts when matched weights reach [threshold]. */
data class DetectionRule(
    val packageName: String,
    val appName: String,
    val sectionName: String,
    val signals: List<WeightedSignal>,
    val threshold: Int,
)
