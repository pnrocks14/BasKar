package app.pratyahara.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pratyahara.core.budget.ChangeType
import app.pratyahara.core.lock.UnlockRules
import app.pratyahara.detection.DetectionRules
import app.pratyahara.detection.ServiceStatus
import app.pratyahara.engine
import app.pratyahara.ui.Routes
import app.pratyahara.ui.components.Muted
import app.pratyahara.ui.components.Screen
import app.pratyahara.ui.components.SecondaryButton
import app.pratyahara.ui.components.SoftCard
import app.pratyahara.ui.onboarding.Stepper
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit, go: (String) -> Unit) {
    val context = LocalContext.current
    val engine = context.engine
    val scope = rememberCoroutineScope()
    val data by engine.store.data.collectAsStateWithLifecycle()
    val pending = data.pendingModels()
    fun waiting(type: ChangeType, value: String? = null) =
        pending.any { it.type == type && (value == null || it.value == value) }

    Screen("Settings", onBack) {
        Muted("Tightening a rule applies now. Loosening one waits 24 hours, and you can cancel it from the home screen while it waits.")

        SoftCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Blocking", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Switch(
                    checked = data.blockingEnabled && !waiting(ChangeType.DISABLE_BLOCKING),
                    onCheckedChange = { on -> scope.launch { engine.setBlockingEnabled(on) } },
                )
            }
            if (waiting(ChangeType.DISABLE_BLOCKING)) Muted("Switching off in 24 hours unless you cancel.")
        }

        SoftCard {
            Text("Apps", style = MaterialTheme.typography.titleLarge)
            DetectionRules.all.distinctBy { it.appName }.forEach { rule ->
                val pkgs = DetectionRules.all.filter { it.appName == rule.appName }.map { it.packageName }
                val on = pkgs.any { it in data.monitored }
                val leaving = pkgs.any { waiting(ChangeType.REMOVE_APP, it) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${rule.appName} ${rule.sectionName}", modifier = Modifier.weight(1f))
                    Switch(checked = on && !leaving, onCheckedChange = { want ->
                        scope.launch { pkgs.forEach { engine.setAppMonitored(it, want) } }
                    })
                }
                if (leaving) Muted("${rule.appName} stops being limited in 24 hours unless you cancel.")
            }
        }

        SoftCard {
            Text("Daily limit: ${data.budgetMinutes} min", style = MaterialTheme.typography.titleLarge)
            Muted("Lowering is instant. Raising needs a reason and waits a few hours to a few days, depending on how much.")
            SecondaryButton("Change daily limit", { go(Routes.BUDGET) })
        }

        SoftCard {
            Text("Squats per unlock", style = MaterialTheme.typography.titleLarge)
            Muted("Each set earns ${UnlockRules.UNLOCK_MINUTES} minutes, up to ${UnlockRules.MAX_UNLOCKS_PER_DAY} times a day.")
            Stepper(data.squats, "", UnlockRules.MIN_SQUATS, UnlockRules.MAX_SQUATS, 5) { v -> scope.launch { engine.setSquats(v) } }
            if (waiting(ChangeType.LOWER_SQUATS)) Muted("Lowering waits 24 hours.")
        }

        SoftCard {
            Text("Permissions", style = MaterialTheme.typography.titleLarge)
            Muted(if (ServiceStatus.isEnabled(context)) "Accessibility: on" else "Accessibility: off")
            SecondaryButton("Accessibility settings", { context.startActivity(ServiceStatus.settingsIntent()) })
            SecondaryButton("Battery settings", {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            })
        }

        SoftCard {
            Text("About", style = MaterialTheme.typography.titleLarge)
            SecondaryButton("Detection check", { go(Routes.DEBUG) })
            SecondaryButton("Privacy policy", { go(Routes.PRIVACY) })
        }
    }
}
