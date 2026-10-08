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

    Screen("Today", onBack) {
        SoftCard {
            Text("Minutes on Reels and Shorts", style = MaterialTheme.typography.titleLarge)
            if (usage.secondsByApp.isEmpty()) Muted("None today.")
            usage.secondsByApp.entries.sortedByDescending { it.value }.forEach { (pkg, secs) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(DetectionRules.appName(pkg))
                    Text(formatMinutes(secs))
                }
            }
            Muted("Daily limit: ${data.budgetMinutes} min")
        }
        SoftCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat("${usage.blocks}", "pauses")
                Stat("${usage.unlocks}", "unlocks")
                Stat("${streaks.honest}", "honest days")
                Stat("${streaks.done}", "done streak")
            }
        }
        SoftCard {
            Text("Today's task", style = MaterialTheme.typography.titleLarge)
            if (task == null) Muted("No task was set for today.")
            else {
                Text("“${task.text}”")
                Muted(
                    when (task.checkIn) {
                        CheckIn.DONE -> "Done."
                        CheckIn.NOT_DONE -> "Not done, and you said so honestly."
                        null -> "Not answered yet. Check in before 4 am."
                    }
                )
            }
        }
        SoftCard {
            Text("This week you saved ${formatMinutes(engine.weekSavedMinutes(data) * 60L)}", style = MaterialTheme.typography.titleLarge)
            Muted("Compared with the ${data.baselineMinutes} min a day you estimated at the start.")
        }
    }
}
