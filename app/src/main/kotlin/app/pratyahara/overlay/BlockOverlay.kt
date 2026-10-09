package app.pratyahara.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import app.pratyahara.R
import app.pratyahara.core.buddy.Quote
import app.pratyahara.core.buddy.Quotes
import app.pratyahara.core.lock.FocusHours
import app.pratyahara.core.lock.LockState
import app.pratyahara.core.lock.UnlockRules
import app.pratyahara.core.tasks.TaskGate

/** What the overlay should say, worked out by the service from the current [LockState]. */
data class OverlayContent(
    @DrawableRes val icon: Int,
    val title: String,
    val body: String,
    val primaryLabel: String?,
    val primaryRoute: String?,
    val quote: Quote? = null,
)

object OverlayCopy {
    fun forState(state: LockState, budgetMinutes: Int, section: String, squats: Int, nowMillis: Long): OverlayContent? {
        // A new quote every few minutes, the same one while it's on screen.
        val quote = Quotes.pick(nowMillis / (5 * 60_000))
        return when (state) {
            is LockState.BudgetLocked -> OverlayContent(
                icon = R.drawable.ic_lotus,
                title = "That's your $budgetMinutes minutes for today",
                body = if (state.unlocksLeft > 0) {
                    "$section is done until tomorrow. Your feed, messages and profile still work. If something really can't wait, earn ${UnlockRules.UNLOCK_MINUTES} more minutes by moving first."
                } else {
                    "$section is done until tomorrow. Your feed, messages and profile still work. Go do something your future self will thank you for."
                },
                primaryLabel = if (state.unlocksLeft > 0) "Earn ${UnlockRules.UNLOCK_MINUTES} min with $squats squats" else null,
                primaryRoute = if (state.unlocksLeft > 0) "unlock" else null,
                quote = quote,
            )
            is LockState.TaskLocked -> when (state.gate) {
                TaskGate.MISSED_CHECKIN -> OverlayContent(
                    icon = R.drawable.ic_edit,
                    title = "Yesterday's check-in is still open",
                    body = "Tell past you how it went, then set one task for next. $section opens right after.",
                    primaryLabel = "Answer and write a task",
                    primaryRoute = "task",
                    quote = quote,
                )
                else -> OverlayContent(
                    icon = R.drawable.ic_edit,
                    title = "One small task first",
                    body = "Write down one thing you'll do next. The moment it's saved, $section is back.",
                    primaryLabel = "Write my task",
                    primaryRoute = "task",
                    quote = quote,
                )
            }
            is LockState.FocusLocked -> OverlayContent(
                icon = R.drawable.ic_moon,
                title = "Focus hours until ${FocusHours.format(state.endMinute)}",
                body = "$section stays closed during the hours you set. Everything else in the app works.",
                primaryLabel = null,
                primaryRoute = null,
                quote = quote,
            )
            is LockState.Cooldown -> {
                val secs = ((state.untilMillis - nowMillis + 999) / 1000).coerceAtLeast(1)
                OverlayContent(
                    icon = R.drawable.ic_lotus,
                    title = "Take a breath",
                    body = "$section opens in $secs seconds.",
                    primaryLabel = null,
                    primaryRoute = null,
                )
            }
            else -> null
        }
    }
}

/**
 * A full-screen pause screen drawn as an accessibility overlay, so no "draw over other apps" permission is needed.
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
        v.findViewById<ImageView>(R.id.icon).setImageResource(content.icon)
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
        val card = v.findViewById<View>(R.id.quote_card)
        if (content.quote != null) {
            card.visibility = View.VISIBLE
            v.findViewById<TextView>(R.id.quote).text = content.quote.text
            v.findViewById<TextView>(R.id.quote_source).text = "— ${content.quote.source}"
        } else {
            card.visibility = View.GONE
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
