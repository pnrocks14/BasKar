package app.pratyahara.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.pratyahara.ui.components.Screen

/** Same text as docs/play/privacy-policy.md. Keep the two in sync. */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Screen("Privacy policy", onBack) {
        PRIVACY_POLICY.split("\n\n").forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
    }
}

const val PRIVACY_POLICY = """Pratyahara does not collect, share or sell any personal data. It has no internet permission, so it cannot send anything off your phone.

What stays on your phone: your settings (which apps to limit, your daily limit, squat count), daily counters (minutes on Reels/Shorts per app, number of pauses and unlocks), and the tasks you write with your yes/no answers. Counters and tasks older than 60 days are deleted automatically. None of it is included in cloud backups.

Accessibility permission: Pratyahara uses Android's Accessibility Service API only to recognise when the Reels or Shorts screen is open in the apps you chose, to count that time, to show a pause screen, and to press Back when you ask it to. It checks the screen's layout in memory and discards it immediately. It never stores or transmits screen content, messages, or anything you type in other apps.

Sensors: the accelerometer is used only while the squat screen is open, to count repetitions. Readings are not stored.

Uninstalling Pratyahara deletes all of its data. Questions: contact the developer through the Google Play listing."""
