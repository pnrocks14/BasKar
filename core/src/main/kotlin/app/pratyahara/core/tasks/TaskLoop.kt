package app.pratyahara.core.tasks

import java.time.LocalDate

enum class CheckIn { DONE, NOT_DONE }

/** A task written one evening for the next [day]. */
data class DayTask(
    val day: LocalDate,
    val text: String,
    val writtenAtMillis: Long,
    val checkIn: CheckIn? = null,
    val checkedAtMillis: Long? = null,
    /** Answered after the day had already ended. */
    val late: Boolean = false,
)

enum class TaskGate {
    /** Nothing owed. */
    OPEN,

    /** You said "no", or forgot to write a task: write one to unlock. Squats still work tomorrow. */
    NEEDS_NEW_TASK,

    /** Yesterday's check-in went unanswered: answer it, then write a new task. */
    MISSED_CHECKIN,
}

/**
 * The nightly accountability loop. Honest "no" is always cheaper than silence:
 * "no" asks only for a new task; silence also resets both streaks and turns squat unlocks off.
 */
object TaskLoop {

    fun gate(today: LocalDate, tasks: List<DayTask>): TaskGate {
        if (tasks.isEmpty()) return TaskGate.OPEN
        val yesterday = tasks.firstOrNull { it.day == today.minusDays(1) }
        if (yesterday != null && yesterday.checkIn == null) return TaskGate.MISSED_CHECKIN

        val latestAnswered = listOfNotNull(tasks.firstOrNull { it.day == today }, yesterday)
            .filter { it.checkIn != null }
            .maxByOrNull { it.day }
        if (latestAnswered != null && (latestAnswered.checkIn == CheckIn.NOT_DONE || latestAnswered.late)) {
            val answeredAt = latestAnswered.checkedAtMillis ?: 0L
            val rewritten = tasks.any { it.day > latestAnswered.day && it.writtenAtMillis >= answeredAt }
            return if (rewritten) TaskGate.OPEN else TaskGate.NEEDS_NEW_TASK
        }

        // The loop has started but there is no task for today: last night's task was skipped.
        val started = tasks.any { it.day < today }
        val hasCurrent = tasks.any { it.day >= today }
        if (started && !hasCurrent) return TaskGate.NEEDS_NEW_TASK
        return TaskGate.OPEN
    }

    /** The day the next task should be written for: today if it has none yet, otherwise tomorrow. */
    fun nextTaskDay(today: LocalDate, tasks: List<DayTask>): LocalDate =
        if (tasks.none { it.day == today }) today else today.plusDays(1)

    /** True if squat unlocks are allowed today: not after a missed check-in. */
    fun squatsAllowedToday(today: LocalDate, tasks: List<DayTask>): Boolean {
        val yesterday = tasks.firstOrNull { it.day == today.minusDays(1) } ?: return true
        return yesterday.checkIn != null && !yesterday.late
    }

    /** The day whose task should be answered now: today's task, during today. */
    fun taskAwaitingCheckIn(today: LocalDate, tasks: List<DayTask>): DayTask? =
        tasks.firstOrNull { it.day == today.minusDays(1) && it.checkIn == null }
            ?: tasks.firstOrNull { it.day == today && it.checkIn == null }

    /** Records an answer; [today] decides whether it was given on time. */
    fun answer(task: DayTask, answer: CheckIn, today: LocalDate, nowMillis: Long): DayTask =
        task.copy(checkIn = answer, checkedAtMillis = nowMillis, late = today > task.day)
}
