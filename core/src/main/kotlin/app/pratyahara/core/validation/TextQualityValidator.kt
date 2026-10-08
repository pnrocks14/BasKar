package app.pratyahara.core.validation

sealed interface ValidationResult {
    data object Ok : ValidationResult
    data class Invalid(val message: String) : ValidationResult
}

data class TextRules(
    val minChars: Int,
    val minWords: Int,
    val minSentences: Int,
    val shortMessage: String,
)

/**
 * Rule-based, on-device check that a piece of writing is a real attempt, not filler.
 * No AI: length, sentence and word counts, repeated characters, keyboard mashing and a
 * small list of lazy phrases.
 */
class TextQualityValidator(private val rules: TextRules) {

    fun validate(input: String): ValidationResult {
        val text = input.trim()
        if (text.isEmpty()) return invalid("Write something first.")

        if (REPEATED_CHARS.containsMatchIn(text)) {
            return invalid("That looks like a held-down key. Write it in your own words.")
        }

        val lazy = LazyPhrases.findIn(text)
        val meaningful = LazyPhrases.strip(text)
        val words = wordsOf(meaningful)

        if (lazy != null && (meaningful.length < rules.minChars || words.size < rules.minWords)) {
            return invalid("\"$lazy\" is honest, but it isn't enough on its own. ${rules.shortMessage}")
        }
        if (words.size < rules.minWords || (words.size <= 1)) {
            return invalid(rules.shortMessage)
        }
        if (meaningful.length < rules.minChars) {
            return invalid(rules.shortMessage)
        }
        if (sentenceCount(meaningful) < rules.minSentences) {
            return invalid(rules.shortMessage)
        }

        val lower = words.map { it.lowercase() }
        if (lower.toSet().size.toDouble() / lower.size < 0.5 && lower.size >= 4) {
            return invalid("The same words keep repeating. Say it the way you'd say it to a friend.")
        }
        val withVowels = lower.count { w -> w.any { it in VOWELS } || w.any { it.isDigit() } || !w.all { it in 'a'..'z' } }
        if (withVowels.toDouble() / lower.size < 0.6) {
            return invalid("That looks like keyboard mashing. Write it in your own words.")
        }
        return ValidationResult.Ok
    }

    private fun invalid(message: String) = ValidationResult.Invalid(message)

    companion object {
        private val REPEATED_CHARS = Regex("(.)\\1{4,}")
        private val WORD = Regex("[\\p{L}\\p{M}\\p{N}']+")
        private val SENTENCE_END = Regex("[.!?\\u0964]+")
        private const val VOWELS = "aeiouy"

        fun wordsOf(text: String): List<String> = WORD.findAll(text).map { it.value }.toList()

        /** Sentences with at least two words, so "Ok. Fine." doesn't count as two. */
        fun sentenceCount(text: String): Int =
            text.split(SENTENCE_END).count { wordsOf(it).size >= 2 }

        /** Reason required before raising the daily budget. */
        val budgetReason = TextQualityValidator(
            TextRules(
                minChars = 100,
                minWords = 15,
                minSentences = 2,
                shortMessage = "Give a real reason in at least two sentences: what's different about today, and why it's worth more time.",
            )
        )

        /** The nightly task for tomorrow. One clear sentence is enough. */
        val nightlyTask = TextQualityValidator(
            TextRules(
                minChars = 15,
                minWords = 3,
                minSentences = 1,
                shortMessage = "Make it a task you could actually check off, like \"Call the bank before noon\".",
            )
        )
    }
}
