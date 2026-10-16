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
        Text("Open-source licences", style = MaterialTheme.typography.titleMedium)
        Text(OPEN_SOURCE, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

const val PRIVACY_POLICY = """BasKar does not collect, share or sell any personal data. It has no internet permission, so it cannot send anything off your phone.

What stays on your phone: your settings (which apps to limit, your daily limit, squat count), daily counters (minutes on Reels/Shorts per app, the number of reels watched, pauses and unlocks), and the tasks you write with your yes/no answers. Counters and tasks older than 60 days are deleted automatically. None of it is included in cloud backups.

Accessibility permission: BasKar uses Android's Accessibility Service API only to recognise when the Reels or Shorts screen is open in the apps you chose, to count that time, how often you open it and how many reels you swipe through, to show a pause screen and small check-in messages, and to press Back when you ask it to. It checks the screen's layout in memory and discards it immediately. It never stores or transmits screen content, messages, or anything you type in other apps.

Sensors: the accelerometer is used only while the squat screen is open, to count repetitions. Readings are not stored.

Children: BasKar is not directed at children under 13.

Uninstalling BasKar deletes all of its data. Questions: contact the developer through the Google Play listing."""


/** Every shipped library is Apache 2.0, which asks us to say so and point to the licence. */
const val OPEN_SOURCE = """BasKar is built with Android Jetpack (AndroidX: Compose, Navigation, Lifecycle, DataStore, WorkManager and Core), Kotlin, kotlinx.coroutines and kotlinx.serialization. These are copyright The Android Open Source Project, Google LLC and JetBrains s.r.o., and are licensed under the Apache License, Version 2.0 (www.apache.org/licenses/LICENSE-2.0). They are used without changes."""
