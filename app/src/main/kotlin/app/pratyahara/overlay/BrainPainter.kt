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
 * Draws the BasKar brain: a soft, bumpy brain with a face. As [health] falls it rots gradually, with no
 * jumps between stages: the pink fades to swamp green, spots spread, the grin sags into a frown, the eyelids
 * droop, drips appear, and at the very end the eyes go dizzy and the tongue lolls out.
 * Shared by the Reels meter and the Home card so they match.
 *
 * All motion is slow and driven by [timeMs], so callers just redraw every frame.
 */
class BrainPainter {
    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fold = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val eyeWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK }
    private val inkLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = INK; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val blush = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tongue = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    /**
     * Draws centred on the origin with radius [r]. [health] is 0..1 and may be a smoothed value.
     * [popMs] is how long ago the last reel arrived, or a negative number for none.
     */
    fun draw(c: Canvas, r: Float, health: Float, timeMs: Long, popMs: Long = -1) {
        val h = health.coerceIn(0f, 1f)
        val rot = 1f - h
        val color = colorFor(h)
        body.color = color
        shade.color = ColorUtils.blendARGB(color, Color.BLACK, 0.12f)
        fold.color = ColorUtils.blendARGB(color, Color.BLACK, 0.3f)
        fold.strokeWidth = r * 0.075f
        inkLine.strokeWidth = r * 0.075f

        val s = timeMs / 1000f
        // Slow breathing that gets a little heavier as it rots, and a soft squish on every new reel.
        val breathe = (0.02f + rot * 0.015f) * sin(TWO_PI * s / 4.2f)
        val squish = if (popMs in 0..1_200) 0.12f * exp(-popMs / 320f) * sin(popMs / 95f) else 0f
        val sway = (1f + rot * 3f) * sin(TWO_PI * s / 6.5f)
        // The rotten brain slumps a little lower.
        val slump = rot * rot * r * 0.06f

        c.save()
        c.translate(0f, slump)
        c.rotate(sway, 0f, r * 0.8f)
        c.scale(1 + breathe + squish, 1 - breathe - squish * 0.8f, 0f, r * 0.8f)

        drawBody(c, r)
        drawSpots(c, r, rot)
        drawFolds(c, r)
        drawDrips(c, r, rot, s)
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
        rect.set(-r * 0.8f, r * 0.55f, r * 0.8f, r * 0.84f)
        c.drawRoundRect(rect, r * 0.2f, r * 0.2f, shade)
    }

    /** Darker rot spots that fade in and grow, one after another. */
    private fun drawSpots(c: Canvas, r: Float, rot: Float) {
        for ((i, p) in SPOTS.withIndex()) {
            val start = 0.25f + i * 0.12f
            val f = ((rot - start) / 0.2f).coerceIn(0f, 1f)
            if (f <= 0f) continue
            spot.color = ColorUtils.setAlphaComponent(Color.parseColor("#4A4A1E"), (110 * f).toInt())
            c.drawCircle(p[0] * r, p[1] * r, p[2] * r * (0.5f + 0.5f * f), spot)
        }
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

    /** Slow drips that fade in from 40% rot, then multiply. Each one stretches, falls and fades. */
    private fun drawDrips(c: Canvas, r: Float, rot: Float, s: Float) {
        for (i in 0 until 3) {
            val f = ((rot - (0.4f + i * 0.15f)) / 0.12f).coerceIn(0f, 1f)
            if (f <= 0f) continue
            val phase = ((s / 3.2f) + i * 0.37f) % 1f
            val eased = phase * phase * (3 - 2 * phase)
            val x = -r * 0.42f + i * r * 0.42f
            val y = r * 0.74f + eased * r * 0.45f
            val dr = r * 0.09f * (1 - phase * 0.4f)
            body.alpha = (255 * f * (1 - phase * phase)).toInt()
            c.drawCircle(x, y, dr, body)
            body.alpha = 255
        }
    }

    private fun drawFace(c: Canvas, r: Float, h: Float, s: Float) {
        val rot = 1f - h
        val eyeY = r * 0.08f
        val eyeX = r * 0.32f
        val eyeR = r * 0.17f

        // Rosy cheeks while it's fresh, fading out.
        val cheeks = ((h - 0.55f) / 0.45f).coerceIn(0f, 1f)
        if (cheeks > 0f) {
            blush.color = ColorUtils.setAlphaComponent(Color.parseColor("#FF7A9A"), (120 * cheeks).toInt())
            c.drawOval(-eyeX - r * 0.32f, eyeY + r * 0.16f, -eyeX - r * 0.06f, eyeY + r * 0.3f, blush)
            c.drawOval(eyeX + r * 0.06f, eyeY + r * 0.16f, eyeX + r * 0.32f, eyeY + r * 0.3f, blush)
        }

        // Normal eyes fade into dizzy spirals over the last stretch.
        val dizzy = ((rot - 0.8f) / 0.12f).coerceIn(0f, 1f)
        // A slow, soft blink about every 5 seconds, and pupils that drift lazily.
        val cycle = s % 5f
        val blink = if (cycle < 0.28f) sin(PI.toFloat() * cycle / 0.28f) else 0f
        val open = (1f - blink * blink).coerceAtLeast(0.06f)
        val lookX = eyeR * 0.3f * sin(s * 0.45f)
        val lookY = eyeR * 0.15f * sin(s * 0.3f + 1f)
        // Eyelids start drooping at 30% rot and get heavy.
        // They lift again as the eyes go dizzy, so the spirals show.
        val lid = ((rot - 0.3f) / 0.5f).coerceIn(0f, 1f) * 0.55f * (1f - dizzy)
        for (side in listOf(-1f, 1f)) {
            val x = side * eyeX
            c.drawCircle(x, eyeY, eyeR, eyeWhite)
            if (dizzy < 1f) {
                c.save()
                c.scale(1f, open, x, eyeY)
                ink.alpha = (255 * (1 - dizzy)).toInt()
                eyeWhite.alpha = ink.alpha
                c.drawCircle(x + lookX, eyeY + lookY + lid * eyeR * 0.5f, eyeR * 0.5f, ink)
                c.drawCircle(x + lookX + eyeR * 0.18f, eyeY + lookY - eyeR * 0.18f + lid * eyeR * 0.5f, eyeR * 0.15f, eyeWhite)
                ink.alpha = 255
                eyeWhite.alpha = 255
                c.restore()
            }
            if (dizzy > 0f) {
                c.save()
                c.translate(x, eyeY)
                c.rotate(s * 70f * side)
                path.reset()
                for (k in 0..24) {
                    val a = k / 24f * 4 * PI.toFloat()
                    val rr = eyeR * 0.9f * k / 24f
                    if (k == 0) path.moveTo(0f, 0f) else path.lineTo(rr * cos(a), rr * sin(a))
                }
                inkLine.alpha = (255 * dizzy).toInt()
                c.drawPath(path, inkLine)
                inkLine.alpha = 255
                c.restore()
            }
            if (lid > 0f) {
                // The lid is the brain's own colour sliding down over the top of the eye.
                val bottom = eyeY - eyeR + lid * eyeR * 2f
                rect.set(x - eyeR * 1.15f, eyeY - eyeR * 1.2f, x + eyeR * 1.15f, bottom)
                c.drawRect(rect, body)
                c.drawLine(x - eyeR * 0.95f, bottom, x + eyeR * 0.95f, bottom, inkLine)
            }
        }

        // One mouth whose curve sags smoothly from a grin to a frown, then opens and lets the tongue out.
        val my = r * 0.46f
        val mw = r * (0.26f - rot * 0.04f)
        val curve = r * (0.22f - rot * 0.42f)
        val gape = ((rot - 0.75f) / 0.15f).coerceIn(0f, 1f)
        // The frown fades into an open, slack mouth with the tongue hanging out.
        path.reset()
        path.moveTo(-mw, my)
        path.quadTo(0f, my + curve, mw, my)
        inkLine.alpha = (255 * (1f - gape)).toInt()
        c.drawPath(path, inkLine)
        inkLine.alpha = 255
        if (gape > 0f) {
            val mouthTop = my - r * 0.06f
            rect.set(-mw * 0.75f, mouthTop, mw * 0.75f, mouthTop + r * 0.2f * gape)
            ink.alpha = (255 * gape).toInt()
            c.drawOval(rect, ink)
            ink.alpha = 255
            tongue.color = ColorUtils.setAlphaComponent(Color.parseColor("#E5626F"), (255 * gape).toInt())
            val lick = r * 0.025f * sin(s * 1.6f)
            c.drawOval(-mw * 0.4f, mouthTop + r * 0.08f * gape, mw * 0.4f, mouthTop + r * 0.3f * gape + lick, tongue)
        }
    }

    companion object {
        private val TWO_PI = (2 * PI).toFloat()
        private val INK = Color.parseColor("#1B1B1F")
        /** Rot spots as x, y, radius in units of the brain's radius. */
        private val SPOTS = listOf(
            floatArrayOf(-0.55f, -0.35f, 0.12f),
            floatArrayOf(0.48f, -0.2f, 0.1f),
            floatArrayOf(-0.72f, 0.38f, 0.09f),
            floatArrayOf(0.15f, -0.72f, 0.08f),
            floatArrayOf(0.7f, 0.42f, 0.11f),
        )

        /** Pink when fresh, swamp green when foggy, olive when rotten, blended smoothly. */
        fun colorFor(health: Float): Int {
            val h = health.coerceIn(0f, 1f)
            val pink = Color.parseColor("#F6A9C4")
            val green = Color.parseColor("#A3C95A")
            val olive = Color.parseColor("#77773A")
            return if (h > 0.5f) ColorUtils.blendARGB(green, pink, (h - 0.5f) * 2) else ColorUtils.blendARGB(olive, green, h * 2)
        }
    }
}
