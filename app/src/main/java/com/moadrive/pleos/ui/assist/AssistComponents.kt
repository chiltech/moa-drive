package com.moadrive.pleos.ui.assist


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moadrive.pleos.assist.AssistState
import com.moadrive.pleos.assist.NearbyIncident
import com.moadrive.pleos.assist.RoutineSuggestion
import com.moadrive.pleos.ui.Blue
import com.moadrive.pleos.ui.Ink
import com.moadrive.pleos.ui.Line
import com.moadrive.pleos.ui.MoaIcon
import com.moadrive.pleos.ui.MoaIconKind
import com.moadrive.pleos.ui.PaleBlue
import com.moadrive.pleos.ui.Secondary
import com.moadrive.pleos.ui.OrangeInk
import com.moadrive.pleos.ui.AccentOrange
import com.moadrive.pleos.ui.common.DetailCard
import java.util.Locale

private val AlertInk: Color @Composable get() = OrangeInk

/** A parked boarding suggestion. The caller owns visibility and the navigation handoff. */
@Composable
fun RoutineSuggestionCard(
    suggestion: RoutineSuggestion,
    enabled: Boolean,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    aiGuideEnabled: Boolean = true,
    speaking: Boolean = false,
    voiceUnavailable: Boolean = false,
) {
    val scenario = suggestion.scenario
    AiCareDialog(scenario.title, onDismiss, modifier, speaking, aiEnabled = aiGuideEnabled,
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                HorizontalDivider(color = Line)
                if (speaking && aiGuideEnabled) Text("모아가 안내하고 있어요", color = Secondary, fontSize = 13.sp)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val stacked = maxWidth < 460.dp || LocalDensity.current.fontScale > 1.2f
                    if (stacked) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AcceptRoutineButton(enabled, onAccept, Modifier.fillMaxWidth())
                        DismissRoutineButton(enabled, onDismiss, Modifier.fillMaxWidth())
                    } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        DismissRoutineButton(enabled, onDismiss, Modifier.weight(1f))
                        AcceptRoutineButton(enabled, onAccept, Modifier.weight(1f))
                    }
                }
            }
        }) {
        AiCareNotice {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MoaIcon(MoaIconKind.DRIVE, Modifier.size(42.dp), Blue)
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("평일 ${clock(scenario.hour)} · ${scenario.label}", color = Blue,
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("익숙한 시간의 이동을 챙겨요", color = Secondary, fontSize = 13.sp)
                }
            }
            if (aiGuideEnabled) Text(scenario.careMessage, color = Ink, fontSize = 18.sp, lineHeight = 28.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(scenario.question, color = Ink, fontSize = 22.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().background(PaleBlue, RoundedCornerShape(18.dp)).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MoaIcon(MoaIconKind.PASSPORT, Modifier.size(32.dp), Blue, outline = true)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("안내할 예시 목적지", color = Secondary, fontSize = 13.sp)
                    Text(scenario.destinationQuery, color = Blue, fontSize = 20.sp, lineHeight = 28.sp,
                        fontWeight = FontWeight.SemiBold)
                }
            }
            Text("루틴 체험 · 예시 이동 ${suggestion.sampleCount}회를 바탕으로 한 안내예요.",
                color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
        }
        if (voiceUnavailable && aiGuideEnabled) Text("음성을 사용할 수 없어 화면으로 안내해요.",
            color = Secondary, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun AcceptRoutineButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Button(onClick = { if (enabled) onClick() }, enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp), shape = RoundedCornerShape(50)) {
        Text("경로 보기", fontSize = 17.sp)
    }
}

@Composable
private fun DismissRoutineButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    TextButton(onClick = { if (enabled) onClick() }, enabled = enabled, modifier = modifier.heightIn(min = 48.dp)) {
        Text("지금은 괜찮아요", fontSize = 16.sp)
    }
}

/** Incident notice content. The caller owns the modal window and dismissal. */
@Composable
fun IncidentAlertBanner(nearby: NearbyIncident, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AiCareDialog("주변 도로를 살펴 주세요", onDismiss, modifier, aiEnabled = false,
        footer = { AiCareConfirmFooter(onDismiss, speaking = false) }) {
        AiCareNotice {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MoaIcon(MoaIconKind.SAFETY, Modifier.size(44.dp), AccentOrange)
                Text("사고 안내 · 체험", color = AlertInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("주변 ${kilometers(nearby.distanceMeters)}km에 큰 사고가 발생했어요",
                color = Ink, fontSize = 24.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold)
            Text("급제동을 피하고 충분한 차간거리를 유지해 주세요.",
                color = Secondary, fontSize = 18.sp, lineHeight = 28.sp)
        }
        Text("현재 위치 기준 직선거리 · 실제 사고 정보가 아닌 체험 안내예요.",
            color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
    }
}

/** Incident controls stay on Safety; routine choices are presented on Home. */
@Composable
fun IncidentScenarioCard(
    state: AssistState,
    browsingAllowed: Boolean,
    onIncidentDemo: () -> Unit,
    onArmIncident: () -> Unit,
    onReset: () -> Unit,
) {
    var optionsExpanded by rememberSaveable { mutableStateOf(false) }
    DetailCard {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            MoaIcon(MoaIconKind.SAFETY, Modifier.size(24.dp), Blue, outline = true)
            Text("주변 10km 사고 안내", Modifier.weight(1f), color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Text("큰 사고 주변에 진입하면 한 번 알려주는 예시예요.", color = Secondary, fontSize = 14.sp, lineHeight = 21.sp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("예시 사고 · 직선 반경 10km", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                state.demoDistanceMeters?.let { distance -> AssistBadge("체험 거리 ${kilometers(distance)}km") }
            }
            TextButton(onClick = { optionsExpanded = !optionsExpanded }, enabled = browsingAllowed,
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(if (optionsExpanded) "옵션 접기" else "주행 체험 옵션", fontSize = 13.sp)
            }
        }
        if (state.incidentDemoArmed) Text("주행 체험 대기 중 · 출발하면 주변 예시 사고를 안내해요.",
            color = Blue, fontSize = 14.sp, lineHeight = 21.sp)
        Button(onClick = { if (browsingAllowed) onIncidentDemo() }, enabled = browsingAllowed,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
            Text("10km 진입 체험", fontSize = 16.sp)
        }
        if (optionsExpanded) {
            Text("12km 밖 → 8km 안으로 이동하며 알림을 확인해요.", color = Ink, fontSize = 14.sp, lineHeight = 21.sp)
            OutlinedButton(onClick = { if (browsingAllowed) onArmIncident() }, enabled = browsingAllowed,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                Text("현재 위치로 주행 체험", fontSize = 16.sp)
            }
            Text("주행 체험은 현재 위치 주변의 가상 사고로 진행해요. 실제 사고 정보와는 연결되어 있지 않아요.",
                color = Secondary, fontSize = 13.sp, lineHeight = 19.sp)
            TextButton(onClick = { if (browsingAllowed) onReset() }, enabled = browsingAllowed,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("체험 초기화", fontSize = 15.sp)
            }
        }
        state.message?.let { message ->
            Text(message, color = Blue, fontSize = 14.sp, lineHeight = 22.sp)
        }
        state.lastIncident?.let { nearby ->
            Text("최근 체험 알림 · ${nearby.incident.title} · ${kilometers(nearby.distanceMeters)}km",
                color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun AssistBadge(text: String) {
    Text(text, Modifier.background(PaleBlue, RoundedCornerShape(7.dp)).padding(horizontal = 9.dp, vertical = 5.dp),
        color = Blue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
}

private fun clock(hour: Int) = String.format(Locale.KOREAN, "%02d:00", hour)
private fun kilometers(meters: Double) = String.format(Locale.KOREAN, "%.1f", meters / 1_000.0)
