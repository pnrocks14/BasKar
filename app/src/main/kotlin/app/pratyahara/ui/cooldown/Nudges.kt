package app.pratyahara.ui.cooldown

/** Small real-world nudges shown during the cooldown, one at a time, rotating. */
object Nudges {
    val all = listOf(
        "refill your water bottle 💧",
        "step outside for 2 min 🌤️",
        "roll your shoulders back 5 times",
        "look at something far away for 20 sec 👀",
        "text someone you haven't talked to this week 💬",
        "open a window, 3 slow breaths 🌬️",
        "put one thing back where it belongs 🧺",
        "stretch your arms over your head 🙆",
        "write down one thing you're grateful for 📝",
        "make tea and drink it phone-free 🍵",
        "water a plant, or go look at one 🪴",
        "read one page of a book 📖",
    )

    fun forToday(seed: Long): String = all[(Math.floorMod(seed, all.size.toLong())).toInt()]
}
