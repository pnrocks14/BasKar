package app.pratyahara.domain

import app.pratyahara.core.budget.BudgetPolicy
import app.pratyahara.core.budget.BudgetRequestResult
import app.pratyahara.core.budget.ChangeType
import app.pratyahara.core.budget.DelayTable
import app.pratyahara.core.budget.PendingChange
import app.pratyahara.core.budget.withRequest
import app.pratyahara.core.lock.LockInputs
import app.pratyahara.core.lock.LockPolicy
import app.pratyahara.core.lock.LockState
import app.pratyahara.core.lock.UnlockRules
import app.pratyahara.core.stats.TimeSaved
import app.pratyahara.core.tasks.CheckIn
import app.pratyahara.core.tasks.DayTask
import app.pratyahara.core.tasks.StreakSummary
import app.pratyahara.core.tasks.Streaks
import app.pratyahara.core.tasks.TaskGate
import app.pratyahara.core.tasks.TaskLoop
import app.pratyahara.core.time.Clock
import app.pratyahara.core.time.DayBoundary
import app.pratyahara.core.time.Stamp
import app.pratyahara.core.validation.TextQualityValidator
import app.pratyahara.core.validation.ValidationResult
import app.pratyahara.data.AppData
import app.pratyahara.data.DayUsage
import app.pratyahara.data.PendingRecord
import app.pratyahara.data.Store
import app.pratyahara.data.TaskRecord
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Everything the app does to its data goes through here, so the rules live in one place. */
class Engine(val store: Store, val clock: Clock) {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun today(): LocalDate = DayBoundary.dayOf(clock.nowMillis(), zone)

    // ---- Reading ----

    fun lockState(d: AppData = store.current): LockState {
        val today = today()
        val tasks = d.taskModels()
        val usage = d.day(today)
        var state = LockPolicy.evaluate(
            LockInputs(
                blockingEnabled = d.blockingEnabled,
                budgetMinutes = d.budgetMinutes,
                usedSecondsToday = usage.totalSeconds,
                unlocksUsedToday = usage.unlocks,
                taskGate = TaskLoop.gate(today, tasks),
                cooldownUntilMillis = d.cooldownUntil,
                nowMillis = clock.nowMillis(),
                focusHours = d.focusHours(),
                minuteOfDay = minuteOfDay(),
            )
        )
        // After a missed check-in, squats can't buy time today.
        if (state is LockState.BudgetLocked && !TaskLoop.squatsAllowedToday(today, tasks)) {
            state = LockState.BudgetLocked(unlocksLeft = 0)
        }
        return state
    }

    fun minuteOfDay(): Int =
        java.time.Instant.ofEpochMilli(clock.nowMillis()).atZone(zone).toLocalTime().let { it.hour * 60 + it.minute }

    /** Focus hours can't be loosened while they're on, or they'd be one tap away from useless. */
    fun focusEditable(d: AppData = store.current): Boolean = d.focusHours()?.isActive(minuteOfDay()) != true

    fun taskGate(d: AppData = store.current): TaskGate = TaskLoop.gate(today(), d.taskModels())
    fun streaks(d: AppData = store.current): StreakSummary = Streaks.compute(d.taskModels(), today())
    fun taskAwaitingCheckIn(d: AppData = store.current): DayTask? = TaskLoop.taskAwaitingCheckIn(today(), d.taskModels(), isEvening())
    fun nextTaskDay(d: AppData = store.current): LocalDate = TaskLoop.nextTaskDay(today(), d.taskModels(), isEvening())

    /** From 6 pm until the day ends at 4 am: tasks written now are for tomorrow, and today's can be answered. */
    fun isEvening(): Boolean = minuteOfDay() >= EVENING_FROM_MINUTE || minuteOfDay() < DayBoundary.ROLLOVER_HOUR * 60
    fun todaysTask(d: AppData = store.current): DayTask? = d.taskModels().firstOrNull { it.day == today() }

    fun weekSavedMinutes(d: AppData = store.current): Int {
        val start = d.startDay?.let { LocalDate.parse(it) } ?: today()
        val used = d.days.mapKeys { LocalDate.parse(it.key) }.mapValues { it.value.totalSeconds }
        return TimeSaved.weekMinutes(d.baselineMinutes, used, today(), start)
    }

    /** The last [days] days, oldest first, ending today. */
    fun recentDays(d: AppData = store.current, days: Int = 7): List<Pair<LocalDate, DayUsage>> {
        val today = today()
        return (days - 1 downTo 0).map { back -> today.minusDays(back.toLong()).let { it to d.day(it) } }
    }

    fun stamp(): Stamp = Stamp.of(clock)

    // ---- Usage counters (called by the accessibility service) ----

    suspend fun addUsage(secondsByApp: Map<String, Long>) {
        if (secondsByApp.isEmpty()) return
        val key = today().toString()
        store.update { d ->
            val day = d.days[key] ?: DayUsage()
            val merged = (day.secondsByApp.keys + secondsByApp.keys).associateWith {
                (day.secondsByApp[it] ?: 0L) + (secondsByApp[it] ?: 0L)
            }
            d.copy(days = d.days + (key to day.copy(secondsByApp = merged)))
        }
    }

    /** Reels/Shorts was opened; returns today's visit count including this one. */
    suspend fun recordVisit(): Int {
        val key = today().toString()
        var visits = 1
        store.update { d ->
            val day = d.days[key] ?: DayUsage()
            visits = day.visits + 1
            d.copy(days = d.days + (key to day.copy(visits = visits)))
        }
        return visits
    }

    /** Returns false while focus hours are running. */
    suspend fun setFocus(enabled: Boolean, start: Int, end: Int): Boolean {
        if (!focusEditable()) return false
        store.update { it.copy(focusEnabled = enabled, focusStart = start, focusEnd = end) }
        return true
    }

    suspend fun updateSettings(transform: (AppData) -> AppData) = store.update(transform)

    suspend fun recordBlock() {
        val key = today().toString()
        store.update { d ->
            val day = d.days[key] ?: DayUsage()
            d.copy(days = d.days + (key to day.copy(blocks = day.blocks + 1)))
        }
    }

    /** Called after the squats are verified. Returns false if no unlock is available. */
    suspend fun grantUnlock(): Boolean {
        if (!LockPolicy.canEarnUnlock(lockState())) return false
        val key = today().toString()
        val until = clock.nowMillis() + UnlockRules.COOLDOWN_SECONDS * 1000L
        store.update { d ->
            val day = d.days[key] ?: DayUsage()
            d.copy(days = d.days + (key to day.copy(unlocks = day.unlocks + 1)), cooldownUntil = until)
        }
        return true
    }

    // ---- Settings that loosen protection wait; settings that tighten it apply now ----

    suspend fun requestBudget(newMinutes: Int, reason: String): BudgetRequestResult {
        val d = store.current
        val result = BudgetPolicy.requestChange(d.budgetMinutes, newMinutes, reason, stamp()) { UUID.randomUUID().toString() }
        when (result) {
            is BudgetRequestResult.AppliedNow -> store.update {
                // Lowering also cancels any waiting increase.
                it.copy(budgetMinutes = result.newMinutes, pending = it.pending.filterNot { p -> p.type == ChangeType.RAISE_BUDGET.name })
            }
            is BudgetRequestResult.Scheduled -> schedule(result.change)
            is BudgetRequestResult.Rejected -> Unit
        }
        return result
    }

    suspend fun setAppMonitored(packageName: String, monitored: Boolean) {
        if (monitored) {
            store.update {
                it.copy(
                    monitored = it.monitored + packageName,
                    pending = it.pending.filterNot { p -> p.type == ChangeType.REMOVE_APP.name && p.value == packageName },
                )
            }
        } else {
            scheduleProtection(ChangeType.REMOVE_APP, packageName)
        }
    }

    suspend fun setBlockingEnabled(enabled: Boolean) {
        if (enabled) {
            store.update {
                it.copy(blockingEnabled = true, pending = it.pending.filterNot { p -> p.type == ChangeType.DISABLE_BLOCKING.name })
            }
        } else {
            scheduleProtection(ChangeType.DISABLE_BLOCKING, "")
        }
    }

    suspend fun setSquats(count: Int) {
        val target = count.coerceIn(UnlockRules.MIN_SQUATS, UnlockRules.MAX_SQUATS)
        val current = store.current.squats
        if (target >= current) {
            store.update { it.copy(squats = target, pending = it.pending.filterNot { p -> p.type == ChangeType.LOWER_SQUATS.name }) }
        } else {
            scheduleProtection(ChangeType.LOWER_SQUATS, target.toString())
        }
    }

    private suspend fun scheduleProtection(type: ChangeType, value: String) =
        schedule(PendingChange.create(UUID.randomUUID().toString(), type, value, stamp(), DelayTable.PROTECTION_DELAY))

    private suspend fun schedule(change: PendingChange) {
        store.update { d ->
            val list = d.pendingModels().withRequest(change)
            d.copy(pending = list.map { PendingRecord.from(it) })
        }
    }

    suspend fun cancelPending(id: String) {
        store.update { it.copy(pending = it.pending.filterNot { p -> p.id == id }) }
    }

    /** Applies every waiting change whose time has come. Safe to call often. */
    suspend fun applyDuePending() {
        val now = stamp()
        if (store.current.pendingModels().none { it.isDue(now) }) return
        store.update { d ->
            var next = d
            val due = d.pendingModels().filter { it.isDue(now) }
            for (c in due) {
                next = when (c.type) {
                    ChangeType.RAISE_BUDGET -> next.copy(budgetMinutes = c.value.toInt().coerceAtMost(DelayTable.MAX_BUDGET_MINUTES))
                    ChangeType.REMOVE_APP -> next.copy(monitored = next.monitored - c.value)
                    ChangeType.DISABLE_BLOCKING -> next.copy(blockingEnabled = false)
                    ChangeType.LOWER_SQUATS -> next.copy(squats = c.value.toInt())
                }
            }
            val dueIds = due.map { it.id }.toSet()
            next.copy(pending = next.pending.filterNot { it.id in dueIds })
        }
    }

    // ---- Nightly task loop ----

    fun validateTask(text: String): ValidationResult = TextQualityValidator.nightlyTask.validate(text)

    suspend fun writeTask(text: String): ValidationResult {
        val check = validateTask(text)
        if (check !is ValidationResult.Ok) return check
        val day = nextTaskDay()
        val record = TaskRecord(day.toString(), text.trim(), clock.nowMillis())
        store.update { d -> d.copy(tasks = d.tasks.filterNot { it.day == record.day } + record) }
        return check
    }

    suspend fun answerCheckIn(task: DayTask, answer: CheckIn) {
        val answered = TaskLoop.answer(task, answer, today(), clock.nowMillis())
        store.update { d ->
            d.copy(tasks = d.tasks.map { if (it.day == task.day.toString()) TaskRecord.from(answered) else it })
        }
    }

    // ---- Onboarding ----

    suspend fun finishOnboarding(monitored: Set<String>, budgetMinutes: Int, baselineMinutes: Int) {
        store.update {
            it.copy(
                onboarded = true,
                monitored = monitored,
                budgetMinutes = budgetMinutes.coerceIn(DelayTable.MIN_BUDGET_MINUTES, DelayTable.MAX_BUDGET_MINUTES),
                baselineMinutes = baselineMinutes,
                startDay = it.startDay ?: today().toString(),
            )
        }
    }

    suspend fun acceptDisclosure() {
        store.update { it.copy(disclosureAcceptedAt = clock.nowMillis()) }
    }

    private companion object {
        const val EVENING_FROM_MINUTE = 18 * 60
    }
}
