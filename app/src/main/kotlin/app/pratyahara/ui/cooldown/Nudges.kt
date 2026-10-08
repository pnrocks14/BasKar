package app.pratyahara.ui.cooldown

/** Small real-world nudges shown during the cooldown, one at a time, rotating. */
object Nudges {
    val all = listOf(
        "Refill your water bottle.",
        "Step outside for two minutes.",
        "Roll your shoulders back five times.",
        "Look at something far away for twenty seconds.",
        "Text someone you haven't spoken to this week.",
        "Open a window and take three slow breaths.",
        "Put one thing back where it belongs.",
        "Stand up and stretch your arms overhead.",
        "Write down one thing you're grateful for.",
        "Make a cup of tea and drink it without your phone.",
        "Water a plant, or go look at one.",
        "Read one page of a book.",
    )

    fun forToday(seed: Long): String = all[(Math.floorMod(seed, all.size.toLong())).toInt()]
}
