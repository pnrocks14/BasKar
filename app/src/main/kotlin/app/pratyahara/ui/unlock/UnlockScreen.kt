package app.pratyahara.ui.unlock

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pratyahara.core.lock.LockPolicy
import app.pratyahara.core.lock.LockState
import app.pratyahara.core.lock.UnlockRules
import app.pratyahara.core.reps.PhonePlacement
import app.pratyahara.core.reps.SquatCounter
import app.pratyahara.engine
import app.pratyahara.sensors.RepFeedback
import app.pratyahara.sensors.RepProgress
import app.pratyahara.sensors.SquatSensorSource
import app.pratyahara.ui.components.AccentButton
import app.pratyahara.ui.components.Meter
import app.pratyahara.ui.components.Muted
import app.pratyahara.ui.components.Screen
import app.pratyahara.ui.components.SecondaryButton
import app.pratyahara.ui.components.SoftCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Phase { SETUP, COUNTDOWN, COUNTING }

@Composable
fun UnlockScreen(onBack: () -> Unit, onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val engine = context.engine
    val scope = rememberCoroutineScope()
    val data by engine.store.data.collectAsStateWithLifecycle()
    val source = remember { SquatSensorSource(context) }
    val placement = runCatching { PhonePlacement.valueOf(data.squatPlacement) }.getOrDefault(PhonePlacement.POCKET)
    var phase by remember { mutableStateOf(Phase.SETUP) }
    var countdown by remember { mutableIntStateOf(0) }
    var progress by remember { mutableStateOf(RepProgress(0, false)) }
    var granted by remember { mutableStateOf(false) }
    var secondsWithoutReps by remember { mutableIntStateOf(0) }
    val target = data.squats
    val state = engine.lockState(data)
    val feedback = remember(data.voiceCount) { RepFeedback(context, speak = data.voiceCount) }
    DisposableEffect(feedback) { onDispose { feedback.release() } }

    // Keep the screen on from the countdown until the set is done.
    val activity = LocalActivity.current
    DisposableEffect(phase) {
        if (phase != Phase.SETUP) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    if (phase == Phase.COUNTDOWN) {
        LaunchedEffect(Unit) {
            // Pocket mode needs a moment to put the phone away and stand still.
            val from = if (placement == PhonePlacement.POCKET) 5 else 3
            for (i in from downTo 1) {
                countdown = i
                feedback.say(if (i == from && placement == PhonePlacement.POCKET) "pocket it and stand tall. $i" else "$i")
                delay(1_000)
            }
            feedback.say("go")
            phase = Phase.COUNTING
        }
    }
    if (phase == Phase.COUNTING) {
        LaunchedEffect(placement) {
            secondsWithoutReps = 0
            progress = RepProgress(0, false)
            var last = 0
            source.reps(SquatCounter.forPlacement(placement)).collect { p ->
                progress = p
                if (p.reps > last) {
                    last = p.reps
                    secondsWithoutReps = 0
                    if (p.reps <= target) feedback.rep(p.reps, target)
                }
                if (p.reps >= target && !granted) {
                    granted = true
                    feedback.done()
                    if (engine.grantUnlock()) onUnlocked()
                }
            }
        }
        LaunchedEffect(placement, progress.reps == 0) {
            while (true) {
                delay(1_000)
                secondsWithoutReps++
            }
        }
    }

    Screen("earn ${UnlockRules.UNLOCK_MINUTES} min 🏋️", onBack, subtitle = "$target squats and reels are back for ${UnlockRules.UNLOCK_MINUTES} min. your phone counts them.") {
        if (!LockPolicy.canEarnUnlock(state) && !granted) {
            SoftCard {
                Text(
                    when (state) {
                        is LockState.BudgetLocked -> "you've used today's unlocks. fresh start tomorrow 🌅"
                        is LockState.TaskLocked -> "squats can't open this one. writing your task will ✍️"
                        else -> "you still have time left today, nothing to unlock rn 👍"
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            return@Screen
        }
        if (!source.available) {
            SoftCard { Text("this phone has no motion sensor, so squats can't be counted 😕", style = MaterialTheme.typography.titleLarge) }
            return@Screen
        }

        when (phase) {
            Phase.SETUP -> {
                Text("where's your phone going?", style = MaterialTheme.typography.titleLarge)
                PlacementOption(
                    selected = placement == PhonePlacement.POCKET,
                    emoji = "👖",
                    title = "in my pocket (best)",
                    body = "front trouser pocket, any way up. your thigh tilts on every squat, which is super easy to count. no need to look at the screen.",
                ) { scope.launch { engine.updateSettings { it.copy(squatPlacement = PhonePlacement.POCKET.name) } } }
                PlacementOption(
                    selected = placement == PhonePlacement.CHEST,
                    emoji = "🤲",
                    title = "holding it at my chest",
                    body = "both hands, screen facing you, elbows tucked in. keep the phone still in your hands and let your legs do the moving.",
                ) { scope.launch { engine.updateSettings { it.copy(squatPlacement = PhonePlacement.CHEST.name) } } }
                SoftCard {
                    Text("how to squat so it counts", style = MaterialTheme.typography.titleMedium)
                    Muted("1. feet shoulder-width, stand tall for a sec\n2. sit back and down until your thighs are about level\n3. stand all the way up\n4. about 2 seconds a rep. slow > fast")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("count out loud 🔊", style = MaterialTheme.typography.titleMedium)
                            Muted("hear every rep, plus a buzz")
                        }
                        Switch(checked = data.voiceCount, onCheckedChange = { on -> scope.launch { engine.updateSettings { it.copy(voiceCount = on) } } })
                    }
                }
                AccentButton("i'm ready", { phase = Phase.COUNTDOWN })
            }
            Phase.COUNTDOWN -> {
                Text("$countdown", style = MaterialTheme.typography.displayLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text(
                    if (placement == PhonePlacement.POCKET) "pocket it and stand tall 👖" else "hold it at your chest and stand tall 🤲",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Phase.COUNTING -> {
                Text(
                    "${progress.reps.coerceAtMost(target)}",
                    style = MaterialTheme.typography.displayLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("of $target", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                SoftCard {
                    Text("depth", style = MaterialTheme.typography.titleMedium)
                    Meter(progress.depth)
                    Muted(if (progress.depth >= 0.99f) "that's deep enough, now stand up ⬆️" else "go down until this fills up ⬇️")
                }
                if (progress.shaking) {
                    SoftCard(container = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text("that looks like shaking 🫨 only steady squats count.", color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
                if (progress.reps == 0 && secondsWithoutReps >= 12) {
                    SoftCard(container = MaterialTheme.colorScheme.secondaryContainer) {
                        Text("not counting? 🤔", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(
                            if (placement == PhonePlacement.POCKET) "make sure it's in a front pocket that sits on your thigh, not a jacket or back pocket. go a bit lower: the depth bar has to fill up."
                            else "keep the phone tight to your chest and squat slower and deeper. pocket mode is way more reliable if you can use it.",
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        val other = if (placement == PhonePlacement.POCKET) PhonePlacement.CHEST else PhonePlacement.POCKET
                        SecondaryButton(if (other == PhonePlacement.POCKET) "switch to pocket mode 👖" else "switch to chest mode 🤲", {
                            scope.launch { engine.updateSettings { it.copy(squatPlacement = other.name) } }
                            phase = Phase.COUNTDOWN
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun PlacementOption(selected: Boolean, emoji: String, title: String, body: String, onSelect: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        ),
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(emoji, style = MaterialTheme.typography.headlineMedium)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
