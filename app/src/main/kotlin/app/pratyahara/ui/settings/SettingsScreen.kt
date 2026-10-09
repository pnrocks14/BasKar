package app.pratyahara.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

    Screen("settings", onBack, subtitle = "tightening applies now. loosening waits 24h, cancel anytime from home.") {

        SoftCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("blocking 🛡️", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Switch(
                    checked = data.blockingEnabled && !waiting(ChangeType.DISABLE_BLOCKING),
                    onCheckedChange = { on -> scope.launch { engine.setBlockingEnabled(on) } },
                )
            }
            if (waiting(ChangeType.DISABLE_BLOCKING)) Muted("switching off in 24h unless you cancel.")
        }

        SoftCard {
            Text("apps 📱", style = MaterialTheme.typography.titleLarge)
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
                if (leaving) Muted("${rule.appName} stops being limited in 24h unless you cancel.")
            }
        }

        SoftCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("check-ins while scrolling 👀", style = MaterialTheme.typography.titleLarge)
                    Muted("a hi when you open reels, a heads-up when time's nearly up, and a research-backed fact every few min.")
                }
                Switch(checked = data.buddyEnabled, onCheckedChange = { on -> scope.launch { engine.updateSettings { it.copy(buddyEnabled = on) } } })
            }
            if (data.buddyEnabled) {
                Text("facts every", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "off", 2 to "2 min", 3 to "3 min", 5 to "5 min", 10 to "10 min").forEach { (m, label) ->
                        FilterChip(
                            selected = data.quoteEveryMinutes == m,
                            onClick = { scope.launch { engine.updateSettings { it.copy(quoteEveryMinutes = m) } } },
                            label = { Text(label) },
                        )
                    }
                }
            }
            Muted("tip: turn on the pratyahara shortcut in accessibility settings and tap it anytime to see how much time you've got left.")
        }

        SoftCard {
            Text("daily limit: ${data.budgetMinutes} min ⏱️", style = MaterialTheme.typography.titleLarge)
            Muted("lowering is instant. raising needs a real reason and waits a few hours to a few days, depending on how much.")
            SecondaryButton("change daily limit", { go(Routes.BUDGET) })
        }

        SoftCard {
            Text("squats per unlock 🏋️", style = MaterialTheme.typography.titleLarge)
            Muted("each set earns ${UnlockRules.UNLOCK_MINUTES} min, up to ${UnlockRules.MAX_UNLOCKS_PER_DAY}× a day.")
            Stepper(data.squats, "", UnlockRules.MIN_SQUATS, UnlockRules.MAX_SQUATS, 5) { v -> scope.launch { engine.setSquats(v) } }
            if (waiting(ChangeType.LOWER_SQUATS)) Muted("lowering waits 24h.")
        }

        SoftCard {
            Text("permissions 🔓", style = MaterialTheme.typography.titleLarge)
            Muted(if (ServiceStatus.isEnabled(context)) "accessibility: on ✅" else "accessibility: off ❌")
            SecondaryButton("accessibility settings", { context.startActivity(ServiceStatus.settingsIntent()) })
            SecondaryButton("battery settings", {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            })
        }

        SoftCard {
            Text("about", style = MaterialTheme.typography.titleLarge)
            SecondaryButton("detection check 🔍", { go(Routes.DEBUG) })
            SecondaryButton("privacy policy", { go(Routes.PRIVACY) })
        }
    }
}
