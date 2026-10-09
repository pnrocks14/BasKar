package app.pratyahara.overlay

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import app.pratyahara.R
import kotlin.math.abs

/**
 * A small round Pratyahara button on the right edge, shown only while a limited app (or Pratyahara itself)
 * is on screen. Tap it for today's status; drag it up or down out of the way.
 */
class FloatingBubble(private val service: AccessibilityService, private val onTap: () -> Unit) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private var view: View? = null
    private var y = -1

    val isShowing: Boolean get() = view != null

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (view != null) return
        val size = dp(48)
        if (y < 0) y = service.resources.displayMetrics.heightPixels / 3
        val bubble = ImageView(service).apply {
            setImageResource(R.drawable.ic_lotus)
            setColorFilter(service.getColor(R.color.lime))
            setPadding(dp(11), dp(11), dp(11), dp(11))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E6111111"))
            }
            elevation = dp(4).toFloat()
            contentDescription = "Pratyahara: time left today"
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
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = e.rawY - downRawY
                    if (abs(dy) > dp(8)) dragged = true
                    if (dragged) {
                        val max = service.resources.displayMetrics.heightPixels - size
                        params.y = (startY + dy.toInt()).coerceIn(0, max)
                        y = params.y
                        runCatching { wm.updateViewLayout(v, params) }
                    }
                }
                MotionEvent.ACTION_UP -> if (!dragged) onTap()
            }
            true
        }
        runCatching { wm.addView(bubble, params) }.onFailure { return }
        view = bubble
    }

    fun hide() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), service.resources.displayMetrics).toInt()
}
