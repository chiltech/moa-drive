package com.moadrive.pleos.ui.passport

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moadrive.pleos.ui.AccentGreen
import com.moadrive.pleos.ui.Blue
import com.moadrive.pleos.ui.OrangeInk
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private val SealDate = DateTimeFormatter.ofPattern("yy.MM.dd", Locale.KOREAN)
private val FullSealDate = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.KOREAN)

/** Only the decoration varies by ID. Names, dates and counts always come from stored visits. */
@Composable
internal fun passportStampInk(id: String): Color = when (Math.floorMod(id.hashCode(), 3)) {
    0 -> Blue
    1 -> OrangeInk
    else -> AccentGreen
}

/** A vector ink impression; its text is duplicated by the accessible record beside it. */
@Composable
internal fun PassportStampSeal(
    id: String,
    label: String,
    firstAt: Long,
    ink: Color,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    impression: Float = 1f,
    numbered: Boolean = false,
    compact: Boolean = false,
) {
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 2f)
    val diameter = (if (compact) 42.dp else if (large) 118.dp else 82.dp) * fontScale
    val tilt = (Math.floorMod(id.hashCode(), 5) - 2) * .8f
    val firstVisit = Instant.ofEpochMilli(firstAt).atZone(ZoneId.systemDefault())
        .format(if (large) FullSealDate else SealDate)
    Box(
        modifier.size(diameter).clearAndSetSemantics {}.graphicsLayer {
            // One 260 ms impression is driven by explicit selection in PassportScreen.
            scaleX = 1f + .09f * (1f - impression)
            scaleY = scaleX
            rotationZ = tilt + 2f * (1f - impression)
            alpha = .72f + .28f * impression
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension * .46f
            drawCircle(ink.copy(alpha = .045f), radius)
            repeat(32) { index ->
                drawArc(ink.copy(alpha = if (index % 7 == 0) .34f else .85f),
                    startAngle = index * 11.25f, sweepAngle = 10.8f, useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2), style = Stroke(1.65.dp.toPx()))
            }
            drawCircle(ink.copy(alpha = .58f), radius * .88f, style = Stroke(.8.dp.toPx()))
            // Uneven, deterministic gaps imitate dry ink without animation or image assets.
            repeat(32) { index ->
                val angle = index * Math.PI * 2 / 32
                val point = Offset(center.x + cos(angle).toFloat() * radius,
                    center.y + sin(angle).toFloat() * radius)
                drawCircle(ink.copy(alpha = if (index % 5 == 0) .14f else .50f),
                    if (index % 3 == 0) 1.1.dp.toPx() else .65.dp.toPx(), point)
            }
            if (compact) drawCitySeal(ink, Math.floorMod(id.hashCode(), 3))
            repeat(12) { index ->
                val x = size.width * (.17f + (index * 17 % 67) / 100f)
                val y = size.height * (.18f + (index * 23 % 63) / 100f)
                val offset = Offset(x, y)
                if ((offset - center).getDistance() < radius * .82f) {
                    drawCircle(ink.copy(alpha = .08f), .75.dp.toPx(), offset)
                }
            }
        }
        if (!compact) Column(
            Modifier.fillMaxWidth(.72f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (large) 5.dp else 3.dp),
        ) {
            Text(if (numbered) "구역 기록" else "방문 기록", color = ink,
                fontSize = if (large) 10.sp else 8.sp, fontWeight = FontWeight.Medium)
            Text(label, color = ink, fontSize = if (numbered) 24.sp else if (large) 18.sp else 13.sp,
                lineHeight = if (numbered) 27.sp else if (large) 21.sp else 15.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
            Box(Modifier.fillMaxWidth(.68f).height(.7.dp).background(ink.copy(alpha = .4f)))
            Text(firstVisit, color = ink,
                fontSize = if (large) 10.sp else 8.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** Decorative civic silhouettes; they do not claim a particular landmark was visited. */
private fun DrawScope.drawCitySeal(ink: Color, motif: Int) {
    fun point(x: Float, y: Float) = Offset(x * size.width, y * size.height)
    val stroke = size.minDimension * .026f
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(ink, point(x1, y1), point(x2, y2), stroke)
    fun outline(vararg points: Pair<Float, Float>) {
        val path = Path().apply {
            points.forEachIndexed { i, (x, y) ->
                if (i == 0) moveTo(x * size.width, y * size.height) else lineTo(x * size.width, y * size.height)
            }
        }
        drawPath(path, ink, style = Stroke(stroke))
    }

    if (motif == 2) {
        outline(.38f to .72f, .44f to .40f, .57f to .40f, .63f to .72f)
        outline(.39f to .40f, .50f to .29f, .62f to .40f, .39f to .40f)
        line(.50f, .44f, .50f, .52f)
        line(.50f, .57f, .50f, .65f)
        line(.29f, .54f, .38f, .50f)
        line(.63f, .50f, .72f, .54f)
    } else {
        if (motif == 0) {
            outline(.22f to .47f, .34f to .44f, .50f to .34f, .66f to .44f, .78f to .47f, .22f to .47f)
            line(.50f, .28f, .50f, .34f)
        } else {
            outline(.24f to .48f, .24f to .38f, .35f to .30f, .46f to .38f, .46f to .45f, .54f to .45f,
                .54f to .38f, .65f to .30f, .76f to .38f, .76f to .48f, .24f to .48f)
        }
        for (x in listOf(.30f, .43f, .57f, .70f)) line(x, .50f, x, .70f)
        line(.26f, .70f, .74f, .70f)
    }
    line(.24f, .74f, .76f, .74f)
}

/** Perforation marks remain behind content so names and dates keep their normal contrast. */
internal fun Modifier.passportPerforation(ink: Color): Modifier = drawBehind {
    val inset = 5.dp.toPx()
    drawRoundRect(
        color = ink.copy(alpha = .24f),
        topLeft = Offset(inset, inset),
        size = Size((size.width - 2 * inset).coerceAtLeast(0f), (size.height - 2 * inset).coerceAtLeast(0f)),
        cornerRadius = CornerRadius(13.dp.toPx()),
        style = Stroke(.8.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5.dp.toPx(), 4.dp.toPx()))),
    )
}
