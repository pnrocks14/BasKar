package app.pratyahara.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import app.pratyahara.R
import app.pratyahara.core.lock.LockState
import app.pratyahara.core.lock.UnlockRules
import app.pratyahara.core.tasks.TaskGate

/** What the overlay should say, worked out by the service from the current [LockState]. */
data class OverlayContent(
    val title: String,
    val body: String,
    val primaryLabel: String?,
    val primaryRoute: String?,
)

object OverlayCopy {
    fun forState(state: LockState, budgetMinutes: Int, section: String, squats: Int, nowMillis: Long): OverlayContent? = when (state) {
        is LockState.BudgetLocked -> OverlayContent(
            title = "That's your $budgetMinutes minutes for today",
            body = if (state.unlocksLeft > 0) {
                "Let's pick $section up tomorrow. If something really can't wait, you can earn ${UnlockRules.UNLOCK_MINUTES} more minutes by moving first."
            } else {
                "Let's pick $section up tomorrow. The rest of the app is still yours: feed, messages, profile."
            },
            primaryLabel = if (state.unlocksLeft > 0) "Earn ${UnlockRules.UNLOCK_MINUTES} minutes with $squats squats" else null,
            primaryRoute = if (state.unlocksLeft > 0) "unlock" else null,
        )
        is LockState.TaskLocked -> when (state.gate) {
            TaskGate.MISSED_CHECKIN -> OverlayContent(
                title = "Yesterday's check-in is still open",
                body = "Tell past you how it went, then set one task for next. $section opens again right after.",
                primaryLabel = "Answer and write a task",
                primaryRoute = "task",
            )
            else -> OverlayContent(
                title = "One small task first",
                body = "Write down one thing you'll do next. As soon as it's written, $section is open again.",
                primaryLabel = "Write my task",
                primaryRoute = "task",
            )
        }
        is LockState.Cooldown -> {
            val secs = ((state.untilMillis - nowMillis + 999) / 1000).coerceAtLeast(1)
            OverlayContent(
                title = "Take a breath",
                body = "$section opens in $secs seconds.",
                primaryLabel = null,
                primaryRoute = null,
            )
        }
        else -> null
    }
}

/**
 * A calm full-screen card drawn as an accessibility overlay, so no "draw over other apps" permission is needed.
 * It is not focusable: the system back gesture still reaches the app underneath.
 */
class BlockOverlay(
    private val service: AccessibilityService,
    private val onBack: () -> Unit,
    private val onOpenRoute: (String) -> Unit,
) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private var view: View? = null
    private var shown: OverlayContent? = null

    val isShowing: Boolean get() = view != null

    fun show(content: OverlayContent) {
        val v = view ?: inflate().also { view = it }
        if (shown == content) return
        shown = content
        v.findViewById<TextView>(R.id.title).text = content.title
        v.findViewById<TextView>(R.id.body).text = content.body
        val primary = v.findViewById<TextView>(R.id.primary)
        if (content.primaryLabel != null && content.primaryRoute != null) {
            primary.visibility = View.VISIBLE
            primary.text = content.primaryLabel
            primary.setOnClickListener { onOpenRoute(content.primaryRoute) }
        } else {
            primary.visibility = View.GONE
        }
    }

    fun hide() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        shown = null
    }

    private fun inflate(): View {
        val v = LayoutInflater.from(service).inflate(R.layout.overlay_block, null)
        v.findViewById<TextView>(R.id.secondary).setOnClickListener { onBack() }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        v.alpha = 0f
        wm.addView(v, params)
        v.animate().alpha(1f).setDuration(250).start()
        return v
    }
}
