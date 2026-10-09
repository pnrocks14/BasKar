package app.pratyahara.overlay

import android.accessibilityservice.AccessibilityService
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.pratyahara.R

/** What the breathing pause says. */
data class PauseContent(val title: String, val body: String, val continueLabel: String, val seconds: Int)

/**
 * The breathing pause in front of Reels/Shorts: a slowly pulsing lotus, a few seconds of waiting, then a
 * choice. Same window type and colours as [BlockOverlay]. Nothing behind it plays while it's up.
 */
class PauseOverlay(
    private val service: AccessibilityService,
    private val onContinue: () -> Unit,
    private val onLeave: () -> Unit,
) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var view: View? = null
    private var pulse: ValueAnimator? = null

    val isShowing: Boolean get() = view != null

    fun show(content: PauseContent) {
        if (view != null) return
        val root = FrameLayout(service).apply {
            setBackgroundColor(service.getColor(R.color.overlay_bg))
            isClickable = true
        }
        val column = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(24), dp(24), dp(40))
        }
        val icon = ImageView(service).apply {
            setImageResource(R.drawable.ic_lotus)
            setColorFilter(service.getColor(R.color.on_lime))
            setBackgroundResource(R.drawable.overlay_icon_bg)
            setPadding(dp(22), dp(22), dp(22), dp(22))
        }
        column.addView(icon, LinearLayout.LayoutParams(dp(96), dp(96)))
        column.addView(text(content.title, 30f, bold = true, muted = false).apply { gravity = Gravity.CENTER }, block(top = 28))
        column.addView(text(content.body, 17f, bold = false, muted = true).apply { gravity = Gravity.CENTER }, block(top = 12))
        val breathe = text("Breathe in", 15f, bold = false, muted = false).apply {
            gravity = Gravity.CENTER
            setTextColor(service.getColor(R.color.lime))
        }
        column.addView(breathe, block(top = 20))

        val primary = button(content.continueLabel, primary = true)
        val secondary = button("Not now", primary = false)
        secondary.setOnClickListener { onLeave() }
        column.addView(primary, block(top = 36))
        column.addView(secondary, block(top = 10))
        root.addView(column, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        runCatching { wm.addView(root, params) }.onFailure { return }
        view = root
        root.alpha = 0f
        root.animate().alpha(1f).setDuration(250).start()

        // Four seconds in, four seconds out.
        pulse = ObjectAnimator.ofPropertyValuesHolder(
            icon,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.85f, 1.15f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.85f, 1.15f),
        ).apply {
            duration = 4_000
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addListener(object : android.animation.AnimatorListenerAdapter() {
                var inhale = true
                override fun onAnimationRepeat(animation: android.animation.Animator) {
                    inhale = !inhale
                    breathe.text = if (inhale) "Breathe in" else "Breathe out"
                }
            })
            start()
        }

        var left = content.seconds
        primary.isEnabled = false
        primary.alpha = 0.4f
        val tick = object : Runnable {
            override fun run() {
                if (view !== root) return
                if (left <= 0) {
                    primary.text = content.continueLabel
                    primary.isEnabled = true
                    primary.alpha = 1f
                    primary.setOnClickListener { onContinue() }
                    return
                }
                primary.text = "${content.continueLabel} in $left"
                left--
                handler.postDelayed(this, 1_000)
            }
        }
        tick.run()
    }

    fun hide() {
        handler.removeCallbacksAndMessages(null)
        pulse?.cancel()
        pulse = null
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }

    private fun text(value: String, sp: Float, bold: Boolean, muted: Boolean) = TextView(service).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(service.getColor(if (muted) R.color.overlay_text_muted else R.color.overlay_text))
        if (bold) typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        setLineSpacing(0f, 1.12f)
    }

    private fun button(label: String, primary: Boolean) = TextView(service).apply {
        text = label
        gravity = Gravity.CENTER
        minHeight = dp(56)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        setBackgroundResource(if (primary) R.drawable.overlay_button_primary else R.drawable.overlay_button_secondary)
        setTextColor(service.getColor(if (primary) R.color.on_lime else R.color.overlay_text))
        if (primary) typeface = Typeface.DEFAULT_BOLD
    }

    private fun block(top: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), service.resources.displayMetrics).toInt()
}
