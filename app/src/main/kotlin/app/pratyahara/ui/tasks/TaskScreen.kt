package app.pratyahara.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pratyahara.core.tasks.CheckIn
import app.pratyahara.core.validation.ValidationResult
import app.pratyahara.engine
import app.pratyahara.ui.components.Muted
import app.pratyahara.ui.components.PrimaryButton
import app.pratyahara.ui.components.Screen
import app.pratyahara.ui.components.SecondaryButton
import app.pratyahara.ui.components.SoftCard
import kotlinx.coroutines.launch

@Composable
fun TaskScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val engine = LocalContext.current.engine
    val scope = rememberCoroutineScope()
    val data by engine.store.data.collectAsStateWithLifecycle()
    val awaiting = engine.taskAwaitingCheckIn(data)
    var text by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var justAnswered by remember { mutableStateOf<CheckIn?>(null) }

    Screen(if (awaiting != null) "Check-in" else "Your next task", onBack) {
        if (awaiting != null) {
            SoftCard {
                Text("You said you'd:", style = MaterialTheme.typography.titleMedium)
                Text("“${awaiting.text}”", style = MaterialTheme.typography.headlineSmall)
                Text("Did you do it?", style = MaterialTheme.typography.titleLarge)
                Muted("Honest answers are always the lighter path here. A \"no\" just means writing a fresh task.")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrimaryButton("Yes", { scope.launch { engine.answerCheckIn(awaiting, CheckIn.DONE); justAnswered = CheckIn.DONE } }, Modifier.weight(1f))
                    SecondaryButton("No", { scope.launch { engine.answerCheckIn(awaiting, CheckIn.NOT_DONE); justAnswered = CheckIn.NOT_DONE } }, Modifier.weight(1f))
                }
            }
            return@Screen
        }

        justAnswered?.let {
            Text(
                if (it == CheckIn.DONE) "Lovely. That's one more for the streak." else "Thanks for being straight about it. Tomorrow's another go.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        val day = engine.nextTaskDay(data)
        val forToday = day == engine.today()
        SoftCard {
            Text(if (forToday) "One thing you'll do today" else "One thing you'll do tomorrow", style = MaterialTheme.typography.titleLarge)
            Muted("Anything at all, as long as you could check it off. Small and specific beats big and vague.")
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; message = null },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Call the bank before noon") },
            )
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
        PrimaryButton("Save", onClick = {
            scope.launch {
                when (val r = engine.writeTask(text)) {
                    is ValidationResult.Invalid -> message = r.message
                    ValidationResult.Ok -> onDone()
                }
            }
        })
    }
}
