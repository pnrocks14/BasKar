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
import app.pratyahara.ui.components.CardTitle
import app.pratyahara.ui.components.Ic
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

    Screen("Daily limit", onBack) {
        SoftCard {
            CardTitle("Now: ${data.budgetMinutes} min a day", Ic.Timer)
            Stepper(target, "min", DelayTable.MIN_BUDGET_MINUTES, DelayTable.MAX_BUDGET_MINUTES, 5) {
                target = it
                message = null
            }
            when {
                target < data.budgetMinutes -> Muted("Lowering applies right away. Good call.")
                raising -> Muted(
                    "Raising by ${target - data.budgetMinutes} min takes effect after " +
                        formatWait(DelayTable.increaseDelay(target - data.budgetMinutes).toMillis()) +
                        ". A bigger jump means a longer wait."
                )
            }
        }
        if (raising) {
            SoftCard {
                CardTitle("Why do you need more?", Ic.Edit)
                Muted("Two honest sentences: what's different, and why it's worth the time. This is just for you.")
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it; message = null },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                    placeholder = { Text("For example: I'm on a 6-hour train tomorrow with nothing to do...") },
                )
                Muted("${reason.trim().length} / 100 characters")
            }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge) }
        PrimaryButton(
            text = when {
                raising -> "Ask for ${target} min"
                target < data.budgetMinutes -> "Lower to $target min"
                else -> "No change"
            },
            enabled = target != data.budgetMinutes,
            onClick = {
                scope.launch {
                    when (val r = engine.requestBudget(target, reason)) {
                        is BudgetRequestResult.AppliedNow -> onBack()
                        is BudgetRequestResult.Scheduled -> {
                            message = "Got it. Your limit becomes ${r.change.value} min in ${formatWait(r.change.delayMillis)}. You can cancel it from Home."
                            reason = ""
                        }
                        is BudgetRequestResult.Rejected -> message = r.message
                    }
                }
            },
        )
    }
}
