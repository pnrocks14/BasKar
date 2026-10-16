package app.pratyahara.overlay

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.pratyahara.R
import kotlin.math.abs

/** What the three pills say when the BasKar button is tapped. */
data class BubbleInfo(val countdown: String, val opened: String, val quote: String)

/**
 * A small round BasKar button on the right edge, shown only while a limited app (or BasKar itself)
 * is on screen. Tap it and three pills slide out to its left: a live countdown, how many times Reels
 * was opened today, and the latest quote. Tap again or anywhere else to close them. Drag it up or down.
 */
class FloatingBubble(
    private val service: AccessibilityService,
    private val info: () -> BubbleInfo,
    private val onOpenApp: () -> Unit,
) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var view: View? = null
    private var pills: LinearLayout? = null
    private var countdownText: TextView? = null
    private var y = -1
    private val size get() = dp(48)

    private val tick = object : Runnable {
        override fun run() {
            countdownText?.text = info().countdown
            handler.postDelayed(this, 1_000)
        }
    }
    private val autoClose = Runnable { collapse() }
    private var closedByOutsideAt = 0L

    val isShowing: Boolean get() = view != null

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (view != null) return
        if (y < 0) y = service.resources.displayMetrics.heightPixels / 3
        val bubble = ImageView(service).apply {
            setImageResource(R.drawable.ic_hand)
            setColorFilter(service.getColor(R.color.sun))
            setPadding(dp(11), dp(11), dp(11), dp(11))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E6111111"))
            }
            elevation = dp(4).toFloat()
            contentDescription = "BasKar: time left today"
            alpha = 0.92f
        }
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(6)
            this.y = this@FloatingBubble.y
        }
        var downRawY = 0f
        var startY = 0
        var dragged = false
        bubble.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawY = e.rawY
                    startY = params.y
                    dragged = false
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(90).start()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = e.rawY - downRawY
                    if (abs(dy) > dp(8)) {
                        if (!dragged) collapse()
                        dragged = true
                    }
                    if (dragged) {
                        val max = service.resources.displayMetrics.heightPixels - size
                        params.y = (startY + dy.toInt()).coerceIn(0, max)
                        y = params.y
                        runCatching { wm.updateViewLayout(v, params) }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setInterpolator(OvershootInterpolator(3f)).setDuration(220).start()
                    if (!dragged && e.actionMasked == MotionEvent.ACTION_UP) toggle()
                }
            }
            true
        }
        runCatching { wm.addView(bubble, params) }.onFailure { return }
        view = bubble
    }

    fun hide() {
        collapse(animate = false)
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }

    private fun toggle() {
        when {
            pills != null -> collapse()
            // The tap that closed the pills from outside was on this button: don't reopen them.
            System.currentTimeMillis() - closedByOutsideAt < 600 -> Unit
            else -> expand()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun expand() {
        val data = info()
        val count = pill(R.drawable.ic_timer, data.countdown, bold = true)
        countdownText = count.getChildAt(1) as TextView
        val stack = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            addView(count)
            addView(pill(R.drawable.ic_eye, data.opened), spaced())
            addView(pill(R.drawable.ic_quote, data.quote, quote = true), spaced())
            setOnTouchListener { _, e ->
                if (e.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    closedByOutsideAt = System.currentTimeMillis()
                    collapse()
                }
                false
            }
        }
        // Lay it out first so the stack can be centred on the button.
        val maxW = service.resources.displayMetrics.widthPixels - size - dp(32)
        stack.measure(
            View.MeasureSpec.makeMeasureSpec(maxW, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val screenH = service.resources.displayMetrics.heightPixels
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(6) + size + dp(8)
            this.y = (this@FloatingBubble.y + size / 2 - stack.measuredHeight / 2).coerceIn(dp(24), (screenH - stack.measuredHeight - dp(24)).coerceAtLeast(dp(24)))
        }
        runCatching { wm.addView(stack, params) }.onFailure { return }
        pills = stack
        // Slide out from the button one after another.
        for (i in 0 until stack.childCount) {
            stack.getChildAt(i).apply {
                alpha = 0f
                translationX = dp(36).toFloat()
                scaleX = 0.9f
                scaleY = 0.9f
                animate().alpha(1f).translationX(0f).scaleX(1f).scaleY(1f)
                    .setStartDelay(i * 55L).setDuration(260).setInterpolator(DecelerateInterpolator(2f)).start()
            }
        }
        handler.postDelayed(tick, 1_000)
        handler.postDelayed(autoClose, 7_000)
    }

    private fun collapse(animate: Boolean = true) {
        handler.removeCallbacks(tick)
        handler.removeCallbacks(autoClose)
        val stack = pills ?: return
        pills = null
        countdownText = null
        if (!animate) {
            runCatching { wm.removeView(stack) }
            return
        }
        for (i in 0 until stack.childCount) {
            stack.getChildAt(i).animate().alpha(0f).translationX(dp(24).toFloat())
                .setStartDelay((stack.childCount - 1 - i) * 35L).setDuration(160).start()
        }
        handler.postDelayed({ runCatching { wm.removeView(stack) } }, 280)
    }

    private fun pill(icon: Int, text: String, bold: Boolean = false, quote: Boolean = false): LinearLayout =
        LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(9), dp(14), dp(9))
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(Color.parseColor("#F0111111"))
                setStroke(dp(1), Color.parseColor("#33FFB547"))
            }
            elevation = dp(3).toFloat()
            addView(ImageView(service).apply {
                setImageResource(icon)
                setColorFilter(service.getColor(R.color.sun))
            }, LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(8) })
            addView(TextView(service).apply {
                this.text = text
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (quote) 13f else 15f)
                if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                if (quote) {
                    setTypeface(typeface, Typeface.ITALIC)
                    maxWidth = dp(220)
                    maxLines = 3
                    ellipsize = TextUtils.TruncateAt.END
                    setLineSpacing(0f, 1.1f)
                } else {
                    maxLines = 1
                }
            })
            setOnClickListener {
                collapse()
                onOpenApp()
            }
        }

    private fun spaced() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(8) }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), service.resources.displayMetrics).toInt()
}
