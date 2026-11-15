package com.moadrive.pleos.ui.drive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moadrive.pleos.ui.Ink
import com.moadrive.pleos.ui.Blue
import com.moadrive.pleos.ui.OrangeInk
import com.moadrive.pleos.ui.Secondary
import com.moadrive.pleos.ui.SurfaceColor
import com.moadrive.pleos.R


/** Values come from the active navigation/vehicle session; absent data stays absent. */
data class HorizonDockState(
    val speedKmh: Int? = null,
    val gear: String? = null,
    val batteryPercent: Int? = null,
    val rangeKm: Int? = null,
    val remainingMeters: Int? = null,
    val remainingSeconds: Int? = null,
    val destination: String? = null,
    val efficiencyKmPerKwh: Double? = null,
    val efficiencyLabel: String = "운행 전비",
)

/** Match the adjacent Pleos vehicle control strip before MOA's own content scaling. */
@Composable
private fun alignedDockHeight() =
    (180f * (LocalConfiguration.current.densityDpi / 160f) / LocalDensity.current.density).dp

// Inter falls back to the system font for Hangul. Reserve a full Korean line, not
// a 14sp numeric track, for captions and the destination below the route values.
@Composable
private fun captionTrackHeight() = with(LocalDensity.current) { 18.sp.toDp() }

private data class HorizonInstrumentReading(
    val metric: String,
    val label: String,
    val value: String,
    val unit: String = "",
    val fraction: Float? = null,
)

/** Capacity is based on the selected metric, never on the current live value. */
private fun horizonBayWidths(bays: List<List<HorizonInstrumentReading>>, width: Float, primaryInFirstBay: Boolean = true): List<Float> {
    val capacities = bays.mapIndexed { bay, readings ->
        val entries = readings.map { reading ->
            val base = when (reading.metric) {
                "Gear" -> 48f to 70f
                "Speed" -> 76f to 115f
                "Battery" -> 88f to 145f
                "Range", "RemainingDistance" -> 84f to 130f
                "OutsideTemperature", "CabinTemperature" -> 80f to 120f
                "CurrentTime", "ArrivalTime" -> 82f to 115f
                "RemainingTime" -> 72f to 115f
                "Efficiency" -> 156f to 235f
                "Odometer" -> 116f to 175f
                "Hidden" -> 48f to 60f
                else -> 76f to 110f
            }
            if (bay == 0 && primaryInFirstBay) {
                if (reading.metric == "Speed") 100f to 170f else maxOf(84f, base.first * 1.12f) to base.second * 1.16f
            } else base
        }
        (entries.sumOf { it.first.toDouble() }.toFloat() + if (readings.size > 1) 12f else 0f) to
            (entries.sumOf { it.second.toDouble() }.toFloat() + if (readings.size > 1) 20f else 0f)
    }
    val minimum = capacities.sumOf { it.first.toDouble() }.toFloat()
    val weight = capacities.sumOf { it.second.toDouble() }.toFloat()
    return capacities.map { if (width < minimum) width.coerceAtLeast(0f) * it.first / minimum else it.first + (width - minimum) * it.second / weight }
}

@Composable
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
fun HorizonDock(state: HorizonDockState, modifier: Modifier = Modifier, layerScale: Float = 1f,
    backgroundColor: Color = SurfaceColor, foregroundColor: Color = Ink, secondaryColor: Color = Secondary,
    accentColor: Color = Blue, compact: Boolean = false) {
    val font = remember {
        FontFamily(listOf(400, 500, 600, 700, 800, 900).map { weight ->
            Font(R.font.inter_variable, weight = FontWeight(weight),
                variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
        })
    }
    val distance = state.remainingMeters?.coerceAtLeast(0)?.let {
        if (it < 1000) it.toString() else String.format(java.util.Locale.getDefault(), "%.1f", it / 1000f)
    } ?: "—"
    val distanceUnit = if (state.remainingMeters != null && state.remainingMeters < 1000) "m" else "km"
    val duration = state.remainingSeconds?.coerceAtLeast(0)?.let {
        val minutes = (it + 59L) / 60L
        if (minutes >= 60) "${minutes / 60}시간 ${minutes % 60}분" else "${minutes}분"
    } ?: "—"
    val readings = listOf(
        HorizonInstrumentReading("Speed", "속도", state.speedKmh?.coerceAtLeast(0)?.toString() ?: "—", "km/h"),
        HorizonInstrumentReading("Gear", "기어", state.gear?.takeIf { it.isNotBlank() } ?: "—"),
        HorizonInstrumentReading("Battery", "배터리", state.batteryPercent?.coerceIn(0, 100)?.toString() ?: "—", "%",
            fraction = state.batteryPercent?.coerceIn(0, 100)?.div(100f)),
        HorizonInstrumentReading("Range", "주행가능거리", state.rangeKm?.toString() ?: "—", "km"),
        HorizonInstrumentReading("RemainingDistance", "남은 거리", distance, distanceUnit),
        HorizonInstrumentReading("RemainingTime", "남은 시간", duration),
        HorizonInstrumentReading("Efficiency", state.efficiencyLabel,
            state.efficiencyKmPerKwh?.let { String.format(java.util.Locale.getDefault(), "%.1f", it) } ?: "—", "km/kWh"),
    )
    val foreground = foregroundColor
    val secondary = secondaryColor
    val dockModifier = modifier.fillMaxWidth().background(backgroundColor).testTag("horizon.instrumentDock")
    if (compact) {
        CompactHorizonDock(readings, font, dockModifier, foreground, secondary, accentColor)
        return
    }
    BoxWithConstraints(dockModifier) {
        val stacked = maxWidth < 600.dp
        val scaledTrack = captionTrackHeight()
        val rowCount = if (stacked) 2 else 1
        val minimumRowHeight = scaledTrack * 2 + with(LocalDensity.current) { 32.sp.toDp() } + 34.dp
        val dockHeight = alignedDockHeight().coerceAtLeast(minimumRowHeight * rowCount + 6.dp)
        Column(Modifier.fillMaxWidth().height(dockHeight).padding(start = 8.dp, end = 8.dp, bottom = 6.dp)) {
            val routeDetail = state.destination?.takeIf { it.isNotBlank() }.orEmpty()
            if (stacked) {
                HorizonDockRow(listOf(listOf(0), listOf(1), listOf(2), listOf(3)), readings, foreground, secondary,
                    font, layerScale, null, null, Modifier.weight(.52f), routeDetail, accentColor)
                HorizonDockRow(listOf(listOf(4, 5), listOf(6)), readings, foreground, secondary,
                    font, layerScale, null, null, Modifier.weight(.48f), routeDetail, accentColor)
            } else HorizonDockRow(listOf(listOf(0), listOf(1), listOf(2), listOf(3), listOf(4, 5), listOf(6)), readings,
                foreground, secondary, font, layerScale, null, null, Modifier.fillMaxSize(), routeDetail, accentColor)
        }
    }
}

/** Split windows keep the readings legible while reserving height for the map. */
@Composable
private fun CompactHorizonDock(
    readings: List<HorizonInstrumentReading>, font: FontFamily, modifier: Modifier,
    foreground: Color, secondary: Color, accent: Color,
) {
    BoxWithConstraints(modifier) {
        val columns = if (maxWidth >= 780.dp && LocalDensity.current.fontScale <= 1.2f) readings.size else 4
        val rows = readings.chunked(columns)
        val labelHeight = captionTrackHeight()
        val valueHeight = with(LocalDensity.current) { 24.sp.toDp() }
        val minimumHeight = (labelHeight + valueHeight + 12.dp) * rows.size + 4.dp + .5.dp * (rows.size - 1)
        Column(Modifier.fillMaxWidth().height(alignedDockHeight().coerceAtLeast(minimumHeight))
            .padding(horizontal = 4.dp, vertical = 2.dp)) {
            rows.forEachIndexed { row, entries ->
                if (row > 0) Box(Modifier.fillMaxWidth().height(.5.dp).background(secondary.copy(alpha = .15f)))
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    entries.forEach { reading ->
                        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 7.dp, vertical = 5.dp)
                            .clearAndSetSemantics { contentDescription = reading.description() },
                            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
                            Text(reading.label, Modifier.height(labelHeight), color = secondary,
                                fontFamily = FontFamily.SansSerif, fontSize = 11.sp, lineHeight = 18.sp,
                                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            HorizonFittingValue(reading.value, font,
                                when (reading.metric) {
                                    "Speed" -> 23f
                                    "RemainingTime" -> 17f
                                    else -> 20f
                                },
                                reading.valueColor(foreground, accent), fixedThreeDigits = reading.metric == "Speed",
                                weight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().height(valueHeight),
                                unit = reading.unit.takeUnless { reading.value == "—" }.orEmpty(), unitColor = secondary)
                        }
                    }
                }
            }
        }
    }
}

private fun HorizonInstrumentReading.description(): String =
    if (value == "—") "$label 정보 없음" else listOf(label, value, unit).filter { it.isNotEmpty() }.joinToString(" ")

@Composable
private fun HorizonInstrumentReading.valueColor(foreground: Color, accent: Color): Color = when {
    metric == "Gear" && value == "D" -> OrangeInk
    metric == "Battery" && fraction != null -> accent
    else -> foreground
}

@Composable
private fun HorizonDockRow(
    bays: List<List<Int>>, readings: List<HorizonInstrumentReading>, foreground: Color, secondary: Color,
    font: FontFamily, textScale: Float, selectedSlot: Int?, onSelectSlot: ((Int) -> Unit)?, modifier: Modifier, routeDetail: String, accentColor: Color,
) {
    val scaledTrack = captionTrackHeight()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = maxWidth.value
        val height = maxHeight.value
        val widths = horizonBayWidths(bays.map { slots -> slots.map { readings[it] } }, width, bays.first().first() == 0)
        Row(Modifier.fillMaxSize()) {
            bays.forEachIndexed { bay, slots ->
                val horizontalPadding = if (height < 100) 9.dp else 16.dp
                val verticalPadding = if (height < 100) 7.dp else minOf(12f, height * .12f).dp
                Box(Modifier.width(widths[bay].dp).fillMaxHeight().padding(
                    start = horizontalPadding, end = horizontalPadding,
                    top = verticalPadding + 4.dp, bottom = verticalPadding)) {
                    val routeBay = slots == listOf(4, 5)
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        slots.forEach { slot ->
                            HorizonReading(readings[slot], slot, height, foreground, secondary, font, textScale,
                                selectedSlot == slot, onSelectSlot, Modifier.weight(1f).fillMaxHeight(), width, !routeBay, accentColor)
                        }
                    }
                    if (routeBay && routeDetail.isNotEmpty()) Text(routeDetail,
                        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(scaledTrack),
                        color = secondary, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
                        fontSize = (height * .085f).coerceIn(10f, 13f).sp,
                        lineHeight = 18.sp,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            var edge = 0f
            widths.dropLast(1).forEachIndexed { index, item ->
                edge += item
                val x = if (width > 0) size.width * edge / width else 0f
                val slant = minOf(18.dp.toPx(), size.height * .115f, minOf(item, widths[index + 1]).dp.toPx() * .18f)
                val inset = 10.dp.toPx()
                drawLine(secondary.copy(alpha = .19f), Offset(x + slant, inset), Offset(x - slant, size.height - inset), .75.dp.toPx())
            }
        }
    }
}

@Composable
private fun HorizonReading(
    reading: HorizonInstrumentReading, slot: Int, height: Float, foreground: Color, secondary: Color,
    font: FontFamily, textScale: Float, selected: Boolean, onSelect: ((Int) -> Unit)?, modifier: Modifier, rowWidth: Float, includesDetail: Boolean, accentColor: Color,
) {
    val scaledTrack = captionTrackHeight()
    val selectionModifier = if (onSelect != null) Modifier.clickable { onSelect(slot) } else Modifier
    Box(modifier.then(selectionModifier).semantics { this.selected = selected }.testTag("horizon.slot.$slot")) {
        if (reading.metric != "Hidden") Column(Modifier.fillMaxSize().clearAndSetSemantics {
            contentDescription = reading.description()
        }, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val labelSize = (height * .105f).coerceIn(10f, 12f)
            val detailSize = (height * .085f).coerceIn(10f, 13f)
            val compactText = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            // Every bay reserves the same caption/value/detail tracks. Battery bars and
            // units never move the live value or change its baseline relative to neighbours.
            // The slanted divider leans into the caption's leading edge near the top.
            Box(Modifier.fillMaxWidth().padding(start = 5.dp).height(scaledTrack), contentAlignment = Alignment.TopStart) {
                Text(reading.label, color = secondary, fontFamily = FontFamily.SansSerif, fontSize = labelSize.sp,
                    fontWeight = FontWeight.Medium, style = compactText, lineHeight = 18.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomStart) {
                val baseSize = when {
                    slot == 0 && reading.metric == "Speed" -> minOf(72f, height * .44f)
                    else -> minOf(height * .29f, rowWidth / 28f).coerceIn(24f, 36f)
                }
                HorizonFittingValue(reading.value, font, baseSize * textScale.coerceIn(.8f, 1.2f),
                    reading.valueColor(foreground, accentColor),
                    fixedThreeDigits = reading.metric == "Speed", weight = if (slot == 0) FontWeight.Bold else FontWeight.SemiBold,
                    modifier = Modifier.fillMaxSize(),
                    unit = reading.unit.takeUnless { reading.value == "—" || reading.metric == "Speed" && slot == 0 }.orEmpty(),
                    unitColor = secondary)
            }
            Row(Modifier.fillMaxWidth().height(scaledTrack), verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val detail = if (includesDetail && reading.metric == "Speed") reading.unit else ""
                if (detail.isNotEmpty()) Text(detail, Modifier.weight(1f, fill = false), fontFamily = font,
                    color = secondary, fontWeight = FontWeight.Medium, fontSize = detailSize.sp,
                    style = compactText, lineHeight = (detailSize * 1.12f).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (includesDetail && reading.fraction != null) Canvas(Modifier.width(36.dp).height(4.dp).align(Alignment.CenterVertically)) {
                    drawRoundRect(secondary.copy(alpha = .2f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
                    reading.fraction?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)?.let { fraction ->
                        drawRoundRect(accentColor, size = size.copy(width = size.width * fraction),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
                    }
                }
            }
        }
        if (reading.metric == "Hidden" && onSelect != null) Text("숨김",
            modifier = Modifier.align(Alignment.Center), color = secondary, fontFamily = font, fontSize = 11.sp)
        if (onSelect != null || selected) Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(if (selected) Color(0xFF2173F4) else secondary.copy(alpha = .42f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                style = Stroke((if (selected) 2f else 1.5f).dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
        }
    }
}

@Composable
private fun HorizonFittingValue(value: String, font: FontFamily, baseSize: Float, color: Color, fixedThreeDigits: Boolean,
    weight: FontWeight, modifier: Modifier, unit: String = "", unitColor: Color = color) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
        val style = TextStyle(fontFamily = font, fontSize = baseSize.sp, lineHeight = (baseSize * 1.05f).sp,
            fontWeight = weight, platformStyle = PlatformTextStyle(includeFontPadding = false))
        fun displayText(number: String) = androidx.compose.ui.text.buildAnnotatedString {
            append(number)
            if (unit.isNotEmpty()) {
                pushStyle(androidx.compose.ui.text.SpanStyle(fontSize = (baseSize * .43f).sp, color = unitColor))
                append(if (unit == "%" || unit.startsWith("°")) unit else " $unit")
                pop()
            }
        }
        val measured = if (fixedThreeDigits) ('0'..'9').maxOf { digit ->
            measurer.measure(AnnotatedString(digit.toString().repeat(3)), style, softWrap = false).size.width
        } else measurer.measure(displayText(value), style, softWrap = false).size.width
        val available = with(density) { maxWidth.toPx() }
        val widthRatio = if (measured > 0) (available / measured * .96f).coerceAtMost(1f) else 1f
        // The Column measures the label/unit/detail first; only the remaining height goes
        // to this value. Its size stays independent of live speed digits.
        val availableHeight = with(density) { maxHeight.toPx() }
        val desiredHeight = with(density) { (baseSize * 1.05f).sp.toPx() }
        val heightRatio = if (availableHeight.isFinite() && desiredHeight > 0f) (availableHeight / desiredHeight).coerceAtMost(1f) else 1f
        val fittedSize = maxOf(7f, baseSize * minOf(widthRatio, heightRatio))
        val displayed = androidx.compose.ui.text.buildAnnotatedString {
            append(value)
            if (unit.isNotEmpty()) {
                pushStyle(androidx.compose.ui.text.SpanStyle(fontSize = (fittedSize * .43f).sp, color = unitColor))
                append(if (unit == "%" || unit.startsWith("°")) unit else " $unit")
                pop()
            }
        }
        Text(displayed, color = color, style = style.copy(fontSize = fittedSize.sp, lineHeight = (fittedSize * 1.05f).sp),
            maxLines = 1, softWrap = false)
    }
}
