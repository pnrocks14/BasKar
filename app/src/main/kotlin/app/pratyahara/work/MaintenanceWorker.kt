package app.pratyahara.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.pratyahara.detection.DetectionRules
import app.pratyahara.detection.ServiceStatus
import app.pratyahara.engine
import app.pratyahara.notifications.Notifier
import app.pratyahara.widget.MinutesWidget
import kotlinx.coroutines.flow.first
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Runs about every 15 minutes: applies waiting changes, notices if blocking was switched off,
 * and sends the evening prompt and morning note once a day each.
 */
class MaintenanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val engine = ctx.engine
        engine.store.loaded.first { it }
        engine.applyDuePending()
        runCatching { MinutesWidget.refresh(ctx) }
        val d = engine.store.current
        if (!d.onboarded) return Result.success()

        // Watchdog: the service can't report its own absence, so we check the system setting.
        if (d.blockingEnabled && !ServiceStatus.isEnabled(ctx)) {
            Notifier.alert(
                ctx, Notifier.ID_SERVICE_OFF,
                "Pratyahara was switched off",
                "The accessibility permission is off, so Reels and Shorts aren't being limited. Tap to turn it back on.",
                route = "home",
            )
            engine.store.update { it.copy(lastServiceOffAlert = System.currentTimeMillis()) }
        } else {
            Notifier.cancel(ctx, Notifier.ID_SERVICE_OFF)
        }

        val today = engine.today()
        val hour = LocalTime.now().hour
        val todayKey = today.toString()

        if (hour >= EVENING_HOUR && d.lastEveningPrompt != todayKey) {
            val usage = d.day(today)
            val minutes = usage.totalSeconds / 60
            val apps = usage.secondsByApp.entries.filter { it.value >= 60 }
                .joinToString { "${DetectionRules.appName(it.key)} ${it.value / 60} min" }
            val streak = engine.streaks(d)
            val summary = buildString {
                append("Today: $minutes min")
                if (apps.isNotEmpty()) append(" ($apps)")
                append(" · opened ${usage.visits} times · paused ${usage.blocks} times. ")
                if (streak.honest > 0) append("Honest streak: ${streak.honest}. ")
                append(if (engine.taskAwaitingCheckIn(d) != null) "Did you do today's task?" else "What's one thing you'll do tomorrow?")
            }
            Notifier.reminder(ctx, Notifier.ID_EVENING, "Evening check-in", summary, route = "task")
            engine.store.update { it.copy(lastEveningPrompt = todayKey) }
        }

        val note = engine.todaysTask(d)
        if (hour in MORNING_HOURS && note != null && d.lastMorningNote != todayKey) {
            Notifier.reminder(ctx, Notifier.ID_MORNING, "A note from past you", note.text, route = "home")
            engine.store.update { it.copy(lastMorningNote = todayKey) }
        }
        return Result.success()
    }

    companion object {
        const val EVENING_HOUR = 21
        val MORNING_HOURS = 6..11

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<MaintenanceWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("maintenance", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
