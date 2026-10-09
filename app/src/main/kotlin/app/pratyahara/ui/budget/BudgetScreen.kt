package app.pratyahara.ui.budget

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pratyahara.core.budget.BudgetRequestResult
import app.pratyahara.core.budget.DelayTable
import app.pratyahara.engine
import app.pratyahara.ui.components.Muted
import app.pratyahara.ui.components.PrimaryButton
import app.pratyahara.ui.components.Screen
import app.pratyahara.ui.components.SoftCard
import app.pratyahara.ui.components.formatWait
import app.pratyahara.ui.onboarding.Stepper
import kotlinx.coroutines.launch

@Composable
fun BudgetScreen(onBack: () -> Unit) {
    val engine = LocalContext.current.engine
    val scope = rememberCoroutineScope()
    val data by engine.store.data.collectAsStateWithLifecycle()
    var target by remember { mutableIntStateOf(data.budgetMinutes) }
    var reason by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val raising = target > data.budgetMinutes

    Screen("daily limit ⏱️", onBack) {
        SoftCard {
            Text("now: ${data.budgetMinutes} min a day", style = MaterialTheme.typography.titleLarge)
            Stepper(target, "min", DelayTable.MIN_BUDGET_MINUTES, DelayTable.MAX_BUDGET_MINUTES, 5) {
                target = it
                message = null
            }
            when {
                target < data.budgetMinutes -> Muted("lowering applies right away. love that for you 🫶")
                raising -> Muted(
                    "raising by ${target - data.budgetMinutes} min kicks in after " +
                        formatWait(DelayTable.increaseDelay(target - data.budgetMinutes).toMillis()) +
                        ". bigger jump, longer wait."
                )
            }
        }
        if (raising) {
            SoftCard {
                Text("why do you need more? 🤔", style = MaterialTheme.typography.titleLarge)
                Muted("2 honest sentences: what's different, and why it's worth the time. this is just for you.")
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it; message = null },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                    placeholder = { Text("e.g. i'm on a 6-hour train tomorrow with nothing to do...") },
                )
                Muted("${reason.trim().length} / 100 characters")
            }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge) }
        PrimaryButton(
            text = when {
                raising -> "ask for ${target} min"
                target < data.budgetMinutes -> "lower to $target min"
                else -> "no change"
            },
            enabled = target != data.budgetMinutes,
            onClick = {
                scope.launch {
                    when (val r = engine.requestBudget(target, reason)) {
                        is BudgetRequestResult.AppliedNow -> onBack()
                        is BudgetRequestResult.Scheduled -> {
                            message = "got it 👍 your limit becomes ${r.change.value} min in ${formatWait(r.change.delayMillis)}. cancel anytime from home."
                            reason = ""
                        }
                        is BudgetRequestResult.Rejected -> message = r.message
                    }
                }
            },
        )
    }
}
