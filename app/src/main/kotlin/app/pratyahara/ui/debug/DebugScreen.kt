package app.pratyahara.ui.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pratyahara.detection.ReelsAccessibilityService
import app.pratyahara.ui.components.CardTitle
import app.pratyahara.ui.components.Ic
import app.pratyahara.ui.components.Muted
import app.pratyahara.ui.components.Screen
import app.pratyahara.ui.components.SecondaryButton
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
        CardTitle(if (running) "Service is running" else "Service is not running", Ic.Shield)
        val r = result
        if (r == null) Muted("No check yet. Open Reels or Shorts in a limited app, then come back.")
        else {
            Text("Last score: ${r.score} of ${r.threshold} needed, so it looked like ${if (r.isShortForm) "Reels or Shorts" else "another screen"}.", style = MaterialTheme.typography.bodyLarge)
            Muted("Matched: ${r.matched.ifEmpty { listOf("nothing") }.joinToString()}")
        }
    }
}

/**
 * Lets the user send a missed screen's layout to the developer. Only view types, IDs, positions and known
 * button words are kept; every other piece of text is replaced before it's stored, and only in memory.
 */
@Composable
private fun LayoutCapture() {
    val context = LocalContext.current
    val recording by ReelsAccessibilityService.recordLayout.collectAsStateWithLifecycle()
    val layout by ReelsAccessibilityService.lastLayout.collectAsStateWithLifecycle()
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CardTitle("Report a missed screen", Ic.Eye)
                Muted("Switch this on, open the screen Pratyahara missed (for example a reel from Explore), then come back here and share it.")
            }
            Switch(checked = recording, onCheckedChange = { ReelsAccessibilityService.recordLayout.value = it })
        }
        Muted("Only the screen's structure is kept: view types, IDs, positions and button words like \"Like\". Names, captions and messages are replaced. Nothing is sent unless you share it.")
        val l = layout
        if (l != null) {
            Muted("Captured ${l.lineSequence().count() - 1} items from ${l.substringBefore(' ')}.")
            SecondaryButton("Copy layout", {
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Pratyahara layout", l))
            })
            SecondaryButton("Share layout", {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, l)
                context.startActivity(Intent.createChooser(send, "Share layout").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
        }
    }
}

@Composable
fun DebugScreen(onBack: () -> Unit) {
    Screen("Detection check", onBack) {
        Muted("If Pratyahara misses Reels or pauses the wrong screen, this shows what it saw.")
        DetectionCheck()
        LayoutCapture()
    }
}
