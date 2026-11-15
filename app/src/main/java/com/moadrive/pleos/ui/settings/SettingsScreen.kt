package com.moadrive.pleos.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.moadrive.pleos.BuildConfig
import com.moadrive.pleos.navigation.NavigationProviderId
import com.moadrive.pleos.settings.AppPreferences
import com.moadrive.pleos.settings.AppearanceMode
import com.moadrive.pleos.settings.MapAppearance
import com.moadrive.pleos.ui.*
import com.moadrive.pleos.ui.common.*
import com.moadrive.pleos.vehicle.VehicleTelemetry
import com.moadrive.pleos.startup.StartupLaunch

private enum class SettingsCategory(val title: String, val description: String, val icon: MoaIconKind) {
    DISPLAY("화면 · 테마", "라이트·다크와 화면 표시", MoaIconKind.SETTINGS),
    NAVIGATION("지도 · 길안내", "지도 색상과 안내 방식", MoaIconKind.DRIVE),
    SAFETY("안전 · 루틴", "출발 제안과 주의 알림", MoaIconKind.SAFETY),
    RECORDS("기록 · 패스포트", "주행기록과 방문한 지역", MoaIconKind.TRIPS),
    SERVICES("사운드 · 보험", "차량 사운드와 도움 요청", MoaIconKind.SOUND),
    APP("권한 · 앱 정보", "차량 연결과 앱 권한", MoaIconKind.INSURANCE),
}

@Composable
fun SettingsScreen(
    provider: NavigationProviderId, onProvider: (NavigationProviderId) -> Unit,
    recordingEnabled: Boolean, recordingMessage: String, onRecordingEnabled: (Boolean) -> Unit,
    weatherAutoRefresh: Boolean, onWeatherAutoRefresh: (Boolean) -> Unit,
    soundProfile: String, soundVolume: Float, insurer: String?,
    telemetry: VehicleTelemetry, tripCount: Int, stampCount: Int,
    browsingAllowed: Boolean, permissionRevision: Int,
    onVehiclePermission: () -> Unit, onLocationPermission: () -> Unit, onNotificationPermission: () -> Unit,
    onHome: () -> Unit, onService: (HomeService) -> Unit,
    appPreferences: AppPreferences,
    onAppPreferences: (AppPreferences) -> Unit,
    onPreviewAiGuide: () -> Unit = {},
    setupAllowed: Boolean = browsingAllowed,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumedCount by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var navigationLayersOpen by remember { mutableStateOf(false) }
    LaunchedEffect(browsingAllowed) { if (!browsingAllowed) navigationLayersOpen = false }
    var categoryName by rememberSaveable { mutableStateOf(SettingsCategory.DISPLAY.name) }
    val category = SettingsCategory.entries.firstOrNull { it.name == categoryName } ?: SettingsCategory.DISPLAY
    val compactCategoryScroll = rememberScrollState()
    val updatePreferences: (AppPreferences) -> Unit = { if (browsingAllowed) onAppPreferences(it) }
    val openService: (HomeService) -> Unit = { if (browsingAllowed) onService(it) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumedCount++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val permissions = remember(permissionRevision, resumedCount) {
        fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        listOf(granted("android.car.permission.CAR_SPEED") && granted("android.car.permission.CAR_ENERGY"),
            granted(Manifest.permission.ACCESS_FINE_LOCATION),
            Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS))
    }
    val startupNotificationsAllowed = remember(permissionRevision, resumedCount, appPreferences.autoLaunchOnBoarding) {
        StartupLaunch.notificationsAllowed(context)
    }
    val revealState = rememberInfoRevealState()
    // A category starts at its first setting instead of inheriting another category's scroll position.
    key(category) {
        DetailPage(HomeService.SETTINGS, browsingAllowed, onHome, onService, left = {
            SettingsIntro()
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                SettingsCategory.entries.forEach { item ->
                    CategoryItem(item, item == category, browsingAllowed) { categoryName = item.name }
                }
            }
            Text(if (browsingAllowed) "변경한 설정은 이 차량에 저장돼요." else "설정은 주차 후 변경해 주세요.",
                color = if (browsingAllowed) Secondary else Blue, fontSize = 13.sp, lineHeight = 20.sp)
        }, compactLeft = {
            Text("설정", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Medium)
            Row(Modifier.fillMaxWidth().horizontalScroll(compactCategoryScroll, enabled = browsingAllowed).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsCategory.entries.forEach { item ->
                    FilterChip(selected = item == category, onClick = { if (browsingAllowed) categoryName = item.name },
                        enabled = browsingAllowed, modifier = Modifier.heightIn(min = 48.dp),
                        label = { Text(item.title, fontSize = 15.sp) })
                }
            }
        }, right = {
            InfoReveal(0) {
                DetailHeading(category.title, category.description)
            }
            when (category) {
                SettingsCategory.DISPLAY -> {
                    InfoReveal(1) {
                        DetailCard {
                            SettingTitle("앱 시작")
                            ToggleSetting("인트로 보기", "켜면 메인 화면, 끄면 내비게이션으로 시작해요. 다음 새 실행부터 적용돼요.",
                                appPreferences.showIntro, browsingAllowed) { updatePreferences(appPreferences.copy(showIntro = it)) }
                            HorizontalDivider(color = Line)
                            ToggleSetting("차량 탑승 시 앱 자동실행", "차량 시스템 시작 시 실행 준비 · 이 환경에서는 알림으로 열기",
                                appPreferences.autoLaunchOnBoarding, browsingAllowed) { updatePreferences(appPreferences.copy(autoLaunchOnBoarding = it)) }
                            Text("현재 차량에서는 시스템 시작 후 실행 알림을 표시해요. 알림을 누르면 앱이 열려요. 절전 복귀나 시동마다 자동으로 열리지는 않아요.",
                                color = Secondary, fontSize = 13.sp, lineHeight = 19.sp)
                            if (appPreferences.autoLaunchOnBoarding && !startupNotificationsAllowed) {
                                Text("실행 알림을 받으려면 알림을 허용해 주세요.", color = Blue, fontSize = 13.sp)
                                if (!permissions[2]) ServiceButton("실행 알림 허용", setupAllowed) {
                                    if (setupAllowed) onNotificationPermission()
                                }
                                ServiceButton("알림 설정 열기", setupAllowed) {
                                    if (setupAllowed && !StartupLaunch.openNotificationSettings(context)) feedback = "차량 설정에서 MOA DRIVE의 알림을 허용해 주세요."
                                }
                                feedback?.let { Text(it, color = Secondary, fontSize = 12.sp, lineHeight = 18.sp) }
                            }
                            AppearanceChoices(appPreferences.appearance, browsingAllowed) {
                                updatePreferences(appPreferences.copy(appearance = it))
                            }
                            ToggleSetting("내 차 이미지", "지도에서 아이오닉 5 예시 차량을 표시해요.",
                                appPreferences.showVehicle, browsingAllowed) { updatePreferences(appPreferences.copy(showVehicle = it)) }
                        }
                    }
                    InfoReveal(2) {
                        Text("시스템 설정을 선택하면 차량의 밝기 모드에 맞춰요.", color = Secondary, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                    InfoReveal(3) {
                        DetailCard {
                            ToggleSetting("주행 수치 표시", "내비게이션과 함께 속도·배터리·전비를 표시해요.",
                                appPreferences.showDrivingMetrics, browsingAllowed) { updatePreferences(appPreferences.copy(showDrivingMetrics = it)) }
                            HorizontalDivider(color = Line)
                            ToggleSetting("화면 켜짐 유지", "앱을 사용하는 동안 화면이 꺼지지 않도록 해요.",
                                appPreferences.keepScreenOn, browsingAllowed) { updatePreferences(appPreferences.copy(keepScreenOn = it)) }
                        }
                    }
                }
                SettingsCategory.NAVIGATION -> {
                    InfoReveal(1) {
                        DetailCard {
                            SettingTitle("내비게이션 제공자")
                            NavigationProviderId.entries.forEach { item ->
                                val enabled = browsingAllowed && item.available
                                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                    .selectable(provider == item, enabled = enabled, role = Role.RadioButton) {
                                        if (enabled) onProvider(item)
                                    }, verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = provider == item, onClick = null, enabled = enabled)
                                    Spacer(Modifier.width(8.dp))
                                    Text(item.label, color = if (item.available) Ink else Secondary, fontSize = 17.sp,
                                        modifier = Modifier.weight(1f))
                                    Text(if (!item.available) "연동 준비 중" else if (provider == item) "사용 중" else "사용 가능",
                                        color = if (item.available) Blue else Secondary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    InfoReveal(2) {
                        DetailCard {
                            SettingTitle("내비게이션 레이어")
                            Text("안내 카드와 하단 계기의 크기·배경·포인트 색상을 조절해요.", color = Secondary, fontSize = 14.sp)
                            ServiceButton("내비게이션 레이어 설정", browsingAllowed) { navigationLayersOpen = true }
                        }
                    }
                    InfoReveal(3) {
                        DetailCard {
                            SettingTitle("지도 색상")
                            ChoiceOptions(appPreferences.mapAppearance, MapAppearance.entries.map { it to it.label }, browsingAllowed) {
                                updatePreferences(appPreferences.copy(mapAppearance = it))
                            }
                            HorizontalDivider(color = Line)
                            ToggleSetting("3D 지도", "현재 위치·경로 미리보기·길안내 지도를 기울여 보여줘요.",
                                appPreferences.map3dEnabled, browsingAllowed) { updatePreferences(appPreferences.copy(map3dEnabled = it)) }
                            Text("길안내 중 교차로 근처에서는 평면 지도로 전환될 수 있어요.",
                                color = Secondary, fontSize = 12.sp, lineHeight = 18.sp)
                        }
                    }
                    InfoReveal(4) {
                        DetailCard {
                            SettingTitle("길안내와 안전 알림")
                            ToggleSetting("음성 길안내", "내비게이션의 길안내 음성을 재생해요.",
                                appPreferences.voiceGuidance, browsingAllowed) { updatePreferences(appPreferences.copy(voiceGuidance = it)) }
                            HorizontalDivider(color = Line)
                            ToggleSetting("과속 경고음", "내비게이션의 과속 주의 소리를 켜요.",
                                appPreferences.speedWarnings, browsingAllowed) { updatePreferences(appPreferences.copy(speedWarnings = it)) }
                        }
                    }
                    InfoReveal(5) {
                        DetailCard {
                            SettingTitle("단속 카메라 안내")
                            ToggleSetting("과속 시 점멸", "제한속도를 넘으면 단속 안내 카드가 부드럽게 점멸해요.",
                                appPreferences.speedWarningFlash, browsingAllowed) { updatePreferences(appPreferences.copy(speedWarningFlash = it)) }
                            HorizontalDivider(color = Line)
                            Text("단속 안내 거리", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                            Text("단속 안내 화면과 음성을 허용할 범위를 선택해요.", color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
                            ChoiceOptions(appPreferences.speedWarningDistanceMeters, listOf(500 to "500 m 이내", 1000 to "1 km 이내"), browsingAllowed) {
                                updatePreferences(appPreferences.copy(speedWarningDistanceMeters = it))
                            }
                            Text("음성 안내 시점은 카카오내비 기준이며, 진행 중인 구간단속은 계속 표시해요.",
                                color = Secondary, fontSize = 12.sp, lineHeight = 18.sp)
                            HorizontalDivider(color = Line)
                            ToggleSetting("이동식 카메라 포함", "이동식 단속 카메라의 안내 카드와 음성 알림을 포함해요.",
                                appPreferences.includeMobileCameras, browsingAllowed) { updatePreferences(appPreferences.copy(includeMobileCameras = it)) }
                        }
                    }
                    InfoReveal(6) {
                        DetailCard {
                            ToggleSetting("도로 CCTV 표시", "지도에서 주변 CCTV 목록과 위치를 확인해요.",
                                appPreferences.showCctv, browsingAllowed) { updatePreferences(appPreferences.copy(showCctv = it)) }
                            Text("영상 서비스는 연결 준비 중이에요. 예시 카메라에는 실제 영상이 없어요.",
                                color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
                        }
                    }
                }
                SettingsCategory.SAFETY -> {
                    InfoReveal(1) {
                        DetailCard {
                            SettingTitle("AI 가이드 · 모아")
                            ToggleSetting("AI 가이드", "탑승 인사, 출퇴근·픽업 루틴과 안전 안내로 하루를 챙겨요.",
                                appPreferences.aiGuideEnabled, browsingAllowed) { updatePreferences(appPreferences.copy(aiGuideEnabled = it)) }
                            HorizontalDivider(color = Line)
                            ToggleSetting("모아 안내 음성", "탑승 인사, 루틴 제안과 안전 브리핑을 음성으로 들려줘요.",
                                appPreferences.aiGuideVoice, browsingAllowed && appPreferences.aiGuideEnabled) {
                                updatePreferences(appPreferences.copy(aiGuideVoice = it))
                            }
                            Text("길안내 중에는 모아가 화면으로 안내하고 내비게이션 음성을 우선해요. 한국어 음성을 사용할 수 없는 차량에서는 화면으로 안내해요.",
                                color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
                            ServiceButton("오늘의 브리핑 듣기", browsingAllowed && appPreferences.aiGuideEnabled) { onPreviewAiGuide() }
                        }
                    }
                    InfoReveal(2) {
                        DetailCard {
                            SettingTitle("탑승과 주행 안내")
                            ToggleSetting("탑승 루틴 제안", "예시 이동 패턴에 맞춘 목적지 제안을 표시해요.",
                                appPreferences.routineSuggestions, browsingAllowed) { updatePreferences(appPreferences.copy(routineSuggestions = it)) }
                            HorizontalDivider(color = Line)
                            ToggleSetting("주변 사고 주의 알림", "10km 안의 예시 사고에 대한 주의 안내를 표시해요.",
                                appPreferences.incidentWarnings, browsingAllowed) { updatePreferences(appPreferences.copy(incidentWarnings = it)) }
                            Text("루틴과 사고 알림은 현재 예시 시나리오로 제공돼요.", color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
                        }
                    }
                    InfoReveal(3) {
                        DetailCard {
                            SettingTitle("날씨 안내")
                            ToggleSetting("출발 전 날씨 안내", "주행을 시작할 때 확인한 날씨 주의사항을 알려줘요.",
                                appPreferences.departureWeather, browsingAllowed) { updatePreferences(appPreferences.copy(departureWeather = it)) }
                            HorizontalDivider(color = Line)
                            ToggleSetting("날씨 자동 새로고침", "앱을 사용하는 동안 위치와 예보를 갱신해요.",
                                weatherAutoRefresh, browsingAllowed, onWeatherAutoRefresh)
                        }
                    }
                    InfoReveal(4) {
                        ServiceButton("안전 안내와 예시 체험 보기", browsingAllowed) { openService(HomeService.SAFETY) }
                    }
                }
                SettingsCategory.RECORDS -> {
                    InfoReveal(1) {
                        DetailCard {
                            ToggleSetting("자동 주행기록", recordingMessage, recordingEnabled, browsingAllowed, onRecordingEnabled)
                            HorizontalDivider(color = Line)
                            SettingInfo("저장한 주행", "${tripCount}회")
                            Text("주행 시작과 종료를 감지해 거리·시간·전비·배터리 사용량을 기록해요.",
                                color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
                            ServiceButton("주행기록 보기", browsingAllowed) { openService(HomeService.TRIPS) }
                        }
                    }
                    InfoReveal(2) {
                        DetailCard {
                            SettingTitle("드라이브 패스포트")
                            SettingInfo("방문한 구역", "${stampCount}개")
                            PermissionRow("정확한 위치", permissions[1], setupAllowed, onLocationPermission)
                            Text("위치를 허용하면 주행과 연결된 방문 구역을 남길 수 있어요.",
                                color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
                            ServiceButton("패스포트 보기", browsingAllowed) { openService(HomeService.PASSPORT) }
                        }
                    }
                    InfoReveal(3) {
                        Text("주행기록과 설정은 이 차량에 저장돼요. 모바일 앱 동기화는 아직 연결되지 않았어요.",
                            color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
                    }
                }
                SettingsCategory.SERVICES -> {
                    InfoReveal(1) {
                        DetailCard {
                            SettingTitle("차량 사운드")
                            SettingInfo("선택한 사운드", soundProfile)
                            SettingInfo("사운드 음량", "${(soundVolume * 100).toInt()}%")
                            Text("실내에서 재생하는 합성 사운드예요. 사운드 화면에서 차종과 음량을 조절해요.",
                                color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
                            ServiceButton("사운드 설정", browsingAllowed) { openService(HomeService.SOUND) }
                        }
                    }
                    InfoReveal(2) {
                        DetailCard {
                            SettingTitle("자동차보험")
                            SettingInfo("선택한 보험사", insurer ?: "선택하지 않음")
                            Text("예시 계약을 확인하고 긴급출동·배터리·견인·타이어 도움 요청을 체험해요.",
                                color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
                            ServiceButton("보험사와 도움 요청", browsingAllowed) { openService(HomeService.INSURANCE) }
                        }
                    }
                }
                SettingsCategory.APP -> {
                    InfoReveal(1) {
                        DetailCard {
                            SettingTitle("앱 권한")
                            PermissionRow("차량 속도 · 배터리", permissions[0], setupAllowed, onVehiclePermission)
                            PermissionRow("정확한 위치", permissions[1], setupAllowed, onLocationPermission)
                            PermissionRow("주행기록 알림", permissions[2], setupAllowed, onNotificationPermission)
                            Text("위치는 내비게이션·날씨·방문 구역 기록에 사용해요.", color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
                            ServiceButton("차량의 앱 권한 설정 열기", setupAllowed) {
                                if (setupAllowed && !ExternalActions.appSettings(context)) feedback = "시스템 설정을 열지 못했어요. 차량의 앱 설정에서 확인해 주세요."
                            }
                            feedback?.let { Text(it, color = Blue, fontSize = 14.sp, lineHeight = 21.sp) }
                        }
                    }
                    InfoReveal(2) {
                        DetailCard {
                            SettingTitle("이 차량의 연결 상태")
                            SettingInfo("차량 데이터", telemetry.connectionLabel)
                            SettingInfo("주행기록", "${tripCount}회 저장")
                            SettingInfo("패스포트", "${stampCount}개 구역")
                            SettingInfo("앱 버전", BuildConfig.VERSION_NAME)
                        }
                    }
                }
            }
        }, revealState = revealState)
    }
    if (navigationLayersOpen && browsingAllowed) com.moadrive.pleos.ui.drive.NavigationLayerSettingsDialog(
        appPreferences.navigationLayers, browsingAllowed,
        onUpdate = { updatePreferences(appPreferences.copy(navigationLayers = it)) },
        onDismiss = { navigationLayersOpen = false },
        speedWarningFlash = appPreferences.speedWarningFlash)
}

@Composable
private fun SettingsIntro() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DetailHeading("설정", "나에게 맞는 드라이브 환경")
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            JourneyArtwork(JourneyArt.SETTINGS, Modifier.fillMaxWidth(.77f)
                .height((maxWidth * .53f).coerceIn(140.dp, 206.dp)))
        }
    }
}

@Composable
private fun CategoryItem(category: SettingsCategory, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().selectable(selected = selected, enabled = enabled,
        role = Role.Tab) { if (enabled) onClick() },
        shape = RoundedCornerShape(15.dp), color = if (selected) PaleBlue else Color.Transparent) {
        Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(31.dp).background(if (selected) Blue.copy(alpha = .08f) else Color.Transparent, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center) {
                MoaIcon(category.icon, Modifier.size(22.dp), if (selected) Blue else Secondary, outline = true)
            }
            Text(category.title, color = if (selected) Blue else Ink, fontSize = 17.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun AppearanceChoices(value: AppearanceMode, enabled: Boolean, onChange: (AppearanceMode) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup()) {
        val stacked = maxWidth < 350.dp || LocalDensity.current.fontScale > 1.3f
        if (stacked) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppearanceMode.entries.forEach { mode -> AppearanceChoice(mode, mode == value, enabled,
                Modifier.fillMaxWidth(), compact = true) { onChange(mode) } }
        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppearanceMode.entries.forEach { mode -> AppearanceChoice(mode, mode == value, enabled,
                Modifier.weight(1f), compact = false) { onChange(mode) } }
        }
    }
}

@Composable
private fun AppearanceChoice(mode: AppearanceMode, selected: Boolean, enabled: Boolean,
    modifier: Modifier, compact: Boolean, onClick: () -> Unit) {
    Surface(modifier = modifier.selectable(selected = selected, enabled = enabled,
        role = Role.RadioButton) { if (enabled) onClick() },
        color = CardColor, shape = RoundedCornerShape(16.dp),
        border = if (selected) BorderStroke(1.5.dp, Blue) else null, shadowElevation = CardShadowElevation) {
        if (compact) Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AppearancePreview(mode, selected, Modifier.width(72.dp).height(46.dp))
            Text(mode.label, Modifier.weight(1f), color = if (selected) Blue else Ink, fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            RadioButton(selected = selected, onClick = null, enabled = enabled)
        } else Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp)) {
            AppearancePreview(mode, selected, Modifier.fillMaxWidth().heightIn(max = 106.dp).aspectRatio(1.62f))
            Text(mode.label, color = if (selected) Blue else Ink, fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
        }
    }
}

/** Native previews keep light, dark and system choices readable without baking controls into artwork. */
@Composable
private fun AppearancePreview(mode: AppearanceMode, selected: Boolean, modifier: Modifier) {
    val cobalt = Blue
    val orange = AccentOrange
    val green = AccentGreen
    val dark = mode == AppearanceMode.DARK || (mode == AppearanceMode.SYSTEM && IsDarkTheme)
    val canvasColor = if (dark) Color(0xFF171B25) else Color(0xFFEBEEF4)
    val selectedMark = if (dark) OnBlue else cobalt
    val tileColor = if (dark) Color(0xFF343E51) else Color.White
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(canvasColor, cornerRadius = CornerRadius(10.dp.toPx()))
        drawRoundRect(if (selected && dark) cobalt else tileColor, Offset(w * .08f, h * .1f),
            Size(w * .55f, h * .48f), CornerRadius(5.dp.toPx()))
        drawRoundRect(tileColor, Offset(w * .69f, h * .1f), Size(w * .23f, h * .48f), CornerRadius(5.dp.toPx()))
        drawRoundRect(if (mode == AppearanceMode.SYSTEM) green else orange,
            Offset(w * .08f, h * .68f), Size(w * .29f, h * .2f), CornerRadius(4.dp.toPx()))
        drawRoundRect(tileColor, Offset(w * .44f, h * .68f), Size(w * .48f, h * .2f), CornerRadius(4.dp.toPx()))
        if (selected) {
            drawLine(selectedMark, Offset(w * .27f, h * .34f), Offset(w * .34f, h * .43f), 2.5.dp.toPx(), StrokeCap.Round)
            drawLine(selectedMark, Offset(w * .34f, h * .43f), Offset(w * .46f, h * .25f), 2.5.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
private fun SettingTitle(title: String) {
    Text(title, color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun <T> ChoiceOptions(value: T, options: List<Pair<T, String>>, enabled: Boolean, onChange: (T) -> Unit) {
    BoxWithConstraints {
        val stacked = maxWidth < 430.dp || LocalDensity.current.fontScale > 1.2f
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                options.forEach { (item, label) -> ChoiceChip(label, item == value, enabled, Modifier.fillMaxWidth()) { onChange(item) } }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (item, label) -> ChoiceChip(label, item == value, enabled, Modifier.weight(1f)) { onChange(item) } }
            }
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = { if (enabled) onClick() }, enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp), label = { Text(label, fontSize = 14.sp) },
        shape = RoundedCornerShape(11.dp))
}

@Composable
private fun ServiceButton(title: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = { if (enabled) onClick() }, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp)) {
        Text(title, fontSize = 15.sp)
    }
}

@Composable
private fun SettingInfo(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(label, color = Secondary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.3f))
    }
}

@Composable
private fun ToggleSetting(title: String, description: String, value: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(description, color = Secondary, fontSize = 13.sp, lineHeight = 19.sp)
        }
        Switch(checked = value, onCheckedChange = { if (enabled) onChange(it) }, enabled = enabled)
    }
}

@Composable
private fun PermissionRow(title: String, granted: Boolean, enabled: Boolean, onRequest: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (granted) Text("허용됨", color = AccentGreen, fontSize = 14.sp)
        else OutlinedButton(onClick = { if (enabled) onRequest() }, enabled = enabled,
            modifier = Modifier.heightIn(min = 48.dp)) { Text("권한 요청", fontSize = 14.sp) }
    }
}
