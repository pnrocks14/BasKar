package app.pratyahara.detection

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

object ServiceStatus {
    /** Reads the system setting, so it is correct even when the service process isn't running. */
    fun isEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(context, ReelsAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    /**
     * True when Android's own accessibility button or shortcut is assigned to Pratyahara. Android shows that
     * button over every app, next to Pratyahara's own floating button.
     */
    fun systemShortcutOn(context: Context): Boolean = runCatching {
        val me = ComponentName(context, ReelsAccessibilityService::class.java)
        listOf("accessibility_button_targets", "accessibility_shortcut_target_service").any { key ->
            Settings.Secure.getString(context.contentResolver, key).orEmpty().split(':').any {
                ComponentName.unflattenFromString(it.trim()) == me
            }
        }
    }.getOrDefault(false)

    fun settingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
