package app.pratyahara.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/** Pink when fresh, swamp green when foggy, olive and dripping when rotten. Shared with the Reels meter's look. */
fun brainColor(health: Int): Color {
    val h = health / 100f
    val pink = Color(0xFFF4A7C3)
    val green = Color(0xFF9BC53D)
    val olive = Color(0xFF6B6B2A)
    return if (h > 0.5f) lerp(green, pink, (h - 0.5f) * 2) else lerp(olive, green, h * 2)
}

/** The same wobbling brain as on Reels, for Home. */
@Composable
fun BrainDoodle(health: Int, modifier: Modifier = Modifier, size: Dp = 72.dp) {
    val t by rememberInfiniteTransition(label = "brain").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2_400, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    val color = brainColor(health)
    val fold = lerp(color, Color.Black, 0.35f)
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2 * 0.9f
        val c = center
        val wobble = (3f + (1f - health / 100f) * 9f) * sin(2 * PI * t).toFloat()
        rotate(wobble, c) {
            val lobe = r * 0.62f
            drawOval(color, Offset(c.x - r * 0.95f, c.y - lobe), Size(r, lobe * 2))
            drawOval(color, Offset(c.x - r * 0.05f, c.y - lobe), Size(r, lobe * 2))
            drawOval(color, Offset(c.x - r * 0.6f, c.y - r * 0.8f), Size(r * 1.2f, r * 0.9f))
            val p = Path().apply {
                moveTo(c.x, c.y - r * 0.7f); cubicTo(c.x - r * 0.12f, c.y - r * 0.3f, c.x + r * 0.12f, c.y + r * 0.1f, c.x, c.y + r * 0.55f)
                moveTo(c.x - r * 0.7f, c.y - r * 0.15f); cubicTo(c.x - r * 0.5f, c.y - r * 0.45f, c.x - r * 0.3f, c.y - r * 0.05f, c.x - r * 0.15f, c.y - r * 0.35f)
                moveTo(c.x - r * 0.65f, c.y + r * 0.3f); cubicTo(c.x - r * 0.45f, c.y + r * 0.05f, c.x - r * 0.3f, c.y + r * 0.45f, c.x - r * 0.12f, c.y + r * 0.2f)
                moveTo(c.x + r * 0.7f, c.y - r * 0.15f); cubicTo(c.x + r * 0.5f, c.y - r * 0.45f, c.x + r * 0.3f, c.y - r * 0.05f, c.x + r * 0.15f, c.y - r * 0.35f)
                moveTo(c.x + r * 0.65f, c.y + r * 0.3f); cubicTo(c.x + r * 0.45f, c.y + r * 0.05f, c.x + r * 0.3f, c.y + r * 0.45f, c.x + r * 0.12f, c.y + r * 0.2f)
            }
            drawPath(p, fold, style = Stroke(width = r * 0.08f, cap = StrokeCap.Round))
            if (health < 60) {
                val drips = if (health < 25) 3 else if (health < 45) 2 else 1
                for (i in 0 until drips) {
                    val phase = (t + i / 3f) % 1f
                    drawCircle(color, r * 0.11f * (1 - phase * 0.4f), Offset(c.x - r * 0.45f + i * r * 0.45f, c.y + lobe * 0.8f + phase * r * 0.3f))
                }
            }
        }
    }
}
