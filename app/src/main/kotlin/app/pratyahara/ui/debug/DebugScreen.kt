package app.pratyahara.ui.debug

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pratyahara.detection.ReelsAccessibilityService
import app.pratyahara.ui.components.Muted
import app.pratyahara.ui.components.Screen
import app.pratyahara.ui.components.SoftCard

/**
 * Shows the live detection score so rules can be tuned on a real phone. It shows only the score and the
 * names of matched signals, never screen content. Open it, switch to Instagram, then come back.
 */
@Composable
fun DetectionCheck() {
    val running by ReelsAccessibilityService.isRunning.collectAsStateWithLifecycle()
    val result by ReelsAccessibilityService.lastResult.collectAsStateWithLifecycle()
    SoftCard {
        Text(if (running) "Service is running" else "Service is not running", style = MaterialTheme.typography.titleLarge)
        val r = result
        if (r == null) Muted("No evaluation yet. Open Reels or Shorts in a monitored app, then come back.")
        else {
            Text("Last score: ${r.score} of ${r.threshold} needed → ${if (r.isShortForm) "Reels/Shorts" else "other screen"}")
            Muted("Matched: ${r.matched.ifEmpty { listOf("nothing") }.joinToString()}")
        }
    }
}

@Composable
fun DebugScreen(onBack: () -> Unit) {
    Screen("detection check 🔍", onBack) {
        Muted("If Pratyahara misses Reels or pauses the wrong screen, this shows what it saw. Only scores are shown, never what's on screen.")
        DetectionCheck()
    }
}
