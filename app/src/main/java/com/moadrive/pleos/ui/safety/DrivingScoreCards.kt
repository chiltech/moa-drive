package com.moadrive.pleos.ui.safety

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moadrive.pleos.R
import com.moadrive.pleos.safety.score.*
import com.moadrive.pleos.ui.*
import com.moadrive.pleos.ui.common.*
import com.moadrive.pleos.ui.common.SoftSurface as Surface
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.first

private val ScoreDateTime = DateTimeFormatter.ofPattern("M/d HH:mm", Locale.KOREAN).withZone(ZoneId.of("Asia/Seoul"))

@Composable
fun DrivingScoreSection(
    model: DrivingScoreViewModel,
    enabled: Boolean,
    tripRecordingEnabled: Boolean = true,
    onRecordingSettings: () -> Unit = {},
) {
    val state by model.state.collectAsState()
    val summary = state.eligibility
    var historyOpen by remember { mutableStateOf(false) }
    var formulaOpen by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DrivingScoreRecord?>(null) }
    LaunchedEffect(enabled) {
        if (!enabled) { historyOpen = false; formulaOpen = false; pendingDelete = null }
    }
    DetailCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("내 안전운전 점수", Modifier.weight(1f), color = Ink, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            Text("MOA 참고", color = Blue, fontSize = 12.sp)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnimatedDrivingScoreValue(summary.score)
            Text("/ 100점", Modifier.padding(bottom = 9.dp), color = Secondary, fontSize = 17.sp)
        }
        Text(if (summary.scoredTrips == 0) "충분히 관측한 운행이 쌓이면 점수를 보여 드려요."
            else "측정 완료 ${summary.scoredTrips}회 · ${scoreKm(summary.distanceKm)} km 관측",
            color = Secondary, fontSize = 14.sp, lineHeight = 21.sp)
        HorizontalDivider(color = Line)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("운행 점수 기록", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(when {
                    !tripRecordingEnabled -> "주행기록을 켜면 관측을 시작할 수 있어요"
                    state.recordingEnabled -> "새 운행의 차량 신호를 관측해요"
                    else -> "새 관측을 잠시 멈췄어요"
                },
                    color = Secondary, fontSize = 12.sp)
            }
            Switch(checked = state.recordingEnabled, enabled = enabled && !state.loading,
                onCheckedChange = model::setRecordingEnabled,
                modifier = Modifier.semantics { contentDescription = "안전운전 점수 기록" })
        }
        if (!tripRecordingEnabled) {
            Text("주행기록이 꺼져 있어요", color = OrangeInk, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text("설정에서 주행기록을 켜야 새 운행의 안전운전 점수를 기록할 수 있어요.",
                color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
            OutlinedButton(onClick = onRecordingSettings, enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("주행기록 설정 열기") }
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Blue, trackColor = PaleBlue)
        state.current?.let { record ->
            Surface(shape = RoundedCornerShape(12.dp), color = PaleBlue) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(if (state.recordingEnabled && tripRecordingEnabled) "이번 운행 관측 중" else "이번 운행 관측 일시정지", color = Blue,
                        fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("${scoreKm(record.observedDistanceMeters / 1_000)} km · ${record.observedDurationMs / 1_000}초 관측",
                        color = Ink, fontSize = 14.sp)
                    Text("운행이 끝난 뒤 시간·거리 관측률을 확인해요.", color = Secondary, fontSize = 12.sp)
                }
            }
        }
        Text("앱을 보고 있는 동안 관측한 기록을 차량에 저장해요. 과거 운행이나 관측하지 못한 구간에는 점수를 만들지 않아요.",
            color = Secondary, fontSize = 12.sp, lineHeight = 19.sp)
        Text("과속은 실제 제한속도를 수신한 구간만 평가해요. 나머지 구간은 미측정이에요.",
            color = Secondary, fontSize = 12.sp, lineHeight = 19.sp)
        state.storageError?.let { message ->
            Text(message, color = OrangeInk, fontSize = 13.sp, lineHeight = 20.sp)
            TextButton(onClick = model::retryStorage, enabled = enabled && !state.loading) { Text("저장 다시 시도") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { historyOpen = true }, enabled = enabled,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("기록 관리 ${state.records.size}건", fontSize = 14.sp) }
            TextButton(onClick = { formulaOpen = true }, enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp)) { Text("계산 기준", fontSize = 14.sp) }
        }
        state.records.take(2).forEach { record ->
            HorizontalDivider(color = Line.copy(alpha = .5f))
            ScoreHistoryRow(record, enabled, onDelete = { pendingDelete = record })
        }
    }
    if (historyOpen && enabled) {
        DimmedDialog(onDismissRequest = { historyOpen = false }) {
            val dismiss = rememberDialogAction { historyOpen = false }
            Surface(Modifier.widthIn(max = 700.dp).fillMaxWidth(.92f).fillMaxHeight(.90f),
                color = SurfaceColor, shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("안전운전 기록", Modifier.weight(1f), color = Ink, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = dismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("닫기") }
                    }
                    Text("날짜별 실제 관측 기록 · 점수 없는 운행은 보험 참고 조건에서 제외돼요.",
                        color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
                    if (state.records.isEmpty()) Text("저장된 운행이 없어요. 기록을 켜고 다음 운행부터 시작해 보세요.",
                        color = Ink, fontSize = 16.sp, lineHeight = 24.sp)
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.records, key = { it.tripId }) { record ->
                            ScoreHistoryRow(record, enabled, onDelete = { pendingDelete = record })
                            HorizontalDivider(color = Line.copy(alpha = .5f))
                        }
                    }
                }
            }
        }
    }
    if (formulaOpen && enabled) {
        DimmedAlertDialog(onDismissRequest = { formulaOpen = false },
            title = { Text("MOA 점수 계산 기준") },
            text = {
                Text("완료된 운행 중 1분·500m 이상을 달리고, 시간과 거리의 80% 이상을 관측한 기록을 계산해요. 관측량이 운행량보다 과도하거나 데이터 공백이 있으면 점수를 보류해요.\n\n100점에서 급가속(3 m/s² 이상·1초) 1회 3점, 급감속(−3.5 m/s² 이하·1초) 1회 5점, 과속(제한+5 km/h 초과·3초) 1회 5점을 차감해요. 연속 동작은 한 번으로 세요. 종합 점수는 관측 거리로 가중 평균해요.\n\n제한속도 미수신 구간은 과속 미측정이에요. MOA 자체 참고 지표이며 보험사가 인정하는 안전운전 점수가 아니에요.",
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    color = Secondary, fontSize = 14.sp, lineHeight = 22.sp)
            }, confirmButton = { TextButton(onClick = rememberDialogAction { formulaOpen = false }) { Text("확인") } })
    }
    pendingDelete?.takeIf { enabled }?.let { record ->
        DimmedAlertDialog(onDismissRequest = { pendingDelete = null },
            title = { Text("운행 점수를 삭제할까요?") },
            text = { Text("${record.startDate} 기록을 삭제해요. 보험의 참고 조건도 남은 기록으로 다시 계산하며 이 작업은 되돌릴 수 없어요.") },
            confirmButton = { TextButton(onClick = rememberDialogAction { model.deleteRecord(record.tripId); pendingDelete = null }) { Text("삭제", color = OrangeInk) } },
            dismissButton = { TextButton(onClick = rememberDialogAction { pendingDelete = null }) { Text("취소") } })
    }
}

/** The live and sample score cards share the same presentation, without changing stored scores. */
@Composable
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
internal fun AnimatedDrivingScoreValue(score: Int?) {
    val finalScore = score?.toString() ?: "—"
    val canAnimate = score != null && score > 0
    var countStarted by rememberSaveable(score) { mutableStateOf(false) }
    val progress = remember(score) {
        Animatable(if (countStarted || !canAnimate || !ValueAnimator.areAnimatorsEnabled()) 1f else 0f)
    }
    var visible by remember { mutableStateOf(false) }
    val revealActive by rememberUpdatedState(LocalInfoRevealActive.current)
    LaunchedEffect(score) {
        if (!countStarted && canAnimate && ValueAnimator.areAnimatorsEnabled()) {
            // Both full and compact detail panes compose cards before they enter the viewport.
            snapshotFlow { visible && revealActive }.first { it }
            withFrameNanos { }
            countStarted = true
            if (ValueAnimator.areAnimatorsEnabled()) {
                progress.animateTo(1f, tween(720, easing = FastOutSlowInEasing))
            } else progress.snapTo(1f)
        } else progress.snapTo(1f)
    }
    val scoreTypeface = remember {
        FontFamily(Font(R.font.inter_variable, weight = FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontVariation.weight(700))))
    }
    val style = TextStyle(color = Blue, fontFamily = scoreTypeface, fontSize = 48.sp,
        fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
    Box(Modifier.onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        visible = bounds.width > 0f && bounds.height > 0f
    }.clearAndSetSemantics { text = AnnotatedString(finalScore) }, contentAlignment = Alignment.CenterEnd) {
        // Keep the final width and spoken score stable while only the visible digits count up.
        Text(finalScore, Modifier.alpha(0f), style = style)
        Text(if (score != null && canAnimate && progress.value < 1f) (score * progress.value).toInt().toString()
            else finalScore, style = style)
    }
}

@Composable
private fun ScoreHistoryRow(record: DrivingScoreRecord, enabled: Boolean, onDelete: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(record.startDate + " · " + ScoreDateTime.format(Instant.ofEpochMilli(record.startedAtMs)).substringAfter(' '),
                    color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(record.score?.let { "$it 점 · ${scoreKm(record.observedDistanceMeters / 1_000)} km 관측" }
                    ?: "점수 보류 · 관측량이 충분하지 않거나 운행이 미완료예요", color = if (record.score == null) Secondary else Blue,
                    fontSize = 13.sp, lineHeight = 20.sp)
            }
            TextButton(onClick = onDelete, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text("삭제", fontSize = 13.sp) }
        }
        Text("급가속 ${record.accelerationEvents} · 급감속 ${record.brakingEvents} · " +
            if (record.hasSpeedLimitObservation) "과속 ${record.overspeedEvents} (제한속도 수신 구간)" else "과속 미측정",
            color = Secondary, fontSize = 12.sp, lineHeight = 19.sp)
        Text("시간 관측 ${(record.durationCoverage * 100).toInt().coerceIn(0, 100)}% · 거리 관측 ${(record.distanceCoverage * 100).toInt().coerceIn(0, 100)}%",
            color = Secondary, fontSize = 12.sp)
    }
}

@Composable
fun DrivingScoreInsuranceCard(state: DrivingScoreState, enabled: Boolean, onManage: () -> Unit) {
    val result = state.eligibility
    var preview by remember { mutableStateOf<DrivingScorePreview?>(null) }
    DetailCard {
        Text("안전운전 점수", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(when {
            result.score == null -> "기록을 더 모아 주세요"
            result.meetsReference -> "앱 참고 조건 충족 · 보험사 확인 필요"
            else -> "앱 참고 조건까지 조금 더 필요해요"
        }, color = if (result.meetsReference) Blue else Ink, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(result.score?.let { "$it 점" } ?: "점수 없음", color = Blue, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                Text("참고 기준 80점", color = Secondary, fontSize = 12.sp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${scoreKm(result.distanceKm)} km", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                Text("참고 기준 500 km", color = Secondary, fontSize = 12.sp)
            }
        }
        if (!result.meetsReference) Text(buildList {
            result.pointsRemaining?.takeIf { it > 0 }?.let { add("점수 ${it}점") }
            if (result.kilometersRemaining > 0) add("관측 거리 ${scoreKm(result.kilometersRemaining)} km")
        }.joinToString(" · ").let { if (it.isBlank()) "충분히 관측한 완료 운행이 필요해요." else "$it 더 필요해요." },
            color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
        Text("MOA 자체 점수와 거리로 확인한 앱 참고 조건이에요. 보험사의 인정 점수·측정 기간·특약 조건은 다르며, 가입 가능 여부와 할인은 보험사 확인이 필요해요.",
            color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
        Text("과속은 제한속도 수신 구간만 측정했으며, 미수신 구간의 안전을 확인한 결과는 아니에요.",
            color = Secondary, fontSize = 12.sp, lineHeight = 19.sp)
        if (state.storageError != null) Text("기록 저장 상태를 안전 메뉴에서 확인해 주세요.", color = OrangeInk, fontSize = 12.sp)
        OutlinedButton(onClick = onManage, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("안전운전 기록 관리") }
        HorizontalDivider(color = Line.copy(alpha = .5f))
        Text("조건 예시 · 내 기록에 반영되지 않아요", color = Secondary, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = preview == DrivingScorePreview.READY,
                onClick = { preview = if (preview == DrivingScorePreview.READY) null else DrivingScorePreview.READY }, enabled = enabled,
                label = { Text("충족 예시") })
            FilterChip(selected = preview == DrivingScorePreview.MORE_DISTANCE,
                onClick = { preview = if (preview == DrivingScorePreview.MORE_DISTANCE) null else DrivingScorePreview.MORE_DISTANCE }, enabled = enabled,
                label = { Text("부족 예시") })
        }
        preview?.let { example ->
            Text("예시: ${example.score}점 · ${scoreKm(example.distanceKm)} km → " +
                if (example.meetsReference) "앱 참고 조건 충족, 보험사 확인 필요"
                else "관측 거리 ${scoreKm(500.0 - example.distanceKm)} km가 더 필요해요",
                color = Blue, fontSize = 13.sp, lineHeight = 20.sp)
        }
    }
}

private fun scoreKm(value: Double): String = String.format(Locale.KOREAN, "%.1f", value)
