package com.moadrive.pleos.navigation.kakao

import android.app.Application
import android.os.SystemClock
import android.util.Log
import com.moadrive.pleos.BuildConfig
import com.moadrive.pleos.navigation.*
import com.kakaomobility.knsdk.*
import com.kakaomobility.knsdk.api.objects.KNSearchResult
import com.kakaomobility.knsdk.common.objects.*
import com.kakaomobility.knsdk.common.util.IntPoint
import com.kakaomobility.knsdk.trip.kntrip.KNTrip
import com.kakaomobility.knsdk.trip.kntrip.knroute.KNRoute
import com.kakaomobility.knsdk.ui.view.KNNaviView
import com.kakaomobility.knsdk.guidance.knguidance.common.KNLocation
import com.kakaomobility.knsdk.guidance.knguidance.routeguide.objects.KNDirection
import com.kakaomobility.knsdk.guidance.knguidance.safetyguide.objects.KNSafety_Camera
import com.kakaomobility.knsdk.guidance.knguidance.safetyguide.objects.KNSafety_Section
import com.kakaomobility.knsdk.guidance.knguidance.safetyguide.objects.KNSafety
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

internal data class KakaoDisplayOptions(
    val night: Boolean = false,
    val showVehicle: Boolean = true,
    val voiceGuidance: Boolean = true,
    val speedWarnings: Boolean = true,
    val safetyOptions: NavigationSafetyOptions = NavigationSafetyOptions(),
    val map3dEnabled: Boolean = false,
)

class KakaoNavigationProvider(private val application: Application) : NavigationProvider {
    override val id = NavigationProviderId.KAKAO
    private val mutableProgress = MutableStateFlow(NavigationProgress())
    override val progress: StateFlow<NavigationProgress> = mutableProgress.asStateFlow()
    private val destinations = mutableMapOf<String, KNPOI>()
    private val routes = mutableMapOf<String, Prepared>()
    private var preparationRevision = 0L
    internal data class Prepared(val trip: KNTrip, val mapRoute: KNRoute, val options: KakaoRouteOptions)
    internal fun prepared(route: NavigationRoute?) = route?.let { routes[it.id] }
    internal var active = false
        private set
    private var generation = 0L
    private val attachment = MutableStateFlow<GuidanceAttachment?>(null)
    private var pending: CompletableDeferred<Unit>? = null
    private var ended: (() -> Unit)? = null
    private var lastSafetySampleElapsedMs: Long? = null
    private var safetyExpiry: Job? = null
    private val safetyScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    internal var displayOptions = KakaoDisplayOptions()
        private set
    internal var hasFreshMapOrigin = false
        private set

    internal fun updateDisplayOptions(options: KakaoDisplayOptions, freshLocation: Boolean) {
        if (displayOptions == options && hasFreshMapOrigin == freshLocation) return
        displayOptions = options
        hasFreshMapOrigin = freshLocation
        attachment.value?.applyDisplayOptions()
        if (installed && attachment.value != null) updated()
    }

    override suspend fun initialize() = withContext(Dispatchers.Main.immediate) {
        initMutex.withLock {
            val startedAt = SystemClock.elapsedRealtime()
            fun diagnose(event: String) {
                if (BuildConfig.DEBUG) Log.d("PleosKakaoInit",
                    "$event elapsedMs=${SystemClock.elapsedRealtime() - startedAt} initSt=${KNSDK.initSt}")
            }
            diagnose("start")
            try {
                if (!installed) {
                    KNSDK.install(application, application.filesDir.resolve("knsdk").apply { mkdirs() }.absolutePath)
                    installed = true
                }
                if (KNSDK.initSt != KNSDKInitState.KNSDKInitState_Initialized) {
                    if (BuildConfig.KAKAO_NATIVE_APP_KEY.isBlank()) throw NavigationFailure("카카오 지도 키가 설정되지 않았습니다.")
                    // This callback also waits for map databases/resources, beyond authentication.
                    // Allow a bounded cold start on the emulator where Realm verification is slow.
                    withTimeoutOrNull(SDK_INITIALIZATION_TIMEOUT_MS) {
                        // KNSDK has no cancellation API for this native request. A reconnecting
                        // caller joins it until its callback settles, even after a UI timeout.
                        nativeInitialization.await { complete ->
                            KNSDK.initializeWithAppKey(BuildConfig.KAKAO_NATIVE_APP_KEY, BuildConfig.VERSION_NAME, null, null,
                                KNLanguageType.KNLanguageType_KOREAN) { error ->
                                // Keep late completion visible without logging credentials, URLs,
                                // SDK error payloads, or any vehicle/location data.
                                diagnose("callback")
                                val code = error?.code?.takeIf { it.matches(Regex("[A-Za-z0-9_.-]{1,32}")) } ?: "UNKNOWN"
                                complete(if (error == null) Result.success(Unit)
                                else Result.failure(NavigationFailure("카카오 지도 초기화에 실패했습니다 ($code). 앱 등록과 네트워크를 확인하세요.")))
                            }
                        }
                    } ?: run {
                        diagnose("timeout")
                        throw NavigationFailure("카카오 지도 준비 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요.")
                    }
                }
                KNSDK.sharedGuidance()?.useBackgroundUpdate = false
                if (active) { KNSDK.handleWillEnterForeground(); KNSDK.handleDidBecomeActive() }
            } finally {
                diagnose("end")
            }
        }
    }

    override suspend fun search(query: String, origin: GeoPoint?): List<NavigationDestination> = withContext(Dispatchers.Main.immediate) {
        val keyword = query.trim()
        if (keyword.isEmpty()) return@withContext emptyList()
        val center = origin?.katec()
        val result = request<KNSearchResult>("목적지를 검색하지 못했습니다.") { callback ->
            KNSDK.reqSearch(keyword, 1, 15, KNSearchReqType.KNSearchReqType_1, center?.x ?: 0, center?.y ?: 0, callback)
        }
        (result.placeResult?.poiList.orEmpty() + result.addressResult?.poiList.orEmpty()).mapNotNull { poi ->
            val guide = poi.guidePoints.orEmpty().firstOrNull { it.pos.x != 0 || it.pos.y != 0 }
            val point = guide?.pos ?: poi.pos
            if ((point.x == 0 && point.y == 0) || poi.name.isBlank()) return@mapNotNull null
            val wgs = KNSDK.convertKATECToWGS84(point.x, point.y)
            val id = UUID.nameUUIDFromBytes("${guide?.poiId}|${point.x}|${point.y}|${poi.name}".toByteArray()).toString()
            val address = poi.rnAddress?.takeIf { it.isNotBlank() } ?: poi.address
            destinations[id] = KNPOI(poi.name, point.x, point.y, address, guide?.poiId, guide?.rpFlag)
            runCatching { NavigationDestination(id, poi.name, address, GeoPoint(wgs.y, wgs.x)) }.getOrNull()
        }.distinctBy { it.id }
    }

    override fun clearPreparedRoutes() { preparationRevision++; routes.clear() }

    override suspend fun prepareRoute(origin: GeoPoint, destination: NavigationDestination, preference: NavigationRoutePreference): NavigationRoute = withContext(Dispatchers.Main.immediate) {
        val revision = preparationRevision
        val options = preference.kakaoOptions()
        val point = origin.katec()
        // Saved places retain the entrance WGS84 coordinate from the original search.
        // Rebuild the provider POI after process restart instead of requiring an in-memory cache hit.
        val goal = destinations[destination.id] ?: destination.point.katec().let { saved ->
            if (destination.name.isBlank()) throw NavigationFailure("저장한 장소의 이름을 확인해 주세요.")
            KNPOI(destination.name, saved.x, saved.y, destination.address, null, null)
        }
        val trip = request<KNTrip>("경로를 만들지 못했습니다.") { KNSDK.makeTripWithStart(KNPOI("현재 위치", point.x, point.y), goal, null, null, it) }
        val choices = request<List<KNRoute>>("경로를 찾지 못했습니다.") {
            trip.routeWithPriority(options.priority, options.avoid, it)
        }
        val route = choices.firstOrNull() ?: throw NavigationFailure("이 목적지까지 경로가 없습니다.")
        if (revision != preparationRevision) throw CancellationException("Route comparison replaced")
        val id = UUID.randomUUID().toString()
        // Keep each option's own KNTrip and map route: selecting a sibling must not retire this one.
        routes[id] = Prepared(trip, route, options)
        // Only log routing flags in debug builds, never destinations, coordinates or credentials.
        if (BuildConfig.DEBUG) Log.d("PleosRouteOptions", "prepared preference=$preference requestedPriority=${options.priority} requestedAvoid=${options.avoid} actualPriority=${route.priority} actualAvoid=${route.avoidOptions}")
        NavigationRoute(id, this@KakaoNavigationProvider.id, destination, route.totalDist, route.totalTime, route.totalCost.takeIf { it >= 0 }, preference)
    }

    override suspend fun start(route: NavigationRoute, onEnded: () -> Unit) = withContext(Dispatchers.Main.immediate) {
        val prepared = prepared(route) ?: throw NavigationFailure("경로를 다시 검색해 주세요.")
        if (!active) throw NavigationFailure("화면이 활성 상태일 때 안내를 시작해 주세요.")
        val token = generation
        val signal = CompletableDeferred<Unit>()
        pending?.cancel()
        pending = signal
        ended = onEnded
        try {
            withTimeout(25_000) {
                val surface = attachment.filterNotNull().first()
                if (generation != token || !active) throw CancellationException("Navigation session invalidated")
                surface.begin(prepared)
                signal.await()
            }
        } catch (error: Throwable) {
            if (generation == token) stop()
            if (error is TimeoutCancellationException) throw NavigationFailure("안내를 시작하지 못했습니다. GPS 수신과 현재 위치를 확인해 주세요.")
            throw error
        } finally { if (pending === signal) pending = null }
    }
    internal fun attach(view: KNNaviView): GuidanceAttachment {
        attachment.value?.detach()
        return GuidanceAttachment(this, view).also { attachment.value = it }
    }
    internal fun detach(surface: GuidanceAttachment) {
        surface.detach()
        if (attachment.value === surface) { stop(); attachment.value = null }
    }
    internal fun started(surface: GuidanceAttachment) {
        if (attachment.value === surface && active && pending != null) pending?.complete(Unit)
    }
    internal fun speedWarning(camera: KNSafety_Camera): NavigationSpeedCamera? {
        if (!isSpeedEnforcementCode(camera.code.name)) return null
        val sampledAt = lastSafetySampleElapsedMs ?: return null
        if (SystemClock.elapsedRealtime() - sampledAt !in 0L..SAFETY_SAMPLE_MAX_AGE_MS) return null
        val location = KNSDK.sharedGuidance()?.locationGuide?.location ?: return null
        if (location.route == null || location.route != camera.location.route) return null
        val meters = location.distToLocation(camera.location)
        val section = camera as? KNSafety_Section
        // The public section object supplies its average, but its remaining-distance
        // getter is SDK-internal. Do not infer an end distance or completion from it.
        return speedCameraWarning(camera.code.name, camera.speedLimit, meters, camera.passed,
            section?.sectionAvrSpeed, displayOptions.safetyOptions)
    }
    internal fun acceptsSafety(safety: KNSafety): Boolean =
        !isSpeedEnforcementCode(safety.code.name) || (safety is KNSafety_Camera && speedWarning(safety) != null)

    internal fun updated(freshSafetySample: Boolean = false) {
        val guidance = KNSDK.sharedGuidance() ?: return
        if (freshSafetySample) lastSafetySampleElapsedMs = SystemClock.elapsedRealtime()
        val trip = guidance.trip
        val location = guidance.locationGuide?.location
        val routeGuide = guidance.routeGuide
        fun distance(target: KNLocation): Int? {
            // distToLocation falls back to straight-line distance for unmatched routes.
            // Only publish an along-route distance from locations on the same native route.
            if (location?.route == null || location.route != target.route) return null
            return location.distToLocation(target).takeIf { it >= 0 }
        }
        fun maneuver(direction: KNDirection?): NavigationManeuver? = direction?.let {
            val presentation = maneuverPresentation(it.rgCode.name)
            val road = it.directionNames.orEmpty().filter(String::isNotBlank).take(2).joinToString(" · ")
                .ifBlank { it.nodeName.orEmpty() }
            NavigationManeuver(presentation.first, presentation.second, road, distance(it.location),
                it.rgCode.name.substringAfterLast('_').toIntOrNull()?.takeIf { clock -> clock in 1..12 })
        }
        val traffic = location?.let {
            val condition = when (it.trafficSt) {
                KNTrafficState.KNTrafficState_Blue -> "원활"
                KNTrafficState.KNTrafficState_Yellow -> "서행"
                KNTrafficState.KNTrafficState_Orange -> "지체"
                KNTrafficState.KNTrafficState_Red -> "정체"
                KNTrafficState.KNTrafficState_Blocked -> "통제"
                else -> "정보 확인 중"
            }
            NavigationTraffic(it.roadName.orEmpty().ifBlank { "현재 도로" },
                it.trafficSpd.takeIf { speed -> speed in 0..200 && it.trafficSt != KNTrafficState.KNTrafficState_Unknown }, condition)
        }
        val speedCamera = selectNavigationSpeedWarning(guidance.safetyGuide?.safetiesOnGuide.orEmpty()
            .filterIsInstance<KNSafety_Camera>().mapNotNull(::speedWarning))
        val restArea = routeGuide?.hwInfo?.nearSas.orEmpty().mapNotNull { area ->
            val meters = distance(area.location) ?: return@mapNotNull null
            NavigationRestArea(area.nodeName.orEmpty().ifBlank { "다음 휴게소" }, meters, buildList {
                if (area.elecChargerExist) add("전기차 충전")
                if (area.foodExist) add("식사")
                if (area.gasBrand.toInt() > 0) add("주유")
                if (area.repairExist) add("정비")
                if (area.atmExist) add("ATM")
            })
        }.minByOrNull { it.distanceMeters }
        mutableProgress.value = NavigationProgress(
            trip?.remainDist()?.takeIf { it >= 0 }, trip?.remainTime()?.takeIf { it >= 0 },
            maneuver(routeGuide?.curDirection), maneuver(routeGuide?.nextDirection), traffic, speedCamera, restArea)
        safetyExpiry?.cancel()
        val sampledAt = lastSafetySampleElapsedMs
        if (speedCamera != null && sampledAt != null) {
            val session = generation
            safetyExpiry = safetyScope.launch {
                delay((SAFETY_SAMPLE_MAX_AGE_MS - (SystemClock.elapsedRealtime() - sampledAt) + 1L).coerceAtLeast(1L))
                if (generation == session && lastSafetySampleElapsedMs == sampledAt) {
                    mutableProgress.value = mutableProgress.value.copy(speedCamera = null)
                    attachment.value?.applyDisplayOptions()
                }
            }
        }
    }
    internal fun finished(surface: GuidanceAttachment) {
        if (attachment.value !== surface) return
        pending?.completeExceptionally(NavigationFailure("안내가 종료되었습니다."))
        val callback = ended
        ended = null
        generation++
        safetyExpiry?.cancel(); safetyExpiry = null; lastSafetySampleElapsedMs = null
        mutableProgress.value = NavigationProgress()
        callback?.invoke()
    }
    override fun stop() {
        generation++
        safetyExpiry?.cancel(); safetyExpiry = null; lastSafetySampleElapsedMs = null
        pending?.cancel(); pending = null; ended = null
        attachment.value?.disarm()
        if (installed) KNSDK.sharedGuidance()?.stop()
        mutableProgress.value = NavigationProgress()
    }
    override fun foreground(active: Boolean) {
        if (this.active == active) return
        this.active = active
        if (!installed) return
        if (active) { KNSDK.handleWillEnterForeground(); KNSDK.handleDidBecomeActive() }
        else { stop(); KNSDK.handleWillResignActive(); KNSDK.handleDidEnterBackground() }
    }
    private fun GeoPoint.katec(): IntPoint = KNSDK.convertWGS84ToKATEC(longitude, latitude).let { IntPoint(it.x.roundToInt(), it.y.roundToInt()) }
    private suspend fun <T : Any> request(message: String, begin: (((KNError?, T?) -> Unit)) -> Unit?): T = withTimeoutOrNull(20_000) {
        suspendCancellableCoroutine<T> { continuation ->
            val finished = AtomicBoolean(false)
            continuation.invokeOnCancellation { finished.set(true) }
            fun finish(result: Result<T>) {
                if (finished.compareAndSet(false, true) && continuation.isActive) continuation.resumeWith(result)
            }
            try {
                val started = begin { error, value ->
                    finish(if (error == null && value != null) Result.success(value) else Result.failure(NavigationFailure(message)))
                }
                if (started == null) finish(Result.failure(NavigationFailure(message)))
            } catch (error: CancellationException) { continuation.cancel(error) }
            catch (error: Exception) { finish(Result.failure(NavigationFailure(message))) }
        }
    } ?: throw NavigationFailure("요청 시간이 초과되었습니다. 다시 시도해 주세요.")
    private companion object {
        const val SAFETY_SAMPLE_MAX_AGE_MS = 3_000L
        const val SDK_INITIALIZATION_TIMEOUT_MS = 60_000L
        var installed = false
        val initMutex = Mutex()
        // KNSDK is process-wide, including when a screen creates a new provider instance.
        val nativeInitialization = NativeInitializationFlight()
    }
}
