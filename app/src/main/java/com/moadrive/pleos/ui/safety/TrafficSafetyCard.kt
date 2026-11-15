package com.moadrive.pleos.ui.safety

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moadrive.pleos.assist.NearbyIncident
import com.moadrive.pleos.cctv.CctvState
import com.moadrive.pleos.safety.*
import com.moadrive.pleos.ui.*
import com.moadrive.pleos.ui.common.DetailCard
import com.moadrive.pleos.ui.common.SoftSurface
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TrafficUpdatedTime = DateTimeFormatter.ofPattern("M/d HH:mm", Locale.KOREAN)
    .withZone(ZoneId.of("Asia/Seoul"))

@Composable
internal fun TrafficSafetyCard(
    cctvState: CctvState,
    incidentDemo: NearbyIncident?,
    nowMs: Long,
    browsingAllowed: Boolean,
    cctvEnabled: Boolean,
    onCctv: () -> Unit,
    preview: Boolean = false,
) {
    var showAlerts by rememberSaveable(preview) { mutableStateOf(true) }
    val summary = if (preview) demoTrafficSafetySummary(
        if (showAlerts) TrafficDemoScenario.ALERTS else TrafficDemoScenario.QUIET, null, nowMs,
    ) else currentTrafficSafetySummary(cctvState, nowMs)

    DetailCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("주변 교통 안전", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(if (preview) "샘플 데이터 · 실제 교통정보가 아니에요" else "최근 조회 위치 기준 · CCTV 반경 10km",
                color = if (preview) OrangeInk else Secondary, fontSize = 12.sp, lineHeight = 18.sp)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 372.dp || LocalDensity.current.fontScale > 1.25f
            val entries = listOf(
                TrafficTile("돌발 교통사고", summary.accidents, Blue, TrafficSymbol.ACCIDENT),
                TrafficTile("보행자 사고 정보", summary.pedestrians, OrangeInk, TrafficSymbol.PEDESTRIAN),
                TrafficTile("주변 CCTV", summary.cctv, AccentGreen, TrafficSymbol.CCTV),
            )
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    entries.forEach { item ->
                        TrafficMetricTile(item, Modifier.fillMaxWidth(), compact = true,
                            enabled = browsingAllowed && cctvEnabled && !preview,
                            onCctv = onCctv)
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    entries.forEach { item ->
                        TrafficMetricTile(item, Modifier.weight(1f), compact = false,
                            enabled = browsingAllowed && cctvEnabled && !preview,
                            onCctv = onCctv)
                    }
                }
            }
        }
        if (preview) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (showAlerts) "돌발 상황 예시" else "기본 예시",
                    Modifier.weight(1f), color = OrangeInk, fontSize = 12.sp)
                TextButton(onClick = { showAlerts = !showAlerts }, enabled = browsingAllowed,
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (showAlerts) "기본 예시 보기" else "돌발 상황 보기", fontSize = 13.sp)
                }
            }
        } else {
            Text("돌발 정보·보행자 사고 이력은 연결 대기 중이에요.",
                color = Secondary, fontSize = 12.sp, lineHeight = 18.sp)
            val cctvStatus = summary.cctv.updatedAtMs?.let { updated ->
                val ageMinutes = ((nowMs - updated).coerceAtLeast(0L) / 60_000L)
                val ageLabel = when {
                    ageMinutes < 1 -> "방금 확인"
                    ageMinutes < 60 -> "${ageMinutes}분 전 확인"
                    ageMinutes < 1_440 -> "${ageMinutes / 60}시간 전 확인"
                    else -> "${ageMinutes / 1_440}일 전 확인"
                }
                "${TrafficUpdatedTime.format(Instant.ofEpochMilli(updated))} · $ageLabel"
            } ?: if (cctvEnabled) "지도에서 조회한 CCTV 수" else "설정에서 CCTV 표시를 켜 주세요"
            Text("CCTV · 국가교통정보센터 고속도로\n$cctvStatus",
                color = Secondary, fontSize = 11.sp, lineHeight = 17.sp)
        }
    }
}

private enum class TrafficSymbol { ACCIDENT, PEDESTRIAN, CCTV }
private data class TrafficTile(val title: String, val metric: TrafficSafetyMetric, val color: Color, val symbol: TrafficSymbol)

@Composable
private fun TrafficMetricTile(item: TrafficTile, modifier: Modifier, compact: Boolean, enabled: Boolean, onCctv: () -> Unit) {
    val actionable = item.symbol == TrafficSymbol.CCTV && item.metric.status != TrafficMetricStatus.DEMO
    val value = item.metric.count?.let { "$it 건" } ?: "—"
    val semantics = modifier.semantics(mergeDescendants = true) {
        contentDescription = "${item.title}, $value, ${item.metric.statusLabel}"
    }
    val content: @Composable () -> Unit = {
        if (compact) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TrafficMetricIcon(item)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TrafficMetricTitle(item, actionable)
                    Text(item.metric.statusLabel, color = Secondary, fontSize = 11.sp, lineHeight = 16.sp)
                }
                TrafficMetricValue(item)
            }
        } else {
            Column(Modifier.fillMaxWidth().heightIn(min = 157.dp).padding(horizontal = 12.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TrafficMetricIcon(item)
                TrafficMetricTitle(item, actionable)
                TrafficMetricValue(item)
                Text(item.metric.statusLabel, color = Secondary, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
    }
    if (actionable) {
        SoftSurface(onClick = onCctv, enabled = enabled, modifier = semantics,
            color = item.color.copy(alpha = if (IsDarkTheme) .12f else .065f),
            shape = RoundedCornerShape(16.dp), content = content)
    } else {
        SoftSurface(modifier = semantics, color = item.color.copy(alpha = if (IsDarkTheme) .12f else .065f),
            shape = RoundedCornerShape(16.dp), content = content)
    }
}

@Composable
private fun TrafficMetricTitle(item: TrafficTile, actionable: Boolean) {
    Text(item.title + if (actionable) " ›" else "", color = Ink, fontSize = 13.sp,
        lineHeight = 19.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun TrafficMetricValue(item: TrafficTile) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(item.metric.count?.toString() ?: "—", color = item.color,
            fontSize = 31.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold)
        if (item.metric.count != null) Text("건", Modifier.padding(bottom = 3.dp), color = item.color,
            fontSize = 13.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TrafficMetricIcon(item: TrafficTile) {
    Box(Modifier.size(34.dp).background(item.color.copy(alpha = .10f), CircleShape), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(22.dp)) {
            val scale = size.width / 24f
            fun at(x: Float, y: Float) = Offset(x * scale, y * scale)
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
                drawLine(item.color, at(x1, y1), at(x2, y2), 1.7f * scale, StrokeCap.Round)
            when (item.symbol) {
                TrafficSymbol.ACCIDENT -> {
                    drawRoundRect(item.color, at(4f, 10f), Size(16 * scale, 8 * scale), CornerRadius(2 * scale), style = Stroke(1.7f * scale))
                    line(6f, 10f, 8f, 6f); line(8f, 6f, 16f, 6f); line(16f, 6f, 18f, 10f)
                    line(6f, 18f, 6f, 20f); line(18f, 18f, 18f, 20f)
                    drawCircle(item.color, 1.2f * scale, at(7f, 14f)); drawCircle(item.color, 1.2f * scale, at(17f, 14f))
                    line(12f, 1f, 12f, 3f); line(4f, 3f, 5.5f, 4.5f); line(20f, 3f, 18.5f, 4.5f)
                }
                TrafficSymbol.PEDESTRIAN -> {
                    drawCircle(item.color, 2.2f * scale, at(13f, 4f))
                    line(12f, 8f, 10f, 14f); line(12f, 8f, 8f, 10f); line(8f, 10f, 6f, 13f)
                    line(12f, 8f, 15f, 12f); line(15f, 12f, 19f, 12f)
                    line(10f, 14f, 7f, 21f); line(10f, 14f, 14f, 17f); line(14f, 17f, 15f, 21f)
                }
                TrafficSymbol.CCTV -> {
                    val body = Path().apply {
                        moveTo(3 * scale, 5 * scale); lineTo(18 * scale, 8 * scale)
                        lineTo(15 * scale, 14 * scale); lineTo(2 * scale, 11 * scale); close()
                    }
                    drawPath(body, item.color, style = Stroke(1.7f * scale))
                    line(17f, 10f, 21f, 11f); line(21f, 11f, 19f, 15f); line(19f, 15f, 15f, 14f)
                    line(9f, 14f, 8f, 19f); line(8f, 19f, 16f, 19f); line(16f, 17f, 16f, 21f)
                }
            }
        }
    }
}
