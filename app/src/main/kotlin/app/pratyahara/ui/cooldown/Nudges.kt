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

    /** What the cooldown says as it hands the minutes over. A little sad on purpose. */
    val farewells = listOf(
        "Fine. They're yours. Your goals will wait for you, they always do.",
        "Spend them like they cost something, because they did.",
        "Go on then. I'll be right here when they run out.",
        "These ones, and then that really is it for today.",
        "Enjoy them. Future you is quietly hoping you'll stop early.",
    )

    fun farewell(seed: Long): String = farewells[(Math.floorMod(seed, farewells.size.toLong())).toInt()]

    fun forToday(seed: Long): String = all[(Math.floorMod(seed, all.size.toLong())).toInt()]
}
