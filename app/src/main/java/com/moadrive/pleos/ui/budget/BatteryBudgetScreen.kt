package com.moadrive.pleos.ui.budget

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import com.moadrive.pleos.ui.common.SoftSurface as Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moadrive.pleos.budget.BatteryBudgetCalculator
import com.moadrive.pleos.budget.BatteryBudgetCandidate
import com.moadrive.pleos.budget.BatteryBudgetState
import com.moadrive.pleos.budget.BatteryBudgetViewModel
import com.moadrive.pleos.navigation.NavigationDestination
import com.moadrive.pleos.ui.*
import com.moadrive.pleos.ui.common.*
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/** Parked outing planner; results reserve battery for returning to the calculation's departure point. */
@Composable
fun BatteryBudgetScreen(
    model: BatteryBudgetViewModel,
    browsingAllowed: Boolean,
    onLocationPermission: () -> Unit,
    onDestination: (NavigationDestination) -> Unit,
    onHome: () -> Unit,
    onService: (HomeService) -> Unit,
    navigationAllowed: Boolean = browsingAllowed,
) {
    val state by model.state.collectAsState()
    var selectedDestinationId by rememberSaveable(state.isPreview, state.calculatedAtMs) { mutableStateOf<String?>(null) }
    val selectedCandidate = state.candidates.firstOrNull { it.destination.id == selectedDestinationId }
        ?: state.candidates.firstOrNull { it.estimate.feasible }
        ?: state.candidates.firstOrNull()
    DetailPage(HomeService.BATTERY_BUDGET, browsingAllowed, onHome, onService, left = {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("MOA DRIVE", color = Ink, fontSize = 13.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.Medium)
            Text("배터리 예산 드라이브", color = Ink, fontSize = 30.sp,
                lineHeight = 38.sp, fontWeight = FontWeight.SemiBold)
            Text("돌아올 여유까지 생각한 여행", color = Secondary, fontSize = 16.sp, lineHeight = 23.sp)
        }
        DetailPageArtwork(JourneyArt.BUDGET, .78f, 300.dp)
        if (state.isPreview) PreviewNotice()
        VehicleBudgetSummary(state)
        if (!browsingAllowed) Text("도시 선택과 예산 설정은 주차 후 이용해 주세요.", color = OrangeInk, fontSize = 13.sp, lineHeight = 20.sp)
    }, right = {
        InfoReveal(0) {
            SampleDataHeader("새로운 도시로 떠나요", "귀가할 배터리까지 여유 있게 계획해요",
                preview = state.isPreview, enabled = browsingAllowed && !state.loading,
                onToggle = { if (browsingAllowed) { if (state.isPreview) model.exitPreview() else model.preview() } },
                previewDescription = "샘플 코스 3개 · 실제 차량과 주행 기록은 그대로예요")
        }
        InfoReveal(1) {
            DetailCard {
                Text("이번 드라이브의 예산", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                BudgetChoices("전체 외출 시간", listOf(60, 90, 120, 180), state.preferences.totalMinutes,
                    browsingAllowed && !state.loading, { "${it}분" }) { minutes ->
                    if (browsingAllowed) model.updatePreferences(state.preferences.copy(totalMinutes = minutes))
                }
                Text("왕복 이동과 방문지 체류 ${state.preferences.visitMinutes}분을 포함해요.",
                    color = Secondary, fontSize = 13.sp, lineHeight = 19.sp)
                HorizontalDivider(color = Line)
                BudgetChoices("귀가 시 남길 배터리", listOf(20, 30, 40, 50), state.preferences.reservePercent,
                    browsingAllowed && !state.loading, { "${it}%" }) { reserve ->
                    if (browsingAllowed) model.updatePreferences(state.preferences.copy(reservePercent = reserve))
                }
                if (state.isPreview) {
                    Text("조건을 바꾸면 체험 결과도 함께 바뀌어요.", color = OrangeInk, fontSize = 12.sp, lineHeight = 18.sp)
                } else {
                    Button(onClick = { if (browsingAllowed) model.calculate() },
                        enabled = browsingAllowed && state.canCalculate && !state.loading,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(26.dp)) {
                        Text(if (state.loading) "왕복 경로 계산 중" else "예산에 맞는 도시 찾기", fontSize = 16.sp)
                    }
                    if (!state.locationReady) {
                        OutlinedButton(onClick = { if (browsingAllowed) onLocationPermission() }, enabled = browsingAllowed && !state.loading,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(24.dp)) {
                            Text("위치 확인 · 권한 설정", fontSize = 14.sp)
                        }
                    }
                }
            }
        }
        InfoReveal(2) {
            ReturnBudgetStrip(state)
        }
        InfoReveal(3) {
            when {
                state.loading -> LoadingCard(state.message)
                selectedCandidate == null -> EmptyBudgetCard(state.message, state.isPreview)
                else -> BudgetResults(
                    state, selectedCandidate, browsingAllowed,
                    onSelect = { if (browsingAllowed) selectedDestinationId = it.destination.id },
                    onDestination = { candidate ->
                        if (browsingAllowed && navigationAllowed && model.canNavigate(candidate)) onDestination(candidate.destination)
                    },
                    navigationAllowed = navigationAllowed,
                )
            }
        }
    })
}

@Composable
private fun PreviewNotice() {
    DetailSummary {
        Text("체험 화면 · 실제 차량 데이터 아님", color = OrangeInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("예시 차량과 경로로 계산 과정을 살펴봐요. 주행기록에는 저장하지 않아요.",
            color = Ink, fontSize = 13.sp, lineHeight = 20.sp)
    }
    HorizontalDivider(color = Line)
}

@Composable
private fun VehicleBudgetSummary(state: BatteryBudgetState) {
    val battery = state.batteryPercent
    val reserve = state.preferences.reservePercent
    DetailSummary {
        if (state.isPreview) Text("체험에 사용하는 차량 조건", color = Secondary, fontSize = 13.sp)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val batteryMetric: @Composable (Modifier) -> Unit = { modifier ->
                VehicleMetric("배터리", battery?.let { "$it%" } ?: "—", Blue, modifier)
            }
            val rangeMetric: @Composable (Modifier) -> Unit = { modifier ->
                VehicleMetric("주행가능거리", state.rangeKm?.let { "$it km" } ?: "—", Ink, modifier)
            }
            if (maxWidth >= 240.dp && LocalDensity.current.fontScale <= 1.2f) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    batteryMetric(Modifier.weight(1f))
                    VerticalDivider(Modifier.fillMaxHeight(), color = Line)
                    rangeMetric(Modifier.weight(1.2f))
                }
            } else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                batteryMetric(Modifier.fillMaxWidth())
                rangeMetric(Modifier.fillMaxWidth())
            }
        }
        if (battery != null) {
            val reserveHeld = minOf(reserve, battery)
            BatterySegments(
                segments = listOf((battery - reserveHeld).toDouble() to Blue,
                    reserveHeld.toDouble() to if (battery >= reserve) AccentGreen else AccentOrange),
                description = "현재 배터리 ${battery}%, 귀가 목표 ${reserve}%",
                modifier = Modifier.height(10.dp),
            )
        } else {
            Text("차량의 잔량과 주행가능거리를 확인할 수 없어요.", color = Secondary, fontSize = 13.sp, lineHeight = 19.sp)
        }
        Text("귀가 목표 ${reserve}% 이상", color = if (battery != null && battery < reserve) OrangeInk else AccentGreen,
            fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun VehicleMetric(label: String, value: String, tint: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Secondary, fontSize = 14.sp)
        Text(value, color = tint, fontSize = if (value.length > 6) 31.sp else 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ReturnBudgetStrip(state: BatteryBudgetState) {
    val preferences = state.preferences
    val drivingMinutes = (preferences.totalMinutes - preferences.visitMinutes).coerceAtLeast(0)
    Surface(Modifier.fillMaxWidth(), color = PaleBlue, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(horizontal = 17.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("출발한 곳으로 돌아오는 예산", color = Blue, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("돌아오는 곳은 계산 시 현재 위치예요. 저장한 집 주소와는 다를 수 있어요.",
                color = Secondary, fontSize = 13.sp, lineHeight = 19.sp)
            val values = listOf(
                "외출 시간" to "${preferences.totalMinutes}분",
                "이동 예산" to "왕복 ${drivingMinutes}분 · 체류 ${preferences.visitMinutes}분",
                "귀가 배터리" to "${preferences.reservePercent}% 이상",
            )
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= 365.dp && LocalDensity.current.fontScale <= 1.2f) {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        values.forEachIndexed { index, (label, value) ->
                            if (index != 0) VerticalDivider(Modifier.fillMaxHeight(), color = Blue.copy(alpha = .14f))
                            ReturnBudgetValue(label, value, Modifier.weight(if (index == 1) 1.7f else 1f))
                        }
                    }
                } else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    values.forEach { (label, value) -> ReturnBudgetValue(label, value, Modifier.fillMaxWidth()) }
                }
            }
        }
    }
}

@Composable
private fun ReturnBudgetValue(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = Secondary, fontSize = 12.sp)
        Text(value, color = Ink, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun BudgetChoices(title: String, values: List<Int>, selected: Int, enabled: Boolean,
    label: (Int) -> String, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth < 330.dp || LocalDensity.current.fontScale > 1.15f) 2 else 4
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                values.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { value ->
                            val active = value == selected
                            OutlinedButton(onClick = { onSelect(value) }, enabled = enabled,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                    .semantics {
                                        contentDescription = "$title ${label(value)}"
                                        this.selected = active
                                    },
                                shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                                border = BorderStroke(if (active) 1.5.dp else .5.dp, if (active) Blue else Line),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (active) PaleBlue else CardColor,
                                    contentColor = if (active) Blue else Ink)) {
                                Text(label(value), fontSize = 15.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetResults(
    state: BatteryBudgetState,
    selected: BatteryBudgetCandidate,
    browsingAllowed: Boolean,
    onSelect: (BatteryBudgetCandidate) -> Unit,
    onDestination: (BatteryBudgetCandidate) -> Unit,
    navigationAllowed: Boolean,
) {
    DetailCard {
        Text("가까운 미방문 도시", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        state.message?.takeIf(String::isNotBlank)?.let { message ->
            Text(message, color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
        }
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = CardColor,
            shadowElevation = CardShadowElevation) {
            Column(Modifier.selectableGroup().padding(5.dp)) {
                state.candidates.forEachIndexed { index, candidate ->
                    if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 9.dp), color = Line)
                    CandidateRow(candidate, selected == candidate, browsingAllowed) { onSelect(candidate) }
                }
            }
        }
        CandidateBreakdown(selected, state)
        if (state.isPreview) {
            Text("체험 경로 · 실제 길안내로 연결하지 않아요.", color = OrangeInk, fontSize = 13.sp, lineHeight = 19.sp)
        } else {
            Button(onClick = { onDestination(selected) },
                enabled = browsingAllowed && navigationAllowed && !state.loading && selected.estimate.feasible,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    .semantics { contentDescription = "${selected.cityName} 경로 보기" }, shape = CircleShape) {
                MoaIcon(MoaIconKind.DRIVE, Modifier.size(21.dp), OnBlue, outline = true)
                Spacer(Modifier.width(8.dp))
                Text("경로 확인", fontSize = 16.sp)
            }
            Text(if (navigationAllowed) "선택한 시청까지의 편도 경로를 내비게이션에서 확인해요." else "경로 확인은 주차 후 이용해 주세요.",
                color = Secondary, fontSize = 13.sp, lineHeight = 19.sp)
        }
        if (state.failedCandidates > 0) {
            Text("일부 도시의 경로는 확인하지 못했어요. 다시 계산하면 재시도해요.", color = Secondary, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun CandidateRow(candidate: BatteryBudgetCandidate, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    val estimate = candidate.estimate
    val statusColor = if (estimate.feasible) AccentGreen else OrangeInk
    val returnColor = if (estimate.withinReserve) AccentGreen else OrangeInk
    val distance = String.format(Locale.KOREAN, "%.1f km", estimate.distanceMeters / 1000.0)
    BoxWithConstraints(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
            .background(if (selected) PaleBlue else CardColor)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        val name: @Composable (Modifier) -> Unit = { modifier ->
            Row(modifier, horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(if (selected) Blue else statusColor, CircleShape))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(candidate.cityName, color = if (selected) Blue else Ink, fontSize = 16.sp,
                        lineHeight = 22.sp, fontWeight = FontWeight.Medium)
                    Text(candidateStatus(candidate), color = statusColor, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
        }
        if (maxWidth >= 330.dp && LocalDensity.current.fontScale <= 1.2f) {
            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                name(Modifier.weight(1f))
                Text("왕복 $distance", Modifier.weight(1f), color = Secondary, fontSize = 13.sp)
                Text("귀가 ${returnPercentLabel(estimate.returnPercent)}", color = returnColor, fontSize = 14.sp,
                    fontWeight = FontWeight.Medium)
            }
        } else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            name(Modifier.fillMaxWidth())
            Text("왕복 $distance · 귀가 ${returnPercentLabel(estimate.returnPercent)}",
                color = returnColor, fontSize = 13.sp, lineHeight = 20.sp)
        }
    }
}

private fun candidateStatus(candidate: BatteryBudgetCandidate): String = with(candidate.estimate) {
    when {
        feasible -> "출발 가능"
        !withinTime && !withinReserve -> "시간 초과 · 잔량 부족"
        !withinTime -> "시간 초과"
        else -> "잔량 부족"
    }
}

@Composable
private fun CandidateBreakdown(candidate: BatteryBudgetCandidate, state: BatteryBudgetState) {
    val estimate = candidate.estimate
    val battery = state.batteryPercent
    val reserve = state.preferences.reservePercent.toDouble()
    val statusColor = if (estimate.feasible) AccentGreen else OrangeInk
    val status = candidateStatus(candidate)
    val driveUse = estimate.consumedPercent / BatteryBudgetCalculator.CONSUMPTION_MARGIN
    val margin = estimate.consumedPercent - driveUse
    val heldReserve = minOf(reserve, estimate.returnPercent)
    val returnSurplus = (estimate.returnPercent - heldReserve).coerceAtLeast(0.0)
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = CardColor,
        shadowElevation = CardShadowElevation) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("배터리 예산 계산", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("${candidate.province} · ${candidate.destination.name}", color = Secondary, fontSize = 13.sp, lineHeight = 19.sp)
            Text("왕복 이동 ${ceil(estimate.travelSeconds / 60.0).toInt()}분 + 방문 ${state.preferences.visitMinutes}분 = ${ceil(estimate.totalSeconds / 60.0).toInt()}분",
                color = if (estimate.withinTime) Secondary else OrangeInk, fontSize = 13.sp, lineHeight = 19.sp)
            if (battery != null) {
                val batteryLimit = battery.toDouble()
                val visibleUse = driveUse.coerceIn(0.0, (batteryLimit - estimate.returnPercent).coerceAtLeast(0.0))
                val visibleMargin = margin.coerceIn(0.0, (batteryLimit - estimate.returnPercent - visibleUse).coerceAtLeast(0.0))
                Text("귀가 목표 ${state.preferences.reservePercent}% · 예상 소모 ${percentPoints(driveUse)} · 여유분 ${percentPoints(margin)}",
                    color = Secondary, fontSize = 12.sp, lineHeight = 18.sp)
                BatterySegments(
                    listOf(heldReserve to if (estimate.withinReserve) AccentGreen else AccentOrange,
                        returnSurplus to AccentGreen.copy(alpha = .42f), visibleUse to Blue, visibleMargin to AccentOrange),
                    "출발 ${battery}% · 귀가 ${returnPercentLabel(estimate.returnPercent)} · 목표 ${state.preferences.reservePercent}%",
                    Modifier.height(15.dp),
                )
                val consumption = percentPoints(estimate.consumedPercent)
                val arrival = returnPercentLabel(estimate.returnPercent)
                Text(if (estimate.consumedPercent > batteryLimit) {
                    "예상 소모 $consumption · 출발 잔량 ${battery}%를 넘어요."
                } else {
                    "출발 ${battery}% − 소모 $consumption → 귀가 $arrival"
                }, color = Ink, fontSize = 14.sp, lineHeight = 21.sp)
            } else {
                Text("귀가 ${returnPercentLabel(estimate.returnPercent)} · 목표 ${state.preferences.reservePercent}% 이상",
                    color = Ink, fontSize = 14.sp, lineHeight = 21.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(statusColor, CircleShape))
                Text(status, color = statusColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun BatterySegments(segments: List<Pair<Double, Color>>, description: String, modifier: Modifier = Modifier) {
    val track = Line
    Canvas(modifier.fillMaxWidth().semantics { contentDescription = description }) {
        val radius = CornerRadius(size.height / 2f)
        val capsule = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), radius)) }
        drawRoundRect(track, cornerRadius = radius)
        clipPath(capsule) {
            var start = 0f
            segments.forEach { (amount, color) ->
                val end = (start + if (amount.isFinite()) (amount / 100.0).toFloat() else 0f).coerceIn(start, 1f)
                if (end > start) {
                    val left = start * size.width
                    val width = (end - start) * size.width
                    val gap = if (end < 1f) minOf(1.5.dp.toPx(), width * .2f) else 0f
                    drawRect(color, topLeft = Offset(left, 0f), size = Size(width - gap, size.height))
                }
                start = end
            }
        }
    }
}

private fun percentPoints(value: Double): String = String.format(Locale.KOREAN, "%.1f%%p", value)

private fun returnPercentLabel(value: Double): String = when {
    !value.isFinite() -> "—"
    value < 0 -> "0% 미만"
    else -> String.format(Locale.KOREAN, "%.1f%%", floor((value + 1e-9) * 10.0) / 10.0)
}

@Composable
private fun LoadingCard(message: String?) {
    DetailCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = Blue)
            Text("왕복 경로를 계산하고 있어요", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
        Text(message ?: "가는 길과 돌아오는 길의 거리와 시간을 각각 확인해요.",
            color = Secondary, fontSize = 14.sp, lineHeight = 22.sp)
    }
}

@Composable
private fun EmptyBudgetCard(message: String?, preview: Boolean) {
    DetailCard {
        Text("여행의 여유를 먼저 정해요", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Text(message ?: if (preview) "다른 조건으로 체험해 보세요." else "외출 시간과 남길 배터리를 정하면 가까운 미방문 도시의 왕복 예산을 계산해요.",
            color = Secondary, fontSize = 14.sp, lineHeight = 23.sp)
    }
}
