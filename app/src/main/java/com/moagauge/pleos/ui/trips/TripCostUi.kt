package com.moagauge.pleos.ui.trips

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.moagauge.pleos.ui.common.SoftSurface as Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.moagauge.pleos.trips.*
import com.moagauge.pleos.ui.*
import com.moagauge.pleos.ui.common.DimmedDialog
import com.moagauge.pleos.ui.common.LocalInfoRevealActive
import com.moagauge.pleos.ui.common.rememberDialogAction
import com.moagauge.pleos.ui.common.RoundedMetricBar
import kotlin.math.abs
import kotlinx.coroutines.flow.first

/** Cost insight lives in the trip summary panel; assumptions stay in a compact footer. */
@Composable
internal fun TripCostInsight(summary: TripCostSummary, previous: TripCostSummary?, previousLabel: String) {
    val savings = summary.savingsWon
    val electricity = summary.electricityCostWon
    val gasoline = summary.gasolineCostWon
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TripAnimatedElectricityCost(electricity)
        Text("예상 전력 비용", color = Ink, fontSize = 19.sp, lineHeight = 27.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(3.dp))
        if (electricity != null && gasoline != null && savings != null) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val compact = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.2f
                val savingsLabel = if (savings < 0) "유류비 대비 예상 추가 비용" else "유류비 대비 예상 절감"
                val savingsColor = if (savings < 0) OrangeInk else AccentGreen
                if (compact) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CostAmount(savingsLabel, abs(savings), savingsColor)
                    CostAmount("기준 유류비", gasoline, Secondary)
                } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CostAmount(savingsLabel, abs(savings), savingsColor, Modifier.weight(1f))
                    CostAmount("기준 유류비", gasoline, Secondary, Modifier.weight(1f), endAligned = true)
                }
            }
            if (savings >= 0) {
                // The full track is the fuel benchmark; the filled part is the electric estimate.
                RoundedMetricBar(if (gasoline > 0.0) (electricity / gasoline).toFloat() else 0f,
                    Modifier.height(16.dp), color = Blue, trackColor = Line)
            } else {
                // If electricity is more expensive, separate common-scale bars avoid a clipped ratio.
                val maximum = maxOf(electricity, gasoline)
                ComparisonBar("예상 전력 비용", electricity, maximum, true)
                ComparisonBar("기준 유류비", gasoline, maximum, false)
            }
        } else {
            RoundedMetricBar(0f, Modifier.height(12.dp), color = Blue, trackColor = Line)
            Text(if (summary.recordCount == 0) "측정값이 있는 주행을 저장하면 비용을 비교할 수 있어요."
                else "완료된 주행 중 거리와 전력량이 확인된 구간으로 비교해요.",
                color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
        }
        val change = summary.efficiencyChangePercent(previous)
        if (change != null) {
            Text("$previousLabel 대비 전비 ${TripCostFormat.decimal(abs(change))}% ${if (change >= 0) "향상" else "감소"}",
                color = if (change >= 0) AccentGreen else OrangeInk, fontSize = 13.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun TripAnimatedElectricityCost(electricity: Double?) {
    val finalCost = TripCostFormat.won(electricity)
    val counter = remember(finalCost) { ReceiptCostCounter(finalCost) }
    var countStarted by rememberSaveable(finalCost) { mutableStateOf(false) }
    val progress = remember(finalCost) {
        Animatable(if (countStarted || !counter.canAnimate || !ValueAnimator.areAnimatorsEnabled()) 1f else 0f)
    }
    var visible by remember { mutableStateOf(false) }
    val revealActive by rememberUpdatedState(LocalInfoRevealActive.current)
    LaunchedEffect(finalCost) {
        if (!countStarted && counter.canAnimate && ValueAnimator.areAnimatorsEnabled()) {
            // Compact panes compose below the fold; start when this amount is actually visible.
            snapshotFlow { visible && revealActive }.first { it }
            withFrameNanos { }
            countStarted = true
            progress.animateTo(1f, tween(720, easing = FastOutSlowInEasing))
        } else {
            progress.snapTo(1f)
        }
    }
    ReceiptAnimatedCost(finalCost, { counter.textAt(progress.value) },
        TextStyle(color = if (electricity == null) Ink else Blue,
            fontSize = 43.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold,
            fontFeatureSettings = "tnum"),
        Modifier.onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            visible = bounds.width > 0f && bounds.height > 0f
        })
}

@Composable
private fun CostAmount(label: String, value: Double, tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier, endAligned: Boolean = false) {
    Column(modifier, horizontalAlignment = if (endAligned) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = Secondary, fontSize = 13.sp)
        Text(TripCostFormat.won(value), color = tint, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun TripCostBasisFooter(summary: TripCostSummary, basis: TripCostBasis,
    onBasis: () -> Unit, basisEnabled: Boolean) {
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth(), color = CardColor, shape = RoundedCornerShape(22.dp),
        border = null, shadowElevation = CardShadowElevation) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(TripCostFormat.basisLabel(basis).removePrefix("비교 기준 · "), color = Ink,
                fontSize = 13.sp, lineHeight = 20.sp)
            Text("직접 설정한 기준 · 실시간 지역 유가나 실제 결제액이 아니에요.",
                color = Secondary, fontSize = 12.sp, lineHeight = 18.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                TextButton({ detailsOpen = !detailsOpen }, Modifier.weight(1f).heightIn(min = 48.dp)
                    .semantics { contentDescription = "주행 비용 산정 기준 ${if (detailsOpen) "접기" else "보기"}" }) {
                    Text(if (detailsOpen) "산정 기준 접기" else "산정 기준 보기", color = Secondary, fontSize = 13.sp)
                }
                TextButton(onBasis, enabled = basisEnabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text("비교 기준 변경", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (detailsOpen) {
                HorizontalDivider(color = Line)
                Text("${summary.measuredCount}/${summary.recordCount}회 · ${TripCostFormat.decimal(summary.measuredDistanceKm)} km 측정 구간 기준",
                    color = Ink, fontSize = 13.sp, lineHeight = 20.sp)
                Text("누락·중단·진행 중인 기록과 짧거나 전력량이 확인되지 않은 기록은 비용 비교에서 제외해요.",
                    color = Secondary, fontSize = 12.sp, lineHeight = 19.sp)
                Text("전력 비용은 기록된 사용량 × 단가예요. 충전 손실·할인·주차료·통행료는 포함하지 않아요.",
                    color = Secondary, fontSize = 12.sp, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun ComparisonBar(label: String, value: Double, maximum: Double, electric: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, Modifier.weight(1f), color = Secondary, fontSize = 13.sp)
            Text(TripCostFormat.won(value), color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        RoundedMetricBar(if (maximum > 0.0) (value / maximum).toFloat() else 0f, Modifier.height(10.dp),
            color = if (electric) Blue else Secondary.copy(alpha = .50f), trackColor = Line)
    }
}

@Composable
internal fun TripCostBasisDialog(basis: TripCostBasis, onSave: (TripCostBasis) -> Unit, onDismiss: () -> Unit) {
    var gasoline by rememberSaveable { mutableStateOf(inputNumber(basis.gasolineWonPerLiter)) }
    var benchmark by rememberSaveable { mutableStateOf(inputNumber(basis.benchmarkKmPerLiter)) }
    var electricity by rememberSaveable { mutableStateOf(inputNumber(basis.electricityWonPerKwh)) }
    val edited = TripCostBasis(gasoline.toDoubleOrNull() ?: Double.NaN,
        benchmark.toDoubleOrNull() ?: Double.NaN, electricity.toDoubleOrNull() ?: Double.NaN)
    // Broad input limits also protect receipts from impractically large user-entered amounts.
    val valid = edited.isValid && edited.gasolineWonPerLiter <= 100_000 &&
        edited.benchmarkKmPerLiter <= 1000 && edited.electricityWonPerKwh <= 100_000
    DimmedDialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val close = rememberDialogAction(onDismiss)
        val save = rememberDialogAction { onSave(edited) }
        BoxWithConstraints(Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 540.dp).fillMaxWidth().heightIn(max = maxHeight),
                shape = RoundedCornerShape(26.dp), color = SurfaceColor) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("비교 기준 변경", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                    Text("평균 연비를 가정한 휘발유 차량과 비교해요. 기본값은 비교용 예시이며, 알고 있는 단가로 바꿀 수 있어요.",
                        color = Secondary, fontSize = 14.sp, lineHeight = 21.sp)
                    BasisField("휘발유 가격 (원/L)", gasoline) { gasoline = it }
                    BasisField("비교 차량 평균 연비 (km/L)", benchmark) { benchmark = it }
                    BasisField("전력 단가 (원/kWh)", electricity) { electricity = it }
                    if (!valid) Text("0보다 큰 수를 입력해 주세요. 단가는 100,000 이하, 연비는 1,000 이하예요.",
                        color = OrangeInk, fontSize = 13.sp, lineHeight = 20.sp)
                    Text("전력 비용은 기록된 배터리 사용량 × 단가예요. 충전 손실·할인·주차료·통행료는 포함하지 않아요.",
                        color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
                    TextButton({
                        val defaults = TripCostBasis()
                        gasoline = inputNumber(defaults.gasolineWonPerLiter)
                        benchmark = inputNumber(defaults.benchmarkKmPerLiter)
                        electricity = inputNumber(defaults.electricityWonPerKwh)
                    }) { Text("기본 비교값 불러오기", fontSize = 14.sp) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                        TextButton(close) { Text("취소", fontSize = 16.sp) }
                        Button(save, enabled = valid, shape = RoundedCornerShape(50)) { Text("저장", fontSize = 16.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BasisField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, { if (it.length <= 12 && it.all { char -> char.isDigit() || char == '.' }) onChange(it) },
        Modifier.fillMaxWidth(), label = { Text(label, fontSize = 14.sp) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(14.dp))
}

private fun inputNumber(value: Double): String = TripCostFormat.exact(value)
