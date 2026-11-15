package com.moadrive.pleos.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.moadrive.pleos.home.HomeWeatherScene
import com.moadrive.pleos.ui.IsDarkTheme
import kotlinx.coroutines.isActive
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Decorative weather clipped to the illustration; the frame clock invalidates drawing only. */
@Composable
internal fun HomeWeatherEffects(scene: HomeWeatherScene, animate: Boolean, modifier: Modifier) {
    if (scene != HomeWeatherScene.RAIN && scene != HomeWeatherScene.SNOW) return
    val rain = scene == HomeWeatherScene.RAIN
    val particles = remember(scene) {
        val random = Random(if (rain) 9047L else 7319L)
        List(if (rain) 56 else 38) {
            WeatherParticle(
                x = random.nextFloat(),
                phase = random.nextDouble(),
                depth = .25f + random.nextFloat() * .75f,
                lifetime = if (rain) .85 + random.nextDouble() * .8 else 6.5 + random.nextDouble() * 6.0,
                swayPhase = random.nextDouble() * PI * 2.0,
            )
        }.sortedBy { it.depth }
    }
    val elapsed = remember(scene) { mutableStateOf(0.0) }
    LaunchedEffect(scene, animate) {
        if (!animate) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (isActive) {
            val now = withFrameNanos { it }
            // Resume from the current picture after a stalled frame rather than teleporting.
            elapsed.value += ((now - previous).coerceAtLeast(0L) / 1_000_000_000.0).coerceAtMost(.05)
            previous = now
        }
    }
    val dark = IsDarkTheme
    val rainTint = if (dark) Color(0xFFA9CEFA) else Color(0xFF5486BB)
    val snowTint = if (dark) Color.White else Color(0xFFEAF4FF)
    val snowEdge = if (dark) Color(0xFFBFD8F6) else Color(0xFF83A8CB)
    Canvas(modifier.clipToBounds().clearAndSetSemantics {}) {
        // This is the only read of elapsed, so there is no per-frame composition or layout.
        val seconds = elapsed.value
        val margin = 28.dp.toPx()
        particles.forEachIndexed { index, particle ->
            val progress = ((seconds / particle.lifetime + particle.phase) % 1.0).toFloat()
            val y = progress * (size.height + margin * 2f) - margin
            if (rain) {
                val length = (10f + 16f * particle.depth).dp.toPx()
                val wind = (11f + 13f * particle.depth).dp.toPx()
                val x = particle.x * (size.width + wind) - progress * wind
                val head = Offset(x, y)
                val tail = head + Offset(length * .22f, -length)
                val stroke = (.6f + .65f * particle.depth).dp.toPx()
                val alpha = (.2f + particle.depth * .35f) * if (dark) 1f else .9f
                // A faint long trail and brighter tip give each drop depth without a heavy overlay.
                drawLine(rainTint.copy(alpha = alpha * .45f), tail, head, stroke, StrokeCap.Round)
                drawLine(rainTint.copy(alpha = alpha), head + Offset(length * .08f, -length * .36f),
                    head, stroke, StrokeCap.Round)
            } else {
                val sway = sin(seconds * (.65 + particle.depth * .45) + particle.swayPhase).toFloat() *
                    (5f + 9f * particle.depth).dp.toPx()
                val breeze = sin(seconds * .23 + particle.swayPhase).toFloat() * 4.dp.toPx()
                val x = particle.x * size.width + sway + breeze
                val center = Offset(x, y)
                val radius = (1.2f + 1.9f * particle.depth).dp.toPx()
                val alpha = .4f + .45f * particle.depth
                // The cool edge keeps white flakes legible on the white city and light canvas.
                drawCircle(snowEdge.copy(alpha = alpha * .25f), radius * 1.35f, center)
                drawCircle(snowTint.copy(alpha = alpha), radius, center)
                if (particle.depth > .72f && index % 3 == 0) {
                    repeat(3) { arm ->
                        val angle = arm * PI / 3.0 + particle.swayPhase + seconds * .12
                        val vector = Offset(cos(angle).toFloat(), sin(angle).toFloat()) * radius * 1.55f
                        drawLine(snowEdge.copy(alpha = alpha * .5f), center - vector, center + vector,
                            .65.dp.toPx(), StrokeCap.Round)
                    }
                }
            }
        }
    }
}

private data class WeatherParticle(
    val x: Float,
    val phase: Double,
    val depth: Float,
    val lifetime: Double,
    val swayPhase: Double,
)
