package app.pratyahara.core.detection

data class DetectionResult(val score: Int, val threshold: Int, val matched: List<String>) {
    val isShortForm: Boolean get() = score >= threshold
}

class DetectionEngine(rules: List<DetectionRule>) {

    private val byPackage = rules.associateBy { it.packageName }

    fun ruleFor(packageName: String): DetectionRule? = byPackage[packageName]

    fun evaluate(snapshot: UiSnapshot): DetectionResult? {
        val rule = byPackage[snapshot.packageName] ?: return null
        val hits = rule.signals.filter { it.signal.matches(snapshot) }
        return DetectionResult(
            score = hits.sumOf { it.weight },
            threshold = rule.threshold,
            matched = hits.map { it.signal::class.simpleName.orEmpty() },
        )
    }
}

/**
 * Requires [required] positive evaluations in a row before reporting "on", and one negative to report "off".
 * Stops a single odd frame from triggering the overlay.
 */
class Debouncer(private val required: Int = 2) {
    private var streak = 0

    fun update(positive: Boolean): Boolean {
        streak = if (positive) streak + 1 else 0
        return streak >= required
    }

    fun reset() {
        streak = 0
    }
}
