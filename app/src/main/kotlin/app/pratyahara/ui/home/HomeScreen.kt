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
import app.pratyahara.ui.components.AccentButton
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
        in 4..11 -> "gm ☀️"
        in 12..16 -> "hey 👋"
        in 17..21 -> "evening 🌆"
        else -> "up late? 🌙"
    }

    Screen(greeting, subtitle = today.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault())).lowercase()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("🔥 ${streaks.done} day streak")
            Pill("✅ ${streaks.honest} honest")
        }

        if (data.blockingEnabled && !serviceOn) {
            SoftCard(container = MaterialTheme.colorScheme.tertiaryContainer) {
                Text("blocking's paused rn 😬", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(
                    "the accessibility switch is off, so reels and shorts aren't being limited. takes 5 seconds to fix.",
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                PrimaryButton("turn it back on", { context.startActivity(ServiceStatus.settingsIntent()) })
            }
        }

        // A note from your past self comes first in the morning.
        if (todaysTask != null && hour < 14) {
            SoftCard(container = MaterialTheme.colorScheme.secondaryContainer) {
                Text("📝 note from past you", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text("“${todaysTask.text}”", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }

        HeroCard(state, data, usage.totalSeconds, usage.unlocks, go)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("👀", "${usage.visits}", "times you opened reels", Modifier.weight(1f))
            StatTile("🛡️", "${usage.blocks}", "times we stopped you", Modifier.weight(1f))
            StatTile("🏋️", "${usage.unlocks}/${UnlockRules.MAX_UNLOCKS_PER_DAY}", "squat unlocks", Modifier.weight(1f))
        }

        WeekCard(engine.recentDays(data).map { (day, u) -> day to (u.totalSeconds / 60).toInt() }, data.budgetMinutes, engine.weekSavedMinutes(data))

        TaskCard(gate, awaiting?.text, data, today.toString(), hour, go)

        AppsCard(data, usage.secondsByApp)

        if (data.pending.isNotEmpty()) {
            SoftCard {
                Text("⏳ waiting to kick in", style = MaterialTheme.typography.titleLarge)
                Muted("loosening a rule takes a while, so you decide with a clear head. cancel any of these anytime.")
                data.pendingModels().forEach { c ->
                    val what = when (c.type) {
                        ChangeType.RAISE_BUDGET -> "limit → ${c.value} min"
                        ChangeType.REMOVE_APP -> "stop limiting ${DetectionRules.appName(c.value)}"
                        ChangeType.DISABLE_BLOCKING -> "blocking off"
                        ChangeType.LOWER_SQUATS -> "squats → ${c.value}"
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Muted("$what · in ${formatWait(c.remainingMillis(engine.stamp()))}", Modifier.weight(1f))
                        TextButton(onClick = { scope.launch { engine.cancelPending(c.id) } }) { Text("cancel") }
                    }
                }
            }
        }

        val quote = remember(today) { Quotes.ofDay(today.toEpochDay()) }
        SoftCard(container = MaterialTheme.colorScheme.secondaryContainer) {
            Text("💭 today's thought", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(quote.text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text("— ${quote.source}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("📊 recap", { go(Routes.SUMMARY) }, Modifier.weight(1f))
            SecondaryButton("⚙️ settings", { go(Routes.SETTINGS) }, Modifier.weight(1f))
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
                "${formatMinutes(usedSeconds)} used of ${data.budgetMinutes} min" + if (unlocks > 0) " (+${unlocks * UnlockRules.UNLOCK_MINUTES} earned)" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val (headline, body) = when (state) {
                is LockState.Allowed -> when {
                    used < 0.5f -> "you're chilling ✨" to "reels and shorts are open. we'll check in while you scroll."
                    used < 0.85f -> "pacing yourself, love that 🫶" to "past the halfway mark. you've got this."
                    else -> "almost out. wrap it up ⏳" to "a couple of minutes left for today."
                }
                is LockState.BudgetLocked -> "reels are done for today 🔒" to "feed, DMs and profile still work. see you tomorrow."
                is LockState.TaskLocked -> "reels are waiting on one task ✍️" to
                    if (state.gate == TaskGate.MISSED_CHECKIN) "answer yesterday's check-in, then write one task. that's it."
                    else "write one clear task for next. the second it's saved, they're back."
                is LockState.Cooldown -> "breathe… 🫧" to "reels open in a few seconds."
                LockState.Disabled -> "blocking's off 💤" to "time still counts. flip it back on in settings, it applies instantly."
            }
            Text(headline, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Muted(body)
            when {
                state is LockState.BudgetLocked && state.unlocksLeft > 0 ->
                    AccentButton("earn ${UnlockRules.UNLOCK_MINUTES} min · ${data.squats} squats 🏋️", { go(Routes.UNLOCK) })
                state is LockState.TaskLocked -> AccentButton("write my task ✍️", { go(Routes.TASK) })
            }
        }
    }
}

@Composable
private fun WeekCard(days: List<Pair<java.time.LocalDate, Int>>, limit: Int, savedMinutes: Int) {
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("this week", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Pill("⏳ ${formatMinutes(savedMinutes * 60L)} saved", container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        val minutes = days.map { it.second }
        val active = minutes.filter { it > 0 }
        Muted(if (active.isEmpty()) "nothing yet. a clean week so far 👌" else "avg ${active.sum() / active.size} min a day · dashed line = your $limit min limit")
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
            gate == TaskGate.MISSED_CHECKIN -> "yesterday's check-in is waiting 👀"
            awaitingText != null -> "so… did you do it?"
            gate == TaskGate.NEEDS_NEW_TASK -> "write your next task ✍️"
            else -> "tomorrow's one thing"
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
        when {
            awaitingText != null -> Muted("“$awaitingText”")
            gate == TaskGate.NEEDS_NEW_TASK -> Muted("one clear thing you'll do. reels and shorts open back up once it's written.")
            else -> Muted("set one thing for tomorrow before bed. future you will thank you.")
        }
        if (!(gate == TaskGate.OPEN && awaitingText == null && data.tasks.any { it.day > today })) {
            PrimaryButton(if (awaitingText != null) "answer" else "write it", { go(Routes.TASK) })
        } else {
            Muted("done for tonight. sleep well 😴")
        }
    }
}

@Composable
private fun AppsCard(data: AppData, secondsByApp: Map<String, Long>) {
    val apps = data.monitored.map { DetectionRules.appName(it) }.distinct()
    if (apps.isEmpty()) return
    SoftCard {
        Text("today by app", style = MaterialTheme.typography.titleLarge)
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

