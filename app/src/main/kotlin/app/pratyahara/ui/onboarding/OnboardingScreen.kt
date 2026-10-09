package app.pratyahara.ui.onboarding

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.pratyahara.core.budget.DelayTable
import app.pratyahara.detection.DetectionRules
import app.pratyahara.detection.ServiceStatus
import app.pratyahara.engine
import app.pratyahara.ui.components.Muted
import app.pratyahara.ui.components.PrimaryButton
import app.pratyahara.ui.components.Screen
import app.pratyahara.ui.components.SecondaryButton
import app.pratyahara.ui.components.SoftCard
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen() {
    var step by rememberSaveable { mutableIntStateOf(0) }
    AnimatedContent(targetState = step, label = "onboarding") { s ->
        when (s) {
            0 -> Welcome(onNext = { step = 1 })
            1 -> Disclosure(onAccept = { step = 2 })
            2 -> Setup(onNext = { step = 3 })
            else -> Permissions()
        }
    }
}

@Composable
private fun Welcome(onNext: () -> Unit) {
    Screen("pratyahara 🪷", subtitle = "the reels limiter that's actually on your side") {
        Text(
            "pratyahara (प्रत्याहार) is the 5th limb of yoga: gently pulling your senses back from whatever's pulling at them. basically: you, not the algorithm, decide.",
            style = MaterialTheme.typography.bodyLarge,
        )
        SoftCard {
            Text("how it works", style = MaterialTheme.typography.titleLarge)
            Muted("⏱️  pick a daily limit for reels + shorts. 30 min is a solid start.")
            Muted("🎯  only reels/shorts pause. feed, DMs and profile stay open.")
            Muted("👀  i'll check in while you scroll, with a hi and some research-backed facts.")
            Muted("🏋️  need more? earn 5 min with squats, twice a day max.")
            Muted("✍️  every night, one task for tomorrow. every evening, an honest \"did you do it?\"")
            Muted("🧊  loosening a rule takes a while. tightening is instant.")
        }
        SoftCard(container = MaterialTheme.colorScheme.primaryContainer) {
            Text("firm in the moment, flexible after a pause. that's the deal 🤝", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        PrimaryButton("let's go", onNext)
    }
}

/** Google Play's prominent disclosure: shown in normal use, before the permission, with an explicit choice. */
@Composable
private fun Disclosure(onAccept: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var agreed by remember { mutableStateOf(false) }
    Screen("before we start blocking", subtitle = "the boring-but-important bit. please actually read it 🙏") {
        SoftCard {
            Text("pratyahara uses the Accessibility Service API", style = MaterialTheme.typography.titleLarge)
            Text(
                "to pause reels and shorts, pratyahara needs android's accessibility permission. here's exactly what it does with it:",
                style = MaterialTheme.typography.bodyLarge,
            )
            Muted("• it only looks at the apps you pick (like Instagram, YouTube or TikTok). it doesn't run in any other app.")
            Muted("• inside those apps it checks the layout of the screen, like which tab is selected and whether a full-screen video player is open, to tell if you're on reels or shorts.")
            Muted("• it never reads, saves or sends your messages, posts, searches or anything else on screen. screen content is checked in memory and thrown away right after.")
            Muted("• the only things it saves are counts: minutes on reels and shorts, how often you opened them, and how often they were paused or unlocked.")
            Muted("• nothing leaves your phone. pratyahara has no internet permission at all.")
            Muted("• it uses the permission to show the pause screen and little check-ins on top of those apps, and, when you tap \"take me back to the feed\", to press back for you.")
        }
        Row(
            Modifier.fillMaxWidth().toggleable(value = agreed, role = Role.Checkbox, onValueChange = { agreed = it }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = agreed, onCheckedChange = null)
            Text("i understand, and i agree to pratyahara using the accessibility permission this way.", style = MaterialTheme.typography.bodyMedium)
        }
        PrimaryButton("agree + continue", enabled = agreed, onClick = {
            scope.launch {
                context.engine.acceptDisclosure()
                onAccept()
            }
        })
        TextButton(onClick = { (context as? Activity)?.finish() }, modifier = Modifier.fillMaxWidth()) {
            Text("not now")
        }
    }
}

@Composable
private fun Setup(onNext: () -> Unit) {
    val context = LocalContext.current
    val engine = context.engine
    val scope = rememberCoroutineScope()
    val installed = remember { DetectionRules.supportedPackages.filter { isInstalled(context, it) }.toSet() }
    var chosen by remember { mutableStateOf(installed.ifEmpty { DetectionRules.defaultPackages }) }
    var budget by remember { mutableIntStateOf(DelayTable.DEFAULT_BUDGET_MINUTES) }
    var baseline by remember { mutableIntStateOf(90) }

    Screen("set your limits", subtitle = "you can tighten these anytime") {
        SoftCard {
            Text("which apps? 📱", style = MaterialTheme.typography.titleLarge)
            Muted("only their reels or shorts part gets limited.")
            DetectionRules.all.distinctBy { it.appName }.forEach { rule ->
                val pkgs = DetectionRules.all.filter { it.appName == rule.appName }.map { it.packageName }.toSet()
                val on = pkgs.any { it in chosen }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        rule.appName + if (pkgs.none { it in installed }) "  (not installed)" else "",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = on, onCheckedChange = { chosen = if (it) chosen + pkgs else chosen - pkgs })
                }
            }
        }
        SoftCard {
            Text("daily reels/shorts limit ⏱️", style = MaterialTheme.typography.titleLarge)
            Muted("30 min a day is a solid target. cutting social media to that made people feel less lonely and down in a 2018 UPenn study.")
            Stepper(value = budget, unit = "min", min = DelayTable.MIN_BUDGET_MINUTES, max = DelayTable.MAX_BUDGET_MINUTES, step = 5) { budget = it }
        }
        SoftCard {
            Text("be honest: how much do you scroll now? 🫣", style = MaterialTheme.typography.titleLarge)
            Muted("a rough daily guess. it's only used to show the time you win back.")
            Stepper(value = baseline, unit = "min", min = 15, max = 480, step = 15) { baseline = it }
        }
        PrimaryButton("continue", enabled = chosen.isNotEmpty(), onClick = {
            scope.launch {
                engine.store.update { it.copy(monitored = chosen, budgetMinutes = budget, baselineMinutes = baseline) }
                onNext()
            }
        })
    }
}

@Composable
private fun Permissions() {
    val context = LocalContext.current
    val engine = context.engine
    val scope = rememberCoroutineScope()
    var serviceOn by remember { mutableStateOf(ServiceStatus.isEnabled(context)) }
    var notificationsOn by remember { mutableStateOf(notificationsGranted(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        serviceOn = ServiceStatus.isEnabled(context)
        notificationsOn = notificationsGranted(context)
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsOn = it }

    Screen("3 quick switches", subtitle = "then you're all set ✨") {
        SoftCard {
            Text("1. accessibility 🔓", style = MaterialTheme.typography.titleLarge)
            Muted("find pratyahara in the list (maybe under \"Downloaded apps\") and switch it on. greyed out? go to Settings → Apps → Pratyahara → ⋮ → Allow restricted settings first.")
            if (serviceOn) Text("on ✅ ty", style = MaterialTheme.typography.titleMedium)
            else SecondaryButton("open accessibility settings", { context.startActivity(ServiceStatus.settingsIntent()) })
        }
        SoftCard {
            Text("2. notifications 🔔", style = MaterialTheme.typography.titleLarge)
            Muted("for the evening check-in, the morning note from past you, and a heads-up if blocking gets switched off.")
            if (notificationsOn) Text("on ✅", style = MaterialTheme.typography.titleMedium)
            else SecondaryButton("allow notifications", {
                if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            })
        }
        SoftCard {
            Text("3. battery 🔋", style = MaterialTheme.typography.titleLarge)
            Muted("some phones kill background apps to save battery, which quietly turns blocking off. set pratyahara to \"Unrestricted\".")
            SecondaryButton("open battery settings", {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            })
        }
        PrimaryButton(if (serviceOn) "start 🚀" else "start (do accessibility later)", onClick = {
            scope.launch {
                val d = engine.store.current
                engine.finishOnboarding(d.monitored, d.budgetMinutes, d.baselineMinutes)
            }
        })
    }
}

@Composable
fun Stepper(value: Int, unit: String, min: Int, max: Int, step: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        SecondaryButton("−", { onChange((value - step).coerceAtLeast(min)) }, Modifier.weight(1f), enabled = value > min)
        Text("$value $unit", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1.4f).padding(horizontal = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        SecondaryButton("+", { onChange((value + step).coerceAtMost(max)) }, Modifier.weight(1f), enabled = value < max)
    }
}

private fun notificationsGranted(context: android.content.Context) =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun isInstalled(context: android.content.Context, pkg: String): Boolean = runCatching {
    context.packageManager.getPackageInfo(pkg, 0)
    true
}.getOrDefault(false)
