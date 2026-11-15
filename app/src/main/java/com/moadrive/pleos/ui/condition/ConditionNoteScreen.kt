package com.moadrive.pleos.ui.condition

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import com.moadrive.pleos.ui.common.SoftSurface as Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moadrive.pleos.condition.*
import com.moadrive.pleos.passport.PassportVisit
import com.moadrive.pleos.trips.TripRecord
import com.moadrive.pleos.trips.TripStatus
import com.moadrive.pleos.ui.*
import com.moadrive.pleos.ui.assist.AiGuideAvatar
import com.moadrive.pleos.ui.common.*
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

@Composable
fun ConditionNoteScreen(
    records: List<TripRecord>,
    visits: List<PassportVisit>,
    browsingAllowed: Boolean,
    onHome: () -> Unit,
    onService: (HomeService) -> Unit,
) {
    var preview by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val previewRecords = remember { conditionPreviewRecords() }
    val source = if (preview) previewRecords else records
    val shownVisits = if (preview) emptyList() else visits
    val note = remember(source, shownVisits, selectedId) {
        ConditionNoteCalculator.analyze(source, shownVisits, selectedId)
    }
    val choices = remember(source) { source.filter { !it.isActive }.distinctBy { it.id }.sortedByDescending { it.startedAtMs } }
    val selectedIndex = choices.indexOfFirst { it.id == note.target?.id }
    LaunchedEffect(browsingAllowed) { if (!browsingAllowed) preview = false }

    DetailPage(HomeService.CONDITION_NOTE, browsingAllowed, onHome, onService, left = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("컨디션 노트", color = Ink, fontSize = 30.sp, lineHeight = 38.sp,
                fontWeight = FontWeight.SemiBold)
            Text("내 차의 평소를 알아가는 기록", color = Secondary, fontSize = 16.sp, lineHeight = 23.sp)
        }
        DetailPageArtwork(JourneyArt.CONDITION, .78f, 300.dp)
        if (preview) {
            Text("샘플 데이터 · 실제 주행 기록이 아니에요", Modifier.fillMaxWidth(),
                color = OrangeInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        DetailSummary {
            Text(if (preview) "샘플 주행" else "비교 주행", color = Secondary, fontSize = 14.sp)
            Text(note.target?.let(::dateLabel) ?: "첫 주행을 기다리고 있어요", color = Ink, fontSize = 22.sp,
                lineHeight = 30.sp, fontWeight = FontWeight.Medium)
            note.target?.let { trip ->
                Text("${decimal(trip.distanceMeters / 1_000.0)} km · ${decimal(trip.durationMs / 60_000.0, 0)}분",
                    color = Secondary, fontSize = 15.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { if (browsingAllowed) selectedId = choices[selectedIndex + 1].id },
                    enabled = browsingAllowed && selectedIndex >= 0 && selectedIndex < choices.lastIndex,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = CircleShape, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) {
                    Text("이전 주행", fontSize = 16.sp)
                }
                OutlinedButton(onClick = { if (browsingAllowed) selectedId = choices[selectedIndex - 1].id },
                    enabled = browsingAllowed && selectedIndex > 0,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = CircleShape, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) {
                    Text("다음 주행", fontSize = 16.sp)
                }
            }
        }
        HorizontalDivider(color = Line)
        DetailSummary {
            Text(if (note.comparison == null) "평소를 알아가는 중" else "비교 기준을 채웠어요",
                color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("비교 가능한 과거 주행", Modifier.weight(1f), color = Secondary, fontSize = 14.sp)
                Text("${note.matchedCount} / ${note.minimumComparisons}회 이상", color = Blue, fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold)
            }
            RoundedMetricBar(note.matchedCount / note.minimumComparisons.toFloat())
            Text("측정된 주행 ${note.eligibleCount}회 중 거리와 출발 시간이 비슷한 이전 기록을 찾아요.",
                color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
        }
        TextButton(onClick = { if (browsingAllowed) onService(HomeService.TRIPS) }, enabled = browsingAllowed,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("주행기록으로 이동") }
        if (!browsingAllowed) Text("주행을 마친 뒤 노트를 살펴볼 수 있어요.", color = Secondary, fontSize = 14.sp)
    }, right = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoReveal(0) {
                ConditionDataHeader(preview, browsingAllowed) {
                    if (browsingAllowed) { preview = !preview; selectedId = null }
                }
            }
            val comparison = note.comparison
            if (comparison == null) {
                EmptyCondition(note, preview, browsingAllowed) {
                    if (browsingAllowed) { preview = true; selectedId = null }
                }
            } else {
                ReadyCondition(note, comparison, source)
            }
        }
    })
}

@Composable
private fun ConditionDataHeader(preview: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val narrow = maxWidth < 410.dp || LocalDensity.current.fontScale > 1.15f
        val heading: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("내 차의 주행 변화", color = Ink,
                    fontSize = 24.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold)
                Text(if (preview) "샘플 주행 4회" else "평소의 기록과 이번 주행을 함께 살펴요",
                    color = if (preview) OrangeInk else Secondary, fontSize = 13.sp, lineHeight = 20.sp)
            }
        }
        val action: @Composable () -> Unit = {
            Button(onClick = onToggle, enabled = enabled, shape = CircleShape,
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                Text(if (preview) "내 기록 보기" else "샘플 데이터 보기", fontSize = 14.sp)
            }
        }
        if (narrow) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                heading()
                action()
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { heading() }
                action()
            }
        }
    }
}

@Composable
private fun EmptyCondition(note: ConditionNote, preview: Boolean, enabled: Boolean, onPreview: () -> Unit) {
    val needsMeasurement = note.status == ConditionNoteStatus.UNMEASURED
    val accent = if (needsMeasurement) OrangeInk else Blue
    val title = when (note.status) {
        ConditionNoteStatus.NO_TRIP -> "첫 노트를 준비하고 있어요"
        ConditionNoteStatus.UNMEASURED -> "측정값이 더 필요해요"
        ConditionNoteStatus.NEED_HISTORY -> "내 차의 평소를 알아가는 중"
        ConditionNoteStatus.READY -> "비교 결과를 준비하고 있어요"
    }
    val detail = when (note.status) {
        ConditionNoteStatus.NO_TRIP -> "주행을 마치고 기록이 저장되면, 평소와 달라진 전력 사용과 주행 시간을 살펴드릴게요."
        ConditionNoteStatus.UNMEASURED -> "이 기록은 전력 사용량이나 주행 조건이 비교 기준에 미치지 못했어요. 다른 기록이나 다음 주행으로 다시 살펴볼 수 있어요."
        ConditionNoteStatus.NEED_HISTORY -> "비슷한 이전 주행이 ${max(0, note.minimumComparisons - note.matchedCount)}회 더 모이면, 이번 주행이 평소와 얼마나 달랐는지 알려드려요."
        ConditionNoteStatus.READY -> "비교할 기록을 다시 확인해 주세요."
    }
    InfoReveal(1) {
        Surface(Modifier.fillMaxWidth(), color = CardColor,
            shape = RoundedCornerShape(24.dp), border = null, shadowElevation = CardShadowElevation) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    AiGuideAvatar(Modifier.size(58.dp), alert = needsMeasurement)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("모아의 컨디션 메모", color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(when (note.status) {
                            ConditionNoteStatus.NO_TRIP -> "첫 주행 기다리는 중"
                            ConditionNoteStatus.UNMEASURED -> "측정값 확인 필요"
                            else -> "비교 기록 모으는 중"
                        }, color = Secondary, fontSize = 13.sp)
                    }
                    MoaIcon(MoaIconKind.CONDITION_NOTE, Modifier.size(34.dp), tint = accent)
                }
                Text(title, color = Ink, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold)
                Text(detail, color = Secondary, fontSize = 14.sp, lineHeight = 22.sp)
                FilledTonalButton(onClick = onPreview, enabled = enabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = CardColor, contentColor = Blue)) {
                    Text(if (preview) "최신 샘플 결과 보기" else "샘플 결과 먼저 보기", fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    InfoReveal(2) {
        DetailCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("비교할 이전 주행", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text("거리·출발 시간이 비슷한 기록", color = Secondary, fontSize = 12.sp)
                }
                Text("${note.matchedCount} / ${note.minimumComparisons}회", color = accent,
                    fontSize = 24.sp, fontWeight = FontWeight.Medium)
            }
            RoundedMetricBar(note.matchedCount / note.minimumComparisons.coerceAtLeast(1).toFloat(), color = accent)
            Text(when (note.status) {
                ConditionNoteStatus.NO_TRIP -> "먼저 측정값이 있는 주행 기록을 남겨 주세요."
                ConditionNoteStatus.UNMEASURED -> "선택한 주행의 측정값을 확인한 뒤 비교를 시작해요."
                else -> "이전 기록이 최소 ${note.minimumComparisons}회 모이면 중앙값과 비교해요."
            }, color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
        }
    }
    InfoReveal(3) {
        ConditionMetricStrip(null)
    }
    InfoReveal(4) {
        DetailCard { ComparisonMethod() }
    }
}

@Composable
private fun ComparisonMethod() {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("어떻게 비교하나요?", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Text("• 거리 ±20%, 출발 시간 ±2시간이 비슷한 과거 기록을 찾아요. 출발·도착 구역이 있으면 방향도 확인해요.",
            color = Secondary, fontSize = 14.sp, lineHeight = 21.sp)
        Text("• 1km 이상·전력 사용 50Wh 이상인 완료 기록을 사용해요. 누락되거나 중단된 기록은 제외해요.",
            color = Secondary, fontSize = 14.sp, lineHeight = 21.sp)
        Text("• 최소 3회가 모이면 중앙값과 비교해요. 배터리 소모의 평소 값은 이번 주행 거리로 환산한 값이에요.", color = Secondary, fontSize = 14.sp, lineHeight = 21.sp)
        Text("기온·정체·공조·적재 상태에 따라 달라질 수 있어요. 배터리 건강이나 차량 고장을 진단하는 결과는 아니에요.",
            color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun ReadyCondition(note: ConditionNote, comparison: ConditionComparison, records: List<TripRecord>) {
    val energy = comparison.energyChangePercent
    val accent = if (energy > 5) AccentOrange else if (energy < -5) AccentGreen else Blue
    val title = when {
        energy > 5 -> "평소보다 전력을 더 썼어요"
        energy < -5 -> "평소보다 전력을 덜 썼어요"
        else -> "평소와 비슷하게 달렸어요"
    }
    InfoReveal(1) {
        Surface(Modifier.fillMaxWidth(), color = CardColor, shape = RoundedCornerShape(26.dp),
            border = null, shadowElevation = CardShadowElevation) {
            BoxWithConstraints(Modifier.padding(horizontal = 22.dp, vertical = 18.dp)) {
                val stacked = maxWidth < 480.dp || LocalDensity.current.fontScale > 1.15f
                val heading: @Composable () -> Unit = {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
                            AiGuideAvatar(Modifier.size(46.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("모아의 주행 메모", color = Blue, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(title, color = Ink, fontSize = 22.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Text("비슷한 이전 주행 ${note.matchedCount}회와 비교", color = Secondary, fontSize = 14.sp)
                    }
                }
                val change: @Composable () -> Unit = {
                    Column(horizontalAlignment = if (stacked) Alignment.Start else Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(signed(energy) + "%", color = accent, fontSize = 49.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.semantics { contentDescription = "거리당 전력 사용 변화 ${signed(energy)} 퍼센트" })
                        Text("거리당 전력 변화", color = Secondary, fontSize = 14.sp)
                    }
                }
                if (stacked) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { heading(); change() }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { heading() }
                        change()
                    }
                }
            }
        }
    }
    InfoReveal(2) {
        ConditionMetricStrip(comparison)
    }
    InfoReveal(3) {
        ConditionHistory(note, comparison, records, accent)
    }
}

@Composable
private fun ConditionMetricStrip(comparison: ConditionComparison?) {
    Surface(Modifier.fillMaxWidth(), color = CardColor, shape = RoundedCornerShape(24.dp),
        border = null, shadowElevation = CardShadowElevation) {
        BoxWithConstraints(Modifier.padding(horizontal = 22.dp, vertical = 16.dp)) {
            val stacked = maxWidth < 430.dp || LocalDensity.current.fontScale > 1.15f
            val energy: @Composable () -> Unit = {
                ConditionMetric("거리당 전력", "Wh/km", comparison?.currentWhPerKm, comparison?.baselineWhPerKm,
                    MoaIconKind.CONDITION_NOTE, Blue, 0)
            }
            val time: @Composable () -> Unit = {
                ConditionMetric("10km당 시간", "분", comparison?.currentMinutesPer10Km, comparison?.baselineMinutesPer10Km,
                    MoaIconKind.TRIPS, AccentOrange, 1, trimWhole = true)
            }
            val battery: @Composable () -> Unit = {
                ConditionMetric("배터리 소모", "%p", comparison?.currentBatteryUsedPoints, comparison?.baselineBatteryUsedPoints,
                    MoaIconKind.BATTERY, AccentGreen, 1)
            }
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    energy(); HorizontalDivider(color = Line); time(); HorizontalDivider(color = Line); battery()
                }
            } else {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Box(Modifier.weight(1f)) { energy() }
                    VerticalDivider(Modifier.fillMaxHeight(), color = Line)
                    Box(Modifier.weight(1f)) { time() }
                    VerticalDivider(Modifier.fillMaxHeight(), color = Line)
                    Box(Modifier.weight(1f)) { battery() }
                }
            }
        }
    }
}

@Composable
private fun ConditionMetric(title: String, unit: String, current: Double?, baseline: Double?,
    icon: MoaIconKind, accent: Color, places: Int, trimWhole: Boolean = false) {
    fun formatted(value: Double?) = decimal(value, if (trimWhole && value != null && abs(value - kotlin.math.round(value)) < .05) 0 else places)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ConditionMetricSymbol(icon, accent)
            Text(title, color = Ink, fontSize = 14.sp, lineHeight = 20.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            Text(formatted(current), color = Ink, fontSize = 34.sp, fontWeight = FontWeight.Medium)
            Text(unit, color = Ink, fontSize = 14.sp, modifier = Modifier.padding(bottom = 5.dp))
        }
        Text("평소 ${formatted(baseline)}" + if (unit == "Wh/km") "" else " $unit", color = Secondary, fontSize = 13.sp,
            modifier = Modifier.semantics {
                if (icon == MoaIconKind.BATTERY) contentDescription = "평소 배터리 소모 ${formatted(baseline)} 퍼센트포인트, 이번 주행 거리로 환산"
            })
        // One capsule preserves the design; the fine tick marks the comparable baseline on the same scale.
        ConditionComparisonBar(current, baseline, accent)
    }
}

@Composable
private fun ConditionComparisonBar(current: Double?, baseline: Double?, accent: Color) {
    val ceiling = max(max(current ?: 0.0, baseline ?: 0.0), 1.0) * 1.25
    val track = accent.copy(alpha = if (IsDarkTheme) .22f else .17f)
    val tick = CardColor
    Canvas(Modifier.fillMaxWidth().height(9.dp).semantics {
        contentDescription = if (current != null && baseline != null) "이번 $current, 기준 $baseline 비교" else "비교 데이터 대기"
    }) {
        val radius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = radius)
        if (current != null && baseline != null) {
            drawRoundRect(accent, size = androidx.compose.ui.geometry.Size(size.width * (current / ceiling).toFloat(), size.height), cornerRadius = radius)
            val x = size.width * (baseline / ceiling).toFloat()
            drawLine(tick, Offset(x, size.height * .18f), Offset(x, size.height * .82f), 1.5.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}

/** Metric-specific symbols match the Seedream dashboard rather than the navigation shortcuts. */
@Composable
private fun ConditionMetricSymbol(kind: MoaIconKind, tint: Color) {
    Canvas(Modifier.size(22.dp)) {
        val u = size.minDimension / 24f
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(1.8f * u)
        when (kind) {
            MoaIconKind.TRIPS -> {
                drawCircle(tint, 8.5f * u, Offset(12f * u, 13f * u), style = stroke)
                drawLine(tint, Offset(12f * u, 7f * u), Offset(12f * u, 13f * u), 1.8f * u,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(tint, Offset(12f * u, 13f * u), Offset(16f * u, 16f * u), 1.8f * u,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(tint, Offset(10f * u, 1.5f * u), Offset(14f * u, 1.5f * u), 1.8f * u)
            }
            MoaIconKind.BATTERY -> {
                drawRoundRect(tint, Offset(1.5f * u, 6f * u), androidx.compose.ui.geometry.Size(18f * u, 12f * u),
                    androidx.compose.ui.geometry.CornerRadius(2f * u), style = stroke)
                drawRect(tint, Offset(21f * u, 9.5f * u), androidx.compose.ui.geometry.Size(2f * u, 5f * u))
                drawRoundRect(tint, Offset(4.5f * u, 9f * u), androidx.compose.ui.geometry.Size(10.5f * u, 6f * u),
                    androidx.compose.ui.geometry.CornerRadius(.6f * u))
            }
            else -> {
                val bolt = androidx.compose.ui.graphics.Path().apply {
                    moveTo(13.5f * u, 1.5f * u); lineTo(4.5f * u, 13f * u)
                    lineTo(10.5f * u, 13f * u); lineTo(9f * u, 22.5f * u)
                    lineTo(20f * u, 9.5f * u); lineTo(13.5f * u, 9.5f * u); close()
                }
                drawPath(bolt, tint)
            }
        }
    }
}

@Composable
private fun ConditionHistory(note: ConditionNote, comparison: ConditionComparison, records: List<TripRecord>, accent: Color) {
    val history = remember(records, comparison.baselineTripIds, note.target) {
        val ids = comparison.baselineTripIds.toSet()
        records.distinctBy { it.id }.filter { it.id in ids }.sortedBy { it.startedAtMs }.takeLast(6) + listOfNotNull(note.target)
    }
    var showMethod by rememberSaveable { mutableStateOf(false) }
    val values = history.map { it.consumedWh!! / (it.distanceMeters / 1_000.0) }
    val ceiling = max(values.maxOrNull() ?: 1.0, comparison.baselineWhPerKm) * 1.12
    val line = Line
    Surface(Modifier.fillMaxWidth(), color = CardColor, shape = RoundedCornerShape(24.dp),
        border = null, shadowElevation = CardShadowElevation) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("비슷한 주행의 전력 사용", Modifier.weight(1f).padding(end = 8.dp), color = Ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text("Wh/km", color = Secondary, fontSize = 11.sp)
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val visible = if (maxWidth < 360.dp) history.takeLast(4) else history
                val narrow = maxWidth < 430.dp
                val plotHeight = if (narrow) 170.dp else 180.dp
                val labelInset = (26 * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp
                val baselineY = plotHeight * (1f - (comparison.baselineWhPerKm / ceiling).toFloat())
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.fillMaxWidth().height(plotHeight + labelInset)) {
                        Column(Modifier.width(38.dp).align(Alignment.TopStart).offset(y = labelInset + baselineY - 14.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("평소", color = Secondary, fontSize = 11.sp)
                            Text(decimal(comparison.baselineWhPerKm, 0), color = Secondary, fontSize = 11.sp)
                        }
                        Box(Modifier.fillMaxSize().padding(start = 40.dp)) {
                            Canvas(Modifier.fillMaxWidth().height(plotHeight).align(Alignment.BottomCenter)) {
                                val y = size.height * (1f - (comparison.baselineWhPerKm / ceiling).toFloat())
                                drawLine(line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
                                drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                            }
                            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                visible.forEach { trip ->
                                    val value = trip.consumedWh!! / (trip.distanceMeters / 1_000.0)
                                    val selected = trip.id == note.target?.id
                                    val tint = if (selected) accent else Blue
                                    val barHeight = plotHeight * (value / ceiling).toFloat()
                                    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                                        val barWidth = minOf(80.dp, maxWidth * .7f)
                                        Box(Modifier.align(Alignment.BottomCenter).width(barWidth).height(barHeight)
                                            .clip(RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp))
                                            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(tint.copy(alpha = .90f), tint)))
                                            .semantics { contentDescription = "${shortDate(trip)} 주행 ${decimal(value, 0)} Wh/km" })
                                        Text(decimal(value, 0), Modifier.align(Alignment.BottomCenter).offset(y = -(barHeight + 7.dp)),
                                            color = if (selected) accent else Secondary, fontSize = if (narrow) 13.sp else 15.sp,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        if (selected) {
                                            Surface(Modifier.align(Alignment.BottomCenter).offset(y = -(barHeight - 5.dp)).size(10.dp),
                                                color = tint, shape = CircleShape, border = BorderStroke(2.dp, CardColor)) {}
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(start = 40.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        visible.forEach { trip ->
                            Text(if (trip.id == note.target?.id) "이번" else shortDate(trip), Modifier.weight(1f),
                                color = Secondary, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                    if (visible.size - 1 < note.matchedCount) Text("최근 ${visible.size - 1}회 표시 · 평소는 이전 ${note.matchedCount}회 전체 중앙값",
                        color = Secondary, fontSize = 11.sp, lineHeight = 17.sp)
                }
            }
            HorizontalDivider(color = Line)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val basis = if (comparison.basis == ConditionMatchBasis.LOCATION_DISTANCE_TIME)
                    "거리·출발 시간·출발 도착 구역 기준" else "거리·출발 시간이 비슷한 기록 기준"
                val action: @Composable () -> Unit = {
                    TextButton(onClick = { showMethod = !showMethod }, modifier = Modifier.heightIn(min = 48.dp)
                        .semantics { contentDescription = if (showMethod) "비교 방법 접기" else "비교 방법 보기" },
                        contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Text(if (showMethod) "비교 방법  ⌃" else "비교 방법  ⌄", fontSize = 14.sp)
                    }
                }
                if (maxWidth < 430.dp || LocalDensity.current.fontScale > 1.15f) {
                    Column { Text(basis, color = Secondary, fontSize = 12.sp, lineHeight = 19.sp); action() }
                } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(basis, Modifier.weight(1f), color = Ink, fontSize = 13.sp, lineHeight = 20.sp)
                    action()
                }
            }
            if (showMethod) {
                Text("그래프는 0을 기준으로 그려요. 표시된 평소 값은 이전 ${note.matchedCount}회 전체 중앙값이에요.",
                    color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
                ComparisonMethod()
            }
        }
    }
}

private fun shortDate(record: TripRecord): String = runCatching {
    DateTimeFormatter.ofPattern("M.d", Locale.KOREAN).withZone(ZoneId.of(record.zoneId))
        .format(Instant.ofEpochMilli(record.startedAtMs))
}.getOrDefault("이전")

private fun decimal(value: Double?, places: Int = 1): String = value?.takeIf { it.isFinite() }
    ?.let { String.format(Locale.KOREAN, "%.${places}f", it) } ?: "—"
private fun signed(value: Double): String = (if (value >= 0) "+" else "−") + decimal(abs(value))
private fun dateLabel(record: TripRecord): String = runCatching {
    DateTimeFormatter.ofPattern("M월 d일 (E) HH:mm", Locale.KOREAN)
        .withZone(ZoneId.of(record.zoneId)).format(Instant.ofEpochMilli(record.startedAtMs))
}.getOrDefault("날짜를 확인할 수 없는 기록")

/** Explicit product walkthrough, only selected by the preview button; never passed to storage. */
private fun conditionPreviewRecords(): List<TripRecord> = (1..4).map { day ->
    val start = ZonedDateTime.parse("2026-11-0${day}T08:00:00+09:00[Asia/Seoul]").toInstant().toEpochMilli()
    val distance = listOf(10_000.0, 10_500.0, 9_800.0, 10_000.0)[day - 1]
    val energyRate = listOf(200.0, 198.0, 204.0, 224.0)[day - 1]
    val minutes = listOf(30L, 32L, 29L, 36L)[day - 1]
    TripRecord("condition-preview-$day", start, start + minutes * 60_000,
        "2026-11-0$day", "Asia/Seoul", minutes * 60_000, distance,
        distance / 1_000.0 * energyRate, 80, if (day == 4) 75 else 76, TripStatus.COMPLETED)
}
