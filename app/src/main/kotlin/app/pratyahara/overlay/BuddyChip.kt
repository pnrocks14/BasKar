package app.pratyahara.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import app.pratyahara.core.buddy.Chip

/**
 * The little message that slides in at the top while you scroll (a hello, a quote, a heads-up).
 * Drawn as an accessibility overlay. It ignores touches unless it has an [onTap], so scrolling carries on underneath.
 */
class BuddyChip(private val service: AccessibilityService) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var view: View? = null
    private val hideRunnable = Runnable { hide() }

    val isShowing: Boolean get() = view != null

    fun show(chip: Chip, durationMs: Long = defaultDuration(chip), onTap: (() -> Unit)? = null) {
        hide()
        val v = build(chip, onTap)
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            (if (onTap == null) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            y = dp(44)
        }
        runCatching { wm.addView(v, params) }.onFailure { return }
        view = v
        v.alpha = 0f
        v.translationY = -dp(24).toFloat()
        v.animate().alpha(1f).translationY(0f).setDuration(220).start()
        handler.postDelayed(hideRunnable, durationMs)
    }

    fun hide() {
        handler.removeCallbacks(hideRunnable)
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }

    private fun defaultDuration(chip: Chip) = when (chip.kind) {
        Chip.Kind.QUOTE -> 8_000L
        else -> 4_500L
    }

    private fun build(chip: Chip, onTap: (() -> Unit)?): View {
        val card = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(Color.parseColor("#F2111111"))
            }
            elevation = dp(6).toFloat()
            if (onTap != null) setOnClickListener { hide(); onTap() }
        }
        card.addView(TextView(service).apply {
            text = chip.title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (chip.kind == Chip.Kind.QUOTE) 15f else 17f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        })
        chip.subtitle?.let { sub ->
            card.addView(TextView(service).apply {
                text = if (chip.kind == Chip.Kind.QUOTE) "— $sub" else sub
                setTextColor(Color.parseColor("#D4F76A"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dp(3), 0, 0)
            })
        }
        return LinearLayout(service).apply {
            setPadding(dp(14), 0, dp(14), 0)
            addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), service.resources.displayMetrics).toInt()
}
