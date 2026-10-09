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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import app.pratyahara.ui.components.Bullet
import app.pratyahara.ui.components.CardTitle
import app.pratyahara.ui.components.Ic
import app.pratyahara.ui.components.IconBadge
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
    Screen("Pratyahara", subtitle = "The Reels limiter that's on your side") {
        IconBadge(Ic.Lotus, container = MaterialTheme.colorScheme.primaryContainer, tint = MaterialTheme.colorScheme.onPrimaryContainer, size = 64.dp)
        Text(
            "Pratyahara (प्रत्याहार) is the fifth limb of yoga: gently drawing your senses back from whatever pulls at them. You decide what you watch, not the algorithm.",
            style = MaterialTheme.typography.bodyLarge,
        )
        SoftCard {
            Text("How it works", style = MaterialTheme.typography.titleLarge)
            Bullet(Ic.Timer, "Pick a daily limit for Reels and Shorts. 30 minutes is a good start.")
            Bullet(Ic.Shield, "Only Reels and Shorts pause. Your feed, messages and profile stay open.")
            Bullet(Ic.Lotus, "A short breathing pause before Reels opens, so every visit is a choice.")
            Bullet(Ic.Eye, "Friendly check-ins while you scroll, with research-backed facts.")
            Bullet(Ic.Fitness, "Need more time? Earn 5 minutes with squats, twice a day at most.")
            Bullet(Ic.Edit, "Each night, one task for tomorrow. Each evening, an honest \"did you do it?\"")
            Bullet(Ic.Hourglass, "Loosening a rule takes a while. Tightening one is instant.")
        }
        SoftCard(container = MaterialTheme.colorScheme.primaryContainer) {
            Text("Firm in the moment, flexible after a pause. That's the deal.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        PrimaryButton("Get started", onNext)
    }
}

/** Google Play's prominent disclosure: shown in normal use, before the permission, with an explicit choice. */
@Composable
private fun Disclosure(onAccept: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var agreed by remember { mutableStateOf(false) }
    Screen("Before we start", subtitle = "The important part. Please take a minute to read it.") {
        SoftCard {
            CardTitle("Pratyahara uses the Accessibility Service API", Ic.Shield)
            Text(
                "To pause Reels and Shorts, Pratyahara needs Android's accessibility permission. Here's exactly what it does with it:",
                style = MaterialTheme.typography.bodyLarge,
            )
            Muted("• It only looks at the apps you pick (like Instagram, YouTube or TikTok). It doesn't look at any other app.")
            Muted("• Inside those apps it checks the layout of the screen, like which tab is selected and whether a full-screen video player is open, to tell if you're on Reels or Shorts.")
            Muted("• It never reads, saves or sends your messages, posts, searches or anything else on screen. Screen content is checked in memory and discarded right away.")
            Muted("• The only things it saves are counts: minutes on Reels and Shorts, how often you opened them, and how often they were paused or unlocked.")
            Muted("• Nothing leaves your phone. Pratyahara has no internet permission at all.")
            Muted("• It uses the permission to show the pause screen, the breathing pause, small check-ins and the floating Pratyahara button on top of those apps, and to press Back for you when you tap \"Back to the feed\".")
        }
        Row(
            Modifier.fillMaxWidth().toggleable(value = agreed, role = Role.Checkbox, onValueChange = { agreed = it }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = agreed, onCheckedChange = null)
            Text("I understand and agree to Pratyahara using the accessibility permission this way.", style = MaterialTheme.typography.bodyMedium)
        }
        PrimaryButton("Agree and continue", enabled = agreed, onClick = {
            scope.launch {
                context.engine.acceptDisclosure()
                onAccept()
            }
        })
        TextButton(onClick = { (context as? Activity)?.finish() }, modifier = Modifier.fillMaxWidth()) {
            Text("Not now")
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

    Screen("Set your limits", subtitle = "You can tighten these anytime") {
        SoftCard {
            CardTitle("Which apps?", Ic.Phone)
            Muted("Only their Reels or Shorts section gets limited.")
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
            CardTitle("Daily Reels and Shorts limit", Ic.Timer)
            Muted("30 minutes a day is a good target. In a 2018 University of Pennsylvania study, cutting social media to that made people feel less lonely and down.")
            Stepper(value = budget, unit = "min", min = DelayTable.MIN_BUDGET_MINUTES, max = DelayTable.MAX_BUDGET_MINUTES, step = 5) { budget = it }
        }
        SoftCard {
            CardTitle("Be honest: how much do you scroll now?", Ic.Hourglass)
            Muted("A rough daily guess. It's only used to show the time you win back.")
            Stepper(value = baseline, unit = "min", min = 15, max = 480, step = 15) { baseline = it }
        }
        PrimaryButton("Continue", enabled = chosen.isNotEmpty(), onClick = {
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

    Screen("Three quick switches", subtitle = "Then you're all set") {
        SoftCard {
            CardTitle("1. Accessibility", Icons.Rounded.Lock)
            Muted("Find Pratyahara in the list (it may be under \"Downloaded apps\") and switch it on. Greyed out? Go to Settings, Apps, Pratyahara, tap the three dots and choose \"Allow restricted settings\" first.")
            if (serviceOn) Done("Switched on")
            else SecondaryButton("Open accessibility settings", { context.startActivity(ServiceStatus.settingsIntent()) })
        }
        SoftCard {
            CardTitle("2. Notifications", Icons.Rounded.Notifications)
            Muted("For the evening check-in, the morning note from past you, and a heads-up if blocking gets switched off.")
            if (notificationsOn) Done("Allowed")
            else SecondaryButton("Allow notifications", {
                if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            })
        }
        SoftCard {
            CardTitle("3. Battery", Icons.Rounded.Settings)
            Muted("Some phones stop background apps to save battery, which quietly turns blocking off. Set Pratyahara to \"Unrestricted\".")
            SecondaryButton("Open battery settings", {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            })
        }
        PrimaryButton(if (serviceOn) "Start" else "Start (set up accessibility later)", onClick = {
            scope.launch {
                val d = engine.store.current
                engine.finishOnboarding(d.monitored, d.budgetMinutes, d.baselineMinutes)
            }
        })
    }
}

@Composable
private fun Done(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
        Text(text, style = MaterialTheme.typography.titleMedium)
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
