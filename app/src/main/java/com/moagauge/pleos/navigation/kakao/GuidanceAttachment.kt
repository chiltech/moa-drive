package com.moagauge.pleos.navigation.kakao

import android.util.Log
import com.moagauge.pleos.BuildConfig
import com.kakaomobility.knsdk.KNSDK
import com.kakaomobility.knsdk.common.objects.KNError
import com.kakaomobility.knsdk.guidance.knguidance.KNGuideRouteChangeReason
import com.kakaomobility.knsdk.guidance.knguidance.KNGuidance
import com.kakaomobility.knsdk.guidance.knguidance.KNGuidance_CitsGuideDelegate
import com.kakaomobility.knsdk.guidance.knguidance.KNGuidance_GuideStateDelegate
import com.kakaomobility.knsdk.guidance.knguidance.KNGuidance_LocationGuideDelegate
import com.kakaomobility.knsdk.guidance.knguidance.KNGuidance_RouteGuideDelegate
import com.kakaomobility.knsdk.guidance.knguidance.KNGuidance_SafetyGuideDelegate
import com.kakaomobility.knsdk.guidance.knguidance.KNGuidance_VoiceGuideDelegate
import com.kakaomobility.knsdk.guidance.knguidance.citsguide.KNGuide_Cits
import com.kakaomobility.knsdk.guidance.knguidance.common.KNLocation
import com.kakaomobility.knsdk.guidance.knguidance.locationguide.KNGuide_Location
import com.kakaomobility.knsdk.guidance.knguidance.routeguide.KNGuide_Route
import com.kakaomobility.knsdk.guidance.knguidance.routeguide.objects.KNHighwayRG
import com.kakaomobility.knsdk.guidance.knguidance.routeguide.objects.KNMultiRouteInfo
import com.kakaomobility.knsdk.guidance.knguidance.safetyguide.KNGuide_Safety
import com.kakaomobility.knsdk.guidance.knguidance.safetyguide.objects.KNSafety
import com.kakaomobility.knsdk.guidance.knguidance.safetyguide.objects.KNSafetyCode
import com.kakaomobility.knsdk.guidance.knguidance.voiceguide.KNGuide_Voice
import com.kakaomobility.knsdk.trip.kntrip.knroute.KNRoute
import com.kakaomobility.knsdk.ui.view.KNNaviView
import com.kakaomobility.knsdk.ui.component.MapViewCameraMode
import com.moagauge.pleos.navigation.allowsSpeedWarning
import com.moagauge.pleos.navigation.isSpeedEnforcementCode
import kotlin.math.roundToInt

/** One receiver per surface: callbacks queued for a retired view cannot reach its replacement. */
internal class GuidanceAttachment(private val provider: KakaoNavigationProvider, private val view: KNNaviView) :
    KNGuidance_GuideStateDelegate, KNGuidance_LocationGuideDelegate, KNGuidance_RouteGuideDelegate,
    KNGuidance_SafetyGuideDelegate, KNGuidance_CitsGuideDelegate, KNGuidance_VoiceGuideDelegate {
    private val guidance = checkNotNull(KNSDK.sharedGuidance())
    private var attached = true
    private var armed = false
    private var viewInitialized = false
    private var cameraDiagnosticCount = 0
    private var cameraLocationSamples = 0
    private var lastCameraDiagnosticState: String? = null
    private var installedAlert: Any? = null
    private var previousAlert: ((KNSafetyCode, Int, Int, Int, Boolean) -> Boolean)? = null
    init {
        guidance.guideStateDelegate = this as KNGuidance_GuideStateDelegate
        guidance.locationGuideDelegate = this as KNGuidance_LocationGuideDelegate
        guidance.routeGuideDelegate = this as KNGuidance_RouteGuideDelegate
        guidance.safetyGuideDelegate = this as KNGuidance_SafetyGuideDelegate
        guidance.citsGuideDelegate = this as KNGuidance_CitsGuideDelegate
        guidance.voiceGuideDelegate = this as KNGuidance_VoiceGuideDelegate
    }
    fun begin(route: KakaoNavigationProvider.Prepared) {
        check(attached && provider.active)
        armed = true
        installIoniqGuidanceImage(view)
        applyDisplayOptions()
        restoreAlert()
        // The native view installs its own hook during init and retires it before
        // OnAttachStateChangeListener runs. Restore the pre-view hook at teardown.
        val beforeViewInitialization = guidance.judgeOverSpeedAlert
        view.initWithGuidance(guidance, route.trip, route.options.priority, route.options.avoid)
        if (BuildConfig.DEBUG) Log.d("PleosRouteOptions", "guidance priority=${route.options.priority} avoid=${route.options.avoid}")
        viewInitialized = true
        val nativeAlert = guidance.judgeOverSpeedAlert
        previousAlert = beforeViewInitialization
        val alert: (KNSafetyCode, Int, Int, Int, Boolean) -> Boolean = { code, limit, speed, distance, highway ->
            attached && armed && provider.active && provider.displayOptions.speedWarnings &&
                provider.displayOptions.safetyOptions.allowsSpeedWarning(code.name, distance) &&
                nativeAlert(code, limit, speed, distance, highway)
        }
        guidance.judgeOverSpeedAlert = alert
        installedAlert = alert
        applyDisplayOptions()
    }

    fun applyDisplayOptions() {
        if (!attached) return
        val options = provider.displayOptions
        if (view.useDarkMode != options.night) view.useDarkMode = options.night
        view.sndVolume = if (options.voiceGuidance) 1f else 0f
        view.movableSpeedViolationCameraSnd = options.voiceGuidance && options.speedWarnings && options.safetyOptions.includeMobileCameras
        if (viewInitialized) {
            val mode = if (options.map3dEnabled) MapViewCameraMode.Bird else MapViewCameraMode.Top
            if (view.mapViewMode != mode) {
                view.mapViewMode = mode
                if (BuildConfig.DEBUG) view.postDelayed({ diagnoseCamera("mode-applied") }, 750L)
            }
        }
        applyVehicleVisibility()
    }

    private fun diagnoseCamera(event: String) {
        if (!BuildConfig.DEBUG || !attached || !armed || !provider.active || !viewInitialized || cameraDiagnosticCount >= 16) return
        val map = view.mapComponent.mapView
        if (!map.isAttachedToWindow || !map.holder.surface.isValid) return
        val tilt = map.tilt.takeIf { it.isFinite() } ?: return
        val state = "${provider.displayOptions.map3dEnabled}:${view.mapViewMode}:${tilt.roundToInt()}"
        // Keep the first few samples and later mode/tilt transitions, with a hard
        // per-surface cap. Never log coordinates, names, destinations, or keys.
        if (event == "location" && cameraLocationSamples++ >= 6 && state == lastCameraDiagnosticState) return
        lastCameraDiagnosticState = state
        cameraDiagnosticCount++
        val location = guidance.locationGuide?.location
        val direction = guidance.routeGuide?.curDirection?.location
        val directionMeters = if (location?.route != null && direction != null && direction.route == location.route) location.distToLocation(direction) else null
        Log.d("PleosKakaoCamera", "$event requested3d=${provider.displayOptions.map3dEnabled} mode=${view.mapViewMode} tilt=$tilt directionMeters=$directionMeters")
    }

    private fun applyVehicleVisibility() {
        // initWithGuidance binds the map asynchronously. Started/location/route callbacks
        // reapply the current setting after the SDK creates or updates its vehicle marker.
        view.mapComponent.mapView.userLocation?.isVisible = provider.displayOptions.showVehicle && provider.hasFreshMapOrigin
    }
    fun disarm() { armed = false }
    fun detach() {
        attached = false; armed = false
        if (guidance.guideStateDelegate === this) guidance.guideStateDelegate = null
        if (guidance.locationGuideDelegate === this) guidance.locationGuideDelegate = null
        if (guidance.routeGuideDelegate === this) guidance.routeGuideDelegate = null
        if (guidance.safetyGuideDelegate === this) guidance.safetyGuideDelegate = null
        if (guidance.citsGuideDelegate === this) guidance.citsGuideDelegate = null
        if (guidance.voiceGuideDelegate === this) guidance.voiceGuideDelegate = null
        restoreAlert()
    }
    private fun restoreAlert() {
        if (installedAlert != null && guidance.judgeOverSpeedAlert === installedAlert) {
            previousAlert?.let { guidance.judgeOverSpeedAlert = it }
        }
        installedAlert = null
        previousAlert = null
    }
    override fun guidanceGuideStarted(guidance: KNGuidance) {
        if (!attached) return
        if (!armed || !provider.active) { guidance.stop(); return }
        view.guidanceGuideStarted(guidance)
        applyDisplayOptions()
        provider.started(this)
        if (BuildConfig.DEBUG) view.postDelayed({ diagnoseCamera("started") }, 750L)
    }

    override fun guidanceCheckingRouteChange(guidance: KNGuidance) {
        if (!attached || !armed || !provider.active) return
        view.guidanceCheckingRouteChange(guidance)
    }

    override fun guidanceRouteUnchanged(guidance: KNGuidance) {
        if (!attached || !armed || !provider.active) return
        view.guidanceRouteUnchanged(guidance)
    }

    override fun guidanceRouteUnchangedWithError(guidance: KNGuidance, error: KNError) {
        if (!attached || !armed || !provider.active) return
        // The SDK error dialog calls guideCancel(), which enters safe-driving mode.
        // End through the app-owned session so focus and UI state are released together.
        armed = false
        guidance.stop()
        provider.finished(this)
    }

    override fun guidanceOutOfRoute(guidance: KNGuidance) {
        if (!attached || !armed || !provider.active) return
        view.guidanceOutOfRoute(guidance)
    }

    override fun guidanceRouteChanged(
        guidance: KNGuidance,
        previousRoute: KNRoute,
        previousLocation: KNLocation,
        currentRoute: KNRoute,
        currentLocation: KNLocation,
        reason: KNGuideRouteChangeReason,
    ) {
        if (!attached || !armed || !provider.active) return
        view.guidanceRouteChanged(guidance)
        applyDisplayOptions()
        provider.updated()
    }

    override fun guidanceGuideEnded(guidance: KNGuidance) {
        if (!attached || !armed || !provider.active) return
        armed = false
        guidance.stop()
        provider.finished(this)
    }

    override fun guidanceDidUpdateRoutes(
        guidance: KNGuidance,
        routes: List<KNRoute>,
        multiRouteInfo: KNMultiRouteInfo?,
    ) {
        if (!attached || !armed || !provider.active) return
        view.guidanceDidUpdateRoutes(guidance, routes, multiRouteInfo)
        applyVehicleVisibility()
    }

    override fun guidanceDidUpdateIndoorRoute(guidance: KNGuidance, route: KNRoute?) {
        if (!attached || !armed || !provider.active) return
        view.guidanceDidUpdateIndoorRoute(guidance, route)
        applyVehicleVisibility()
    }

    override fun guidanceDidUpdateLocation(guidance: KNGuidance, locationGuide: KNGuide_Location) {
        if (!attached || !armed || !provider.active) return
        view.guidanceDidUpdateLocation(guidance, locationGuide)
        applyVehicleVisibility()
        provider.updated(freshSafetySample = true)
        diagnoseCamera("location")
    }

    override fun guidanceDidUpdateRouteGuide(guidance: KNGuidance, routeGuide: KNGuide_Route) {
        if (!attached || !armed || !provider.active) return
        view.guidanceDidUpdateRouteGuide(guidance, routeGuide)
        applyVehicleVisibility()
        provider.updated()
    }

    override fun guidanceDidUpdateSafetyGuide(guidance: KNGuidance, safetyGuide: KNGuide_Safety?) {
        if (!attached || !armed || !provider.active) return
        provider.updated(freshSafetySample = true)
        // KNGuide_Safety's constructor is Kotlin-internal in the pinned SDK. Forward
        // its original public callback object; Compose cards/voice use our filter.
        view.guidanceDidUpdateSafetyGuide(guidance, safetyGuide)
    }

    override fun guidanceDidUpdateAroundSafeties(guidance: KNGuidance, safeties: List<KNSafety>?) {
        if (!attached || !armed || !provider.active) return
        view.guidanceDidUpdateAroundSafeties(guidance, safeties?.filter(provider::acceptsSafety))
    }

    override fun didUpdateCitsGuide(guidance: KNGuidance, citsGuide: KNGuide_Cits) {
        if (!attached || !armed || !provider.active) return
        view.didUpdateCitsGuide(guidance, citsGuide)
    }

    override fun shouldPlayVoiceGuide(
        guidance: KNGuidance,
        voiceGuide: KNGuide_Voice,
        newData: MutableList<ByteArray>,
    ): Boolean {
        if (!attached || !armed || !provider.active || !provider.displayOptions.voiceGuidance) return false
        // Public guideObj carries KNSafety for safety speech. Filter only speed enforcement;
        // turns, incidents and other ordinary route guidance keep the SDK's behavior.
        val safety = voiceGuide.guideObj as? KNSafety
        if (safety != null && isSpeedEnforcementCode(safety.code.name) && !provider.displayOptions.speedWarnings) return false
        if (safety != null && !provider.acceptsSafety(safety)) return false
        return view.shouldPlayVoiceGuide(guidance, voiceGuide, newData)
    }

    override fun willPlayVoiceGuide(guidance: KNGuidance, voiceGuide: KNGuide_Voice) {
        if (!attached || !armed || !provider.active) return
        view.willPlayVoiceGuide(guidance, voiceGuide)
    }

    override fun didFinishPlayVoiceGuide(guidance: KNGuidance, voiceGuide: KNGuide_Voice) {
        if (!attached || !armed || !provider.active) return
        view.didFinishPlayVoiceGuide(guidance, voiceGuide)
    }
}
