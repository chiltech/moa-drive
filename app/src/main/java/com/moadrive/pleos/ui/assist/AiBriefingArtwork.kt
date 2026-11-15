package com.moadrive.pleos.ui.assist

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.moadrive.pleos.ui.IsDarkTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Dialog-only artwork; compact navigation guidance keeps its existing avatar. */
@Composable
internal fun BriefingAvatar(modifier: Modifier = Modifier, speaking: Boolean = false) {
    Canvas(modifier.semantics { contentDescription = "모아 AI 가이드" }) {
        inBriefingCoordinates {
            val blue = Color(0xFF315ACF)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFF5F7FF), Color(0xFFD8E0FA)),
                center = Offset(9f, 7f), radius = 19f), 11.8f, Offset(12f, 12f))
            drawRoundRect(Color(0xFF5B70AF).copy(alpha = .17f), Offset(4.3f, 7f), Size(16f, 13.3f), CornerRadius(4.5f))
            drawRoundRect(blue, Offset(2.8f, 9.2f), Size(2.4f, 5.4f), CornerRadius(1.2f))
            drawRoundRect(blue, Offset(18.8f, 9.2f), Size(2.4f, 5.4f), CornerRadius(1.2f))
            drawRoundRect(Brush.linearGradient(listOf(Color.White, Color(0xFFE9EDFA)),
                Offset(5f, 5f), Offset(19f, 19f)), Offset(4.7f, 5.2f), Size(14.6f, 13.5f), CornerRadius(4.3f))
            drawRoundRect(Color(0xFFADBCE5), Offset(4.7f, 5.2f), Size(14.6f, 13.5f), CornerRadius(4.3f),
                style = Stroke(.25f))
            drawRoundRect(Brush.linearGradient(listOf(Color(0xFF456DDC), Color(0xFF234ABF)),
                Offset(8f, 8f), Offset(17f, 15f)), Offset(6.3f, 8.4f), Size(11.4f, 6.1f), CornerRadius(2.6f))
            briefingLine(Color.White, 9.5f, 10.4f, 9.5f, 12.3f, .9f)
            briefingLine(Color.White, 14.5f, 10.4f, 14.5f, 12.3f, .9f)
            if (speaking) drawRoundRect(blue, Offset(11f, 16f), Size(2f, 1.4f), CornerRadius(.7f))
            else briefingPath(blue, .65f) {
                moveTo(10.5f, 16.2f); quadraticBezierTo(12f, 16.9f, 13.5f, 16.2f)
            }
        }
    }
}

internal enum class BriefingWeatherSymbol { RAIN, SNOW, THUNDER, FOG, WIND, ICE, REVIEWED, UNKNOWN, DISABLED, CAUTION }

@Composable
internal fun BriefingWeatherIcon(symbol: BriefingWeatherSymbol, modifier: Modifier, tint: Color) {
    // Adjacent text states the precise forecast/status; these symbols are decorative.
    Canvas(modifier) {
        inBriefingCoordinates {
            when (symbol) {
                BriefingWeatherSymbol.RAIN, BriefingWeatherSymbol.SNOW, BriefingWeatherSymbol.THUNDER -> {
                    drawCircle(tint.copy(alpha = .7f), 3.5f, Offset(16.8f, 7.3f))
                    drawCircle(tint.copy(alpha = .7f), 2.5f, Offset(19.4f, 9.1f))
                    cloud(tint)
                    when (symbol) {
                        BriefingWeatherSymbol.SNOW -> listOf(6.5f, 12f, 17.5f).forEach { x ->
                            briefingLine(tint, x - 1f, 19f, x + 1f, 21f, .85f)
                            briefingLine(tint, x - 1f, 21f, x + 1f, 19f, .85f)
                            briefingLine(tint, x, 18.6f, x, 21.4f, .85f)
                        }
                        BriefingWeatherSymbol.THUNDER -> drawPath(Path().apply {
                            moveTo(11.8f, 16.2f); lineTo(8.6f, 20.2f); lineTo(11.2f, 20.2f)
                            lineTo(10.3f, 23f); lineTo(15.3f, 18.2f); lineTo(12.8f, 18.2f); close()
                        }, tint)
                        else -> listOf(6f, 11.7f, 17.4f).forEach { x -> briefingLine(tint, x + 1f, 18.6f, x - .8f, 21.2f, 1.3f) }
                    }
                }
                BriefingWeatherSymbol.FOG -> {
                    cloud(tint, alpha = .72f)
                    briefingLine(tint, 4f, 17f, 20f, 17f, 1.4f)
                    briefingLine(tint, 2.5f, 20f, 16.5f, 20f, 1.4f)
                    briefingLine(tint, 8f, 23f, 21f, 23f, 1.4f)
                }
                BriefingWeatherSymbol.WIND -> {
                    briefingPath(tint, 1.5f) { moveTo(3f, 8f); lineTo(16.5f, 8f); cubicTo(22f, 8f, 21f, 2.8f, 17.5f, 4f) }
                    briefingPath(tint, 1.5f) { moveTo(2f, 12f); lineTo(20f, 12f) }
                    briefingPath(tint, 1.5f) { moveTo(5f, 16f); lineTo(15f, 16f); cubicTo(21f, 16f, 20f, 22f, 16f, 20f) }
                }
                BriefingWeatherSymbol.ICE -> {
                    repeat(6) { index ->
                        val angle = index * PI / 3
                        val end = Offset(12f + 9f * cos(angle).toFloat(), 12f + 9f * sin(angle).toFloat())
                        drawLine(tint, Offset(12f, 12f), end, 1.3f, StrokeCap.Round)
                        val joint = Offset(12f + 6f * cos(angle).toFloat(), 12f + 6f * sin(angle).toFloat())
                        listOf(-1, 1).forEach { side ->
                            val branch = angle + side * PI / 3
                            drawLine(tint, joint, joint + Offset(3f * cos(branch).toFloat(), 3f * sin(branch).toFloat()), 1.2f, StrokeCap.Round)
                        }
                    }
                }
                BriefingWeatherSymbol.REVIEWED, BriefingWeatherSymbol.DISABLED -> {
                    briefingPath(tint, 1.3f) {
                        moveTo(12f, 2.8f); lineTo(20f, 6f); lineTo(19.2f, 14f)
                        cubicTo(18.8f, 18f, 15f, 20.3f, 12f, 21.8f)
                        cubicTo(9f, 20.3f, 5.2f, 18f, 4.8f, 14f); lineTo(4f, 6f); close()
                    }
                    if (symbol == BriefingWeatherSymbol.REVIEWED) briefingPath(tint, 1.5f) {
                        moveTo(8f, 12f); lineTo(10.8f, 15f); lineTo(16.2f, 9.3f)
                    } else {
                        briefingLine(tint, 12f, 10.3f, 12f, 15.8f, 1.5f)
                        drawCircle(tint, .8f, Offset(12f, 7.4f))
                    }
                }
                BriefingWeatherSymbol.UNKNOWN -> {
                    drawCircle(tint.copy(alpha = .1f), 10.5f, Offset(12f, 12f))
                    briefingPath(tint, 1.5f) {
                        moveTo(8.8f, 8.5f); cubicTo(9f, 4.7f, 16.2f, 4.7f, 16.2f, 9f)
                        cubicTo(16.2f, 12.3f, 12f, 11.4f, 12f, 15f)
                    }
                    drawCircle(tint, .9f, Offset(12f, 18.4f))
                }
                BriefingWeatherSymbol.CAUTION -> {
                    briefingPath(tint, 1.4f) {
                        moveTo(12f, 3f); lineTo(22f, 20f); lineTo(2f, 20f); close()
                    }
                    briefingLine(tint, 12f, 9f, 12f, 13.8f, 1.7f)
                    drawCircle(tint, .8f, Offset(12f, 17f))
                }
            }
        }
    }
}

@Composable
internal fun BriefingCheckIcon(index: Int, modifier: Modifier) {
    val dark = IsDarkTheme
    val tint = if (dark) Color(0xFFD5DAE3) else Color(0xFF606977)
    val background = if (dark) Color(0xFF333B48) else Color(0xFFF0F2F5)
    Canvas(modifier) {
        inBriefingCoordinates {
            drawCircle(background, 12f, Offset(12f, 12f))
            when (index) {
                0 -> {
                    drawCircle(tint, 2f, Offset(11f, 5.6f))
                    briefingPath(tint, 1.7f) {
                        moveTo(10f, 9f); lineTo(8.4f, 14.5f); lineTo(13f, 14.5f)
                        quadraticBezierTo(15.7f, 14.5f, 16f, 17f); lineTo(16.5f, 19f)
                        moveTo(8.4f, 16.8f); lineTo(8f, 20f)
                        moveTo(5f, 11f); lineTo(4.5f, 16f); quadraticBezierTo(4.5f, 18f, 6.7f, 18f)
                    }
                    briefingLine(tint, 18f, 5.5f, 6.2f, 17.5f, 1.7f)
                    briefingLine(background, 16.9f, 5.5f, 5.6f, 16.8f, .55f)
                }
                1 -> {
                    frontCar(tint, 0f, 2f)
                    briefingLine(tint, 5.8f, 5f, 4.2f, 3.5f, 1f)
                    briefingLine(tint, 18.2f, 5f, 19.8f, 3.5f, 1f)
                    briefingLine(tint, 3.7f, 10.5f, 1.9f, 9.7f, 1f)
                    briefingLine(tint, 20.3f, 10.5f, 22.1f, 9.7f, 1f)
                    briefingLine(tint, 8.3f, 3.1f, 15.7f, 3.1f, 1f)
                }
                else -> {
                    briefingLine(tint, 6.3f, 5.7f, 2.8f, 20f, 1.3f)
                    briefingLine(tint, 17.7f, 5.7f, 21.2f, 20f, 1.3f)
                    withTransform({ scale(.68f, .68f, pivot = Offset(12f, 7f)) }) { frontCar(tint, 0f, 0f) }
                    briefingLine(tint, 12f, 16.2f, 12f, 19.5f, 1.2f)
                    briefingLine(tint, 8.8f, 20.5f, 15.2f, 20.5f, 1f)
                }
            }
        }
    }
}

private fun DrawScope.inBriefingCoordinates(block: DrawScope.() -> Unit) {
    val unit = size.minDimension / 24f
    withTransform({
        translate((size.width - size.minDimension) / 2f, (size.height - size.minDimension) / 2f)
        scale(unit, unit, pivot = Offset.Zero)
    }, block)
}

private fun DrawScope.cloud(color: Color, alpha: Float = 1f) {
    val fill = color.copy(alpha = alpha)
    drawCircle(fill, 4.8f, Offset(9.4f, 9.7f))
    drawCircle(fill, 3.8f, Offset(5.2f, 12.4f))
    drawCircle(fill, 4.2f, Offset(15.7f, 12.3f))
    drawRoundRect(fill, Offset(5f, 10.8f), Size(11f, 5.8f), CornerRadius(2f))
}

private fun DrawScope.frontCar(color: Color, dx: Float, dy: Float) {
    withTransform({ translate(dx, dy) }) {
        briefingPath(color, 1.25f) {
            moveTo(5f, 13f); lineTo(7.1f, 7.1f); lineTo(16.9f, 7.1f); lineTo(19f, 13f)
            lineTo(19f, 17f); lineTo(5f, 17f); close()
            moveTo(6f, 12f); lineTo(18f, 12f)
        }
        briefingLine(color, 6.7f, 17f, 6.7f, 19f, 2.4f)
        briefingLine(color, 17.3f, 17f, 17.3f, 19f, 2.4f)
        drawCircle(color, .9f, Offset(7.8f, 14.7f))
        drawCircle(color, .9f, Offset(16.2f, 14.7f))
    }
}

private fun DrawScope.briefingLine(color: Color, x1: Float, y1: Float, x2: Float, y2: Float, width: Float) =
    drawLine(color, Offset(x1, y1), Offset(x2, y2), width, StrokeCap.Round)

private fun DrawScope.briefingPath(color: Color, width: Float, path: Path.() -> Unit) =
    drawPath(Path().apply(path), color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
