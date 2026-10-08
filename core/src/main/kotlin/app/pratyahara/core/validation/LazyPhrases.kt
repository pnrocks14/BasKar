package app.pratyahara.core.validation

/** Phrases that, on their own, are not a reason. */
object LazyPhrases {
    val phrases = listOf(
        "just bored",
        "i'm bored",
        "im bored",
        "bored",
        "need it",
        "i need it",
        "want it",
        "i want it",
        "idk",
        "i don't know",
        "i dont know",
        "just because",
        "because i want",
        "whatever",
        "no reason",
        "just one more",
        "one more",
        "please",
        "pls",
        "plz",
        "asdf",
        "nothing",
        "test",
    )

    private val patterns = phrases
        .sortedByDescending { it.length }
        .map { it to Regex("(?<![\\p{L}])" + Regex.escape(it) + "(?![\\p{L}])", RegexOption.IGNORE_CASE) }

    fun findIn(text: String): String? = patterns.firstOrNull { (_, re) -> re.containsMatchIn(text) }?.first

    /** The text with every lazy phrase removed, so only the real content is measured. */
    fun strip(text: String): String =
        patterns.fold(text) { acc, (_, re) -> re.replace(acc, " ") }.replace(Regex("\\s+"), " ").trim()
}
