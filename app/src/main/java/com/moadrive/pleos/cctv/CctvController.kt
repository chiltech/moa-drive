package com.moadrive.pleos.cctv

import com.moadrive.pleos.assist.distanceMeters
import com.moadrive.pleos.navigation.GeoPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Main-dispatcher owner of catalog work. The caller supplies only a fresh current GPS point. */
class CctvController(
    private val source: CctvSource?,
    private val scope: CoroutineScope,
    private val demoAllowed: Boolean = false,
    private val nowMs: () -> Long = System::currentTimeMillis,
    initiallyOnline: Boolean = true,
) {
    private val mutableState = MutableStateFlow(CctvState(networkOnline = initiallyOnline))
    val state = mutableState.asStateFlow()
    private var visible = false
    private var browsingAllowed = false
    private var networkOnline = initiallyOnline
    private var networkRefreshPending = !initiallyOnline
    private var origin: GeoPoint? = null
    private var lastFetchOrigin: GeoPoint? = null
    private var lastFetchAt = 0L
    private data class CachedCatalog(val cameras: List<CctvCamera>, val origin: GeoPoint, val fetchedAt: Long)
    private var catalogCache: CachedCatalog? = null
    private var operation: Job? = null
    private var retry: Job? = null
    private var ticker: Job? = null
    private var generation = 0L
    private var playbackGeneration = 0L
    // A quota denial cannot recover through GPS, timers, or network changes.
    // Keep it latched across screen visits until the user explicitly retries after adjustment.
    private var quotaExceeded = false
    // A cancelled HTTP request can still be finishing its blocking read. Never overlap it.
    private val fetchMutex = Mutex()

    fun setVisible(active: Boolean) {
        if (visible == active) return
        visible = active
        if (active) {
            maybeRefresh()
            ticker = scope.launch {
                while (isActive) { delay(30_000); maybeRefresh() }
            }
        } else {
            cancelRequest()
            ticker?.cancel(); ticker = null
            origin = null
            // Returning to navigation can reuse camera locations. Playback URLs are
            // renewed only when the user chooses a real camera below.
            lastFetchOrigin = catalogCache?.origin
            lastFetchAt = catalogCache?.fetchedAt ?: 0L
            mutableState.value = CctvState(status = if (source == null) CctvStatus.NEEDS_CONFIGURATION else CctvStatus.IDLE,
                message = if (source == null) CONFIGURATION_MESSAGE else null, networkOnline = networkOnline)
        }
    }

    fun setNetworkOnline(online: Boolean) {
        if (networkOnline == online) return
        networkOnline = online
        mutableState.value = state.value.copy(networkOnline = online)
        if (quotaExceeded && !state.value.demo) {
            networkRefreshPending = false
            cancelRequest()
            if (visible) showQuotaExceeded()
            return
        }
        if (!online) {
            networkRefreshPending = true
            cancelRequest()
            if (visible && !state.value.demo && source != null && origin != null) showOffline()
        } else if (visible && !state.value.demo) {
            maybeRefresh(force = true, recovery = true)
        }
    }

    fun updateLocation(point: GeoPoint?, browsingAllowed: Boolean) {
        this.browsingAllowed = browsingAllowed
        if (!browsingAllowed) mutableState.value = state.value.copy(selected = null, panelOpen = false, reconnecting = false)
        if (!visible) { origin = null; return }
        origin = point
        if (quotaExceeded && !state.value.demo) { showQuotaExceeded(); return }
        if (point == null) {
            cancelRequest(); lastFetchOrigin = null
            mutableState.value = state.value.copy(status = if (source == null) CctvStatus.NEEDS_CONFIGURATION else CctvStatus.NO_LOCATION,
                cameras = emptyList(), selected = null, demo = false, lastUpdatedMs = null, reconnecting = false,
                message = if (source == null) CONFIGURATION_MESSAGE else LOCATION_MESSAGE)
            return
        }
        // Apply the exact radius on every fix, including movement smaller than the fetch threshold.
        val cache = catalogCache.takeUnless { state.value.demo }
        val nearby = CctvCatalog.nearby(cache?.cameras ?: state.value.cameras, point)
        val selected = state.value.selected?.takeIf { selected -> nearby.any { it.id == selected.id } }
        val restoredCache = cache != null && networkOnline && operation?.isActive != true &&
            state.value.status in listOf(CctvStatus.IDLE, CctvStatus.NO_LOCATION)
        mutableState.value = state.value.copy(cameras = nearby, selected = selected,
            status = if (restoredCache) CctvStatus.READY else state.value.status,
            lastUpdatedMs = if (restoredCache) cache?.fetchedAt else state.value.lastUpdatedMs,
            reconnecting = state.value.reconnecting && selected != null)
        if (!state.value.demo) maybeRefresh()
    }

    fun openPanel(cameraId: String? = null) {
        if (!visible || !browsingAllowed) return
        val cameras = state.value.cameras
        val first = cameraId?.let { id -> cameras.find { it.id == id } }
        mutableState.value = state.value.copy(panelOpen = true, selected = null, reconnecting = false,
            cameras = if (first == null) cameras else listOf(first) + cameras.filter { it.id != first.id })
    }

    fun closePanel() { mutableState.value = state.value.copy(panelOpen = false, selected = null, reconnecting = false) }

    fun selectCamera(id: String) {
        if (!visible || !browsingAllowed || !state.value.panelOpen) return
        val camera = state.value.cameras.find { it.id == id } ?: return
        mutableState.value = state.value.copy(selected = camera, message = null,
            playbackGeneration = ++playbackGeneration,
            reconnecting = !camera.isDemo)
        // Marker data can outlive a signed video URL. Wait for fresh playback data
        // on actual viewing intent; an already-running fetch is shared.
        if (!camera.isDemo) maybeRefresh(force = true, recovery = true)
    }

    fun dismissVideo() { mutableState.value = state.value.copy(selected = null, reconnecting = false) }

    fun refresh() {
        if (!visible || !browsingAllowed) return
        cancelRequest()
        lastFetchOrigin = null
        quotaExceeded = false
        mutableState.value = state.value.copy(demo = false, cameras = emptyList(), selected = null, reconnecting = false)
        maybeRefresh(force = true)
    }

    fun showDemoCameras() {
        if (!demoAllowed || !visible || !browsingAllowed) return
        val point = origin
        if (point == null) {
            mutableState.value = state.value.copy(status = CctvStatus.NO_LOCATION, message = LOCATION_MESSAGE,
                cameras = emptyList(), selected = null, demo = false, reconnecting = false)
            return
        }
        cancelRequest()
        val cameras = listOf(
            CctvCamera("demo-north", "북측 교차로 · 예시", GeoPoint((point.latitude + .003).coerceAtMost(90.0), point.longitude), null, true),
            CctvCamera("demo-east", "동측 진입로 · 예시", GeoPoint(point.latitude, (point.longitude + .006).coerceAtMost(180.0)), null, true),
            CctvCamera("demo-west", "서측 분기점 · 예시", GeoPoint(point.latitude, (point.longitude - .009).coerceAtLeast(-180.0)), null, true),
        )
        mutableState.value = state.value.copy(status = CctvStatus.READY, cameras = CctvCatalog.nearby(cameras, point),
            message = "예시 카메라 위치예요. 실제 CCTV 영상은 연결되지 않았어요.", selected = null,
            lastUpdatedMs = null, demo = true, reconnecting = false)
    }

    fun onStreamError(cameraId: String? = state.value.selected?.id,
                      playbackGeneration: Long = state.value.playbackGeneration) {
        if (!visible || !browsingAllowed || !networkOnline || state.value.reconnecting || cameraId == null ||
            state.value.selected?.id != cameraId || state.value.playbackGeneration != playbackGeneration) return
        // Transport errors can precede Android's offline signal. The open dialog keeps
        // the user's camera intent while its player shows an error and releases resources.
        mutableState.value = state.value.copy(status = CctvStatus.ERROR, reconnecting = false,
            message = "CCTV 영상을 불러오지 못했어요. 새로고침 후 다시 선택해 주세요.")
    }

    private fun canResumeSelection() = visible && browsingAllowed && origin != null && state.value.panelOpen &&
        !state.value.demo && state.value.selected?.isDemo == false

    private fun showOffline() {
        mutableState.value = state.value.copy(status = CctvStatus.OFFLINE, reconnecting = canResumeSelection(),
            message = "인터넷 연결을 기다리고 있어요. 연결되면 자동으로 다시 불러와요.")
    }

    private fun showQuotaExceeded() {
        mutableState.value = state.value.copy(status = CctvStatus.QUOTA_EXCEEDED,
            cameras = emptyList(), selected = null, demo = false, reconnecting = false,
            lastUpdatedMs = null, message = QUOTA_MESSAGE)
    }

    private fun maybeRefresh(force: Boolean = false, recovery: Boolean = false, retryIndex: Int = 0) {
        if (!visible || state.value.demo) return
        if (quotaExceeded) { showQuotaExceeded(); return }
        val activeSource = source ?: run {
            mutableState.value = state.value.copy(status = CctvStatus.NEEDS_CONFIGURATION, cameras = emptyList(),
                selected = null, reconnecting = false, lastUpdatedMs = null, message = CONFIGURATION_MESSAGE)
            return
        }
        val point = origin ?: run {
            mutableState.value = state.value.copy(status = CctvStatus.NO_LOCATION, message = LOCATION_MESSAGE)
            return
        }
        if (!networkOnline) { showOffline(); return }
        val previousOrigin = lastFetchOrigin
        val moved = previousOrigin != null && distanceMeters(previousOrigin, point) >= 1_000.0
        // Static map markers do not need polling. Refresh an open list periodically,
        // but never interrupt a playing video merely because the catalog age changed.
        val listDue = state.value.panelOpen && state.value.selected == null && nowMs() - lastFetchAt >= 300_000L
        val due = previousOrigin == null || listDue || nowMs() < lastFetchAt
        val recovering = recovery || networkRefreshPending || state.value.reconnecting || retry?.isActive == true
        if (retry?.isActive == true && !force && !moved) return
        if (!force && !recovering && !moved && !due) return
        if (operation?.isActive == true && !moved) return
        cancelRequest()
        val token = generation
        networkRefreshPending = false
        lastFetchOrigin = point; lastFetchAt = nowMs()
        val selected = state.value.selected?.takeIf { recovering && canResumeSelection() }
        mutableState.value = state.value.copy(status = CctvStatus.LOADING, selected = selected,
            reconnecting = selected != null, message = null)
        operation = scope.launch {
            try {
                val result = fetchMutex.withLock { activeSource.fetchNearby(point) }
                val currentOrigin = origin
                if (token != generation || !visible || !networkOnline || currentOrigin == null || state.value.demo) return@launch
                val cached = CachedCatalog(CctvCatalog.nearby(result, point), point, nowMs())
                catalogCache = cached
                val nearby = CctvCatalog.nearby(cached.cameras, currentOrigin)
                // Read current intent after the request: closing or changing cameras wins over recovery.
                val resumed = state.value.selected?.takeIf { canResumeSelection() }
                    ?.let { previous -> nearby.find { it.id == previous.id && !it.isDemo } }
                mutableState.value = state.value.copy(status = CctvStatus.READY, cameras = nearby, selected = resumed,
                    reconnecting = false, playbackGeneration = if (resumed != null) ++playbackGeneration else state.value.playbackGeneration,
                    lastUpdatedMs = cached.fetchedAt, message = if (nearby.isEmpty()) "주변 10km 내 고속도로 CCTV가 없어요." else null)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (token != generation || !visible || !networkOnline || origin == null || state.value.demo) return@launch
                if (error is CctvFailure && error.kind == CctvFailureKind.MONTHLY_QUOTA_EXCEEDED) {
                    quotaExceeded = true
                    networkRefreshPending = false
                    showQuotaExceeded()
                } else if (recovering && retryIndex < RECOVERY_DELAYS_MS.size) {
                    mutableState.value = state.value.copy(status = CctvStatus.LOADING,
                        reconnecting = canResumeSelection(), message = "CCTV 연결을 다시 시도하고 있어요.")
                    retry = scope.launch {
                        delay(RECOVERY_DELAYS_MS[retryIndex])
                        if (token == generation && visible && networkOnline && origin != null && !state.value.demo) {
                            retry = null
                            maybeRefresh(force = true, recovery = true, retryIndex = retryIndex + 1)
                        }
                    }
                } else {
                    mutableState.value = state.value.copy(status = CctvStatus.ERROR,
                        cameras = emptyList(), selected = null, reconnecting = false, lastUpdatedMs = null,
                        message = "CCTV 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.")
                }
            } finally {
                if (token == generation) operation = null
            }
        }
    }

    private fun cancelRequest() {
        generation++
        operation?.cancel(); operation = null
        retry?.cancel(); retry = null
    }

    companion object {
        private val RECOVERY_DELAYS_MS = longArrayOf(1_000, 3_000, 8_000)
        private const val CONFIGURATION_MESSAGE = "CCTV 서비스 연결을 준비하고 있어요."
        private const val LOCATION_MESSAGE = "현재 위치를 확인한 뒤 주변 CCTV를 볼 수 있어요."
        private const val QUOTA_MESSAGE = "이번 달 CCTV 조회 한도를 초과했어요. 국가교통정보센터에서 인증키의 월 호출 한도를 늘린 뒤 다시 확인해 주세요."
    }
}
