package app.pratyahara.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pratyahara.core.budget.ChangeType
import app.pratyahara.core.lock.LockState
import app.pratyahara.core.lock.UnlockRules
import app.pratyahara.core.tasks.TaskGate
import app.pratyahara.detection.DetectionRules
import app.pratyahara.detection.ServiceStatus
import app.pratyahara.engine
import app.pratyahara.ui.Routes
import app.pratyahara.ui.components.Muted
import app.pratyahara.ui.components.PrimaryButton
import app.pratyahara.ui.components.Screen
import app.pratyahara.ui.components.SecondaryButton
import app.pratyahara.ui.components.SoftCard
import app.pratyahara.ui.components.Stat
import app.pratyahara.ui.components.formatMinutes
import app.pratyahara.ui.components.formatWait
import app.pratyahara.ui.components.rememberNow
import kotlinx.coroutines.launch
import java.time.LocalTime

@Composable
fun HomeScreen(go: (String) -> Unit) {
    val context = LocalContext.current
    val engine = context.engine
    val scope = rememberCoroutineScope()
    val data by engine.store.data.collectAsStateWithLifecycle()
    val now = rememberNow(30_000)
    var serviceOn by remember { mutableStateOf(ServiceStatus.isEnabled(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        serviceOn = ServiceStatus.isEnabled(context)
        scope.launch { engine.applyDuePending() }
    }

    @Suppress("UNUSED_VARIABLE") val tick = now
    val state = engine.lockState(data)
    val today = engine.today()
    val usage = data.day(today)
    val streaks = engine.streaks(data)
    val todaysTask = engine.todaysTask(data)
    val awaiting = engine.taskAwaitingCheckIn(data)
    val gate = engine.taskGate(data)
    val hour = LocalTime.now().hour

    Screen("Pratyahara") {
        // 1. A note from your past self comes before anything else in the morning.
        if (todaysTask != null && hour < 14) {
            SoftCard(container = MaterialTheme.colorScheme.tertiaryContainer) {
                Text("A note from your past self", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text("“${todaysTask.text}”", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }

        if (data.blockingEnabled && !serviceOn) {
            SoftCard(container = MaterialTheme.colorScheme.secondaryContainer) {
                Text("Blocking is paused", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    "Pratyahara's accessibility permission is off, so Reels and Shorts aren't being limited right now. It only takes a moment to turn back on.",
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                PrimaryButton("Turn it back on", { context.startActivity(ServiceStatus.settingsIntent()) })
            }
        }

        StatusCard(state, data.budgetMinutes, usage.totalSeconds, usage.unlocks, data.squats, go)

        if (gate != TaskGate.OPEN || awaiting != null || hour >= 18) {
            SoftCard {
                val title = when {
                    gate == TaskGate.MISSED_CHECKIN -> "Yesterday's check-in is waiting"
                    awaiting != null -> "Did you do it?"
                    gate == TaskGate.NEEDS_NEW_TASK -> "Write your next task"
                    else -> "Tomorrow's task"
                }
                Text(title, style = MaterialTheme.typography.titleLarge)
                when {
                    awaiting != null -> Muted("“${awaiting.text}”")
                    gate == TaskGate.NEEDS_NEW_TASK -> Muted("One clear thing you'll do. Reels and Shorts open again once it's written.")
                    else -> Muted("Set one thing for tomorrow before bed.")
                }
                if (!(gate == TaskGate.OPEN && awaiting == null && data.tasks.any { it.day > today.toString() })) {
                    SecondaryButton(if (awaiting != null) "Answer" else "Write it", { go(Routes.TASK) })
                } else {
                    Muted("Done for tonight. Sleep well.")
                }
            }
        }

        SoftCard {
            Text("Your wins", style = MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat(formatMinutes(engine.weekSavedMinutes(data) * 60L), "saved this week")
                Stat("${streaks.honest}", "honest days")
                Stat("${streaks.done}", "tasks done in a row")
            }
        }

        SoftCard {
            Text("Today", style = MaterialTheme.typography.titleLarge)
            val apps = data.monitored.map { DetectionRules.appName(it) to it }.distinctBy { it.first }
            apps.forEach { (name, _) ->
                val secs = DetectionRules.all.filter { it.appName == name }.sumOf { usage.secondsByApp[it.packageName] ?: 0L }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name)
                    Text(formatMinutes(secs), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Muted("${usage.blocks} pauses · ${usage.unlocks} unlocks")
        }

        if (data.pending.isNotEmpty()) {
            SoftCard {
                Text("Waiting to take effect", style = MaterialTheme.typography.titleLarge)
                Muted("Changes that loosen your rules wait a while, so they're decided calmly. You can cancel any of them.")
                data.pendingModels().forEach { c ->
                    val what = when (c.type) {
                        ChangeType.RAISE_BUDGET -> "Daily limit to ${c.value} min"
                        ChangeType.REMOVE_APP -> "Stop limiting ${DetectionRules.appName(c.value)}"
                        ChangeType.DISABLE_BLOCKING -> "Switch blocking off"
                        ChangeType.LOWER_SQUATS -> "Squats down to ${c.value}"
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Muted("$what · in ${formatWait(c.remainingMillis(engine.stamp()))}")
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { scope.launch { engine.cancelPending(c.id) } }) { Text("Cancel") }
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("Today's summary", { go(Routes.SUMMARY) }, Modifier.weight(1f))
            SecondaryButton("Settings", { go(Routes.SETTINGS) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatusCard(state: LockState, budgetMinutes: Int, usedSeconds: Long, unlocks: Int, squats: Int, go: (String) -> Unit) {
    SoftCard(container = MaterialTheme.colorScheme.primaryContainer) {
        val on = MaterialTheme.colorScheme.onPrimaryContainer
        when (state) {
            is LockState.Allowed -> {
                Text("${(state.remainingSeconds + 59) / 60} minutes left today", style = MaterialTheme.typography.headlineSmall, color = on)
                val allowance = (budgetMinutes + unlocks * UnlockRules.UNLOCK_MINUTES) * 60f
                LinearProgressIndicator(progress = { (usedSeconds / allowance).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text("${formatMinutes(usedSeconds)} of $budgetMinutes min used on Reels and Shorts.", color = on)
            }
            is LockState.BudgetLocked -> {
                Text("You've hit your $budgetMinutes minutes for today.", style = MaterialTheme.typography.headlineSmall, color = on)
                Text("Let's pick this up tomorrow. Everything else in your apps is still open.", color = on)
                if (state.unlocksLeft > 0) {
                    SecondaryButton("Earn ${UnlockRules.UNLOCK_MINUTES} minutes with $squats squats", { go(Routes.UNLOCK) })
                }
            }
            is LockState.TaskLocked -> {
                Text("Reels and Shorts are waiting on one task", style = MaterialTheme.typography.headlineSmall, color = on)
                Text(
                    if (state.gate == TaskGate.MISSED_CHECKIN) "Answer yesterday's check-in, then write one task. That's all it takes."
                    else "Write one clear task for next. As soon as it's saved, they're open again.",
                    color = on,
                )
            }
            is LockState.Cooldown -> {
                Text("A short pause", style = MaterialTheme.typography.headlineSmall, color = on)
                Text("Reels and Shorts open in a few seconds.", color = on)
            }
            LockState.Disabled -> {
                Text("Blocking is off", style = MaterialTheme.typography.headlineSmall, color = on)
                Text("Time is still counted. Turn it back on in Settings whenever you're ready; that applies instantly.", color = on)
            }
        }
    }
}
