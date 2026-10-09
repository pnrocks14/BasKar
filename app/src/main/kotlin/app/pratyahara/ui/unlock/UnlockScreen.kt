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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import app.pratyahara.ui.components.Ic
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
                feedback.say(if (i == from && placement == PhonePlacement.POCKET) "Pocket it and stand tall. $i" else "$i")
                delay(1_000)
            }
            feedback.say("Go")
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

    Screen("Earn ${UnlockRules.UNLOCK_MINUTES} minutes", onBack, subtitle = "Do $target squats and Reels are back for ${UnlockRules.UNLOCK_MINUTES} minutes. Your phone counts them.") {
        if (!LockPolicy.canEarnUnlock(state) && !granted) {
            SoftCard {
                Text(
                    when (state) {
                        is LockState.BudgetLocked -> "You've used today's unlocks. Fresh start tomorrow."
                        is LockState.TaskLocked -> "Squats can't open this one, but writing your task will."
                        is LockState.FocusLocked -> "Focus hours are on. Squats can't open Reels until they end."
                        else -> "You still have time left today, so there's nothing to unlock."
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            return@Screen
        }
        if (!source.available) {
            SoftCard { Text("This phone has no motion sensor, so squats can't be counted.", style = MaterialTheme.typography.titleLarge) }
            return@Screen
        }

        when (phase) {
            Phase.SETUP -> {
                Text("Where will your phone be?", style = MaterialTheme.typography.titleLarge)
                PlacementOption(
                    selected = placement == PhonePlacement.POCKET,
                    icon = Icons.Rounded.Person,
                    title = "In my pocket (best)",
                    body = "Front trouser pocket, any way up. Your thigh tilts on every squat, which is easy to count. No need to look at the screen.",
                ) { scope.launch { engine.updateSettings { it.copy(squatPlacement = PhonePlacement.POCKET.name) } } }
                PlacementOption(
                    selected = placement == PhonePlacement.CHEST,
                    icon = Ic.Phone,
                    title = "Holding it at my chest",
                    body = "Both hands, screen facing you, elbows tucked in. Keep the phone still in your hands and let your legs do the moving.",
                ) { scope.launch { engine.updateSettings { it.copy(squatPlacement = PhonePlacement.CHEST.name) } } }
                SoftCard {
                    Text("How to squat so it counts", style = MaterialTheme.typography.titleMedium)
                    Muted("1. Feet shoulder-width apart, stand tall for a second\n2. Sit back and down until your thighs are about level\n3. Stand all the way up\n4. About 2 seconds a rep. Slow beats fast")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Count out loud", style = MaterialTheme.typography.titleMedium)
                            Muted("Hear every rep, plus a buzz")
                        }
                        Switch(checked = data.voiceCount, onCheckedChange = { on -> scope.launch { engine.updateSettings { it.copy(voiceCount = on) } } })
                    }
                }
                AccentButton("I'm ready", { phase = Phase.COUNTDOWN })
            }
            Phase.COUNTDOWN -> {
                Text("$countdown", style = MaterialTheme.typography.displayLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text(
                    if (placement == PhonePlacement.POCKET) "Pocket it and stand tall" else "Hold it at your chest and stand tall",
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
                    Text("Depth", style = MaterialTheme.typography.titleMedium)
                    Meter(progress.depth)
                    Muted(if (progress.depth >= 0.99f) "That's deep enough. Now stand up." else "Go down until this fills up.")
                }
                if (progress.shaking) {
                    SoftCard(container = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text("That looks like shaking. Only steady squats count.", color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
                if (progress.reps == 0 && secondsWithoutReps >= 12) {
                    SoftCard(container = MaterialTheme.colorScheme.secondaryContainer) {
                        Text("Not counting?", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(
                            if (placement == PhonePlacement.POCKET) "Make sure it's in a front pocket that sits on your thigh, not a jacket or back pocket. Go a bit lower: the depth bar has to fill up."
                            else "Keep the phone tight to your chest and squat slower and deeper. Pocket mode is much more reliable if you can use it.",
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        val other = if (placement == PhonePlacement.POCKET) PhonePlacement.CHEST else PhonePlacement.POCKET
                        SecondaryButton(if (other == PhonePlacement.POCKET) "Switch to pocket mode" else "Switch to chest mode", {
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
private fun PlacementOption(selected: Boolean, icon: ImageVector, title: String, body: String, onSelect: () -> Unit) {
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
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
