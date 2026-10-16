package app.pratyahara.overlay

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import androidx.core.graphics.ColorUtils
import app.pratyahara.core.reels.BrainState
import kotlin.math.PI
import kotlin.math.sin

/**
 * A small animated brain in the top corner while Reels or Shorts plays, with the day's reel count.
 * Each reel makes it pop, and the brain slowly turns from pink to swampy green, then starts to drip.
 * It never takes touches, so it can't get in the way.
 */
class BrainRotMeter(private val service: AccessibilityService) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private var view: MeterView? = null

    val isShowing: Boolean get() = view != null

    fun show(reelsToday: Int) {
        view?.let { it.setReels(reelsToday, pop = false); return }
        val v = MeterView(service).apply { setReels(reelsToday, pop = false) }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(service, 12)
            y = dp(service, 44)
        }
        runCatching { wm.addView(v, params) }.onFailure { return }
        view = v
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
    private class MeterView(context: Context) : View(context) {
        private var reels = 0
        private var t = 0f
        private var popStart = -1L
        private val ticker = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2_400
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { t = it.animatedValue as Float; invalidate() }
        }

        private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E6111111") }
        private val brain = Paint(Paint.ANTI_ALIAS_FLAG)
        private val folds = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = dp(context, 2).toFloat()
        }
        private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = sp(context, 17f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sp(context, 12.5f) }
        private val plus = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sp(context, 15f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val brainBox = dp(context, 44).toFloat()
        private val pad = dp(context, 10).toFloat()

        fun setReels(n: Int, pop: Boolean) {
            reels = n
            if (pop) popStart = System.currentTimeMillis()
            contentDescription = "Reel $n today"
            requestLayout()
            invalidate()
        }

        fun start() = ticker.start()
        fun stop() = ticker.cancel()

        private fun line1() = "Reel $reels"
        private fun line2() = "Brain ${BrainState.health(reels)}% · ${BrainState.of(reels).label}"

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val text = maxOf(title.measureText(line1()), sub.measureText(line2()))
            setMeasuredDimension((pad * 3 + brainBox + text + pad).toInt(), (brainBox + pad * 2).toInt())
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            canvas.drawRoundRect(RectF(0f, 0f, w, h), h / 2, h / 2, bg)

            // Healthy pink, then swamp green, then a rotten olive.
            val health = BrainState.health(reels) / 100f
            val color = if (health > 0.5f) {
                ColorUtils.blendARGB(Color.parseColor("#9BC53D"), Color.parseColor("#F4A7C3"), (health - 0.5f) * 2)
            } else {
                ColorUtils.blendARGB(Color.parseColor("#6B6B2A"), Color.parseColor("#9BC53D"), health * 2)
            }
            brain.color = color
            folds.color = ColorUtils.blendARGB(color, Color.BLACK, 0.35f)
            sub.color = color

            // A gentle wobble that gets sloppier as the brain rots, and a pop on every new reel.
            val sinceePop = System.currentTimeMillis() - popStart
            val popScale = if (popStart > 0 && sinceePop < 420) 1f + 0.28f * sin(PI * sinceePop / 420.0).toFloat() else 1f
            val wobble = (3f + (1f - health) * 9f) * sin(2 * PI * t).toFloat()
            val cx = pad * 1.5f + brainBox / 2
            val cy = h / 2
            canvas.save()
            canvas.translate(cx, cy)
            canvas.rotate(wobble)
            canvas.scale(popScale, popScale)
            drawBrain(canvas, brainBox / 2, health)
            canvas.restore()

            if (popStart > 0 && sinceePop < 700) {
                val f = sinceePop / 700f
                plus.color = ColorUtils.setAlphaComponent(color, (255 * (1 - f)).toInt())
                canvas.drawText("+1", cx + brainBox / 3, cy - brainBox / 4 - f * pad * 2, plus)
            }

            val tx = pad * 2.5f + brainBox
            canvas.drawText(line1(), tx, cy - dp(context, 2), title)
            canvas.drawText(line2(), tx, cy + sub.textSize + dp(context, 1), sub)
        }

        /** Two lobes with a few folds, centred on the origin. Drips start once it's melting. */
        private fun drawBrain(c: Canvas, r: Float, health: Float) {
            val lobe = r * 0.62f
            c.drawOval(RectF(-r * 0.95f, -lobe, r * 0.05f, lobe), brain)
            c.drawOval(RectF(-r * 0.05f, -lobe, r * 0.95f, lobe), brain)
            c.drawOval(RectF(-r * 0.6f, -r * 0.8f, r * 0.6f, r * 0.1f), brain)
            val p = Path()
            p.moveTo(0f, -r * 0.7f); p.cubicTo(-r * 0.12f, -r * 0.3f, r * 0.12f, r * 0.1f, 0f, r * 0.55f)
            p.moveTo(-r * 0.7f, -r * 0.15f); p.cubicTo(-r * 0.5f, -r * 0.45f, -r * 0.3f, -r * 0.05f, -r * 0.15f, -r * 0.35f)
            p.moveTo(-r * 0.65f, r * 0.3f); p.cubicTo(-r * 0.45f, r * 0.05f, -r * 0.3f, r * 0.45f, -r * 0.12f, r * 0.2f)
            p.moveTo(r * 0.7f, -r * 0.15f); p.cubicTo(r * 0.5f, -r * 0.45f, r * 0.3f, -r * 0.05f, r * 0.15f, -r * 0.35f)
            p.moveTo(r * 0.65f, r * 0.3f); p.cubicTo(r * 0.45f, r * 0.05f, r * 0.3f, r * 0.45f, r * 0.12f, r * 0.2f)
            c.drawPath(p, folds)
            if (health < 0.6f) {
                // Up to three drips, each sliding down and starting over.
                val drips = if (health < 0.25f) 3 else if (health < 0.45f) 2 else 1
                for (i in 0 until drips) {
                    val phase = (t + i / 3f) % 1f
                    val x = -r * 0.45f + i * r * 0.45f
                    val y = lobe * 0.8f + phase * r * 0.55f
                    c.drawCircle(x, y, r * 0.11f * (1 - phase * 0.4f), brain)
                }
            }
        }
    }

    private companion object {
        fun dp(c: Context, v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), c.resources.displayMetrics).toInt()
        fun sp(c: Context, v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, c.resources.displayMetrics)
    }
}
