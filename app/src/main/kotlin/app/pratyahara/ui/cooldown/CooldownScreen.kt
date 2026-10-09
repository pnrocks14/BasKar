package app.pratyahara.ui.cooldown

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pratyahara.core.lock.UnlockRules
import app.pratyahara.engine
import app.pratyahara.ui.components.CardTitle
import app.pratyahara.ui.components.Ic
import app.pratyahara.ui.components.Muted
import app.pratyahara.ui.components.PrimaryButton
import app.pratyahara.ui.components.Screen
import app.pratyahara.ui.components.SoftCard
import app.pratyahara.ui.components.formatMinutes
import app.pratyahara.ui.components.rememberNow

/** Deliberately plain: a pause, one stat and one nudge. Nothing here is meant to hold your attention. */
@Composable
fun CooldownScreen(onDone: () -> Unit) {
    val engine = LocalContext.current.engine
    val data by engine.store.data.collectAsStateWithLifecycle()
    val now = rememberNow(250)
    val secondsLeft = ((data.cooldownUntil - now + 999) / 1000).coerceAtLeast(0)
    val nudge = remember { Nudges.forToday(System.currentTimeMillis() / 60_000) }

    Screen("Nicely done", subtitle = "Squats done. Now a short pause.") {
        Text(
            if (secondsLeft > 0) "$secondsLeft" else "0",
            style = MaterialTheme.typography.displayLarge,
                        textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Muted(
            if (secondsLeft > 0) "Breathe in, breathe out."
            else "You've got ${UnlockRules.UNLOCK_MINUTES} more minutes. Spend them on purpose."
        )
        SoftCard {
            CardTitle("Saved this week: ${formatMinutes(engine.weekSavedMinutes(data) * 60L)}", Ic.Hourglass)
        }
        SoftCard(container = MaterialTheme.colorScheme.primaryContainer) {
            Text("Before you go back", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(nudge, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        PrimaryButton("Done", onDone, enabled = secondsLeft == 0L)
    }
}
