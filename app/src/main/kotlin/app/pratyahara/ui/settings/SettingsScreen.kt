package app.pratyahara.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.ui.text.style.TextAlign
import app.pratyahara.core.lock.FocusHours
import app.pratyahara.ui.components.CardTitle
import app.pratyahara.ui.components.Ic
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

    Screen("Settings", onBack, subtitle = "Tightening applies now. Loosening waits 24 hours, and you can cancel it from Home.") {

        SoftCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { CardTitle("Blocking", Ic.Shield) }
                Switch(
                    checked = data.blockingEnabled && !waiting(ChangeType.DISABLE_BLOCKING),
                    onCheckedChange = { on -> scope.launch { engine.setBlockingEnabled(on) } },
                )
            }
            if (waiting(ChangeType.DISABLE_BLOCKING)) Muted("Switching off in 24 hours unless you cancel.")
        }

        SoftCard {
            CardTitle("Apps", Ic.Phone)
            DetectionRules.all.distinctBy { it.appName }.forEach { rule ->
                val pkgs = DetectionRules.all.filter { it.appName == rule.appName }.map { it.packageName }
                val on = pkgs.any { it in data.monitored }
                val leaving = pkgs.any { waiting(ChangeType.REMOVE_APP, it) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${rule.appName} ${rule.sectionName}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = on && !leaving, onCheckedChange = { want ->
                        scope.launch { pkgs.forEach { engine.setAppMonitored(it, want) } }
                    })
                }
                if (leaving) Muted("${rule.appName} stops being limited in 24 hours unless you cancel.")
            }
        }

        SoftCard {
            CardTitle("Breathing pause", Ic.Lotus)
            Muted("A few calm seconds before Reels or Shorts opens, so each visit is a choice and not a reflex. It asks again after a long stretch of scrolling.")
            Text("Pause length", style = MaterialTheme.typography.titleMedium)
            Choices(listOf(0 to "Off", 3 to "3 sec", 5 to "5 sec", 10 to "10 sec"), data.pauseSeconds) { v ->
                scope.launch { engine.updateSettings { it.copy(pauseSeconds = v) } }
            }
            Text("Ask again after", style = MaterialTheme.typography.titleMedium)
            Choices(listOf(0 to "Never", 5 to "5 min", 10 to "10 min", 15 to "15 min", 20 to "20 min"), data.checkEveryMinutes) { v ->
                scope.launch { engine.updateSettings { it.copy(checkEveryMinutes = v) } }
            }
        }

        FocusCard()

        SoftCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CardTitle("Check-ins while scrolling", Ic.Eye)
                    Muted("A hello when you open Reels, a heads-up when time's nearly up, and a research-backed fact every few minutes.")
                }
                Switch(checked = data.buddyEnabled, onCheckedChange = { on -> scope.launch { engine.updateSettings { it.copy(buddyEnabled = on) } } })
            }
            if (data.buddyEnabled) {
                Text("Facts every", style = MaterialTheme.typography.titleMedium)
                Choices(listOf(0 to "Off", 2 to "2 min", 3 to "3 min", 5 to "5 min", 10 to "10 min"), data.quoteEveryMinutes) { v ->
                    scope.launch { engine.updateSettings { it.copy(quoteEveryMinutes = v) } }
                }
            }
        }

        SoftCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CardTitle("Floating button", Ic.Lotus)
                    Muted("A small Pratyahara button on the edge of the screen, only inside the apps you limit. Tap it to see your time left. Drag it up or down.")
                }
                Switch(checked = data.bubbleEnabled, onCheckedChange = { on -> scope.launch { engine.updateSettings { it.copy(bubbleEnabled = on) } } })
            }
            Muted("If you also see Android's accessibility shortcut everywhere, turn it off in Accessibility, Pratyahara, \"Pratyahara shortcut\".")
        }

        SoftCard {
            CardTitle("Daily limit: ${data.budgetMinutes} min", Ic.Timer)
            Muted("Lowering is instant. Raising needs a real reason and waits a few hours to a few days, depending on how much.")
            SecondaryButton("Change daily limit", { go(Routes.BUDGET) })
        }

        SoftCard {
            CardTitle("Squats per unlock", Ic.Fitness)
            Muted("Each set earns ${UnlockRules.UNLOCK_MINUTES} minutes, up to ${UnlockRules.MAX_UNLOCKS_PER_DAY} times a day.")
            Stepper(data.squats, "", UnlockRules.MIN_SQUATS, UnlockRules.MAX_SQUATS, 5) { v -> scope.launch { engine.setSquats(v) } }
            if (waiting(ChangeType.LOWER_SQUATS)) Muted("Lowering waits 24 hours.")
        }

        SoftCard {
            CardTitle("Permissions", Icons.Rounded.Lock)
            Muted(if (ServiceStatus.isEnabled(context)) "Accessibility is on." else "Accessibility is off, so nothing is being limited.")
            SecondaryButton("Accessibility settings", { context.startActivity(ServiceStatus.settingsIntent()) })
            SecondaryButton("Battery settings", {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            })
        }

        SoftCard {
            CardTitle("About", Icons.Rounded.Info)
            Muted("Tip: add the Pratyahara widget to your home screen to see your minutes left at a glance.")
            SecondaryButton("Detection check", { go(Routes.DEBUG) })
            SecondaryButton("Privacy policy", { go(Routes.PRIVACY) })
        }
    }
}

/** A row of single-choice chips. */
@Composable
private fun Choices(options: List<Pair<Int, String>>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (v, label) ->
            FilterChip(selected = selected == v, onClick = { onSelect(v) }, label = { Text(label, style = MaterialTheme.typography.labelLarge) })
        }
    }
}

@Composable
private fun FocusCard() {
    val context = LocalContext.current
    val engine = context.engine
    val scope = rememberCoroutineScope()
    val data by engine.store.data.collectAsStateWithLifecycle()
    val editable = engine.focusEditable(data)
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CardTitle("Focus hours", Ic.Moon)
                Muted("Reels and Shorts stay closed during these hours every day, whatever time you have left. Great for bedtime or study time.")
            }
            Switch(
                checked = data.focusEnabled,
                enabled = editable,
                onCheckedChange = { on -> scope.launch { engine.setFocus(on, data.focusStart, data.focusEnd) } },
            )
        }
        if (data.focusEnabled) {
            TimeRow("From", data.focusStart, editable) { v -> scope.launch { engine.setFocus(true, v, data.focusEnd) } }
            TimeRow("Until", data.focusEnd, editable) { v -> scope.launch { engine.setFocus(true, data.focusStart, v) } }
            if (!editable) Muted("Focus hours are on right now, so they can't be changed until ${FocusHours.format(data.focusEnd)}.")
        }
    }
}

@Composable
private fun TimeRow(label: String, minute: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val step = 30
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        SecondaryButton("−", { onChange(Math.floorMod(minute - step, FocusHours.MINUTES_PER_DAY)) }, Modifier.width(64.dp), enabled = enabled)
        Text(FocusHours.format(minute), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.width(96.dp))
        SecondaryButton("+", { onChange(Math.floorMod(minute + step, FocusHours.MINUTES_PER_DAY)) }, Modifier.width(64.dp), enabled = enabled)
    }
}
