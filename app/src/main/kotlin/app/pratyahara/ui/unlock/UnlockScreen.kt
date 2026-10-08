package app.pratyahara.ui.unlock

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pratyahara.core.lock.LockPolicy
import app.pratyahara.core.lock.LockState
import app.pratyahara.core.lock.UnlockRules
import app.pratyahara.engine
import app.pratyahara.sensors.RepProgress
import app.pratyahara.sensors.SquatSensorSource
import app.pratyahara.ui.components.Muted
import app.pratyahara.ui.components.PrimaryButton
import app.pratyahara.ui.components.Screen
import app.pratyahara.ui.components.SoftCard

@Composable
fun UnlockScreen(onBack: () -> Unit, onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val engine = context.engine
    val data by engine.store.data.collectAsStateWithLifecycle()
    val source = remember { SquatSensorSource(context) }
    var started by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(RepProgress(0, false)) }
    var granted by remember { mutableStateOf(false) }
    val target = data.squats
    val state = engine.lockState(data)

    // Keep the screen on while counting.
    val activity = LocalActivity.current
    DisposableEffect(started) {
        if (started) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    if (started) {
        LaunchedEffect(Unit) {
            source.reps().collect { p ->
                progress = p
                if (p.reps >= target && !granted) {
                    granted = true
                    if (engine.grantUnlock()) onUnlocked()
                }
            }
        }
    }

    Screen("Move first", onBack) {
        if (!LockPolicy.canEarnUnlock(state) && !granted) {
            SoftCard {
                Text(
                    when {
                        state is LockState.BudgetLocked -> "You've used today's unlocks. Tomorrow is a fresh start."
                        state is LockState.TaskLocked -> "Squats can't open this one. Writing your task will."
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
        SoftCard {
            Text("$target squats for ${UnlockRules.UNLOCK_MINUTES} minutes", style = MaterialTheme.typography.titleLarge)
            Muted("Hold your phone against your chest with both hands. Slow, full squats: down until your thighs are about level, then all the way up.")
        }
        if (!started) {
            PrimaryButton("I'm ready", { started = true })
        } else {
            Text(
                "${progress.reps.coerceAtMost(target)}",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("of $target", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            LinearProgressIndicator(progress = { (progress.reps.toFloat() / target).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            if (progress.shaking) {
                Muted("That looks like shaking. Only steady squats count.")
            }
        }
    }
}
