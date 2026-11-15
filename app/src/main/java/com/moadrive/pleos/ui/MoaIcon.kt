package com.moadrive.pleos.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class MoaIconKind {
    HOME, SEARCH, DRIVE, TRIPS, CONDITION_NOTE, PASSPORT, SAFETY, CHILD_CARE, PET_CARE, SOUND, INSURANCE, SETTINGS, CLOSE, BATTERY
}

/** Crisp 24-unit symbols matching the approved rail and colorful service shortcuts. */
@Composable
fun MoaIcon(
    kind: MoaIconKind,
    modifier: Modifier = Modifier,
    tint: Color = Ink,
    outline: Boolean = false,
    artworkAccent: Color? = null,
) {
    val shortcutColor = artworkAccent ?: tint
    Canvas(modifier = modifier) {
        val side = size.minDimension
        val unit = side / 24f
        withTransform({
            translate((size.width - side) / 2f, (size.height - side) / 2f)
            scale(unit, unit, pivot = Offset.Zero)
        }) {
            if (!outline && drawShortcut(kind, shortcutColor)) return@withTransform
            when (kind) {
                MoaIconKind.HOME -> iconPath(tint) {
                    moveTo(3f, 10f); lineTo(12f, 3f); lineTo(21f, 10f)
                    moveTo(5f, 9f); lineTo(5f, 21f); lineTo(10f, 21f)
                    lineTo(10f, 14f); lineTo(14f, 14f); lineTo(14f, 21f)
                    lineTo(19f, 21f); lineTo(19f, 9f)
                }
                MoaIconKind.SEARCH -> {
                    drawCircle(tint, 7f, Offset(10f, 10f), style = IconStroke)
                    iconLine(tint, 15f, 15f, 21f, 21f)
                }
                MoaIconKind.DRIVE -> {
                    drawCircle(tint, radius = 9f, center = Offset(12f, 12f), style = IconStroke)
                    drawCircle(tint, radius = 2.6f, center = Offset(12f, 12f), style = IconStroke)
                    iconPath(tint) {
                        moveTo(3.2f, 10f)
                        cubicTo(6f, 8.7f, 8f, 8.7f, 10.1f, 10.2f)
                        moveTo(20.8f, 10f)
                        cubicTo(18f, 8.7f, 16f, 8.7f, 13.9f, 10.2f)
                        moveTo(10.6f, 14.2f)
                        cubicTo(9.3f, 16.3f, 8.9f, 18.4f, 9.4f, 20.5f)
                        moveTo(13.4f, 14.2f)
                        cubicTo(14.7f, 16.3f, 15.1f, 18.4f, 14.6f, 20.5f)
                    }
                }
                MoaIconKind.TRIPS -> {
                    iconPath(tint) {
                        moveTo(4f, 3.5f)
                        lineTo(4f, 20f)
                        lineTo(21f, 20f)
                        moveTo(7f, 15f)
                        lineTo(11f, 10f)
                        lineTo(15f, 12.8f)
                        lineTo(20f, 6.8f)
                    }
                    drawCircle(tint, radius = 1.7f, center = Offset(20f, 5f), style = IconStroke)
                }
                MoaIconKind.CONDITION_NOTE -> {
                    drawRoundRect(tint, Offset(4f, 4.5f), Size(16f, 16.5f), CornerRadius(2f), style = IconStroke)
                    listOf(8f, 12f, 16f).forEach { x -> iconLine(tint, x, 2.5f, x, 6.5f) }
                    iconPath(tint) {
                        moveTo(7f, 14f); lineTo(9f, 14f); lineTo(11f, 10.5f)
                        lineTo(13f, 17f); lineTo(15f, 13f); lineTo(17f, 13f)
                    }
                }
                MoaIconKind.PASSPORT -> {
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(5f, 3f),
                        size = Size(14f, 18f),
                        cornerRadius = CornerRadius(1.5f, 1.5f),
                        style = IconStroke
                    )
                    iconLine(tint, 8f, 3f, 8f, 21f)
                    drawCircle(tint, radius = 3f, center = Offset(13.5f, 9.5f), style = IconStroke)
                    iconPath(tint) {
                        moveTo(10.7f, 9.2f)
                        lineTo(12.6f, 10.1f)
                        lineTo(14.1f, 8.8f)
                        lineTo(16.2f, 9.8f)
                    }
                    iconLine(tint, 11f, 16f, 16f, 16f)
                    iconLine(tint, 11f, 18f, 14f, 18f)
                }
                MoaIconKind.SAFETY -> {
                    iconPath(tint) {
                        moveTo(12f, 2.5f)
                        lineTo(20f, 6f)
                        lineTo(19.3f, 13.1f)
                        cubicTo(18.9f, 17f, 15.5f, 20.1f, 12f, 21.5f)
                        cubicTo(8.5f, 20.1f, 5.1f, 17f, 4.7f, 13.1f)
                        lineTo(4f, 6f)
                        close()
                        moveTo(8.3f, 11.7f)
                        lineTo(11f, 14.5f)
                        lineTo(15.9f, 9.5f)
                    }
                }
                MoaIconKind.SOUND -> {
                    val heights = floatArrayOf(3f, 7f, 12f, 18f, 12f, 7f, 3f)
                    heights.forEachIndexed { index, height ->
                        val x = 3f + index * 3f
                        iconLine(tint, x, 12f - height / 2f, x, 12f + height / 2f)
                    }
                }
                MoaIconKind.INSURANCE -> {
                    iconPath(tint) {
                        moveTo(3f, 17.5f)
                        lineTo(2.5f, 14f)
                        lineTo(5.5f, 8.5f)
                        lineTo(12.5f, 8.5f)
                        lineTo(16f, 13f)
                        lineTo(19.2f, 13.8f)
                        quadraticBezierTo(21f, 14.3f, 21f, 16f)
                        lineTo(21f, 17.5f)
                        lineTo(19.6f, 17.5f)
                        moveTo(14.4f, 17.5f)
                        lineTo(9.6f, 17.5f)
                        moveTo(4.8f, 17.5f)
                        lineTo(3f, 17.5f)
                        moveTo(3.1f, 13f)
                        lineTo(16f, 13f)
                        moveTo(10f, 8.5f)
                        lineTo(10f, 13f)
                    }
                    drawCircle(tint, radius = 2.4f, center = Offset(7.2f, 17.5f), style = IconStroke)
                    drawCircle(tint, radius = 2.4f, center = Offset(17f, 17.5f), style = IconStroke)
                    iconLine(tint, 19f, 3f, 19f, 8f)
                    iconLine(tint, 16.5f, 5.5f, 21.5f, 5.5f)
                }
                MoaIconKind.CHILD_CARE -> {
                    drawCircle(tint, radius = 6f, center = Offset(12f, 9f), style = IconStroke)
                    iconPath(tint) { moveTo(9f, 3.8f); quadraticBezierTo(12f, 7f, 14.3f, 3.8f) }
                    drawCircle(tint, .7f, Offset(10f, 9f))
                    drawCircle(tint, .7f, Offset(14f, 9f))
                    iconPath(tint) { moveTo(10f, 11.6f); quadraticBezierTo(12f, 13.3f, 14f, 11.6f) }
                    iconPath(tint) { moveTo(4f, 22f); cubicTo(4f, 13f, 20f, 13f, 20f, 22f) }
                }
                MoaIconKind.PET_CARE -> {
                    drawOval(tint, Offset(2.5f, 7f), Size(4f, 5.4f), style = IconStroke)
                    drawOval(tint, Offset(7f, 2f), Size(4f, 5.4f), style = IconStroke)
                    drawOval(tint, Offset(13f, 2f), Size(4f, 5.4f), style = IconStroke)
                    drawOval(tint, Offset(17.5f, 7f), Size(4f, 5.4f), style = IconStroke)
                    iconPath(tint) {
                        moveTo(12f, 11f); cubicTo(9f, 11f, 9f, 14f, 6f, 16f)
                        cubicTo(2f, 20f, 7f, 23f, 12f, 20.4f)
                        cubicTo(17f, 23f, 22f, 20f, 18f, 16f)
                        cubicTo(15f, 14f, 15f, 11f, 12f, 11f); close()
                    }
                }
                MoaIconKind.SETTINGS -> {
                    iconPath(tint) {
                        repeat(32) { index ->
                            val angle = -PI / 2 + index * PI / 16
                            val radius = if (index % 4 == 1 || index % 4 == 2) 9.2f else 7.4f
                            val x = 12f + radius * cos(angle).toFloat()
                            val y = 12f + radius * sin(angle).toFloat()
                            if (index == 0) moveTo(x, y) else lineTo(x, y)
                        }
                        close()
                    }
                    drawCircle(tint, radius = 3.2f, center = Offset(12f, 12f), style = IconStroke)
                }
                MoaIconKind.CLOSE -> {
                    iconLine(tint, 6f, 6f, 18f, 18f)
                    iconLine(tint, 18f, 6f, 6f, 18f)
                }
                MoaIconKind.BATTERY -> {
                    drawRoundRect(tint, Offset(6f, 4f), Size(12f, 17f), CornerRadius(2f), style = IconStroke)
                    drawRoundRect(tint, Offset(9f, 1f), Size(6f, 3f), CornerRadius(.8f))
                    drawPath(Path().apply {
                        moveTo(13f, 7f); lineTo(9f, 13f); lineTo(12f, 13f)
                        lineTo(11f, 18f); lineTo(16f, 11f); lineTo(13f, 11f); close()
                    }, tint)
                }
            }
        }
    }
}

/** Filled home shortcuts stay clear at vehicle viewing distance; rail uses outlines. */
private fun DrawScope.drawShortcut(kind: MoaIconKind, color: Color): Boolean {
    when (kind) {
        MoaIconKind.HOME -> {
            drawPath(Path().apply {
                moveTo(2f, 10f); lineTo(12f, 2.5f); lineTo(22f, 10f)
                lineTo(19.5f, 10f); lineTo(19.5f, 21f); lineTo(14f, 21f)
                lineTo(14f, 14f); lineTo(10f, 14f); lineTo(10f, 21f)
                lineTo(4.5f, 21f); lineTo(4.5f, 10f); close()
            }, color)
        }
        MoaIconKind.DRIVE -> {
            drawCircle(color, 10f, Offset(12f, 12f), style = Stroke(2f))
            drawPath(Path().apply {
                moveTo(12f, 5f); lineTo(18.5f, 18f); lineTo(12f, 14.8f)
                lineTo(5.5f, 18f); close()
            }, color)
        }
        MoaIconKind.TRIPS -> {
            val points = listOf(Offset(3f, 18f), Offset(9f, 11f), Offset(15f, 13f), Offset(21f, 5f))
            points.zipWithNext().forEach { (a, b) -> drawLine(color, a, b, 2.8f, StrokeCap.Round) }
            points.forEach { drawCircle(Color.White, 2.6f, it); drawCircle(color, 2.6f, it, style = Stroke(1.8f)) }
        }
        MoaIconKind.CONDITION_NOTE -> {
            drawRoundRect(color, Offset(4f, 4.5f), Size(16f, 16.5f), CornerRadius(2f))
            listOf(8f, 12f, 16f).forEach { x ->
                iconLine(color, x, 2.5f, x, 4.5f)
                iconLine(Color.White.copy(alpha = .8f), x, 5f, x, 6.5f)
            }
            iconPath(Color.White) {
                moveTo(7f, 14f); lineTo(9f, 14f); lineTo(11f, 10.5f)
                lineTo(13f, 17f); lineTo(15f, 13f); lineTo(17f, 13f)
            }
        }
        MoaIconKind.PASSPORT -> {
            drawPath(Path().apply {
                moveTo(2f, 9f); lineTo(8f, 6.8f); lineTo(15f, 9f); lineTo(21f, 7f)
                lineTo(23f, 21f); lineTo(16f, 23f); lineTo(9f, 20.8f); lineTo(1f, 23f); close()
            }, color.copy(alpha = .75f))
            iconLine(Color.White.copy(alpha = .8f), 8f, 12f, 9f, 19f)
            iconLine(Color.White.copy(alpha = .8f), 15f, 13f, 16f, 20f)
            drawPath(Path().apply {
                moveTo(12f, 14f); cubicTo(9f, 10f, 6.9f, 7.1f, 7.1f, 5f)
                cubicTo(7.1f, -1f, 16.9f, -1f, 16.9f, 5f)
                cubicTo(17.1f, 7.1f, 15f, 10f, 12f, 14f); close()
            }, color)
            drawCircle(Color.White, 1.8f, Offset(12f, 4.5f))
        }
        MoaIconKind.SAFETY -> {
            drawPath(Path().apply {
                moveTo(12f, 1f); lineTo(22f, 5f); lineTo(20.8f, 14f)
                quadraticBezierTo(19.5f, 20f, 12f, 23f)
                quadraticBezierTo(4.5f, 20f, 3.2f, 14f); lineTo(2f, 5f); close()
            }, color)
            iconPath(Color.White) { moveTo(7.5f, 11.7f); lineTo(10.8f, 15f); lineTo(17f, 8.5f) }
        }
        MoaIconKind.SOUND -> {
            drawPath(Path().apply {
                moveTo(2f, 9f); lineTo(6f, 9f); lineTo(12f, 4f)
                lineTo(12f, 20f); lineTo(6f, 15f); lineTo(2f, 15f); close()
            }, color)
            iconLine(Color.White, 5f, 10.5f, 5f, 13.5f)
            drawArc(color, -55f, 110f, false, Offset(10f, 6f), Size(11f, 12f), style = Stroke(1.8f, cap = StrokeCap.Round))
            drawArc(color, -55f, 110f, false, Offset(9f, 2.5f), Size(17f, 19f), style = Stroke(1.8f, cap = StrokeCap.Round))
        }
        MoaIconKind.INSURANCE -> {
            iconPath(color) {
                moveTo(12f, 1.5f); lineTo(21f, 5f); lineTo(20f, 14f)
                quadraticBezierTo(18f, 20f, 12f, 23f)
                quadraticBezierTo(6f, 20f, 4f, 14f); lineTo(3f, 5f); close()
            }
            drawPath(Path().apply {
                moveTo(6f, 13f); lineTo(8.3f, 8.5f); lineTo(16f, 8.5f)
                lineTo(18.3f, 13f); lineTo(18.3f, 17f); lineTo(6f, 17f); close()
            }, color)
            drawPath(Path().apply { moveTo(8f, 12f); lineTo(9.4f, 9.8f); lineTo(14.8f, 9.8f); lineTo(16.2f, 12f); close() }, Color.White)
            drawCircle(Color.White, .8f, Offset(8.1f, 14.8f)); drawCircle(Color.White, .8f, Offset(16.2f, 14.8f))
        }
        else -> return false
    }
    return true
}

private val IconStroke = Stroke(width = 1.55f, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.iconPath(color: Color, block: Path.() -> Unit) {
    drawPath(Path().apply(block), color = color, style = IconStroke)
}

private fun DrawScope.iconLine(color: Color, x1: Float, y1: Float, x2: Float, y2: Float) {
    drawLine(
        color = color,
        start = Offset(x1, y1),
        end = Offset(x2, y2),
        strokeWidth = IconStroke.width,
        cap = StrokeCap.Round
    )
}
