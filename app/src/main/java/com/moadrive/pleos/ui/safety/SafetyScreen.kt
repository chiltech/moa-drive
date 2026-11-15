package com.moadrive.pleos.ui.safety

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import com.moadrive.pleos.ui.common.SoftSurface as Surface
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moadrive.pleos.assist.NearbyIncident
import com.moadrive.pleos.cctv.CctvState
import com.moadrive.pleos.safety.*
import com.moadrive.pleos.safety.score.DrivingScoreViewModel
import com.moadrive.pleos.ui.*
import com.moadrive.pleos.ui.common.*
import com.moadrive.pleos.ui.assist.BriefingAvatar
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private val CautionInk: Color @Composable get() = OrangeInk
private val TimeLabel = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN).withZone(ZoneId.of("Asia/Seoul"))
private val DateTimeLabel = DateTimeFormatter.ofPattern("M/d HH:mm", Locale.KOREAN).withZone(ZoneId.of("Asia/Seoul"))

@Composable
fun SafetyScreen(
    model: SafetyViewModel,
    browsingAllowed: Boolean,
    onEnableLocation: () -> Unit,
    onHome: () -> Unit,
    onService: (HomeService) -> Unit,
    onAiBriefing: () -> Unit = {},
    onCctv: () -> Unit = { onService(HomeService.DRIVE) },
    aiGuideEnabled: Boolean = true,
    cctvEnabled: Boolean = true,
    aiBriefingAvailable: Boolean = true,
    cctvState: CctvState = CctvState(),
    incidentDemo: NearbyIncident? = null,
    drivingScoreModel: DrivingScoreViewModel? = null,
    tripRecordingEnabled: Boolean = true,
    assistContent: @Composable () -> Unit = {},
) {
    val state by model.state.collectAsState()
    val autoRefresh by model.autoRefresh.collectAsState()
    var preview by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(browsingAllowed) { if (!browsingAllowed) preview = false }
    val shownState = if (preview) remember(state.nowMs) { sampleSafetyState(state.nowMs) } else state
    val current = shownState.forecast?.centerSample?.current
    val assessment = shownState.assessment
    val fresh = shownState.forecast?.isFresh(shownState.nowMs) == true
    val settingsEnabled = browsingAllowed && !preview
    DetailPage(HomeService.SAFETY, browsingAllowed, onHome, onService,
        left = {
            DetailHeading("안전", "운전 습관과 출발 전 정보를 살펴요")
            DetailPageArtwork(JourneyArt.SAFETY, .72f, 300.dp)
            DetailSummary {
                Row(Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SafetyTarget.entries.forEach { target ->
                        val selected = state.target == target
                        val enabled = settingsEnabled && (target == SafetyTarget.CURRENT || state.destinationName != null)
                        Column(
                            modifier = Modifier.weight(1f).selectable(
                                selected = selected, enabled = enabled, role = Role.Tab,
                                onClick = { if (enabled) model.selectTarget(target) },
                            ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                                Text(if (target == SafetyTarget.CURRENT) "현재 위치" else "목적지",
                                    color = when {
                                        selected -> Blue
                                        enabled -> Ink
                                        else -> Secondary
                                    }, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
                            }
                            HorizontalDivider(thickness = 2.dp, color = if (selected) Blue else Color.Transparent)
                        }
                    }
                }
                Text(state.destinationName?.let { "목적지 · $it" } ?: "주행 메뉴에서 목적지를 선택해 주세요.",
                    color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
                HorizontalDivider(color = Line)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = autoRefresh, enabled = settingsEnabled,
                        onCheckedChange = { if (settingsEnabled) model.setAutoRefresh(it) },
                        modifier = Modifier.semantics { contentDescription = "날씨 자동 확인" })
                    Text("자동 확인", Modifier.weight(1f), color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    if (autoRefresh) Text("15분 또는 5km 이동 시", Modifier.weight(1.3f),
                        color = Secondary, fontSize = 13.sp, lineHeight = 18.sp)
                }
                if (!state.preciseLocationGranted) {
                    OutlinedButton(onEnableLocation, enabled = settingsEnabled,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(24.dp)) {
                        Text("위치 권한 설정", fontSize = 15.sp)
                    }
                }
                OutlinedButton(onClick = model::refresh, enabled = settingsEnabled && !state.loading,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                    Text(if (state.loading) "날씨를 확인하고 있어요" else "날씨 새로고침", fontSize = 15.sp)
                }
            }
            Text(if (preview) "샘플 화면을 보고 있어요. 실제 데이터로 돌아가면 현재 위치와 날씨 설정을 사용할 수 있어요."
                else "주행 시작을 감지하면 최근 현재 위치 주변 예보로 짧게 안내해요. 운전 중에는 날씨를 확인하는 조작이 제한돼요.",
                color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
        },
        right = {
            InfoReveal(0) {
                SampleDataHeader(
                    title = "오늘의 안전 케어",
                    description = "운전 습관과 주변 안전 정보를 함께 살펴요",
                    preview = preview,
                    enabled = browsingAllowed,
                    onToggle = { if (browsingAllowed) preview = !preview },
                    previewDescription = "샘플 점수·교통·날씨 · 실제 기록과 알림은 그대로 유지돼요",
                )
            }
            InfoReveal(1) {
                SafetyActionCard(
                    title = "모아 AI 브리핑",
                    description = when {
                        preview -> "샘플 브리핑 · 비가 오는 날에는 속도를 줄이고 차간 거리를 넉넉히 유지해 주세요."
                        !aiGuideEnabled -> "설정에서 AI 가이드를 켜 주세요."
                        !aiBriefingAvailable -> "차량이 주차 상태일 때 브리핑을 들을 수 있어요."
                        else -> "출발 전 오늘의 안전 안내를 확인해요."
                    },
                    ai = true,
                    enabled = browsingAllowed && !preview && aiGuideEnabled && aiBriefingAvailable,
                    onClick = onAiBriefing,
                )
            }
            if (preview) {
                InfoReveal(2) { SampleSafetyScoreCard() }
            } else drivingScoreModel?.let { scoreModel ->
                InfoReveal(2) {
                    DrivingScoreSection(scoreModel, browsingAllowed, tripRecordingEnabled,
                        onRecordingSettings = { onService(HomeService.SETTINGS) })
                }
            }
            InfoReveal(3) {
                TrafficSafetyCard(cctvState = cctvState, incidentDemo = incidentDemo, nowMs = state.nowMs,
                    browsingAllowed = browsingAllowed, cctvEnabled = cctvEnabled, onCctv = onCctv,
                    preview = preview)
            }
            InfoReveal(4) {
                DetailHeading("출발 전 안전 브리핑",
                    if (preview) "서울 도심 샘플 · 지금부터 6시간" else "${state.targetLabel} 기준 · 지금부터 6시간")
            }
            InfoReveal(5) {
                WeatherPanel(shownState, assessment, fresh, detailed = true)
            }
            if (!preview) assistContent()
            InfoReveal(6) {
                SafetyActionCard(
                    title = "주변 CCTV",
                    description = if (preview) "샘플 CCTV 수를 위 카드에서 확인해요. 실제 데이터에서 지도를 열 수 있어요."
                        else if (cctvEnabled) "지도에서 주변 CCTV 위치를 확인해요."
                        else "설정에서 CCTV 표시를 켜 주세요.",
                    ai = false,
                    enabled = browsingAllowed && !preview && cctvEnabled,
                    onClick = onCctv,
                )
            }
            shownState.forecast?.centerSample?.hours?.takeIf { it.isNotEmpty() }?.let { hours ->
                InfoReveal(7) {
                    DetailCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("시간별 예보", fontSize = 19.sp, fontWeight = FontWeight.Medium, color = Ink)
                            if (!fresh) Text("이전 예보", fontSize = 12.sp, color = CautionInk)
                        }
                        Text("중심 지점 · 강수 확률 / 기온", fontSize = 12.sp, color = Secondary)
                        HourlyWeather(hours)
                    }
                }
            }
            shownState.forecast?.let { forecast ->
                InfoReveal(8) {
                    DetailCard {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("주변 10km 날씨", Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.Medium, color = Ink)
                            Box(Modifier.size(7.dp).background(if (fresh) AccentGreen else AccentOrange, RoundedCornerShape(4.dp)))
                            Spacer(Modifier.width(6.dp))
                            Text(if (preview) "샘플 날씨" else TimeLabel.format(Instant.ofEpochMilli(forecast.fetchedAtMs)) + " 수신",
                                fontSize = 12.sp, color = Secondary)
                        }
                        forecast.samples.forEach { sample ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(sample.label, Modifier.weight(1f), color = Ink, fontSize = 14.sp)
                                WeatherSymbol(sample.current?.weatherCode, Modifier.size(25.dp))
                                Text(weatherDescription(sample.current?.weatherCode), Modifier.weight(1f), color = Secondary, fontSize = 14.sp)
                                Text(sample.current?.temperatureC?.let { "${decimal(it)}°" } ?: "—", fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Medium)
                            }
                        }
                        current?.let {
                            Text("중심 예보 기준 ${DateTimeLabel.format(Instant.ofEpochMilli(it.timeMs))}", fontSize = 12.sp, color = Secondary)
                        }
                        Text("중심과 동서남북 5개 지점의 표본이에요. 반경 전체나 도로 위험을 감지하지 않으며 인접 지점은 같은 예보 격자를 공유할 수 있어요.",
                            color = Secondary, fontSize = 12.sp, lineHeight = 19.sp)
                    }
                }
            }
            assessment.notices.forEach { notice ->
                DetailCard {
                    Text(notice.title, color = CautionInk, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(notice.detail, color = Secondary, fontSize = 14.sp, lineHeight = 22.sp)
                }
            }
            InfoReveal(9) {
                Text(if (preview) "화면 체험을 위한 샘플 날씨예요. 현재 위치의 실제 예보나 기상특보가 아니에요."
                    else "날씨: Open-Meteo · open-meteo.com · CC BY 4.0\n앱의 기상 참고 안내이며 공식 기상특보가 아니에요.",
                    color = Secondary, fontSize = 12.sp, lineHeight = 19.sp)
            }
        })
}

@Composable
private fun WeatherPanel(state: SafetyState, assessment: WeatherAssessment, fresh: Boolean, detailed: Boolean) {
    val center = state.forecast?.centerSample
    val current = center?.current
    val caution = assessment.attention in listOf(WeatherAttention.CAUTION, WeatherAttention.HIGH)
    val title = listOfNotNull(
        current?.weatherCode?.let(::weatherDescription),
        current?.temperatureC?.let { "${decimal(it).removeSuffix(".0")}°C" },
    ).joinToString(" ").ifBlank { "날씨 정보 없음" }
    val metrics = buildList {
        if (detailed) {
            val peak = center?.hours?.mapNotNull { it.precipitationProbability }?.maxOrNull()
            add("강수 예보" to (peak?.let { "최대 ${it.toInt()}%" } ?: "—"))
        }
        add("강수" to (current?.precipitationMm?.let { "${decimal(it)} mm" } ?: "—"))
        add("바람" to (current?.windMps?.let { "${decimal(it)} m/s" } ?: "—"))
        add("시정" to (current?.visibilityMeters?.let { "${decimal(it / 1000)} km" } ?: "—"))
    }
    DetailCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            WeatherSymbol(current?.weatherCode, Modifier.size(if (detailed) 56.dp else 42.dp), blueSun = !detailed)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (detailed) Text(if (caution) "날씨 · ${assessment.attention.label}" else "날씨",
                    color = if (caution) CautionInk else Secondary, fontSize = 13.sp)
                Text(title, color = Ink, fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
                Text(when {
                    !fresh && current != null -> "이전 예보 · 새로 확인해 주세요"
                    else -> assessment.summary
                }, color = if (caution || (!fresh && current != null)) CautionInk else Secondary,
                    fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
        WeatherMetrics(metrics)
        if (detailed) {
            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Blue, trackColor = PaleBlue, strokeCap = StrokeCap.Round)
            }
            val detail = state.message ?: when {
                !fresh -> "현재 상태를 판단하려면 최신 예보가 필요해요."
                !assessment.complete -> "일부 항목이 없어 주의 신호를 모두 확인할 수 없어요."
                else -> "표본 지점의 모델 예보예요. 실제 도로와 기상 상황도 함께 살펴 주세요."
            }
            Text(detail, color = Secondary, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun WeatherMetrics(metrics: List<Pair<String, String>>) {
    Surface(Modifier.fillMaxWidth(), color = BackgroundColor, shape = RoundedCornerShape(14.dp)) {
        BoxWithConstraints(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            if (maxWidth < 72.dp * metrics.size || LocalDensity.current.fontScale > 1.25f) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    metrics.forEach { (label, value) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(label, Modifier.weight(1f), color = Secondary, fontSize = 13.sp)
                            Text(value, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    metrics.forEachIndexed { index, (label, value) ->
                        if (index > 0) VerticalDivider(Modifier.height(34.dp), color = Line)
                        Column(Modifier.weight(if (metrics.size > 3 && index == 0) 1.35f else 1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(label, color = Secondary, fontSize = 13.sp)
                            Text(value, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SafetyActionCard(title: String, description: String, ai: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = { if (enabled) onClick() }, enabled = enabled, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp), color = CardColor, shadowElevation = CardShadowElevation) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            val showAiButtonLabel = ai && enabled && maxWidth >= 390.dp && LocalDensity.current.fontScale <= 1.25f
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (ai) BriefingAvatar(Modifier.size(54.dp))
                else SafetyActionIcon(ai = false, modifier = Modifier.size(38.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, color = Ink, fontSize = if (ai) 20.sp else 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(description, color = Secondary, fontSize = 14.sp, lineHeight = 21.sp)
                }
                if (showAiButtonLabel) {
                    Text("브리핑 보기  ›", Modifier.clearAndSetSemantics {}
                        .background(Blue, RoundedCornerShape(24.dp)).padding(horizontal = 14.dp, vertical = 9.dp),
                        color = OnBlue, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                } else {
                    Text("›", color = if (enabled) Blue else Secondary, fontSize = 23.sp, modifier = Modifier.clearAndSetSemantics {})
                }
            }
        }
    }
}

@Composable
private fun SafetyActionIcon(ai: Boolean, modifier: Modifier) {
    val blue = Blue
    val paleBlue = PaleBlue
    val green = AccentGreen
    val onBlue = OnBlue
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (ai) {
            drawCircle(paleBlue)
            drawLine(blue, Offset(w * .5f, h * .22f), Offset(w * .5f, h * .39f), 2.dp.toPx(), StrokeCap.Round)
            drawCircle(green, w * .065f, Offset(w * .5f, h * .23f))
            drawRoundRect(blue, Offset(w * .19f, h * .35f), Size(w * .62f, h * .43f), CornerRadius(w * .14f))
            drawCircle(onBlue, w * .054f, Offset(w * .39f, h * .55f))
            drawCircle(onBlue, w * .054f, Offset(w * .61f, h * .55f))
        } else {
            val camera = Path().apply {
                moveTo(w * .14f, h * .39f)
                lineTo(w * .78f, h * .18f)
                lineTo(w * .9f, h * .53f)
                lineTo(w * .28f, h * .74f)
                close()
            }
            drawPath(camera, blue, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(blue, w * .048f, Offset(w * .73f, h * .46f))
            drawLine(blue, Offset(w * .26f, h * .63f), Offset(w * .07f, h * .71f), 2.dp.toPx(), StrokeCap.Round)
            drawLine(blue, Offset(w * .07f, h * .62f), Offset(w * .07f, h * .83f), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
private fun HourlyWeather(hours: List<WeatherReading>) {
    BoxWithConstraints {
        val columns = if (maxWidth < 380.dp || LocalDensity.current.fontScale > 1.2f) 2 else 3
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            hours.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { hour ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(TimeLabel.format(Instant.ofEpochMilli(hour.timeMs)), fontSize = 13.sp, color = Secondary)
                            WeatherSymbol(hour.weatherCode, Modifier.size(34.dp))
                            Text(hour.temperatureC?.let { "${decimal(it)}°" } ?: "—", fontSize = 20.sp, color = Ink, fontWeight = FontWeight.Normal)
                            Text(hour.precipitationProbability?.let { "강수 ${it.toInt()}%" } ?: "강수 —", fontSize = 11.sp, color = Blue)
                            Text(weatherDescription(hour.weatherCode), fontSize = 11.sp, color = Secondary)
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Small native weather symbols complement the illustration; labels carry the exact forecast. */
@Composable
private fun WeatherSymbol(code: Int?, modifier: Modifier, blueSun: Boolean = false) {
    val cloud = if (IsDarkTheme) Color(0xFFACB8CD) else Color(0xFF8EB5EE)
    val rain = Blue
    val sun = if (blueSun && code == 0) Blue else AccentYellow
    val muted = Secondary
    Canvas(modifier.semantics { contentDescription = weatherDescription(code) }) {
        val w = size.width
        val h = size.height
        if (code == null) {
            drawLine(muted, Offset(w * .3f, h * .5f), Offset(w * .7f, h * .5f), 2.dp.toPx(), StrokeCap.Round)
        } else if (code == 0) {
            drawCircle(sun, w * .23f, Offset(w * .5f, h * .5f))
            repeat(8) { index ->
                val angle = index * Math.PI.toFloat() / 4f
                drawLine(sun,
                    Offset(w * (.5f + cos(angle) * .32f), h * (.5f + sin(angle) * .32f)),
                    Offset(w * (.5f + cos(angle) * .43f), h * (.5f + sin(angle) * .43f)),
                    2.dp.toPx(), StrokeCap.Round)
            }
        } else {
            if (code in 1..2) drawCircle(sun, w * .21f, Offset(w * .7f, h * .3f))
            drawOval(cloud, Offset(w * .13f, h * .34f), Size(w * .74f, h * .34f))
            drawCircle(cloud, w * .21f, Offset(w * .45f, h * .35f))
            drawCircle(cloud, w * .16f, Offset(w * .66f, h * .43f))
            when (code) {
                71, 73, 75, 77, 85, 86 -> repeat(3) { i ->
                    drawCircle(cloud, w * .045f, Offset(w * (.3f + i * .2f), h * .85f))
                }
                51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99 -> repeat(3) { i ->
                    val x = w * (.3f + i * .2f)
                    drawLine(rain, Offset(x, h * .76f), Offset(x - w * .035f, h * .89f), 2.dp.toPx(), StrokeCap.Round)
                }
                45, 48 -> drawLine(muted, Offset(w * .2f, h * .82f), Offset(w * .8f, h * .82f), 2.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

private fun decimal(value: Double) = String.format(Locale.KOREAN, "%.1f", value)
