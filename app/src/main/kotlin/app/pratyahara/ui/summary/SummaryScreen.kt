package app.pratyahara.ui.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pratyahara.core.tasks.CheckIn
import app.pratyahara.detection.DetectionRules
import app.pratyahara.engine
import app.pratyahara.ui.components.CardTitle
import app.pratyahara.ui.components.Ic
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.pratyahara.ui.components.IconBadge
import app.pratyahara.ui.components.Muted
import app.pratyahara.ui.components.Screen
import app.pratyahara.ui.components.SoftCard
import app.pratyahara.ui.components.formatMinutes

@Composable
fun SummaryScreen(onBack: () -> Unit) {
    val engine = LocalContext.current.engine
    val data by engine.store.data.collectAsStateWithLifecycle()
    val today = engine.today()
    val usage = data.day(today)
    val task = engine.todaysTask(data)
    val streaks = engine.streaks(data)

    Screen("Today's recap", onBack) {
        SoftCard {
            CardTitle("Minutes on Reels and Shorts", Ic.Chart)
            if (usage.secondsByApp.isEmpty()) Muted("Zero. Nicely done.")
            usage.secondsByApp.entries.sortedByDescending { it.value }.forEach { (pkg, secs) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(DetectionRules.appName(pkg), style = MaterialTheme.typography.bodyLarge)
                    Text(formatMinutes(secs), style = MaterialTheme.typography.bodyLarge)
                }
            }
            Muted("Daily limit: ${data.budgetMinutes} min")
        }
        SoftCard {
            Text("How today went", style = MaterialTheme.typography.titleLarge)
            Story(
                Ic.Eye, plural(usage.visits, "trip", "trips") + " to Reels",
                when {
                    usage.visits == 0 -> "Didn't even peek. Legend."
                    usage.visits <= 3 -> "Pretty restrained, honestly."
                    usage.visits <= 9 -> "Your thumb knows the way by now."
                    else -> "That's a lot of trips. Notice what sends you there."
                },
            )
            Story(
                Ic.Shield, "I stepped in " + plural(usage.blocks, "time", "times"),
                if (usage.blocks == 0) "You stayed under your limit. Nothing to stop." else "That's ${plural(usage.blocks, "scroll", "scrolls")} that never happened.",
            )
            Story(
                Ic.Fitness, plural(usage.unlocks, "squat pass", "squat passes") + " earned",
                if (usage.unlocks == 0) "No extra time bought today." else "Bought with ${usage.unlocks * data.squats} squats. Your legs paid for that.",
            )
            Story(
                Icons.Rounded.CheckCircle, plural(streaks.honest, "honest check-in", "honest check-ins") + " in a row",
                if (streaks.honest == 0) "Answer tonight's check-in to start one." else "Telling the truth, even when it's \"no\".",
            )
            Story(
                Ic.Flame, plural(streaks.done, "day", "days") + " of keeping your word",
                if (streaks.done == 0) "Do tomorrow's task and this starts at 1." else "Tasks done, back to back. Keep it going.",
            )
        }
        SoftCard {
            CardTitle("Today's task", Ic.Edit)
            if (task == null) Muted("No task set for today.")
            else {
                Text("“${task.text}”", style = MaterialTheme.typography.bodyLarge)
                Muted(
                    when (task.checkIn) {
                        CheckIn.DONE -> "Done."
                        CheckIn.NOT_DONE -> "Not done, and you were honest about it. That counts."
                        null -> "Not answered yet. Check in before 4 am."
                    }
                )
            }
        }
        SoftCard {
            CardTitle("You saved ${formatMinutes(engine.weekSavedMinutes(data) * 60L)} this week", Ic.Hourglass)
            Muted("Compared with the ${data.baselineMinutes} min a day you said you used to scroll.")
        }
    }
}

private fun plural(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"

/** One line of the day's story: a big statement and a small, honest comment on it. */
@Composable
private fun Story(icon: ImageVector, headline: String, caption: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, size = 44.dp)
        Column {
            Text(headline, style = MaterialTheme.typography.titleMedium)
            Muted(caption)
        }
    }
}
