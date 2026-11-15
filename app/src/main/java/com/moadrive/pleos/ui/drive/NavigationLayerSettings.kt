package com.moadrive.pleos.ui.drive

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.moadrive.pleos.ui.common.SoftSurface as Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.moadrive.pleos.navigation.NavigationProgress
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moadrive.pleos.settings.*
import com.moadrive.pleos.ui.*
import com.moadrive.pleos.ui.common.DimmedDialog
import com.moadrive.pleos.ui.common.rememberDialogAction
import kotlin.math.roundToInt

/** Changes apply immediately; leaving the parked state closes the editable modal. */
@Composable
fun NavigationLayerSettingsDialog(
    preferences: NavigationLayerPreferences,
    browsingAllowed: Boolean,
    onUpdate: (NavigationLayerPreferences) -> Unit,
    onDismiss: () -> Unit,
    speedWarningFlash: Boolean = true,
) {
    LaunchedEffect(browsingAllowed) { if (!browsingAllowed) onDismiss() }
    if (!browsingAllowed) return
    val chosen = preferences.normalized()
    val change: (NavigationLayerPreferences) -> Unit = { if (browsingAllowed) onUpdate(it.normalized()) }
    DimmedDialog(onDismissRequest = onDismiss) {
        val close = rememberDialogAction(onDismiss)
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val compact = maxWidth < 600.dp || maxHeight < 480.dp
            val showSubtitle = maxHeight >= 400.dp
            val inset = if (compact) 12.dp else 24.dp
            val dialogHeight = (maxHeight - inset * 2).coerceAtLeast(1.dp).coerceAtMost(780.dp)
            Surface(Modifier.padding(inset).widthIn(max = 760.dp).fillMaxWidth().heightIn(max = dialogHeight),
                shape = RoundedCornerShape(24.dp), color = SurfaceColor) {
                Column(Modifier.padding(if (compact) 16.dp else 24.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("내비게이션 레이어", color = Ink, fontSize = if (compact) 22.sp else 26.sp, fontWeight = FontWeight.Medium)
                            if (showSubtitle) Text("변경 즉시 적용하고 차량에 저장해요", color = Secondary, fontSize = 13.sp)
                        }
                        IconButton(onClick = close, modifier = Modifier.semantics { contentDescription = "레이어 설정 닫기" }) {
                            MoaIcon(MoaIconKind.CLOSE, Modifier.size(22.dp))
                        }
                    }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val split = maxWidth >= 620.dp && LocalDensity.current.fontScale <= 1.3f
                        if (split) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Column(Modifier.weight(.42f).fillMaxHeight().verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                LayerPreview(chosen, speedWarningFlash)
                                Text("선택한 크기와 색상은 지도 위 안내와 하단 계기에 함께 적용돼요.",
                                    color = Secondary, fontSize = 12.sp, lineHeight = 19.sp)
                            }
                            Column(Modifier.weight(.58f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                                LayerControls(chosen, change)
                            }
                        } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            LayerControls(chosen, change)
                            LayerPreview(chosen, speedWarningFlash)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = { change(NavigationLayerPreferences()) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(if (compact) "기본값 복원" else "레이어 기본값 복원", fontSize = 14.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        Button(onClick = close, modifier = Modifier.heightIn(min = 48.dp).widthIn(min = if (compact) 72.dp else 110.dp),
                            shape = RoundedCornerShape(24.dp)) { Text("닫기", fontSize = 16.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LayerControls(chosen: NavigationLayerPreferences, change: (NavigationLayerPreferences) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        LayerChoiceRow("안내 카드·하단 계기 크기", NavigationCardScale.entries, chosen.cardScale, { it.label }) {
            change(chosen.copy(cardScale = it))
        }
        LayerChoiceRow("카드 배경", NavigationCardBackground.entries, chosen.background, { it.label }) {
            change(chosen.copy(background = it))
        }
        LayerChoiceRow("포인트 색상", NavigationCardAccent.entries, chosen.accent, { it.label }) {
            change(chosen.copy(accent = it))
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("카드 불투명도", Modifier.weight(1f), color = Ink, fontSize = 17.sp)
                Text("${(chosen.opacity * 100).roundToInt()}%", color = Blue, fontSize = 17.sp)
            }
            Slider(value = chosen.opacity, onValueChange = { change(chosen.copy(opacity = it)) },
                valueRange = .7f..1f, steps = 5,
                modifier = Modifier.semantics { contentDescription = "안내 카드 불투명도" })
            Text("지도 위의 안내가 잘 보이도록 70–100% 안에서 조절해요.", color = Secondary, fontSize = 12.sp)
        }
        Surface(color = CardColor, shape = RoundedCornerShape(18.dp), shadowElevation = CardShadowElevation) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                LayerToggle("교통 정보", "경로에서 확인된 도로 교통 정보를 표시해요", chosen.showTraffic) { change(chosen.copy(showTraffic = it)) }
                HorizontalDivider(color = Line)
                LayerToggle("과속·안전 안내", "안내 데이터가 있을 때 단속과 제한속도를 표시해요", chosen.showSpeed) { change(chosen.copy(showSpeed = it)) }
                HorizontalDivider(color = Line)
                LayerToggle("휴게소 안내", "경로에서 확인된 다음 휴게소를 표시해요", chosen.showRestArea) { change(chosen.copy(showRestArea = it)) }
            }
        }
        Text("과속 경고와 음성 안내는 설정의 지도·길안내 항목에서 별도로 관리해요.", color = Secondary, fontSize = 12.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun <T> LayerChoiceRow(title: String, values: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = Ink, fontSize = 17.sp)
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            values.forEach { value ->
                val active = value == selected
                Surface(Modifier.weight(1f).heightIn(min = 50.dp)
                    .selectable(active, role = Role.RadioButton, onClick = { onSelect(value) }),
                    color = if (active) PaleBlue else SurfaceColor, shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(if (active) 1.5.dp else .7.dp, if (active) Blue else Line)) {
                    Box(Modifier.padding(horizontal = 10.dp, vertical = 13.dp), contentAlignment = Alignment.Center) {
                        Text(label(value), color = if (active) Blue else Secondary, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun LayerToggle(title: String, detail: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = Ink, fontSize = 16.sp)
            Text(detail, color = Secondary, fontSize = 12.sp, lineHeight = 18.sp)
        }
        Switch(checked, onChecked, modifier = Modifier.semantics { contentDescription = title })
    }
}

@Composable
private fun LayerPreview(preferences: NavigationLayerPreferences, speedWarningFlash: Boolean) {
    val style = navigationLayerStyle(preferences)
    var scenario by rememberSaveable { mutableStateOf("기본") }
    val previewHeight = (200f * LocalDensity.current.fontScale.coerceAtLeast(1f)).coerceAtLeast(220f).dp
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("미리보기 · 예시 안내", color = Secondary, fontSize = 12.sp)
        LayerChoiceRow("안내 상황", listOf("기본", "과속", "구간"), scenario, { it }) { scenario = it }
        if (!preferences.showTraffic && !preferences.showSpeed && !preferences.showRestArea) {
            Surface(color = CardColor, shape = RoundedCornerShape(16.dp), shadowElevation = CardShadowElevation) {
                Text("교통·과속·휴게소 카드가 꺼져 있어요. 기본 방향 안내는 유지돼요.", Modifier.padding(18.dp),
                    color = Secondary, fontSize = 14.sp, lineHeight = 22.sp)
            }
        } else Box(Modifier.fillMaxWidth().heightIn(max = previewHeight)
            .background(PaleBlue, RoundedCornerShape(18.dp)).verticalScroll(rememberScrollState()).padding(12.dp),
            contentAlignment = Alignment.TopCenter) {
            NavigationGuidanceCards(NavigationProgress(), guiding = true,
                scale = preferences.cardScale.factor, accent = style.accent, cardColor = style.card, contentColor = style.ink,
                showTraffic = preferences.showTraffic, showSpeed = preferences.showSpeed,
                showRestArea = preferences.showRestArea, preview = true,
                compact = scenario != "기본", availableHeight = previewHeight - 24.dp,
                currentSpeedKmh = if (scenario == "기본") null else 70,
                speedWarningFlash = speedWarningFlash,
                previewProgress = if (scenario == "구간") navigationSectionGuidancePreview() else null)
        }
        if (preferences.showTraffic || preferences.showSpeed || preferences.showRestArea)
            Text("예시 영역을 스크롤해 각 안내 카드를 확인하세요.", color = Secondary, fontSize = 11.sp)
        if (scenario != "기본") Text(
            if (scenario == "구간") "구간 평균 88 · 제한 80 km/h의 표시 예시예요."
            else "현재 속도 70 · 제한 60 km/h의 표시 예시예요.",
            color = Secondary, fontSize = 11.sp)
    }
}
