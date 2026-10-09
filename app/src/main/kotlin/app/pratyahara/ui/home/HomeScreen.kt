package app.pratyahara.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pratyahara.core.budget.ChangeType
import app.pratyahara.core.buddy.Quotes
import app.pratyahara.core.lock.LockState
import app.pratyahara.core.lock.UnlockRules
import app.pratyahara.core.tasks.TaskGate
import app.pratyahara.data.AppData
import app.pratyahara.detection.DetectionRules
import app.pratyahara.detection.ServiceStatus
import app.pratyahara.engine
import app.pratyahara.ui.Routes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import app.pratyahara.core.lock.FocusHours
import app.pratyahara.ui.components.AccentButton
import app.pratyahara.ui.components.CardTitle
import app.pratyahara.ui.components.Ic
import app.pratyahara.ui.components.Muted
import app.pratyahara.ui.components.Pill
import app.pratyahara.ui.components.PrimaryButton
import app.pratyahara.ui.components.Ring
import app.pratyahara.ui.components.SecondaryButton
import app.pratyahara.ui.components.SectionLabel
import app.pratyahara.ui.components.SoftCard
import app.pratyahara.ui.components.StatTile
import app.pratyahara.ui.components.WeekBars
import app.pratyahara.ui.components.formatMinutes
import app.pratyahara.ui.components.formatWait
import app.pratyahara.ui.components.rememberNow
import app.pratyahara.ui.components.Screen
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

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
    val greeting = when (hour) {
        in 4..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Up late?"
    }

    Screen(greeting, subtitle = today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("${streaks.done}-day streak", icon = Ic.Flame)
            Pill("${streaks.honest} honest check-ins", icon = Icons.Rounded.CheckCircle)
        }

        if (data.blockingEnabled && !serviceOn) {
            SoftCard(container = MaterialTheme.colorScheme.tertiaryContainer) {
                CardTitle("Blocking is paused", Icons.Rounded.Warning, MaterialTheme.colorScheme.onTertiaryContainer)
                Text(
                    "The accessibility switch is off, so Reels and Shorts aren't being limited. It takes a few seconds to fix.",
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                PrimaryButton("Turn it back on", { context.startActivity(ServiceStatus.settingsIntent()) })
            }
        }

        // A note from your past self comes first in the morning.
        if (todaysTask != null && hour < 14) {
            SoftCard(container = MaterialTheme.colorScheme.secondaryContainer) {
                CardTitle("A note from past you", Ic.Edit, MaterialTheme.colorScheme.onSecondaryContainer)
                Text("“${todaysTask.text}”", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }

        HeroCard(state, data, usage.totalSeconds, usage.unlocks, go)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(Ic.Eye, "${usage.visits}", "Times opened", Modifier.weight(1f))
            StatTile(Ic.Shield, "${usage.blocks}", "Times paused", Modifier.weight(1f))
            StatTile(Ic.Fitness, "${usage.unlocks}/${UnlockRules.MAX_UNLOCKS_PER_DAY}", "Squat unlocks", Modifier.weight(1f))
        }

        WeekCard(engine.recentDays(data).map { (day, u) -> day to (u.totalSeconds / 60).toInt() }, data.budgetMinutes, engine.weekSavedMinutes(data))

        TaskCard(gate, awaiting?.text, data, today.toString(), hour, go)

        AppsCard(data, usage.secondsByApp)

        if (data.pending.isNotEmpty()) {
            SoftCard {
                CardTitle("Waiting to take effect", Ic.Hourglass)
                Muted("Loosening a rule takes a while, so you decide with a clear head. You can cancel any of these.")
                data.pendingModels().forEach { c ->
                    val what = when (c.type) {
                        ChangeType.RAISE_BUDGET -> "Daily limit to ${c.value} min"
                        ChangeType.REMOVE_APP -> "Stop limiting ${DetectionRules.appName(c.value)}"
                        ChangeType.DISABLE_BLOCKING -> "Turn blocking off"
                        ChangeType.LOWER_SQUATS -> "Squats to ${c.value}"
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Muted("$what · in ${formatWait(c.remainingMillis(engine.stamp()))}", Modifier.weight(1f))
                        TextButton(onClick = { scope.launch { engine.cancelPending(c.id) } }) { Text("Cancel") }
                    }
                }
            }
        }

        val quote = remember(today) { Quotes.ofDay(today.toEpochDay()) }
        SoftCard(container = MaterialTheme.colorScheme.secondaryContainer) {
            CardTitle("Today's thought", Ic.Quote, MaterialTheme.colorScheme.onSecondaryContainer)
            Text(quote.text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text("— ${quote.source}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("Recap", { go(Routes.SUMMARY) }, Modifier.weight(1f))
            SecondaryButton("Settings", { go(Routes.SETTINGS) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroCard(state: LockState, data: AppData, usedSeconds: Long, unlocks: Int, go: (String) -> Unit) {
    val allowance = (data.budgetMinutes + unlocks * UnlockRules.UNLOCK_MINUTES) * 60L
    val used = (usedSeconds.toFloat() / allowance.coerceAtLeast(1)).coerceIn(0f, 1f)
    val minutesLeft = when (state) {
        is LockState.Allowed -> (state.remainingSeconds + 59) / 60
        else -> 0L
    }
    val ringColor = if (used >= 0.85f) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primaryContainer
    SoftCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Ring(progress = used, color = ringColor) {
                Text(if (state is LockState.Allowed || state is LockState.BudgetLocked) "$minutesLeft" else "—", style = MaterialTheme.typography.displayLarge)
                Text("min left today", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "${formatMinutes(usedSeconds)} used of ${data.budgetMinutes} min" + if (unlocks > 0) " (+${unlocks * UnlockRules.UNLOCK_MINUTES} min earned)" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val (headline, body) = when (state) {
                is LockState.Allowed -> when {
                    used < 0.5f -> "You're doing great" to "Reels and Shorts are open. I'll check in while you scroll."
                    used < 0.85f -> "Nice pacing" to "You're past the halfway mark for today."
                    else -> "Almost out, time to wrap up" to "Just a few minutes left for today."
                }
                is LockState.BudgetLocked -> "Reels are done for today" to "Your feed, messages and profile still work. See you tomorrow."
                is LockState.TaskLocked -> "Reels are waiting on one task" to
                    if (state.gate == TaskGate.MISSED_CHECKIN) "Answer yesterday's check-in, then write one task. That's it."
                    else "Write one clear task for next. The moment it's saved, they're back."
                is LockState.Cooldown -> "Take a breath" to "Reels open in a few seconds."
                is LockState.FocusLocked -> "Focus hours are on" to "Reels and Shorts are closed until ${FocusHours.format(state.endMinute)}. Everything else works."
                LockState.Disabled -> "Blocking is off" to "Time still counts. Turn it back on in Settings and it applies right away."
            }
            Text(headline, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Muted(body)
            when {
                state is LockState.BudgetLocked && state.unlocksLeft > 0 ->
                    AccentButton("Earn ${UnlockRules.UNLOCK_MINUTES} min with ${data.squats} squats", { go(Routes.UNLOCK) })
                state is LockState.TaskLocked -> AccentButton("Write my task", { go(Routes.TASK) })
            }
        }
    }
}

@Composable
private fun WeekCard(days: List<Pair<java.time.LocalDate, Int>>, limit: Int, savedMinutes: Int) {
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("This week", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Pill("${formatMinutes(savedMinutes * 60L)} saved", icon = Ic.Hourglass, container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        val minutes = days.map { it.second }
        val active = minutes.filter { it > 0 }
        Muted(if (active.isEmpty()) "Nothing yet. A clean week so far." else "Average ${active.sum() / active.size} min a day. The dashed line is your $limit min limit.")
        WeekBars(
            values = minutes,
            labels = days.map { it.first.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()) },
            limit = limit,
        )
    }
}

@Composable
private fun TaskCard(gate: TaskGate, awaitingText: String?, data: AppData, today: String, hour: Int, go: (String) -> Unit) {
    if (gate == TaskGate.OPEN && awaitingText == null && hour < 18) return
    SoftCard {
        val title = when {
            gate == TaskGate.MISSED_CHECKIN -> "Yesterday's check-in is waiting"
            awaitingText != null -> "So, did you do it?"
            gate == TaskGate.NEEDS_NEW_TASK -> "Write your next task"
            else -> "Tomorrow's one thing"
        }
        CardTitle(title, Ic.Edit)
        when {
            awaitingText != null -> Muted("“$awaitingText”")
            gate == TaskGate.NEEDS_NEW_TASK -> Muted("One clear thing you'll do. Reels and Shorts open again once it's written.")
            else -> Muted("Set one thing for tomorrow before bed. Future you will thank you.")
        }
        if (!(gate == TaskGate.OPEN && awaitingText == null && data.tasks.any { it.day > today })) {
            PrimaryButton(if (awaitingText != null) "Answer" else "Write it", { go(Routes.TASK) })
        } else {
            Muted("All set for tonight. Sleep well.")
        }
    }
}

@Composable
private fun AppsCard(data: AppData, secondsByApp: Map<String, Long>) {
    val apps = data.monitored.map { DetectionRules.appName(it) }.distinct()
    if (apps.isEmpty()) return
    SoftCard {
        CardTitle("Today by app", Ic.Phone)
        val total = secondsByApp.values.sum().coerceAtLeast(1)
        apps.forEach { name ->
            val secs = DetectionRules.all.filter { it.appName == name }.sumOf { secondsByApp[it.packageName] ?: 0L }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text(formatMinutes(secs), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                app.pratyahara.ui.components.Meter(secs.toFloat() / total, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

