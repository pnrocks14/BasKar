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
    /** Reels a day before they close too, whatever minutes are left; 0 means no reel limit. */
    val reelLimit: Int = 0,
    val reelsToday: Int = 0,
)

object UnlockRules {
    const val UNLOCK_MINUTES = 5
    const val MAX_UNLOCKS_PER_DAY = 2
    const val COOLDOWN_SECONDS = 10
    /** Reels each squat pass adds on top of a reel limit. */
    const val UNLOCK_REELS = 10
    val REEL_LIMITS = listOf(0, 30, 50, 100)
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
        val unlocksLeft = (UnlockRules.MAX_UNLOCKS_PER_DAY - i.unlocksUsedToday).coerceAtLeast(0)
        if (remaining <= 0) return LockState.BudgetLocked(unlocksLeft)
        if (i.reelLimit > 0 && i.reelsToday >= i.reelLimit + i.unlocksUsedToday * UnlockRules.UNLOCK_REELS) {
            return LockState.BudgetLocked(unlocksLeft, byReels = true)
        }
        return LockState.Allowed(remaining)
    }

    /** Squats can only buy time when the budget is the reason for the lock. */
    fun canEarnUnlock(state: LockState): Boolean = state is LockState.BudgetLocked && state.unlocksLeft > 0
}
