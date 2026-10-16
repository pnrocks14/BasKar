package app.pratyahara.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pratyahara.overlay.BrainPainter

/** Pink when fresh, swamp green when foggy, olive when rotten. Matches the Reels meter. */
fun brainColor(health: Int): Color = Color(BrainPainter.colorFor(health / 100f))

/** The same brain with a face as on Reels, for Home. */
@Composable
fun BrainDoodle(health: Int, modifier: Modifier = Modifier, size: Dp = 72.dp) {
    val painter = remember { BrainPainter() }
    // An infinite transition (not a frame loop) so UI tests can still tell when the screen is idle.
    val seconds by rememberInfiniteTransition(label = "brain").animateFloat(
        0f, 3_600f, infiniteRepeatable(tween(3_600_000, easing = LinearEasing), RepeatMode.Restart), label = "clock",
    )
    val shown by animateFloatAsState(health / 100f, tween(600), label = "health")
    Canvas(modifier.size(size)) {
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            nc.save()
            nc.translate(center.x, center.y + this.size.minDimension * 0.04f)
            painter.draw(nc, this.size.minDimension / 2 * 0.86f, shown, (seconds * 1000).toLong())
            nc.restore()
        }
    }
}
