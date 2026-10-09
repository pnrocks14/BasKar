package app.pratyahara.ui.cooldown

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pratyahara.core.lock.UnlockRules
import app.pratyahara.engine
import app.pratyahara.ui.components.CardTitle
import app.pratyahara.ui.components.Ic
import app.pratyahara.ui.components.IconBadge
import app.pratyahara.ui.components.Muted
import app.pratyahara.ui.components.PrimaryButton
import app.pratyahara.ui.components.Screen
import app.pratyahara.ui.components.SoftCard
import app.pratyahara.ui.components.formatMinutes
import app.pratyahara.ui.components.rememberNow
import kotlinx.coroutines.delay

/**
 * Deliberately plain: a pause, one stat and one nudge. When the pause ends it hands over the minutes with a
 * slightly sad goodbye and gets out of the way, back to whatever app you came from.
 */
@Composable
fun CooldownScreen(onDone: () -> Unit) {
    val engine = LocalContext.current.engine
    val activity = LocalActivity.current
    val data by engine.store.data.collectAsStateWithLifecycle()
    val now = rememberNow(250)
    val secondsLeft = ((data.cooldownUntil - now + 999) / 1000).coerceAtLeast(0)
    val nudge = remember { Nudges.forToday(System.currentTimeMillis() / 60_000) }
    val farewell = remember { Nudges.farewell(System.currentTimeMillis() / 1_000) }

    if (secondsLeft == 0L) {
        LaunchedEffect(Unit) {
            delay(FAREWELL_MS)
            onDone()
            // Back to the app you were in, the way the home button would.
            activity?.moveTaskToBack(true)
        }
        Screen("Here are your ${UnlockRules.UNLOCK_MINUTES} minutes") {
            IconBadge(Ic.Hourglass, container = MaterialTheme.colorScheme.tertiaryContainer, tint = MaterialTheme.colorScheme.onTertiaryContainer, size = 64.dp)
            Text(farewell, style = MaterialTheme.typography.headlineSmall)
            Muted("This screen closes by itself in a moment.")
            PrimaryButton("Go", {
                onDone()
                activity?.moveTaskToBack(true)
            })
        }
        return
    }

    Screen("Nicely done", subtitle = "Squats done. Now a short pause.") {
        Text("$secondsLeft", style = MaterialTheme.typography.displayLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Muted("Breathe in, breathe out.")
        SoftCard {
            CardTitle("Saved this week: ${formatMinutes(engine.weekSavedMinutes(data) * 60L)}", Ic.Hourglass)
        }
        SoftCard(container = MaterialTheme.colorScheme.primaryContainer) {
            Text("Before you go back", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(nudge, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

private const val FAREWELL_MS = 3_500L
