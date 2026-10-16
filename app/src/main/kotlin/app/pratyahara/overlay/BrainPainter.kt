package app.pratyahara.overlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.ColorUtils
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Draws the BasKar brain: a soft, bumpy brain with a face that goes from happy and pink to dizzy,
 * swampy and dripping as [health] drops. Shared by the Reels meter and the Home card so they match.
 *
 * Everything is driven by the clock ([timeMs]), so callers just redraw every frame for smooth motion.
 */
class BrainPainter {
    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fold = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val eyeWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1B1B1F") }
    private val inkLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1B1B1F"); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val blush = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tongue = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E5626F") }
    private val path = Path()
    private val rect = RectF()

    /**
     * Draws centred on the origin with radius [r]. [health] is 0..1 (it may be a smoothed value).
     * [popMs] is how long ago the last reel arrived, or a negative number for none.
     */
    fun draw(c: Canvas, r: Float, health: Float, timeMs: Long, popMs: Long = -1) {
        val h = health.coerceIn(0f, 1f)
        val color = colorFor(h)
        body.color = color
        shade.color = ColorUtils.blendARGB(color, Color.BLACK, 0.12f)
        fold.color = ColorUtils.blendARGB(color, Color.BLACK, 0.32f)
        fold.strokeWidth = r * 0.075f
        inkLine.strokeWidth = r * 0.075f

        val s = timeMs / 1000f
        // Breathing squash, a little sloppier as it rots, plus a springy squish on every new reel.
        val breathe = 0.035f * sin(2 * PI.toFloat() * s / (2.6f - (1 - h) * 0.8f))
        val spring = if (popMs in 0..900) 0.22f * exp(-popMs / 180f) * cos(popMs / 38f) else 0f
        val sway = (1.5f + (1 - h) * 5f) * sin(2 * PI.toFloat() * s / 3.4f)

        c.save()
        c.rotate(sway)
        c.scale(1 + breathe + spring, 1 - breathe - spring * 0.8f, 0f, r * 0.7f)

        drawBody(c, r)
        drawFolds(c, r)
        if (h < 0.6f) drawDrips(c, r, h, s)
        drawFace(c, r, h, s)
        c.restore()
    }

    private fun drawBody(c: Canvas, r: Float) {
        // A soft base with five bumps on top: reads as a brain without needing detail.
        rect.set(-r * 0.95f, -r * 0.55f, r * 0.95f, r * 0.82f)
        c.drawRoundRect(rect, r * 0.6f, r * 0.6f, body)
        c.drawCircle(-r * 0.58f, -r * 0.42f, r * 0.42f, body)
        c.drawCircle(0f, -r * 0.6f, r * 0.46f, body)
        c.drawCircle(r * 0.58f, -r * 0.42f, r * 0.42f, body)
        c.drawCircle(-r * 0.8f, r * 0.05f, r * 0.36f, body)
        c.drawCircle(r * 0.8f, r * 0.05f, r * 0.36f, body)
        // A soft shadow along the bottom gives it some volume.
        rect.set(-r * 0.8f, r * 0.55f, r * 0.8f, r * 0.84f)
        c.drawRoundRect(rect, r * 0.2f, r * 0.2f, shade)
    }

    private fun drawFolds(c: Canvas, r: Float) {
        path.reset()
        path.moveTo(0f, -r * 0.98f); path.quadTo(-r * 0.1f, -r * 0.7f, 0f, -r * 0.42f)
        path.moveTo(-r * 0.75f, -r * 0.45f); path.quadTo(-r * 0.55f, -r * 0.7f, -r * 0.35f, -r * 0.5f)
        path.moveTo(r * 0.75f, -r * 0.45f); path.quadTo(r * 0.55f, -r * 0.7f, r * 0.35f, -r * 0.5f)
        path.moveTo(-r * 0.3f, -r * 0.82f); path.quadTo(-r * 0.22f, -r * 0.65f, -r * 0.32f, -r * 0.55f)
        path.moveTo(r * 0.3f, -r * 0.82f); path.quadTo(r * 0.22f, -r * 0.65f, r * 0.32f, -r * 0.55f)
        c.drawPath(path, fold)
    }

    private fun drawDrips(c: Canvas, r: Float, h: Float, s: Float) {
        val drips = if (h < 0.25f) 3 else if (h < 0.45f) 2 else 1
        for (i in 0 until drips) {
            val phase = ((s / 1.8f) + i * 0.37f) % 1f
            val eased = phase * phase
            val x = -r * 0.42f + i * r * 0.42f
            val y = r * 0.72f + eased * r * 0.5f
            val dr = r * 0.1f * (1 - phase * 0.5f)
            body.alpha = (255 * (1 - phase * 0.6f)).toInt()
            c.drawCircle(x, y, dr, body)
            body.alpha = 255
        }
    }

    private fun drawFace(c: Canvas, r: Float, h: Float, s: Float) {
        val eyeY = r * 0.08f
        val eyeX = r * 0.32f
        val eyeR = r * 0.17f

        if (h > 0.6f) {
            blush.color = ColorUtils.setAlphaComponent(Color.parseColor("#FF7A9A"), (120 * (h - 0.6f) / 0.4f).toInt())
            c.drawOval(-eyeX - r * 0.32f, eyeY + r * 0.16f, -eyeX - r * 0.06f, eyeY + r * 0.3f, blush)
            c.drawOval(eyeX + r * 0.06f, eyeY + r * 0.16f, eyeX + r * 0.32f, eyeY + r * 0.3f, blush)
        }

        if (h < 0.15f) {
            // Fully rotten: dizzy spiral eyes.
            for (side in listOf(-1f, 1f)) {
                c.save()
                c.translate(side * eyeX, eyeY)
                c.drawCircle(0f, 0f, eyeR * 1.05f, eyeWhite)
                c.rotate(s * 240f * side)
                path.reset()
                for (k in 0..24) {
                    val a = k / 24f * 4 * PI.toFloat()
                    val rr = eyeR * k / 24f
                    if (k == 0) path.moveTo(0f, 0f) else path.lineTo(rr * cos(a), rr * sin(a))
                }
                c.drawPath(path, inkLine)
                c.restore()
            }
        } else {
            // Blink about every 3.5 seconds, and let the pupils wander a little.
            val cycle = s % 3.5f
            val blink = if (cycle < 0.16f) sin(PI.toFloat() * cycle / 0.16f) else 0f
            val open = (1f - blink).coerceAtLeast(0.08f)
            val lookX = eyeR * 0.35f * sin(s * 0.9f)
            val lookY = eyeR * 0.2f * sin(s * 0.6f + 1f)
            // Heavy lids once the brain gets mushy.
            val lid = if (h < 0.5f) (0.5f - h) * 1.1f else 0f
            for (side in listOf(-1f, 1f)) {
                val x = side * eyeX
                c.save()
                c.scale(1f, open, x, eyeY)
                c.drawCircle(x, eyeY, eyeR, eyeWhite)
                c.drawCircle(x + lookX, eyeY + lookY + lid * eyeR * 0.6f, eyeR * 0.5f, ink)
                c.drawCircle(x + lookX + eyeR * 0.18f, eyeY + lookY - eyeR * 0.18f + lid * eyeR * 0.6f, eyeR * 0.15f, eyeWhite)
                if (lid > 0f) {
                    rect.set(x - eyeR * 1.1f, eyeY - eyeR * 1.1f, x + eyeR * 1.1f, eyeY - eyeR + lid * eyeR * 2f)
                    c.drawRect(rect, body)
                    c.drawLine(x - eyeR, rect.bottom, x + eyeR, rect.bottom, inkLine)
                }
                c.restore()
            }
        }

        // Mouth: a grin, then a flat line, then a wobbly frown, then an open mouth with its tongue out.
        val my = r * 0.46f
        val mw = r * 0.26f
        path.reset()
        when {
            h >= 0.75f -> {
                path.moveTo(-mw, my - r * 0.04f); path.quadTo(0f, my + r * 0.2f, mw, my - r * 0.04f)
                c.drawPath(path, inkLine)
            }
            h >= 0.5f -> {
                path.moveTo(-mw * 0.8f, my); path.quadTo(0f, my + r * 0.05f, mw * 0.8f, my)
                c.drawPath(path, inkLine)
            }
            h >= 0.3f -> {
                val wob = r * 0.05f * sin(s * 6f)
                path.moveTo(-mw, my + r * 0.04f)
                path.cubicTo(-mw * 0.4f, my - r * 0.08f + wob, mw * 0.4f, my + r * 0.1f - wob, mw, my - r * 0.02f)
                c.drawPath(path, inkLine)
            }
            else -> {
                rect.set(-mw * 0.7f, my - r * 0.08f, mw * 0.7f, my + r * 0.14f)
                c.drawOval(rect, ink)
                val lick = r * 0.04f * sin(s * 3f)
                c.drawOval(-mw * 0.35f, my + r * 0.02f, mw * 0.35f, my + r * 0.22f + lick, tongue)
            }
        }
    }

    companion object {
        /** Pink when fresh, swamp green when foggy, olive when rotten. */
        fun colorFor(health: Float): Int {
            val h = health.coerceIn(0f, 1f)
            val pink = Color.parseColor("#F6A9C4")
            val green = Color.parseColor("#A3C95A")
            val olive = Color.parseColor("#77773A")
            return if (h > 0.5f) ColorUtils.blendARGB(green, pink, (h - 0.5f) * 2) else ColorUtils.blendARGB(olive, green, h * 2)
        }
    }
}
