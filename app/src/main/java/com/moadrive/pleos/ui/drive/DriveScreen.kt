package com.moadrive.pleos.ui.drive

import com.moadrive.pleos.ui.common.infoReveal
import com.moadrive.pleos.ui.common.rememberInfoRevealState

import com.moadrive.pleos.ui.FloatingShadowElevation

import android.Manifest
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.moadrive.pleos.ui.common.SoftSurface as Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.moadrive.pleos.navigation.*
import com.moadrive.pleos.navigation.kakao.KakaoNavigationSurface
import com.moadrive.pleos.ui.HomeService
import com.moadrive.pleos.ui.HomeMenuIllustration
import com.moadrive.pleos.ui.MoaIcon
import com.moadrive.pleos.ui.MoaIconKind
import com.moadrive.pleos.vehicle.VehicleTelemetry
import com.moadrive.pleos.vehicle.freshSpeedKmh
import kotlinx.coroutines.delay
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moadrive.pleos.cctv.CctvViewModel
import com.moadrive.pleos.ui.cctv.*
import com.moadrive.pleos.settings.AppPreferences
import com.moadrive.pleos.settings.MapAppearance
import com.moadrive.pleos.ui.BackgroundColor
import com.moadrive.pleos.ui.SurfaceColor
import com.moadrive.pleos.ui.IsDarkTheme
import com.moadrive.pleos.ui.Ink
import com.moadrive.pleos.ui.Blue
import com.moadrive.pleos.ui.common.DimmedAlertDialog
import com.moadrive.pleos.ui.common.DimmedDialog
import com.moadrive.pleos.ui.common.LocalDialogMotion
import com.moadrive.pleos.ui.common.rememberDialogAction
import com.moadrive.pleos.ui.common.DimmedSideSheet
import com.moadrive.pleos.ui.Secondary
import com.moadrive.pleos.ui.Line
import com.moadrive.pleos.ui.PaleBlue
import com.moadrive.pleos.assist.AiGuideMessage
import com.moadrive.pleos.ui.assist.AiGuideCard

private val DriveBlue: Color @Composable get() = Blue
private val DriveInk: Color @Composable get() = Ink
private enum class DriveModal { SEARCH, LAYERS, PROVIDER }

@Composable
fun DriveScreen(model: DriveViewModel, telemetry: VehicleTelemetry, onHome: () -> Unit, onService: (HomeService) -> Unit,
    preferences: AppPreferences = AppPreferences(), onPreferences: (AppPreferences) -> Unit = {},
    aiGuideMessage: AiGuideMessage? = null, onDismissAiGuide: () -> Unit = {}, voiceUnavailable: Boolean = false,
    openCctvOnEntry: Boolean = false, onCctvEntryConsumed: () -> Unit = {},
    searchQueryOnEntry: String? = null, onSearchEntryConsumed: () -> Unit = {},
    floatingDialogsAllowed: Boolean = true,
    startupReady: Boolean = true) {
    val revealState = rememberInfoRevealState()
    val cctvModel: CctvViewModel = viewModel()
    val mapZoomController = remember { NavigationMapZoomController() }
    val cctvState by cctvModel.state.collectAsState()
    val state by model.state.collectAsState()
    val policy by model.policy.collectAsState()
    val progress by model.progress.collectAsState()
    val places by model.placesState.collectAsState()
    val layerStyle = navigationLayerStyle(preferences.navigationLayers)
    val layerScale = preferences.navigationLayers.cardScale.factor
    val location by model.location.location.collectAsState()
    var finePermission by remember { mutableStateOf(model.location.hasPrecisePermission()) }
    var modal by rememberSaveable { mutableStateOf<DriveModal?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(searchQueryOnEntry, visible, state.ready, policy.searchAllowed, floatingDialogsAllowed) {
        val query = searchQueryOnEntry?.trim()?.takeIf { it.isNotEmpty() }
        if (query != null && visible && state.ready && policy.searchAllowed && floatingDialogsAllowed) {
            // Care categories open the existing result chooser; choosing a result remains explicit.
            cctvModel.closePanel()
            model.search(query)
            modal = DriveModal.SEARCH
            onSearchEntryConsumed()
        }
    }
    val measuredWarningSpeed by produceState<Int?>(null, telemetry.active, telemetry.speedKmh,
        telemetry.speedObservedElapsedMs, visible) {
        val now = SystemClock.elapsedRealtime()
        value = if (visible) telemetry.freshSpeedKmh(now) else null
        if (value != null) {
            // Expire the warning even if no later vehicle callback triggers a recomposition.
            delay((3_001L - (now - (telemetry.speedObservedElapsedMs ?: now))).coerceAtLeast(1L))
            value = null
        }
    }
    var freshPoint by remember { mutableStateOf<GeoPoint?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val currentVideoAllowed by rememberUpdatedState(policy.videoAllowed)
    val mapNight = when (preferences.mapAppearance) {
        MapAppearance.DAY -> false
        MapAppearance.NIGHT -> true
        MapAppearance.FOLLOW_APP -> IsDarkTheme
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        finePermission = model.location.hasPrecisePermission()
        if (finePermission) model.initialize()
    }
    DisposableEffect(model, lifecycle) {
        fun update() {
            visible = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            finePermission = model.location.hasPrecisePermission()
            model.setVisible(visible)
        }
        val observer = LifecycleEventObserver { _, _ -> update() }
        lifecycle.addObserver(observer)
        update()
        onDispose { lifecycle.removeObserver(observer); model.setVisible(false) }
    }
    DisposableEffect(cctvModel, lifecycle, preferences.showCctv, policy.videoAllowed) {
        fun update() { cctvModel.setVisible(preferences.showCctv && policy.videoAllowed && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
        val observer = LifecycleEventObserver { _, _ -> update() }
        lifecycle.addObserver(observer); update()
        onDispose { lifecycle.removeObserver(observer); cctvModel.setVisible(false) }
    }
    LaunchedEffect(location, visible, preferences.showCctv) {
        while (visible) {
            freshPoint = model.location.currentPoint()
            cctvModel.updateLocation(freshPoint, currentVideoAllowed)
            delay(1_000)
        }
        freshPoint = null
    }
    LaunchedEffect(policy.videoAllowed) { cctvModel.updateLocation(freshPoint, policy.videoAllowed) }
    LaunchedEffect(openCctvOnEntry, visible) {
        if (openCctvOnEntry && visible) {
            if (preferences.showCctv && model.policy.value.videoAllowed) {
                cctvModel.updateLocation(freshPoint, browsingAllowed = true)
                cctvModel.openPanel()
                if (modal == DriveModal.SEARCH) model.cancelSearch()
                modal = null
            }
            onCctvEntryConsumed()
        }
    }
    LaunchedEffect(policy.searchAllowed, policy.settingsAllowed) {
        if (!policy.searchAllowed) {
            if (modal == DriveModal.SEARCH) modal = null
            model.cancelSearch()
            keyboard?.hide()
        }
        if (!policy.settingsAllowed && modal in listOf(DriveModal.LAYERS, DriveModal.PROVIDER)) modal = null
    }
    val guiding = state.phase == DrivePhase.GUIDING || state.phase == DrivePhase.STARTING
    val comparingRoutes = !guiding && state.routeDestination != null
    val routeOptionsAllowed = state.ready && visible && policy.searchAllowed && !guiding && !state.suggestedDestinationPending
    val matchingRoute = state.route?.takeIf {
        it.preference == state.routePreference && it.destination == state.routeDestination
    }
    val comparisonActionsAllowed = routeOptionsAllowed && state.phase in listOf(DrivePhase.IDLE, DrivePhase.ROUTING)
    val floatingDialogAvailable = floatingDialogsAllowed && visible && modal == null &&
        !cctvState.panelOpen && cctvState.selected == null
    val openCctv: (String?) -> Unit = { id ->
        if (model.policy.value.videoAllowed && preferences.showCctv && modal == null) cctvModel.openPanel(id)
    }
    BackHandler {
        when {
            cctvState.panelOpen -> cctvModel.closePanel()
            modal != null -> {
                if (modal == DriveModal.SEARCH) model.cancelSearch()
                modal = null
            }
            else -> onHome()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(BackgroundColor).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val compactWindow = maxWidth < 780.dp || maxHeight < 560.dp
        val shortWindow = maxHeight < 560.dp
        val railWidth = serviceRailWidth(maxWidth)
        val cctvPanelWidth = (maxWidth * .24f).coerceIn(168.dp, 260.dp)
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().background(BackgroundColor)) {
                    var headerHeight by remember { mutableStateOf(56.dp) }
                    var comparisonPanelHeight by remember { mutableStateOf(250.dp) }
                    var cctvActionHeight by remember { mutableStateOf(48.dp) }
                    val overlayInset = if (compactWindow) 12.dp else 20.dp
                    val showCctvAction = preferences.showCctv && !cctvState.panelOpen && !comparingRoutes
                    val showCctvLabel = maxWidth >= 340.dp
                    val guidanceWidth = minOf(304.dp * layerScale, maxWidth * if (compactWindow) .65f else .37f)
                    val guidanceTop = overlayInset
                    val mapNoticeTop = overlayInset + headerHeight + 10.dp
                    val guidanceBottomReserve = if (showCctvAction) cctvActionHeight + 10.dp else 0.dp
                    val guidanceHeight = (maxHeight - guidanceTop - overlayInset - guidanceBottomReserve).coerceAtLeast(0.dp)
                    val minimumGuidanceHeight = (if (policy.settingsAllowed) 370.dp else 450.dp) * layerScale
                    val compactGuidance = guidanceWidth < 200.dp || guidanceHeight < minimumGuidanceHeight || density.fontScale > 1.2f
                    if (startupReady && state.ready && finePermission && visible) {
                        KakaoNavigationSurface(model.kakao, matchingRoute, guiding, freshPoint,
                            Modifier.fillMaxSize(), model::surfaceError,
                            cameras = if (preferences.showCctv && policy.videoAllowed) cctvState.cameras else emptyList(),
                            onCameraClick = openCctv,
                            heading = location?.takeIf { freshPoint != null && it.hasBearing() }?.bearing,
                            night = mapNight, showVehicle = preferences.showVehicle,
                            voiceGuidance = preferences.voiceGuidance, speedWarnings = preferences.speedWarnings,
                            showSpeedLayer = preferences.navigationLayers.showSpeed,
                            safetyOptions = NavigationSafetyOptions(
                                flashOnOverspeed = preferences.speedWarningFlash,
                                warningDistanceMeters = preferences.speedWarningDistanceMeters,
                                includeMobileCameras = preferences.includeMobileCameras),
                            map3dEnabled = preferences.map3dEnabled,
                            previewBottomInsetDp = if (comparingRoutes) comparisonPanelHeight.value else 0f,
                            zoomController = mapZoomController)
                    }
                    val mapAvailable = state.ready && finePermission
                    val searchAvailable = mapAvailable && !guiding && !comparingRoutes
                    val availableHeaderWidth = (maxWidth - guidanceWidth - overlayInset * 3).coerceAtLeast(0.dp)
                    val compactActions = maxWidth < 560.dp || density.fontScale > 1.2f ||
                        (guiding && availableHeaderWidth < 280.dp)
                    val headerActionCount = 1 + (if (searchAvailable) 1 else 0)
                    val minimumHeaderWidth = (headerActionCount * 48 + (headerActionCount - 1) * 8).dp
                    if (!mapAvailable) {
                        Surface(Modifier.align(Alignment.Center)
                            .padding(start = overlayInset, end = overlayInset, top = mapNoticeTop, bottom = overlayInset)
                            .widthIn(max = 420.dp)
                            .heightIn(max = (maxHeight - mapNoticeTop - overlayInset).coerceAtLeast(0.dp)),
                            shape = RoundedCornerShape(22.dp), color = SurfaceColor, border = BorderStroke(1.dp, Line)) {
                            Column(Modifier.verticalScroll(rememberScrollState()).padding(if (compactWindow) 16.dp else 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.size(48.dp).background(PaleBlue, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                                    MoaIcon(MoaIconKind.DRIVE, Modifier.size(26.dp), DriveBlue, outline = true)
                                }
                                Text(if (!finePermission) "위치 접근이 필요해요" else "내비게이션 연결",
                                    fontSize = if (compactWindow) 20.sp else 24.sp, fontWeight = FontWeight.SemiBold, color = DriveInk)
                                Text(if (!finePermission) "차량의 현재 위치로 길을 안내하려면 정확한 위치 접근을 허용해 주세요."
                                    else if (!state.internetAvailable) "인터넷 연결을 기다리고 있어요. 연결되면 지도를 자동으로 다시 준비해요."
                                    else if (state.initializing) "${state.provider.label} 지도를 준비하고 있어요."
                                    else state.message ?: "${state.provider.label}를 연결해 주행을 시작하세요.",
                                    color = Secondary, textAlign = TextAlign.Center)
                                if (state.initializing) CircularProgressIndicator(Modifier.size(28.dp), color = DriveBlue)
                                else Button(enabled = if (!finePermission) policy.setupAllowed else state.internetAvailable, onClick = {
                                    if (!finePermission) {
                                        if (model.policy.value.setupAllowed && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                        }
                                    } else model.initialize()
                                }, modifier = Modifier.heightIn(min = 48.dp)) {
                                    Text(if (!finePermission && !policy.setupAllowed) "주차 후 위치 권한 허용" else if (!finePermission) "위치 권한 허용" else "다시 연결")
                                }
                            }
                        }
                    }
                    val headerActions: @Composable () -> Unit = {
                        if (searchAvailable) MapAction(
                            label = if (policy.searchAllowed) "목적지 검색" else "주차 후 검색 가능",
                            enabled = policy.searchAllowed && state.phase != DrivePhase.ROUTING,
                            showLabel = !compactActions, emphasized = true,
                            icon = { MoaIcon(MoaIconKind.SEARCH, Modifier.size(21.dp), it, outline = true) },
                            onClick = {
                                model.updateSearchQuery("")
                                modal = DriveModal.SEARCH
                            },
                        )
                        MapAction("레이어 설정", policy.settingsAllowed, !compactActions,
                            icon = { MoaIcon(MoaIconKind.SETTINGS, Modifier.size(20.dp), it, outline = true) },
                            onClick = { modal = DriveModal.LAYERS },
                        )
                    }
                    val headerModifier = Modifier.align(Alignment.TopEnd).padding(overlayInset)
                        .onSizeChanged { headerHeight = with(density) { it.height.toDp() } }
                        .infoReveal(revealState, 0)
                    if (guiding && availableHeaderWidth < minimumHeaderWidth) {
                        Column(headerModifier, verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.End) { headerActions() }
                    } else {
                        Row(headerModifier, horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) { headerActions() }
                    }
                    if (mapAvailable && guiding) key(state.route?.id, policy.settingsAllowed) {
                        NavigationGuidanceCards(progress, guiding, Modifier.align(Alignment.TopStart)
                            .padding(start = overlayInset, top = guidanceTop)
                            .width(guidanceWidth).heightIn(max = guidanceHeight)
                            .verticalScroll(rememberScrollState(), enabled = policy.settingsAllowed),
                            scale = layerScale, accent = layerStyle.accent, cardColor = layerStyle.card,
                            contentColor = layerStyle.ink, showTraffic = preferences.navigationLayers.showTraffic,
                            showSpeed = preferences.navigationLayers.showSpeed, showRestArea = preferences.navigationLayers.showRestArea,
                            currentSpeedKmh = measuredWarningSpeed, speedWarningFlash = preferences.speedWarningFlash,
                            compact = compactGuidance, availableHeight = guidanceHeight)
                    }
                    if (showCctvAction || (searchAvailable && freshPoint == null)) {
                        Column(Modifier.align(Alignment.BottomStart).padding(overlayInset)
                            .widthIn(max = (maxWidth - overlayInset * 2).coerceAtLeast(0.dp))
                            .onSizeChanged { cctvActionHeight = with(density) { it.height.toDp() } },
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (searchAvailable && freshPoint == null) Surface(shape = RoundedCornerShape(10.dp), color = SurfaceColor) {
                                Text("현재 위치 확인 중", Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    color = Secondary, fontSize = 12.sp)
                            }
                            if (showCctvAction) MapAction(
                                "CCTV", policy.videoAllowed, showLabel = showCctvLabel,
                                icon = { CctvGlyph(Modifier.size(20.dp), it) }, onClick = { openCctv(null) },
                            )
                        }
                    }
                    if (guiding) MapAction(if (state.phase == DrivePhase.STARTING) "시작 취소" else "안내 종료",
                        enabled = true, modifier = Modifier.align(Alignment.BottomEnd).padding(overlayInset),
                        onClick = { model.stopGuidance("경로 안내를 종료했어요.") })
                    if (state.phase == DrivePhase.ROUTING && !comparingRoutes) CircularProgressIndicator(Modifier.align(Alignment.Center), color = DriveBlue)
                    if (floatingDialogAvailable && state.suggestedDestinationPending) {
                        DimmedDialog(onDismissRequest = model::cancelSuggestedDestination) {
                            val cancel = rememberDialogAction(model::cancelSuggestedDestination)
                            BoxWithConstraints(Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                                Surface(Modifier.widthIn(max = 480.dp).fillMaxWidth().heightIn(max = maxHeight),
                                    color = SurfaceColor, shape = RoundedCornerShape(16.dp), shadowElevation = FloatingShadowElevation) {
                                    Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(state.message ?: "제안한 목적지의 경로를 준비하고 있어요.", color = DriveInk, fontSize = 16.sp)
                                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = DriveBlue, strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                                        if (!finePermission && policy.setupAllowed) {
                                            TextButton(onClick = {
                                                if (model.policy.value.setupAllowed && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                                    permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                                }
                                            }) { Text("위치 권한 허용") }
                                        } else if (!state.ready && !state.initializing && state.internetAvailable) {
                                            TextButton(onClick = model::initialize) { Text("다시 연결") }
                                        }
                                        TextButton(onClick = cancel) { Text("경로 준비 취소") }
                                    }
                                }
                            }
                        }
                    } else if (floatingDialogAvailable && mapAvailable && state.message != null && !comparingRoutes) {
                        DimmedDialog(onDismissRequest = model::dismissMessage) {
                            val close = rememberDialogAction(model::dismissMessage)
                            BoxWithConstraints(Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                                Surface(Modifier.widthIn(max = 480.dp).fillMaxWidth().heightIn(max = maxHeight), color = SurfaceColor,
                                    shape = RoundedCornerShape(16.dp), shadowElevation = FloatingShadowElevation) {
                                    Row(Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text(state.message!!, Modifier.weight(1f), color = DriveInk, fontSize = 14.sp)
                                        IconButton(onClick = close,
                                            modifier = Modifier.semantics { contentDescription = "내비게이션 안내 닫기" }) {
                                            MoaIcon(MoaIconKind.CLOSE, Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (preferences.showVehicle && state.ready && !guiding && !comparingRoutes && !compactWindow) {
                        Surface(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                            color = SurfaceColor.copy(alpha = .92f), shape = RoundedCornerShape(8.dp)) {
                            Text("IONIQ 5 · 예시 차량", Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                color = Secondary, fontSize = 11.sp)
                        }
                    }
                    if (mapAvailable && !comparingRoutes) {
                        MapZoomControls(mapZoomController::zoomIn, mapZoomController::zoomOut,
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = overlayInset).infoReveal(revealState, 1),
                            enabled = mapZoomController.available && visible)
                    }
                    if (comparingRoutes && floatingDialogAvailable && !state.suggestedDestinationPending) {
                        val dismissComparison = { if (!guiding) model.clearRoute() }
                        val comparisonMapHeight = maxHeight
                        DimmedDialog(onDismissRequest = dismissComparison) {
                            val close = rememberDialogAction(dismissComparison)
                            val motion = LocalDialogMotion.current
                            BoxWithConstraints(Modifier.fillMaxSize()) {
                                val inset = if (maxWidth < 600.dp || maxHeight < 480.dp) 12.dp else 20.dp
                                val bottomInset = (maxHeight - comparisonMapHeight).coerceAtLeast(0.dp)
                                val panelHeight = minOf(620.dp, comparisonMapHeight * if (comparisonMapHeight < 560.dp) .80f else .65f,
                                    (comparisonMapHeight - inset * 2).coerceAtLeast(1.dp))
                                Box(Modifier.matchParentSize().clickable(
                                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                                    onClickLabel = "경로 비교 닫기", onClick = close,
                                ).semantics { contentDescription = "경로 비교 바깥 영역" })
                                Box(Modifier.fillMaxSize().padding(bottom = bottomInset)) {
                                    NavigationRouteComparisonPanel(
                                        destination = state.routeDestination!!,
                                        alternatives = state.routeAlternatives,
                                        selected = state.routePreference,
                                        actionsAllowed = comparisonActionsAllowed,
                                        retryAllowed = routeOptionsAllowed && state.phase == DrivePhase.IDLE,
                                        message = state.message,
                                        onSelect = { if (comparisonActionsAllowed) model.selectRoutePreference(it) },
                                        onStart = { preference ->
                                            val ready = state.routeAlternatives.any { alternative ->
                                                alternative.preference == preference && !alternative.loading &&
                                                    alternative.route?.let {
                                                        it.preference == preference && it.destination == state.routeDestination
                                                    } == true
                                            }
                                            if (comparisonActionsAllowed && ready) motion.close { model.startGuidance(preference) }
                                        },
                                        onRetry = { if (routeOptionsAllowed && state.phase == DrivePhase.IDLE) model.retryRoute(it) },
                                        onChangeDestination = {
                                            if (comparisonActionsAllowed) motion.close {
                                                model.clearRoute()
                                                model.updateSearchQuery("")
                                                modal = DriveModal.SEARCH
                                            }
                                        },
                                        onCancel = close,
                                        modifier = Modifier.align(Alignment.BottomCenter).padding(inset)
                                            .widthIn(max = 980.dp).fillMaxWidth().heightIn(max = panelHeight)
                                            .onSizeChanged {
                                                comparisonPanelHeight = with(density) { it.height.toDp() } + inset
                                            },
                                    )
                                }
                            }
                        }
                    }
                }
                if (state.ready && !state.internetAvailable) {
                    Surface(Modifier.fillMaxWidth(), color = PaleBlue) {
                        Text("인터넷 연결을 기다리고 있어요 · 연결되면 자동으로 복구돼요",
                            Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                            color = Secondary, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }
                // Reserve a strip for the co-driver instead of covering turn instructions.
                if (aiGuideMessage != null && modal == null && !cctvState.panelOpen) {
                    AiGuideCard(aiGuideMessage, onDismissAiGuide,
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        voiceUnavailable = voiceUnavailable, compact = shortWindow, inline = true)
                }
                // Route comparison has its own metrics; preserve map space on a short split window.
                if (preferences.showDrivingMetrics && !(shortWindow && comparingRoutes)) {
                    HorizonDock(HorizonDockState(
                        speedKmh = telemetry.speedKmh,
                        gear = telemetry.gear,
                        batteryPercent = telemetry.batteryPercent,
                        rangeKm = telemetry.rangeKm,
                        efficiencyKmPerKwh = telemetry.efficiencyKmPerKwh,
                        efficiencyLabel = telemetry.efficiencyLabel,
                        remainingMeters = if (state.phase == DrivePhase.GUIDING) progress.remainingMeters else matchingRoute?.distanceMeters,
                        remainingSeconds = if (state.phase == DrivePhase.GUIDING) progress.remainingSeconds else matchingRoute?.durationSeconds,
                        destination = state.routeDestination?.name ?: matchingRoute?.destination?.name,
                    ), layerScale = layerScale, compact = compactWindow,
                        backgroundColor = layerStyle.card, foregroundColor = layerStyle.ink,
                        secondaryColor = layerStyle.secondary, accentColor = layerStyle.accent)
                }
            }
            ServiceRail(railWidth, policy.settingsAllowed, onHome, { service ->
                when (service) {
                    HomeService.DRIVE -> Unit
                    else -> if (policy.settingsAllowed) { model.stopGuidance(); onService(service) }
                }
            })
        }
        if (floatingDialogsAllowed && modal == null && preferences.showCctv && cctvState.panelOpen &&
            cctvState.selected == null && policy.videoAllowed) {
            DimmedSideSheet(cctvModel::closePanel, width = cctvPanelWidth) {
                val close = rememberDialogAction(cctvModel::closePanel)
                val motion = LocalDialogMotion.current
                CctvSidePanel(cctvState, policy.videoAllowed, { id -> motion.close { cctvModel.selectCamera(id) } }, close,
                    cctvModel::refresh, cctvModel::showDemoCameras, Modifier.fillMaxSize())
            }
        }
    }
    cctvState.selected?.takeIf { floatingDialogsAllowed && modal == null && preferences.showCctv && policy.videoAllowed }?.let {
        CctvVideoDialog(it, policy.videoAllowed, cctvModel::dismissVideo, cctvModel::onStreamError,
            networkOnline = cctvState.networkOnline, reconnecting = cctvState.reconnecting,
            playbackGeneration = cctvState.playbackGeneration)
    }
    when (modal.takeIf { floatingDialogsAllowed }) {
        DriveModal.PROVIDER -> if (policy.settingsAllowed && !guiding) NavigationSettingsDialog(state.provider,
            onSelect = {
                if (policy.settingsAllowed) model.selectProvider(it)
                modal = null
            }, onDismiss = { modal = null })
        DriveModal.LAYERS -> if (policy.settingsAllowed) NavigationLayerSettingsDialog(preferences.navigationLayers,
            policy.settingsAllowed, onUpdate = { if (policy.settingsAllowed) onPreferences(preferences.copy(navigationLayers = it)) },
            onDismiss = { modal = null }, speedWarningFlash = preferences.speedWarningFlash)
        DriveModal.SEARCH -> if (policy.searchAllowed) NavigationSearchDialog(state, places, policy.searchAllowed, policy.maxItems,
            onDismiss = { model.cancelSearch(); modal = null; keyboard?.hide() },
            onQueryChange = model::updateSearchQuery, onSearch = model::search,
            onSelect = {
                if (policy.searchAllowed) {
                    keyboard?.hide()
                    model.cancelSearch()
                    modal = null
                    model.prepare(it)
                }
            },
            onSavePlace = model::savePlace, onRemovePlace = model::removePlace,
            onRemoveRecentQuery = model::removeRecentQuery, onClearRecentQueries = model::clearRecentQueries)
        null -> Unit
    }
}

@Composable
private fun MapAction(
    label: String,
    enabled: Boolean,
    showLabel: Boolean = true,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    supporting: String? = null,
    icon: (@Composable (Color) -> Unit)? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val tint = if (!enabled) Secondary else if (emphasized) Blue else Ink
    Surface(
        onClick = onClick, enabled = enabled, shape = shape,
        color = if (emphasized && enabled) PaleBlue else SurfaceColor,
        border = BorderStroke(1.dp, Line), shadowElevation = if (enabled) FloatingShadowElevation else 0.dp,
        modifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .then(if (!showLabel) Modifier.semantics { contentDescription = label } else Modifier),
    ) {
        Row(Modifier.padding(horizontal = if (showLabel) 14.dp else 13.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            icon?.invoke(tint)
            if (showLabel) Column {
                Text(label, color = tint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                supporting?.let { Text(it, color = if (enabled) Blue else Secondary, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

internal fun serviceRailWidth(windowWidth: Dp): Dp = (windowWidth * .06f).coerceIn(60.dp, 72.dp)

@Composable
internal fun ServiceRail(width: Dp, servicesEnabled: Boolean, onHome: () -> Unit, onService: (HomeService) -> Unit, selectedService: HomeService? = HomeService.DRIVE) {
    Row(Modifier.width(width).fillMaxHeight().background(SurfaceColor).selectableGroup()) {
        VerticalDivider(color = Line)
        Column(Modifier.fillMaxSize().padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
                RailButton("메인 화면", MoaIconKind.HOME, selectedService == null, action = onHome)
                HomeService.entries.filter { it != HomeService.SETTINGS }.forEach { service ->
                    RailButton(service.label, service.icon, service == selectedService,
                        servicesEnabled || service == HomeService.DRIVE) { onService(service) }
                }
            }
            HorizontalDivider(Modifier.width(42.dp).padding(bottom = 8.dp), color = Line)
            RailButton("설정", MoaIconKind.SETTINGS, selectedService == HomeService.SETTINGS, servicesEnabled) { onService(HomeService.SETTINGS) }
        }
    }
}

@Composable
private fun RailButton(label: String, icon: MoaIconKind, selected: Boolean, enabled: Boolean = true, action: () -> Unit) {
    val shape = RoundedCornerShape(15.dp)
    Surface(Modifier.size(48.dp).clip(shape).semantics { contentDescription = label }
        .selectable(selected, enabled = enabled, role = Role.Tab, onClick = action),
        shape = shape, color = if (selected) PaleBlue else Color.Transparent) {
        Box(contentAlignment = Alignment.Center) {
            val iconModifier = Modifier.size(26.dp).alpha(if (enabled || selected) 1f else .4f)
            val iconColor = if (selected) Blue else Secondary
            if (icon == MoaIconKind.HOME || icon == MoaIconKind.SETTINGS) {
                MoaIcon(icon, iconModifier, iconColor, outline = true)
            } else {
                HomeMenuIllustration(icon, iconModifier, tint = iconColor,
                    detailColor = if (selected) PaleBlue else SurfaceColor)
            }
        }
    }
}

@Composable
fun NavigationSettingsDialog(selected: NavigationProviderId, onSelect: (NavigationProviderId) -> Unit, onDismiss: () -> Unit) {
    DimmedAlertDialog(onDismissRequest = onDismiss, title = { Text("기본 내비게이션") }, text = {
        val motion = LocalDialogMotion.current
        Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("길 안내에 사용할 서비스를 선택하세요.", color = Secondary)
            NavigationProviderId.entries.forEach { provider ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .selectable(selected == provider, enabled = provider.available, role = Role.RadioButton) { motion.close { onSelect(provider) } }
                    .padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RadioButton(selected == provider, onClick = null, enabled = provider.available)
                    Text(provider.label, Modifier.weight(1f), color = if (provider.available) Ink else Secondary)
                    if (!provider.available) Text("준비 중", fontSize = 12.sp, color = Secondary)
                }
            }
        }
    }, confirmButton = { TextButton(rememberDialogAction(onDismiss)) { Text("닫기") } })
}
