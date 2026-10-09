package app.pratyahara.ui.cooldown

/** Small real-world nudges shown during the cooldown, one at a time, rotating. */
object Nudges {
    val all = listOf(
        "Refill your water bottle",
        "Step outside for 2 min",
        "Roll your shoulders back 5 times",
        "Look at something far away for 20 sec",
        "Text someone you haven't talked to this week",
        "Open a window, 3 slow breaths",
        "Put one thing back where it belongs",
        "Stretch your arms over your head",
        "Write down one thing you're grateful for",
        "Make tea and drink it phone-free",
        "Water a plant, or go look at one",
        "Read one page of a book",
    )

    fun forToday(seed: Long): String = all[(Math.floorMod(seed, all.size.toLong())).toInt()]
}
