package app.pratyahara.core.budget

import app.pratyahara.core.time.Stamp
import app.pratyahara.core.validation.TextQualityValidator
import app.pratyahara.core.validation.ValidationResult

sealed interface BudgetRequestResult {
    data class AppliedNow(val newMinutes: Int) : BudgetRequestResult
    data class Scheduled(val change: PendingChange) : BudgetRequestResult
    data class Rejected(val message: String) : BudgetRequestResult
}

/**
 * Lowering the budget is instant. Raising it needs a genuine written reason and then waits
 * according to [DelayTable.increaseDelay].
 */
object BudgetPolicy {

    fun requestChange(
        currentMinutes: Int,
        requestedMinutes: Int,
        reason: String,
        now: Stamp,
        newId: () -> String,
    ): BudgetRequestResult {
        val target = requestedMinutes.coerceIn(DelayTable.MIN_BUDGET_MINUTES, DelayTable.MAX_BUDGET_MINUTES)
        if (target == currentMinutes) return BudgetRequestResult.Rejected("That's already your budget.")
        if (target < currentMinutes) return BudgetRequestResult.AppliedNow(target)

        val check = TextQualityValidator.budgetReason.validate(reason)
        if (check is ValidationResult.Invalid) return BudgetRequestResult.Rejected(check.message)

        val delay = DelayTable.increaseDelay(target - currentMinutes)
        return BudgetRequestResult.Scheduled(
            PendingChange.create(newId(), ChangeType.RAISE_BUDGET, target.toString(), now, delay)
        )
    }
}
