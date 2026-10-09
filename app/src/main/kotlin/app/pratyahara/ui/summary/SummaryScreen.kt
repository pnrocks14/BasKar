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
import app.pratyahara.ui.components.Muted
import app.pratyahara.ui.components.Screen
import app.pratyahara.ui.components.SoftCard
import app.pratyahara.ui.components.Stat
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat("${usage.visits}", "Opens")
                Stat("${usage.blocks}", "Pauses")
                Stat("${usage.unlocks}", "Unlocks")
                Stat("${streaks.honest}", "Honest")
                Stat("${streaks.done}", "Streak")
            }
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
