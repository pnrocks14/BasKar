package app.pratyahara.core.lock

import app.pratyahara.core.tasks.TaskGate

/** The one answer every part of the app asks for: may Reels/Shorts play right now? */
sealed interface LockState {
    /** Blocking is switched off (only reachable after the 24-hour wait). */
    data object Disabled : LockState

    data class Allowed(val remainingSeconds: Long) : LockState

    /** Just unlocked: a short pause before Reels/Shorts can open. */
    data class Cooldown(val untilMillis: Long) : LockState

    data class BudgetLocked(val unlocksLeft: Int) : LockState

    data class TaskLocked(val gate: TaskGate) : LockState

    /** Inside the daily focus hours; squats can't open it. [endMinute] counts from midnight. */
    data class FocusLocked(val endMinute: Int) : LockState

    val blocksReels: Boolean get() = this is Cooldown || this is BudgetLocked || this is TaskLocked || this is FocusLocked
}
