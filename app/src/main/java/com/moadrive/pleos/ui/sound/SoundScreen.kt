package com.moadrive.pleos.ui.sound

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.moadrive.pleos.ui.common.SoftSurface as Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moadrive.pleos.sound.SoundKind
import com.moadrive.pleos.sound.SoundMode
import com.moadrive.pleos.sound.SoundProfile
import com.moadrive.pleos.sound.SoundViewModel
import com.moadrive.pleos.sound.canStartSound
import com.moadrive.pleos.sound.canPreviewSound
import com.moadrive.pleos.ui.*
import com.moadrive.pleos.ui.common.*
import com.moadrive.pleos.vehicle.VehicleTelemetry
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun SoundScreen(
    model: SoundViewModel,
    telemetry: VehicleTelemetry,
    browsingAllowed: Boolean,
    onHome: () -> Unit,
    onService: (HomeService) -> Unit,
) {
    val state by model.state.collectAsState()
    val context = LocalContext.current
    var soundSettingsUnavailable by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf(state.profile.kind) }
    var sourceOpen by remember { mutableStateOf(false) }
    val profiles = SoundProfile.entries.filter { it.kind == category }
    val previewAllowed = browsingAllowed && !state.isActive && telemetry.canPreviewSound()
    val playbackSpeed = state.previewSpeedKmh ?: telemetry.speedKmh ?: 0
    val waitingForMotion = state.isPlaying && playbackSpeed == 0
    val audible = state.isPlaying && playbackSpeed > 0 && state.volume > 0f
    LaunchedEffect(browsingAllowed) {
        if (!browsingAllowed) sourceOpen = false
    }
    Box(Modifier.fillMaxSize()) {
        DetailPage(HomeService.SOUND, browsingAllowed, onHome, onService, left = {
            DetailHeading("사운드", "주행에 나만의 리듬을")
            DetailPageArtwork(JourneyArt.SOUND, .72f, 300.dp)
            SoundBars(audible, playbackSpeed)
            HorizontalDivider(color = Line)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("선택한 사운드", color = Secondary, fontSize = 13.sp)
                        Text(state.profile.title, color = Ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (state.profile.credit != null) {
                        TextButton(onClick = { sourceOpen = true }, enabled = browsingAllowed,
                            modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(if (state.profile.kind == SoundKind.GENERATED) "사운드 정보" else "음원 출처", fontSize = 14.sp)
                        }
                    } else {
                        Text("전자 사운드", color = Secondary, fontSize = 13.sp)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(62.dp).background(PaleBlue, CircleShape), contentAlignment = Alignment.Center) {
                        Text(telemetry.gear ?: "—", color = Ink, fontSize = 34.sp, fontWeight = FontWeight.Medium)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(telemetry.speedKmh?.toString() ?: "—", color = Ink, fontSize = 36.sp)
                        Text("km/h", color = Secondary, fontSize = 13.sp)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(if (state.mode == SoundMode.PREVIEW) "5초 미리듣기" else "차량 속도 연동",
                            color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Normal)
                        Text(when {
                            waitingForMotion -> "정차 중 · 무음"
                            state.previewSpeedKmh != null -> "체험 속도 ${state.previewSpeedKmh} km/h"
                            state.isPlaying -> "재생 중"
                            state.pausedForFocus -> "일시 정지"
                            else -> "차량 대기"
                        }, color = if (audible) AccentGreen else Secondary, fontSize = 13.sp)
                    }
                }
                PlaybackStrip(
                    active = state.isActive,
                    playing = state.isPlaying,
                    waitingForMotion = waitingForMotion,
                    enabled = state.isActive || previewAllowed,
                    onClick = { if (state.isActive) model.stop() else model.preview(browsingAllowed) },
                )
                if (state.profile.recordingAsset == null && state.isActive && state.syntheticRpm != null) {
                    val gear = when (state.syntheticGear) { -1 -> "R"; 0, null -> "공회전"; else -> "${state.syntheticGear}단" }
                    val reaction = if (state.profile.kind == SoundKind.RECORDING) "가상 엔진 · $gear" else "가상 구동 · $gear"
                    Text("$reaction · ${state.syntheticRpm} RPM${if (state.isShifting) " · 변속" else ""}",
                        color = if (state.isShifting) AccentOrange else Blue, fontSize = 14.sp, lineHeight = 20.sp)
                }
                Text(state.message, color = Secondary, fontSize = 13.sp, lineHeight = 19.sp)
            }
            if (!browsingAllowed) Text("차량 연결 상태를 확인해 주세요.", color = Secondary, fontSize = 13.sp)
        }, right = {
            InfoReveal(0) {
                DetailHeading("사운드 프로필", "나만의 주행 분위기를 골라 보세요")
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoReveal(1) {
                    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SoundKind.entries.forEach { kind ->
                            val selected = category == kind
                            OutlinedButton(onClick = { category = kind }, enabled = browsingAllowed,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                    .semantics { this.selected = selected },
                                shape = RoundedCornerShape(24.dp),
                                border = BorderStroke(if (selected) 1.5.dp else .5.dp, if (selected) Blue else Line),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selected) PaleBlue else CardColor,
                                    contentColor = if (selected) Blue else Secondary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
                                Text(when (kind) {
                                    SoundKind.GENERATED -> "모아 컬렉션"
                                    SoundKind.ORIGINAL -> "기본 전자음"
                                    SoundKind.RECORDING -> "실차 녹음"
                                }, fontSize = 15.sp)
                            }
                        }
                    }
                }
                InfoReveal(1) {
                    Text(when (category) {
                        SoundKind.GENERATED -> "드라이브에 어울리는 다섯 가지 사운드예요. 출발하면 서서히 커져요."
                        SoundKind.ORIGINAL -> "모아의 기본 전자 사운드가 속도에 따라 달라져요."
                        SoundKind.RECORDING -> "실제 차량 녹음을 1배속으로 재생해요. 속도에 따라 음량만 부드럽게 달라져요."
                    },
                        color = Secondary, fontSize = 13.sp, lineHeight = 19.sp)
                }
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    profiles.forEachIndexed { index, profile ->
                        val selected = state.profile == profile
                        val accent = when (index % 3) { 1 -> AccentOrange; 2 -> AccentGreen; else -> Blue }
                        InfoReveal(index + 2) {
                            Surface(
                                modifier = Modifier.fillMaxWidth().selectable(
                                    selected = selected, enabled = browsingAllowed, role = Role.RadioButton,
                                    onClick = { if (browsingAllowed) model.selectProfile(profile) },
                                ),
                                shape = RoundedCornerShape(20.dp),
                                border = if (selected) BorderStroke(1.5.dp, Blue) else null,
                                shadowElevation = CardShadowElevation,
                                color = if (selected) PaleBlue else CardColor,
                            ) {
                                Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 18.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(profile.title, modifier = Modifier.weight(1f, fill = false),
                                                color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                                            if (profile.kind != SoundKind.GENERATED) {
                                                Text(if (profile.kind == SoundKind.ORIGINAL) "전자 사운드" else "실차 녹음",
                                                    color = Secondary, fontSize = 13.sp)
                                            }
                                        }
                                        Text(profile.character, color = Secondary, fontSize = 14.sp, lineHeight = 19.sp)
                                    }
                                    ProfileWave(accent, Modifier.width(34.dp).height(24.dp))
                                    RadioButton(selected = selected, onClick = null, enabled = browsingAllowed)
                                }
                            }
                        }
                    }
                }
            }
            InfoReveal(profiles.size + 2) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("앱 사운드 음량", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        Text("${(state.volume * 100).roundToInt()}%", color = Ink, fontSize = 24.sp)
                    }
                    Slider(value = state.volume, onValueChange = { if (browsingAllowed) model.setVolume(it) },
                        enabled = browsingAllowed, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "앱 사운드 음량" })
                    BoxWithConstraints {
                        val openSoundSettings: @Composable () -> Unit = {
                            TextButton(onClick = {
                                if (browsingAllowed) soundSettingsUnavailable = !ExternalActions.soundSettings(context)
                            }, enabled = browsingAllowed, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("차량 소리 설정", fontSize = 15.sp)
                            }
                        }
                        if (maxWidth < 400.dp || LocalDensity.current.fontScale > 1.2f) {
                            Column {
                                Text("설정 음량을 최대치로, 정차 시 무음이에요.", color = Secondary, fontSize = 13.sp, lineHeight = 19.sp)
                                openSoundSettings()
                            }
                        } else {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("설정 음량을 최대치로, 정차 시 무음이에요.", Modifier.weight(1f),
                                    color = Secondary, fontSize = 13.sp, lineHeight = 19.sp)
                                openSoundSettings()
                            }
                        }
                    }
                    if (soundSettingsUnavailable) {
                        Text("소리 설정을 열 수 없어요. 차량 설정에서 미디어 음량을 확인해 주세요.",
                            color = Secondary, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                    BoxWithConstraints {
                        val stacked = maxWidth < 350.dp || LocalDensity.current.fontScale > 1.2f
                        val preview: @Composable (Modifier) -> Unit = { modifier ->
                            OutlinedButton(onClick = { model.preview(browsingAllowed) },
                                enabled = previewAllowed,
                                modifier = modifier.heightIn(min = 50.dp), shape = RoundedCornerShape(25.dp),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = CardColor)) {
                                Text("5초 미리듣기", fontSize = 15.sp)
                            }
                        }
                        val playback: @Composable (Modifier) -> Unit = { modifier ->
                            Button(onClick = { if (state.isActive) model.stop() else model.start(browsingAllowed) },
                                enabled = state.isActive || (browsingAllowed && telemetry.canStartSound()),
                                modifier = modifier.heightIn(min = 50.dp), shape = RoundedCornerShape(25.dp)) {
                                Text(if (state.isActive) "사운드 끄기" else "속도 연동 켜기", fontSize = 15.sp)
                            }
                        }
                        if (stacked) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            preview(Modifier.fillMaxWidth())
                            playback(Modifier.fillMaxWidth())
                        } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            preview(Modifier.weight(1f))
                            playback(Modifier.weight(1f))
                        }
                    }
                }
            }
            InfoReveal(profiles.size + 3) {
                Text("실내 스피커로 재생하는 앱 사운드예요. 실차 녹음은 각 프로필에 표시된 차량의 소리이며, 현대자동차 공식 음원이 아니에요. 다른 음성 안내 중에는 자동으로 줄이거나 잠시 멈춰요.",
                    color = Secondary, fontSize = 12.sp, lineHeight = 19.sp)
            }
        })
        // Stop stays reachable even if vehicle policy information becomes unavailable.
        if (state.isActive && !browsingAllowed) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 24.dp, end = 112.dp, bottom = 24.dp),
                contentAlignment = Alignment.Center) {
                Button(onClick = model::stop,
                    modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().heightIn(min = 56.dp),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = FloatingShadowElevation,
                        pressedElevation = FloatingShadowElevation,
                        focusedElevation = FloatingShadowElevation,
                        hoveredElevation = FloatingShadowElevation,
                        disabledElevation = 0.dp,
                    ),
                    shape = RoundedCornerShape(28.dp)) {
                    Text("사운드 끄기", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    if (sourceOpen && browsingAllowed) SoundSourceDialog(state.profile, onDismiss = { sourceOpen = false })
}

@Composable
private fun SoundSourceDialog(profile: SoundProfile, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var unavailableUrl by remember { mutableStateOf<String?>(null) }
    DimmedDialog(onDismissRequest = onDismiss) {
        val close = rememberDialogAction(onDismiss)
        Surface(
            modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(.92f).heightIn(max = 680.dp),
            shape = RoundedCornerShape(24.dp), color = SurfaceColor, shadowElevation = CardShadowElevation,
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (profile.kind == SoundKind.GENERATED) "사운드 정보" else "음원 출처",
                    color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(if (profile.kind == SoundKind.GENERATED)
                        "모아 드라이브를 위해 준비한 창작 사운드예요."
                    else "실제 차량의 녹음을 바탕으로 만들었어요. 제작자와 원본, 이용 조건을 확인할 수 있어요.",
                        color = Secondary, fontSize = 14.sp, lineHeight = 22.sp)
                    run {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(profile.title, color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Medium)
                            Text(profile.character, color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
                            val credit = profile.credit
                            val sourceUrl = profile.sourceUrl
                            if (sourceUrl != null) {
                                TextButton(onClick = {
                                    unavailableUrl = if (ExternalActions.web(context, sourceUrl)) null else sourceUrl
                                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)) {
                                    Text(credit ?: "원본 음원과 제작자 보기", modifier = Modifier.fillMaxWidth(),
                                        fontSize = 14.sp, lineHeight = 22.sp)
                                }
                            } else if (credit != null) {
                                Text(credit, color = Secondary, fontSize = 14.sp, lineHeight = 22.sp)
                            }
                            profile.licenseUrl?.let { url ->
                                TextButton(onClick = {
                                    unavailableUrl = if (ExternalActions.web(context, url)) null else url
                                }, modifier = Modifier.heightIn(min = 48.dp),
                                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)) {
                                    Text("라이선스 보기", fontSize = 14.sp)
                                }
                            }
                            HorizontalDivider(color = Line)
                        }
                    }
                    Text(if (profile.kind == SoundKind.GENERATED)
                        "원래의 1배속으로 재생해요. 정차하면 무음, 출발하면 속도에 따라 음량이 부드럽게 커져요."
                    else "편집 내용: 원본의 일부 구간에 모노 변환·음량 보정·반복 연결을 적용했어요. 재생속도는 원래의 1배속을 유지해요. 정차하면 무음, 출발하면 속도에 따라 음량이 부드럽게 커져요.",
                        color = Secondary, fontSize = 13.sp, lineHeight = 21.sp)
                    Text(if (profile.kind == SoundKind.GENERATED) "창작 주행 사운드이며, 특정 차량의 실차 녹음이 아니에요."
                    else "현대자동차 공식 음원이 아니에요.",
                        color = Secondary, fontSize = 13.sp, lineHeight = 21.sp)
                    unavailableUrl?.let { url ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("링크를 열 수 없어요. 주소를 복사해 다른 기기에서 확인해 주세요.",
                                color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
                            Text(url, color = Blue, fontSize = 12.sp, lineHeight = 18.sp)
                            TextButton(onClick = { ExternalActions.copy(context, "음원 출처", url) }) {
                                Text("주소 복사", fontSize = 14.sp)
                            }
                        }
                    }
                }
                Button(onClick = close, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)) {
                    Text("닫기", fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun ProfileWave(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val heights = floatArrayOf(.3f, .6f, 1f, .7f, .4f)
        heights.forEachIndexed { index, height ->
            val x = size.width * (index + .5f) / heights.size
            drawLine(color, Offset(x, size.height * (1 - height) / 2),
                Offset(x, size.height * (1 + height) / 2), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun PlaybackStrip(active: Boolean, playing: Boolean, waitingForMotion: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = if (active) "사운드 끄기" else "5초 미리듣기"
        },
        shape = RoundedCornerShape(20.dp), color = BackgroundColor, shadowElevation = CardShadowElevation,
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = CircleShape, color = CardColor, shadowElevation = CardShadowElevation) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    val iconColor = if (enabled) Blue else Secondary
                    Canvas(Modifier.size(16.dp)) {
                        val w = size.width
                        val h = size.height
                        if (active) {
                            drawRoundRect(iconColor, cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                        } else {
                            drawPath(Path().apply {
                                moveTo(w * .24f, 0f)
                                lineTo(w, h / 2)
                                lineTo(w * .24f, h)
                                close()
                            }, iconColor)
                        }
                    }
                }
            }
            Text(when {
                waitingForMotion -> "출발 대기"
                playing -> "재생 중"
                active -> "일시 정지"
                else -> "재생 대기"
            }, color = if (enabled) Ink else Secondary, fontSize = 16.sp, modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

/** Static speed-responsive bars avoid distracting animations while the vehicle is moving. */
@Composable
private fun SoundBars(playing: Boolean, speed: Int) {
    val blue = Blue
    val orange = AccentOrange
    val yellow = AccentYellow
    val green = AccentGreen
    val quiet = Line
    val heights = remember {
        listOf(.13f, .16f, .22f, .32f, .27f, .21f, .24f, .31f, .45f, .58f, .82f, .57f,
            .43f, .29f, .21f, .18f, .24f, .34f, .46f, .61f, .82f, 1f, .65f, .45f, .32f,
            .24f, .19f, .16f, .24f, .33f, .24f, .18f, .13f)
    }
    Canvas(Modifier.fillMaxWidth().height(44.dp)) {
        val count = heights.size
        val gap = size.width / count
        val response = if (playing) speed.coerceIn(0, 120) / 120f else 0f
        val stroke = (gap * .46f).coerceAtMost(5.dp.toPx())
        heights.forEachIndexed { index, envelope ->
            val height = ((envelope + abs(sin(index * .65f)) * response * .24f) * (size.height - stroke))
                .coerceAtMost(size.height - stroke)
            val x = gap * (index + .5f)
            val color = when (index) {
                in 0..14 -> blue
                in 15..19 -> orange
                in 20..21 -> yellow
                in 22..26 -> green
                else -> quiet
            }
            drawLine(color, Offset(x, (size.height - height) / 2f),
                Offset(x, (size.height + height) / 2f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}
