package com.moadrive.pleos.ui.trips

import android.animation.ValueAnimator
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.moadrive.pleos.ui.common.SoftSurface as Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.moadrive.pleos.ui.*
import com.moadrive.pleos.ui.common.DimmedDialog
import com.moadrive.pleos.ui.common.rememberDialogAction
import java.io.ByteArrayOutputStream
import kotlin.math.floor
import kotlin.math.min

/** The same paper, large cost hero and perforated sections used by MoaGauge's receipt. */
@Composable
internal fun TripReceiptDialog(receipt: TripReceiptSnapshot, onDismiss: () -> Unit) {
    val cost = receipt.rows.firstOrNull { it.label == "예상 전력 비용" }?.value ?: "—"
    val counter = remember(cost) { ReceiptCostCounter(cost) }
    // Save the start, rather than the end, so Activity restoration never replays a partial count.
    var countStarted by rememberSaveable { mutableStateOf(false) }
    val countProgress = remember {
        Animatable(if (countStarted || !counter.canAnimate || !ValueAnimator.areAnimatorsEnabled()) 1f else 0f)
    }
    DimmedDialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val close = rememberDialogAction(onDismiss)
        var receiptPlaced by remember { mutableStateOf(false) }
        LaunchedEffect(receiptPlaced) {
            if (!receiptPlaced) return@LaunchedEffect
            // Wait for the popup's own first frame, rather than spending the count on its host screen.
            withFrameNanos { }
            val shouldAnimate = !countStarted && counter.canAnimate && ValueAnimator.areAnimatorsEnabled()
            countStarted = true
            if (shouldAnimate) countProgress.animateTo(1f, tween(720, easing = FastOutSlowInEasing))
            else countProgress.snapTo(1f)
        }
        Box(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 20.dp)
            .onGloballyPositioned { if (it.size.width > 0 && it.size.height > 0) receiptPlaced = true },
            contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Keep dismissal reachable while the paper itself scrolls.
                Surface(color = SurfaceColor, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(receipt.title, Modifier.weight(1f), color = Ink,
                            fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
                        TextButton(close, Modifier.heightIn(min = 48.dp)) { Text("닫기", fontSize = 16.sp) }
                    }
                }
                Surface(Modifier.fillMaxWidth().weight(1f)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
                    shape = ReceiptPaperShape, color = SurfaceColor) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        ReceiptHero(receipt, cost = counter.finalText, animatedCost = { counter.textAt(countProgress.value) })
                        ReceiptPerforation()
                        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("주행 내역", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            receipt.rows.forEachIndexed { index, row ->
                                if (index == 7 || row.label.startsWith("주행 ")) {
                                    HorizontalDivider(color = Line.copy(alpha = .65f))
                                }
                                ReceiptValueRow(row)
                            }
                        }
                        ReceiptPerforation()
                        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Text("설정한 단가를 기준으로 계산한 예상 비용입니다.",
                                color = Secondary, fontSize = 12.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
                            Text("결제·세금 증빙용 영수증이 아닙니다.",
                                color = Secondary, fontSize = 12.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
                            Text("A DRIVE WITH MOA DRIVE", Modifier.padding(top = 10.dp, bottom = 9.dp),
                                color = Secondary, fontSize = 10.sp, letterSpacing = 1.7.sp,
                                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptHero(receipt: TripReceiptSnapshot, cost: String, animatedCost: () -> String) {
    val distance = receipt.rows.firstOrNull { it.label == "총 기록 거리 · 추정" }?.value ?: "—"
    val efficiency = receipt.rows.firstOrNull { it.label == "비교 구간 평균 전비 · 추정" }?.value ?: "—"
    val duration = receipt.rows.firstOrNull { it.label == "기록 시간" }?.value ?: "—"
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 26.dp)) {
        val stacked = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.3f
        val costFontSize = if (maxWidth < 390.dp) 40.sp else 50.sp
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                MoaIcon(MoaIconKind.BATTERY, Modifier.size(18.dp), Blue, outline = true)
                Text("DRIVE RECEIPT", color = Blue, fontSize = 11.sp,
                    letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(receipt.selectionLabel, Modifier.padding(top = 12.dp), color = Secondary,
                fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
            Text("비교 구간의 예상 전력 비용", Modifier.padding(top = 20.dp), color = Ink,
                fontSize = 16.sp, lineHeight = 24.sp, textAlign = TextAlign.Center)
            ReceiptAnimatedCost(cost, animatedCost, TextStyle(color = Ink, fontSize = costFontSize,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontFeatureSettings = "tnum"))
            Text("총 기록 거리 $distance · 설정한 전력 단가 기준", Modifier.padding(top = 5.dp),
                color = Secondary, fontSize = 12.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
            if (stacked) {
                Column(Modifier.fillMaxWidth().padding(top = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(15.dp)) {
                    ReceiptMetric("평균 전비 · 추정", efficiency)
                    HorizontalDivider(color = Line)
                    ReceiptMetric("주행 시간", duration)
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(top = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                    ReceiptMetric("평균 전비 · 추정", efficiency, Modifier.weight(1f))
                    VerticalDivider(Modifier.height(44.dp), color = Line)
                    ReceiptMetric("주행 시간", duration, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun ReceiptAnimatedCost(finalCost: String, animatedCost: () -> String, style: TextStyle,
    modifier: Modifier = Modifier.padding(top = 3.dp)) {
    Box(modifier.clearAndSetSemantics { text = AnnotatedString(finalCost) },
        contentAlignment = Alignment.CenterEnd) {
        // The final amount reserves its full footprint; spoken text also stays at this exact value.
        Text(finalCost, Modifier.alpha(0f), style = style)
        Text(animatedCost(), style = style)
    }
}

@Composable
private fun ReceiptMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Secondary, fontSize = 12.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
        Text(value, color = Ink, fontSize = 19.sp, lineHeight = 27.sp,
            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            style = TextStyle(fontFeatureSettings = "tnum"))
    }
}

@Composable
private fun ReceiptValueRow(row: ReceiptRow) {
    val emphasized = row.label.startsWith("기준 유류비 대비")
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 390.dp || LocalDensity.current.fontScale > 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(row.label, color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
                Text(row.value, color = if (emphasized) Blue else Ink, fontSize = if (emphasized) 22.sp else 16.sp,
                    lineHeight = 25.sp, fontWeight = FontWeight.SemiBold,
                    style = TextStyle(fontFeatureSettings = "tnum"))
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top) {
                Text(row.label, Modifier.weight(1f), color = Secondary, fontSize = 13.sp, lineHeight = 21.sp)
                Text(row.value, Modifier.weight(1.35f), color = if (emphasized) Blue else Ink,
                    fontSize = if (emphasized) 22.sp else 16.sp, lineHeight = 25.sp,
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End,
                    style = TextStyle(fontFeatureSettings = "tnum"))
            }
        }
    }
}

@Composable
private fun ReceiptPerforation() {
    val color = Line
    Canvas(Modifier.fillMaxWidth().height(16.dp)) {
        drawLine(color, Offset(20.dp.toPx(), size.height / 2), Offset(size.width - 20.dp.toPx(), size.height / 2),
            strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
        // The paper's offscreen layer lets these holes show the dimmed page beneath.
        drawCircle(Color.Transparent, 8.dp.toPx(), Offset(0f, size.height / 2), blendMode = BlendMode.Clear)
        drawCircle(Color.Transparent, 8.dp.toPx(), Offset(size.width, size.height / 2), blendMode = BlendMode.Clear)
    }
}

/** MoaGauge's rounded paper top and fine torn lower edge, kept inside layout bounds. */
private object ReceiptPaperShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val radius = min(with(density) { 22.dp.toPx() }, min(size.width, size.height) / 2)
        val toothDepth = min(with(density) { 7.dp.toPx() }, size.height / 3)
        val teeth = floor(size.width / with(density) { 15.dp.toPx() }).toInt().coerceAtLeast(1)
        val toothWidth = size.width / teeth
        val bottom = size.height - toothDepth
        return Outline.Generic(Path().apply {
            moveTo(radius, 0f)
            lineTo(size.width - radius, 0f)
            quadraticBezierTo(size.width, 0f, size.width, radius)
            lineTo(size.width, bottom)
            for (tooth in teeth - 1 downTo 0) {
                lineTo((tooth + .5f) * toothWidth, size.height)
                lineTo(tooth * toothWidth, bottom)
            }
            lineTo(0f, radius)
            quadraticBezierTo(0f, 0f, radius, 0f)
            close()
        })
    }
}

/** All pages are generated locally before a Downloads entry is created. */
internal fun receiptPdf(receipt: TripReceiptSnapshot): ByteArray {
    val document = PdfDocument()
    var openPage: PdfDocument.Page? = null
    try {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            color = AndroidColor.rgb(29, 38, 52)
        }
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()).also { openPage = it }
        var y = 46f
        fun line(text: String, fontSize: Float = 11f, bold: Boolean = false) {
            paint.textSize = fontSize
            paint.typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            var rest = text
            do {
                if (y + fontSize * 1.6f > 800f) {
                    document.finishPage(page)
                    openPage = null
                    page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, ++pageNumber).create()).also { openPage = it }
                    y = 46f
                }
                val count = paint.breakText(rest, true, 503f, null).coerceAtLeast(1).coerceAtMost(rest.length)
                page.canvas.drawText(rest.take(count), 46f, y, paint)
                rest = rest.drop(count)
                y += fontSize * 1.6f
            } while (rest.isNotEmpty())
        }
        line("MOA DRIVE · ${receipt.title}", 21f, true)
        line(receipt.selectionLabel, 12f)
        y += 14f
        receipt.rows.forEach { row ->
            line(row.label, 10f)
            line(row.value, 12f, true)
            y += 8f
        }
        y += 12f
        receipt.notes.forEach { line(it, 10f); y += 5f }
        document.finishPage(page)
        openPage = null
        return ByteArrayOutputStream().use { output -> document.writeTo(output); output.toByteArray() }
    } finally {
        // PdfDocument cannot close with an unfinished page if drawing failed midway.
        openPage?.let { runCatching { document.finishPage(it) } }
        document.close()
    }
}

