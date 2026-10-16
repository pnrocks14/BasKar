package app.pratyahara.overlay

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.graphics.ColorUtils
import app.pratyahara.core.reels.BrainState
import kotlin.math.exp

/**
 * A small pill at the top centre while Reels or Shorts plays: the brain with its face, and today's reel count.
 * Each reel makes the brain squish and the count tick up; the brain slowly rots as the count grows.
 * It never takes touches, so it can't get in the way.
 */
class BrainRotMeter(private val service: AccessibilityService) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private var view: MeterView? = null

    val isShowing: Boolean get() = view != null

    /** Height of the pill plus its top margin, so other chips can sit below it. */
    val bottomPx: Int get() = dp(service, TOP_DP + 8) + (view?.height ?: dp(service, 44))

    fun show(reelsToday: Int) {
        view?.let { it.setReels(reelsToday, pop = false); return }
        val v = MeterView(service).apply { setReels(reelsToday, pop = false, snap = true) }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(service, TOP_DP)
        }
        runCatching { wm.addView(v, params) }.onFailure { return }
        view = v
        v.alpha = 0f
        v.scaleX = 0.8f
        v.scaleY = 0.8f
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260).start()
        v.start()
    }

    /** One more reel. */
    fun bump(reelsToday: Int) {
        view?.setReels(reelsToday, pop = true)
    }

    fun hide() {
        view?.let {
            it.stop()
            runCatching { wm.removeView(it) }
        }
        view = null
    }

    @SuppressLint("ViewConstructor")
    private class MeterView(context: Context) : View(context), Choreographer.FrameCallback {
        private val painter = BrainPainter()
        private var reels = 0
        private var shownHealth = 1f
        private var targetHealth = 1f
        private var shownCount = 0f
        private var popAt = -1L
        private var lastFrame = 0L
        private var running = false

        private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E6111111") }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(context, 1.5f) }
        private val count = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = sp(context, 20f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val plus = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sp(context, 13f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val brainBox = dp(context, 34f)
        private val padV = dp(context, 5f)
        private val padH = dp(context, 8f)
        private val gap = dp(context, 6f)
        private val rect = RectF()

        init {
            setLayerType(LAYER_TYPE_HARDWARE, null)
        }

        fun setReels(n: Int, pop: Boolean, snap: Boolean = false) {
            val widthChanged = digits(n) != digits(reels)
            reels = n
            targetHealth = BrainState.health(n) / 100f
            if (snap) {
                shownHealth = targetHealth
                shownCount = n.toFloat()
            }
            if (pop) popAt = System.currentTimeMillis()
            contentDescription = "Reel $n today, brain ${BrainState.health(n)}%"
            if (widthChanged) requestLayout()
        }

        fun start() {
            if (running) return
            running = true
            lastFrame = 0L
            Choreographer.getInstance().postFrameCallback(this)
        }

        fun stop() {
            running = false
            Choreographer.getInstance().removeFrameCallback(this)
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            val now = frameTimeNanos / 1_000_000
            val dt = if (lastFrame == 0L) 16f else (now - lastFrame).coerceIn(1, 100).toFloat()
            lastFrame = now
            // Ease the colour and the number towards their new values instead of jumping.
            val k = 1f - exp(-dt / 260f)
            shownHealth += (targetHealth - shownHealth) * k
            shownCount += (reels - shownCount) * (1f - exp(-dt / 120f))
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }

        private fun digits(n: Int) = n.toString().length

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val textW = count.measureText("0".repeat(digits(reels).coerceAtLeast(2)))
            setMeasuredDimension((padH * 2 + brainBox + gap + textW + padH * 0.5f).toInt(), (brainBox + padV * 2).toInt())
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val color = BrainPainter.colorFor(shownHealth)
            rect.set(0f, 0f, w, h)
            canvas.drawRoundRect(rect, h / 2, h / 2, bg)
            ring.color = ColorUtils.setAlphaComponent(color, 110)
            rect.inset(ring.strokeWidth / 2, ring.strokeWidth / 2)
            canvas.drawRoundRect(rect, h / 2, h / 2, ring)

            val now = System.currentTimeMillis()
            val sincePop = if (popAt > 0) now - popAt else -1
            val cx = padH + brainBox / 2
            val cy = h / 2
            canvas.save()
            canvas.translate(cx, cy + brainBox * 0.04f)
            painter.draw(canvas, brainBox * 0.44f, shownHealth, now, sincePop)
            canvas.restore()

            // The number rolls up smoothly to the new count.
            val shown = kotlin.math.round(shownCount).toInt()
            val tx = padH + brainBox + gap
            val ty = cy - (count.descent() + count.ascent()) / 2
            canvas.drawText(shown.toString(), tx, ty, count)

            if (sincePop in 0..800) {
                val f = sincePop / 800f
                plus.color = ColorUtils.setAlphaComponent(color, (255 * (1 - f)).toInt())
                canvas.drawText("+1", cx + brainBox * 0.25f, cy - brainBox * 0.12f - f * padV * 2, plus)
            }
        }
    }

    private companion object {
        const val TOP_DP = 40
        fun dp(c: Context, v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), c.resources.displayMetrics).toInt()
        fun dp(c: Context, v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.resources.displayMetrics)
        fun sp(c: Context, v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, c.resources.displayMetrics)
    }
}
