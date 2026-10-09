package app.pratyahara.data

import app.pratyahara.core.budget.ChangeType
import app.pratyahara.core.budget.DelayTable
import app.pratyahara.core.budget.PendingChange
import app.pratyahara.core.lock.UnlockRules
import app.pratyahara.core.tasks.CheckIn
import app.pratyahara.core.tasks.DayTask
import app.pratyahara.core.time.Stamp
import app.pratyahara.detection.DetectionRules
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * Everything Pratyahara stores, in one on-device record. It holds settings, counters and your own
 * task text. It never holds anything read from another app's screen.
 */
@Serializable
data class AppData(
    val onboarded: Boolean = false,
    val disclosureAcceptedAt: Long = 0,
    val blockingEnabled: Boolean = true,
    val monitored: Set<String> = DetectionRules.defaultPackages,
    val budgetMinutes: Int = DelayTable.DEFAULT_BUDGET_MINUTES,
    val squats: Int = UnlockRules.DEFAULT_SQUATS,
    /** The user's own estimate of daily Reels/Shorts time before Pratyahara, for "time saved". */
    val baselineMinutes: Int = 90,
    val startDay: String? = null,
    val cooldownUntil: Long = 0,
    /** Keyed by ISO day (4 am to 4 am). */
    val days: Map<String, DayUsage> = emptyMap(),
    val tasks: List<TaskRecord> = emptyList(),
    val pending: List<PendingRecord> = emptyList(),
    /** ISO days on which each reminder was already shown, so each fires once. */
    val lastEveningPrompt: String? = null,
    val lastMorningNote: String? = null,
    val lastServiceOffAlert: Long = 0,
    /** Check-ins and quotes shown at the top of the screen while scrolling. */
    val buddyEnabled: Boolean = true,
    /** Minutes of continuous scrolling between quotes; 0 means no quotes, only hellos and heads-ups. */
    val quoteEveryMinutes: Int = 3,
    /** [app.pratyahara.core.reps.PhonePlacement] name. */
    val squatPlacement: String = "POCKET",
    /** Count squats out loud, so the screen doesn't need watching. */
    val voiceCount: Boolean = true,
) {
    fun day(day: LocalDate): DayUsage = days[day.toString()] ?: DayUsage()
    fun taskModels(): List<DayTask> = tasks.map { it.toModel() }
    fun pendingModels(): List<PendingChange> = pending.map { it.toModel() }
}

@Serializable
data class DayUsage(
    val secondsByApp: Map<String, Long> = emptyMap(),
    val blocks: Int = 0,
    val unlocks: Int = 0,
    /** How many times Reels/Shorts was opened. */
    val visits: Int = 0,
) {
    val totalSeconds: Long get() = secondsByApp.values.sum()
}

@Serializable
data class TaskRecord(
    val day: String,
    val text: String,
    val writtenAt: Long,
    val checkIn: String? = null,
    val checkedAt: Long? = null,
    val late: Boolean = false,
) {
    fun toModel() = DayTask(LocalDate.parse(day), text, writtenAt, checkIn?.let { CheckIn.valueOf(it) }, checkedAt, late)

    companion object {
        fun from(t: DayTask) = TaskRecord(t.day.toString(), t.text, t.writtenAtMillis, t.checkIn?.name, t.checkedAtMillis, t.late)
    }
}

@Serializable
data class PendingRecord(
    val id: String,
    val type: String,
    val value: String,
    val wall: Long,
    val elapsed: Long,
    val boot: Int,
    val delayMillis: Long,
) {
    fun toModel() = PendingChange(id, ChangeType.valueOf(type), value, Stamp(wall, elapsed, boot), delayMillis)

    companion object {
        fun from(c: PendingChange) = PendingRecord(
            c.id, c.type.name, c.value, c.requested.wallMillis, c.requested.elapsedMillis, c.requested.bootCount, c.delayMillis,
        )
    }
}
