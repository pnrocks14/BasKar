package app.pratyahara.core.budget

import java.time.Duration

/** How long protective changes wait before they take effect. */
object DelayTable {
    const val DEFAULT_BUDGET_MINUTES = 30
    const val MIN_BUDGET_MINUTES = 5
    const val MAX_BUDGET_MINUTES = 120

    /** Disabling blocking, removing a monitored app or lowering protection. */
    val PROTECTION_DELAY: Duration = Duration.ofHours(24)

    /** The delay before a budget increase of [increaseMinutes] takes effect. */
    fun increaseDelay(increaseMinutes: Int): Duration = when {
        increaseMinutes <= 0 -> Duration.ZERO
        increaseMinutes <= 10 -> Duration.ofHours(4)
        increaseMinutes <= 20 -> Duration.ofHours(12)
        increaseMinutes <= 30 -> Duration.ofHours(24)
        increaseMinutes <= 60 -> Duration.ofDays(2)
        else -> Duration.ofDays(3)
    }
}
