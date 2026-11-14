package com.moagauge.pleos.ui

import android.Manifest
import android.os.Build
import android.os.SystemClock
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import com.moagauge.pleos.ui.common.SoftSurface as Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.moagauge.pleos.navigation.DriveViewModel
import com.moagauge.pleos.home.HomeWeatherViewModel
import com.moagauge.pleos.ui.drive.DriveScreen
import com.moagauge.pleos.safety.SafetyViewModel
import com.moagauge.pleos.safety.score.DrivingScoreViewModel
import com.moagauge.pleos.sound.SoundViewModel
import com.moagauge.pleos.insurance.InsuranceViewModel
import com.moagauge.pleos.ui.safety.SafetyScreen
import com.moagauge.pleos.ui.sound.SoundScreen
import com.moagauge.pleos.ui.insurance.InsuranceScreen
import com.moagauge.pleos.ui.settings.SettingsScreen
import com.moagauge.pleos.vehicle.VehicleViewModel
import com.moagauge.pleos.ui.trips.TripsScreen
import com.moagauge.pleos.ui.common.DimmedDialog
import com.moagauge.pleos.ui.common.LocalDialogMotion
import com.moagauge.pleos.ui.common.rememberDialogAction
import com.moagauge.pleos.ui.common.LocalInfoRevealActive
import com.moagauge.pleos.ui.passport.PassportScreen
import com.moagauge.pleos.assist.AssistViewModel
import com.moagauge.pleos.assist.AiGuideViewModel
import com.moagauge.pleos.assist.AiGuideContext
import com.moagauge.pleos.assist.AiGuideKind
import com.moagauge.pleos.assist.AiGuideOptions
import com.moagauge.pleos.assist.AiGuideSpeech
import com.moagauge.pleos.safety.assessWeather
import com.moagauge.pleos.ui.assist.*
import com.moagauge.pleos.navigation.DrivePhase
import com.moagauge.pleos.settings.AppSettingsViewModel
import com.moagauge.pleos.startup.StartupDestination
import com.moagauge.pleos.startup.StartupPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.platform.LocalView
import com.moagauge.pleos.budget.BatteryBudgetViewModel
import com.moagauge.pleos.ui.budget.BatteryBudgetScreen
import com.moagauge.pleos.ui.condition.ConditionNoteScreen
import com.moagauge.pleos.ui.child.ChildCareScreen
import com.moagauge.pleos.ui.pet.PetCareScreen
import com.moagauge.pleos.cctv.CctvViewModel
import com.moagauge.pleos.cctv.CctvState
import com.moagauge.pleos.cctv.CctvStatus
import com.moagauge.pleos.safety.trafficCatalogForSummary

private const val CONDITION_NOTE_PAGE = "condition_note"
private const val BATTERY_BUDGET_PAGE = "battery_budget"

@Composable
fun MoaDriveApp(startupReady: Boolean = true) {
    // Pleos' 160-dpi emulator has a much larger canvas than a tablet in dp.
    // Scale the whole UI coherently while still respecting the user's font scale.
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Base layout on this task's bounds, including short wide Pleos displays.
        val uiScale = moaWindowScale(maxWidth.value, maxHeight.value)
        CompositionLocalProvider(
            LocalDensity provides Density(density.density * uiScale, density.fontScale),
            LocalInfoRevealActive provides startupReady,
        ) {
            MoaDriveContent(startupReady)
        }
    }
}

@Composable
private fun MoaDriveContent(startupReady: Boolean) {
    val driveModel: DriveViewModel = viewModel()
    val vehicleModel: VehicleViewModel = viewModel()
    val safetyModel: SafetyViewModel = viewModel()
    val drivingScoreModel: DrivingScoreViewModel = viewModel()
    val drivingScoreState by drivingScoreModel.state.collectAsState()
    val cctvModel: CctvViewModel = viewModel()
    val cctvState by cctvModel.state.collectAsState()
    var lastCctvCatalog by remember { mutableStateOf(CctvState()) }
    var lastCctvObservation by remember { mutableStateOf(CctvState()) }
    LaunchedEffect(cctvState) {
        // Keep only the latest successful catalog for safety's dated read-only summary.
        // Navigation continues to own fetching, location and player lifecycle.
        if (!cctvState.demo && cctvState.status != CctvStatus.IDLE) lastCctvObservation = cctvState
        if (cctvState.status == CctvStatus.READY && cctvState.lastUpdatedMs != null && !cctvState.demo) {
            lastCctvCatalog = cctvState
        }
    }
    val homeWeatherModel: HomeWeatherViewModel = viewModel()
    val homeWeather by homeWeatherModel.state.collectAsState()
    val soundModel: SoundViewModel = viewModel()
    val insuranceModel: InsuranceViewModel = viewModel()
    val assistModel: AssistViewModel = viewModel()
    val aiGuideModel: AiGuideViewModel = viewModel()
    val settingsModel: AppSettingsViewModel = viewModel()
    val budgetModel: BatteryBudgetViewModel = viewModel()
    val budgetState by budgetModel.state.collectAsState()
    val appPreferences by settingsModel.state.collectAsState()
    val assistState by assistModel.state.collectAsState()
    val aiGuideState by aiGuideModel.state.collectAsState()
    val guideForecast by safetyModel.currentForecast.collectAsState()
    val weatherAutoRefresh by safetyModel.autoRefresh.collectAsState()
    val departureMessage by safetyModel.departureMessage.collectAsState()
    val soundState by soundModel.state.collectAsState()
    val insurer by insuranceModel.provider.collectAsState()
    val telemetry by vehicleModel.state.collectAsState()
    val drivingPolicy by driveModel.policy.collectAsState()
    val budgetBrowsingAllowed = drivingPolicy.settingsAllowed
    val driveState by driveModel.state.collectAsState()
    val navigationProgress by driveModel.progress.collectAsState()
    val trips = vehicleModel.trips
    val tripHistory by trips.history.collectAsState()
    val currentTrip by trips.current.collectAsState()
    val recordingEnabled by trips.enabled.collectAsState()
    val recordingMessage by trips.message.collectAsState()
    val passportVisits by trips.passportVisits.collectAsState()
    val passportMessage by trips.passportMessage.collectAsState()
    val passportLocationActive by trips.passportLocationActive.collectAsState()
    // Choose only on a fresh composition. Saved page state wins on recreation, and
    // changing the preference in Settings never navigates away from that screen.
    var pageName by rememberSaveable {
        mutableStateOf<String?>(if (StartupPolicy.initialDestination(appPreferences.showIntro) == StartupDestination.NAVIGATION)
            HomeService.DRIVE.name else null)
    }
    var openCctvFromSafety by rememberSaveable { mutableStateOf(false) }
    var careSearchQuery by rememberSaveable { mutableStateOf<String?>(null) }
    var permissionRevision by remember { mutableStateOf(0) }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    var notificationRequested by rememberSaveable { mutableStateOf(false) }
    var resumed by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    val guideSpeech = remember(context) { AiGuideSpeech(context.applicationContext) }
    val guideVoiceAvailable by guideSpeech.available.collectAsState()
    val guideSpeaking by guideSpeech.speaking.collectAsState()
    val guideSpeechFailure by guideSpeech.lastFailureId.collectAsState()
    val guiding = driveState.phase in setOf(DrivePhase.GUIDING, DrivePhase.STARTING)
    // A navigation update is evidence of a fresh camera limit. Periodic score
    // checks must never refresh the timestamp of an unchanged navigation value.
    val scoreCamera = navigationProgress.speedCamera.takeIf { driveState.phase == DrivePhase.GUIDING }
    val scoreCameraObservedMs = remember(navigationProgress, driveState.phase) {
        SystemClock.elapsedRealtime().takeIf { scoreCamera != null }
    }
    val observeDrivingScore by rememberUpdatedState(newValue = {
        drivingScoreModel.observe(telemetry, currentTrip, tripHistory, appVisible = resumed,
            camera = scoreCamera, cameraObservedElapsedMs = scoreCameraObservedMs)
    })
    LaunchedEffect(telemetry, currentTrip, tripHistory, resumed, navigationProgress, driveState.phase) {
        observeDrivingScore()
    }
    LaunchedEffect(resumed, drivingScoreModel) {
        while (resumed) {
            delay(1_000)
            observeDrivingScore()
        }
    }
    DisposableEffect(guideSpeech) { onDispose { guideSpeech.close() } }
    val hostView = LocalView.current
    DisposableEffect(hostView, appPreferences.keepScreenOn) {
        val original = hostView.keepScreenOn
        hostView.keepScreenOn = appPreferences.keepScreenOn
        onDispose { hostView.keepScreenOn = original }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionRevision++ }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionRevision++
        trips.enablePassportLocation()
        safetyModel.setVisible(false)
        safetyModel.setVisible(startupReady && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        safetyModel.refresh()
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionRevision++
        vehicleModel.setVisible(startupReady && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(vehicleModel, safetyModel, soundModel, assistModel, aiGuideModel, homeWeatherModel, lifecycle, startupReady) {
        fun update() {
            resumed = startupReady && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            visible = startupReady && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (!resumed) drivingScoreModel.pauseObservation()
            vehicleModel.setVisible(visible)
            safetyModel.setVisible(visible)
            soundModel.setVisible(resumed)
            assistModel.setVisible(visible)
            aiGuideModel.setVisible(visible)
            if (!visible) homeWeatherModel.setVisible(false)
            if (!visible) driveModel.cancelSuggestedDestination()
            if (resumed) permissionRevision++
        }
        val observer = LifecycleEventObserver { _, _ -> update() }
        lifecycle.addObserver(observer)
        update()
        onDispose {
            lifecycle.removeObserver(observer)
            drivingScoreModel.pauseObservation()
            vehicleModel.setVisible(false)
            safetyModel.setVisible(false)
            soundModel.setVisible(false)
            assistModel.setVisible(false)
            aiGuideModel.setVisible(false)
            homeWeatherModel.setVisible(false)
        }
    }
    LaunchedEffect(visible, pageName, permissionRevision, weatherAutoRefresh) {
        homeWeatherModel.setAutoRefresh(weatherAutoRefresh)
        homeWeatherModel.setVisible(visible && pageName == null)
    }
    // Deliver speed changes to audio without waiting for a Compose frame.
    LaunchedEffect(soundModel, vehicleModel) {
        vehicleModel.state.collect { soundModel.updateTelemetry(it) }
    }
    LaunchedEffect(telemetry) { budgetModel.updateVehicle(telemetry) }
    LaunchedEffect(passportVisits) { budgetModel.updateVisits(passportVisits) }
    LaunchedEffect(budgetBrowsingAllowed) { budgetModel.setBrowsingAllowed(budgetBrowsingAllowed) }
    LaunchedEffect(visible, pageName) { budgetModel.setVisible(visible && pageName == BATTERY_BUDGET_PAGE) }
    LaunchedEffect(permissionRevision, visible, pageName) {
        if (visible && pageName == BATTERY_BUDGET_PAGE) budgetModel.refreshLocation()
    }
    DisposableEffect(budgetModel) { onDispose { budgetModel.setVisible(false) } }
    LaunchedEffect(telemetry, drivingPolicy.settingsAllowed, guideForecast, driveState.phase,
        navigationProgress.speedCamera, assistState.nearbyIncident, assistState.suggestion, appPreferences) {
        aiGuideModel.updateContext(AiGuideContext(
            telemetry = telemetry,
            browsingAllowed = drivingPolicy.settingsAllowed,
            forecast = guideForecast,
            guiding = guiding,
            camera = navigationProgress.speedCamera.takeIf { driveState.phase == DrivePhase.GUIDING },
            incident = assistState.nearbyIncident,
            options = AiGuideOptions(
                enabled = appPreferences.aiGuideEnabled,
                voiceEnabled = appPreferences.aiGuideVoice && appPreferences.voiceGuidance,
                departureWeather = appPreferences.departureWeather,
                speedWarnings = appPreferences.speedWarnings,
                incidentWarnings = appPreferences.incidentWarnings,
                warningDistanceMeters = appPreferences.speedWarningDistanceMeters,
                includeMobileCameras = appPreferences.includeMobileCameras,
            ),
            routineSuggestion = assistState.suggestion.takeIf { appPreferences.routineSuggestions },
        ))
    }
    val guideMessage = aiGuideState.message.takeIf { visible && appPreferences.aiGuideEnabled }
    val urgentGuideVisible = guideMessage?.kind in setOf(
        AiGuideKind.INCIDENT, AiGuideKind.OVERSPEED, AiGuideKind.CAMERA, AiGuideKind.SECTION)
    val routinePromptVisible = resumed && !guiding && !urgentGuideVisible && appPreferences.routineSuggestions &&
        assistState.suggestion != null && drivingPolicy.settingsAllowed
    val nearby = assistState.nearbyIncident
    val suggestion = assistState.suggestion
    val incidentPromptVisible = resumed && !appPreferences.aiGuideEnabled &&
        appPreferences.incidentWarnings && nearby != null
    val departurePromptMessage = departureMessage.takeIf {
        resumed && appPreferences.departureWeather && !appPreferences.aiGuideEnabled
    }
    val rootPopupVisible = incidentPromptVisible || routinePromptVisible || departurePromptMessage != null
    // A routine takes the boarding greeting's place, including speech. Stable IDs prevent
    // another utterance when telemetry, layout or activity focus changes.
    val routineSpeechId = assistState.suggestionId?.let { "routine-$it" }
    val speechId = if (routinePromptVisible) routineSpeechId else guideMessage?.id
    val speechText = if (routinePromptVisible) suggestion?.scenario?.speechText else guideMessage?.speech
    LaunchedEffect(speechId, speechText, guideVoiceAvailable, resumed, guiding,
        routinePromptVisible, appPreferences.aiGuideEnabled, appPreferences.aiGuideVoice, appPreferences.voiceGuidance) {
        if (resumed && !guiding && appPreferences.aiGuideEnabled && appPreferences.aiGuideVoice &&
            appPreferences.voiceGuidance && speechId != null && speechText != null && guideVoiceAvailable) {
            // Let the boarding and routine sessions settle before starting a generic greeting.
            if (!routinePromptVisible && guideMessage?.kind == AiGuideKind.BOARDING) delay(300)
            if (!guideSpeech.speak(speechId, speechText) && !guideSpeech.available.value) {
                // stop()/onStop may finish within one UI frame. Wait on the source flow so
                // a fast false→true transition cannot drop the replacement utterance.
                val ready = withTimeoutOrNull(5_500) { guideSpeech.available.first { it } }
                if (ready == true) guideSpeech.speak(speechId, speechText)
            }
        } else guideSpeech.stop()
    }
    LaunchedEffect(appPreferences.routineSuggestions, appPreferences.incidentWarnings) {
        assistModel.setOptions(appPreferences.routineSuggestions, appPreferences.incidentWarnings)
    }
    LaunchedEffect(telemetry, drivingPolicy.settingsAllowed, driveState.phase) {
        assistModel.updateVehicle(telemetry, drivingPolicy.settingsAllowed &&
            driveState.phase !in setOf(DrivePhase.GUIDING, DrivePhase.STARTING))
    }
    LaunchedEffect(driveState.route?.destination) { safetyModel.setDestination(driveState.route?.destination) }
    LaunchedEffect(currentTrip?.id) { safetyModel.onTripChanged(currentTrip?.id) }
    LaunchedEffect(resumed, drivingPolicy.setupAllowed) {
        if (resumed && drivingPolicy.setupAllowed && !permissionRequested) {
            permissionRequested = true
            val missing = vehicleModel.missingPermissions()
            if (missing.isNotEmpty()) permissionLauncher.launch(missing)
        }
        if (resumed && drivingPolicy.setupAllowed && vehicleModel.missingPermissions().isEmpty() &&
            Build.VERSION.SDK_INT >= 33 && !notificationRequested) {
            notificationRequested = true
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    val openService: (HomeService) -> Unit = { service ->
        if (service == HomeService.DRIVE || drivingPolicy.settingsAllowed) {
            pageName = when (service) {
                HomeService.CONDITION_NOTE -> CONDITION_NOTE_PAGE
                HomeService.BATTERY_BUDGET -> BATTERY_BUDGET_PAGE
                else -> service.name
            }
        }
    }
    val setRecording: (Boolean) -> Unit = { enabled ->
        if (drivingPolicy.settingsAllowed) {
            trips.setEnabled(enabled)
            val missing = vehicleModel.missingPermissions()
            if (enabled && drivingPolicy.setupAllowed && missing.isNotEmpty()) permissionLauncher.launch(missing)
        }
    }
    val requestLocation: () -> Unit = {
        if (drivingPolicy.settingsAllowed) {
            if (trips.hasFineLocationPermission()) { trips.enablePassportLocation(); safetyModel.refresh() }
            else if (drivingPolicy.setupAllowed) locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    val findCarePlace: (String) -> Unit = { query ->
        if (drivingPolicy.searchAllowed && query.isNotBlank()) {
            careSearchQuery = query.trim().take(100)
            pageName = HomeService.DRIVE.name
        }
    }
    Box(Modifier.fillMaxSize()) {
        when (pageName) {
            CONDITION_NOTE_PAGE -> ConditionNoteScreen(tripHistory, passportVisits, drivingPolicy.settingsAllowed,
                onHome = { pageName = null }, onService = openService)
            BATTERY_BUDGET_PAGE -> BatteryBudgetScreen(budgetModel, budgetBrowsingAllowed,
                navigationAllowed = drivingPolicy.searchAllowed,
                onLocationPermission = requestLocation,
                onDestination = { destination ->
                    val candidate = budgetState.candidates.firstOrNull { it.destination == destination }
                    if (drivingPolicy.searchAllowed && candidate != null && budgetModel.canNavigate(candidate)) {
                        // Budget owns only calculations; navigation owns its own route cache and focus.
                        budgetModel.setVisible(false)
                        driveModel.requestDestination(destination)
                        pageName = HomeService.DRIVE.name
                    }
                },
                onHome = { pageName = null }, onService = openService)
            HomeService.DRIVE.name -> DriveScreen(driveModel, telemetry, onHome = { pageName = null }, onService = openService,
                preferences = appPreferences, onPreferences = { if (drivingPolicy.settingsAllowed) settingsModel.update(it) },
                aiGuideMessage = guideMessage, onDismissAiGuide = aiGuideModel::dismiss,
                openCctvOnEntry = openCctvFromSafety,
                searchQueryOnEntry = careSearchQuery,
                onSearchEntryConsumed = { careSearchQuery = null },
                floatingDialogsAllowed = startupReady && !rootPopupVisible,
                startupReady = startupReady,
                onCctvEntryConsumed = { openCctvFromSafety = false },
                voiceUnavailable = guideMessage?.speech != null &&
                    (!guideVoiceAvailable || guideSpeechFailure == guideMessage?.id))
            HomeService.TRIPS.name -> TripsScreen(tripHistory, currentTrip, recordingEnabled, recordingMessage,
                drivingPolicy.settingsAllowed, onRecordingEnabled = setRecording,
                onHome = { pageName = null }, onService = openService,
                textInputAllowed = drivingPolicy.textInputAllowed && drivingPolicy.setupAllowed)
            HomeService.PASSPORT.name -> PassportScreen(passportVisits, tripHistory, currentTrip, recordingEnabled,
                passportLocationActive, passportMessage, drivingPolicy.settingsAllowed,
                onEnableLocation = requestLocation, onHome = { pageName = null }, onService = openService, onCellNamed = trips::namePassportCell,
                onRecommendCity = { query ->
                    if (drivingPolicy.searchAllowed) {
                        driveModel.requestSuggestedDestination(query)
                        pageName = HomeService.DRIVE.name
                    }
                }, onRecommendPlace = { destination ->
                    if (drivingPolicy.searchAllowed) {
                        driveModel.requestDestination(destination)
                        pageName = HomeService.DRIVE.name
                    }
                }, navigationAllowed = drivingPolicy.searchAllowed)
            HomeService.SAFETY.name -> SafetyScreen(safetyModel, drivingPolicy.settingsAllowed, requestLocation, { pageName = null }, openService,
                cctvState = trafficCatalogForSummary(
                    if (cctvState.status == CctvStatus.IDLE) lastCctvObservation.copy(
                        status = if (lastCctvObservation.status == CctvStatus.LOADING) CctvStatus.IDLE else lastCctvObservation.status,
                        networkOnline = cctvState.networkOnline) else cctvState,
                    lastCctvCatalog),
                incidentDemo = assistState.nearbyIncident,
                onAiBriefing = aiGuideModel::requestBriefing,
                onCctv = {
                    if (drivingPolicy.videoAllowed && appPreferences.showCctv) {
                        openCctvFromSafety = true
                        pageName = HomeService.DRIVE.name
                    }
                }, aiGuideEnabled = appPreferences.aiGuideEnabled, cctvEnabled = appPreferences.showCctv && drivingPolicy.videoAllowed,
                drivingScoreModel = drivingScoreModel,
                tripRecordingEnabled = recordingEnabled,
                aiBriefingAvailable = visible && telemetry.active && telemetry.ignitionOn != false &&
                    telemetry.gear == "P" && telemetry.speedKmh == 0 && !guiding) {
                IncidentScenarioCard(assistState, drivingPolicy.settingsAllowed,
                    assistModel::playIncidentDemo, assistModel::armIncident, assistModel::resetDemo)
            }
            HomeService.CHILD_CARE.name -> ChildCareScreen(drivingPolicy.settingsAllowed,
                drivingPolicy.textInputAllowed && drivingPolicy.setupAllowed,
                onHome = { pageName = null }, onService = openService, onFindPlace = findCarePlace,
                navigationAllowed = drivingPolicy.searchAllowed)
            HomeService.PET_CARE.name -> PetCareScreen(drivingPolicy.settingsAllowed,
                drivingPolicy.textInputAllowed && drivingPolicy.setupAllowed,
                onHome = { pageName = null }, onService = openService, onFindPlace = findCarePlace,
                navigationAllowed = drivingPolicy.searchAllowed)
            HomeService.SOUND.name -> SoundScreen(soundModel, telemetry, drivingPolicy.settingsAllowed, { pageName = null }, openService)
            HomeService.INSURANCE.name -> InsuranceScreen(insuranceModel, tripHistory, drivingPolicy.settingsAllowed, { pageName = null }, openService,
                formsAllowed = drivingPolicy.setupAllowed && drivingPolicy.textInputAllowed,
                drivingScoreState = drivingScoreState)
            HomeService.SETTINGS.name -> SettingsScreen(
                provider = driveState.provider, onProvider = driveModel::selectProvider,
                recordingEnabled = recordingEnabled, recordingMessage = recordingMessage, onRecordingEnabled = setRecording,
                weatherAutoRefresh = weatherAutoRefresh, onWeatherAutoRefresh = { if (drivingPolicy.settingsAllowed) safetyModel.setAutoRefresh(it) },
                soundProfile = soundState.profile.title, soundVolume = soundState.volume, insurer = insurer?.title,
                telemetry = telemetry, tripCount = tripHistory.count { !it.isActive }, stampCount = passportVisits.map { it.cell.id }.distinct().size,
                browsingAllowed = drivingPolicy.settingsAllowed, permissionRevision = permissionRevision,
                onVehiclePermission = { if (drivingPolicy.setupAllowed) vehicleModel.missingPermissions().takeIf { it.isNotEmpty() }?.let { permissionLauncher.launch(it) } },
                onLocationPermission = requestLocation,
                onNotificationPermission = { if (drivingPolicy.setupAllowed && Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                onHome = { pageName = null }, onService = openService,
                appPreferences = appPreferences,
                onAppPreferences = { if (drivingPolicy.settingsAllowed) settingsModel.update(it) },
                onPreviewAiGuide = aiGuideModel::requestBriefing,
                setupAllowed = drivingPolicy.setupAllowed,
            )
            else -> HomeScreen(state = HomeUiState.fromVehicle(telemetry), servicesEnabled = drivingPolicy.settingsAllowed,
                onServiceClick = openService, onRoutineScenario = assistModel::simulateRoutine,
                routinesEnabled = appPreferences.routineSuggestions,
                weather = homeWeather, animateWeather = resumed,
                aiGuideEnabled = appPreferences.aiGuideEnabled,
                aiGuideSummary = guideForecast?.let { assessWeather(it, System.currentTimeMillis()).summary },
                onAiBriefing = aiGuideModel::requestBriefing)
        }
        when {
            incidentPromptVisible -> {
                DimmedDialog(onDismissRequest = assistModel::dismissIncident) {
                    IncidentAlertBanner(nearby, onDismiss = rememberDialogAction(assistModel::dismissIncident),
                        modifier = Modifier.padding(20.dp))
                }
            }
            routinePromptVisible && suggestion != null -> {
                DimmedDialog(onDismissRequest = assistModel::dismissRoutine) {
                    val motion = LocalDialogMotion.current
                    RoutineSuggestionCard(suggestion, true,
                        onAccept = {
                            if (drivingPolicy.searchAllowed) motion.close {
                                assistModel.dismissRoutine()
                                driveModel.requestSuggestedDestination(suggestion.scenario.destinationQuery)
                                pageName = HomeService.DRIVE.name
                            }
                        }, onDismiss = rememberDialogAction(assistModel::dismissRoutine),
                        modifier = Modifier.padding(20.dp), aiGuideEnabled = appPreferences.aiGuideEnabled,
                        speaking = guideSpeaking,
                        voiceUnavailable = appPreferences.aiGuideEnabled && appPreferences.aiGuideVoice &&
                            appPreferences.voiceGuidance && (!guideVoiceAvailable || guideSpeechFailure == routineSpeechId))
                }
            }
            departurePromptMessage != null -> {
                DimmedDialog(onDismissRequest = safetyModel::dismissDeparture) {
                    Surface(Modifier.padding(20.dp).widthIn(max = 680.dp).fillMaxWidth(),
                        color = PaleBlue, shape = RoundedCornerShape(18.dp), shadowElevation = FloatingShadowElevation) {
                        Column(Modifier.padding(horizontal = 22.dp, vertical = 14.dp)) {
                            Text("출발 전 날씨 안내", color = Blue, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(departurePromptMessage, color = Ink, fontSize = 17.sp)
                            TextButton(onClick = rememberDialogAction(safetyModel::dismissDeparture), modifier = Modifier.align(Alignment.End)) {
                                Text("닫기")
                            }
                        }
                    }
                }
            }
            resumed && pageName != HomeService.DRIVE.name && guideMessage != null -> {
                DimmedDialog(onDismissRequest = aiGuideModel::dismiss) {
                    AiGuideCard(guideMessage, rememberDialogAction(aiGuideModel::dismiss),
                        Modifier.padding(20.dp).widthIn(max = 620.dp).fillMaxWidth(),
                        voiceUnavailable = guideMessage.speech != null &&
                            (!guideVoiceAvailable || guideSpeechFailure == guideMessage.id), speaking = guideSpeaking)
                }
            }
        }
    }
}
