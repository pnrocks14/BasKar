package app.pratyahara.core.lock

import app.pratyahara.core.tasks.TaskGate

data class LockInputs(
    val blockingEnabled: Boolean,
    val budgetMinutes: Int,
    val usedSecondsToday: Long,
    val unlocksUsedToday: Int,
    val taskGate: TaskGate,
    val cooldownUntilMillis: Long,
    val nowMillis: Long,
    val focusHours: FocusHours? = null,
    /** Local time as minutes since midnight, for [focusHours]. */
    val minuteOfDay: Int = 0,
)

object UnlockRules {
    const val UNLOCK_MINUTES = 5
    const val MAX_UNLOCKS_PER_DAY = 2
    const val COOLDOWN_SECONDS = 10
    const val DEFAULT_SQUATS = 20
    const val MIN_SQUATS = 10
    const val MAX_SQUATS = 50
}

/** Pure decision function. The accessibility service, overlay and home screen all use it. */
object LockPolicy {

    fun evaluate(i: LockInputs): LockState {
        if (!i.blockingEnabled) return LockState.Disabled
        if (i.focusHours?.isActive(i.minuteOfDay) == true) return LockState.FocusLocked(i.focusHours.endMinute)
        if (i.taskGate != TaskGate.OPEN) return LockState.TaskLocked(i.taskGate)
        if (i.nowMillis < i.cooldownUntilMillis) return LockState.Cooldown(i.cooldownUntilMillis)

        val allowance = i.budgetMinutes * 60L + i.unlocksUsedToday * UnlockRules.UNLOCK_MINUTES * 60L
        val remaining = allowance - i.usedSecondsToday
        if (remaining > 0) return LockState.Allowed(remaining)
        return LockState.BudgetLocked(unlocksLeft = (UnlockRules.MAX_UNLOCKS_PER_DAY - i.unlocksUsedToday).coerceAtLeast(0))
    }

    /** Squats can only buy time when the budget is the reason for the lock. */
    fun canEarnUnlock(state: LockState): Boolean = state is LockState.BudgetLocked && state.unlocksLeft > 0
}
