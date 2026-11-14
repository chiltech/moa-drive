package com.moagauge.pleos.navigation

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moagauge.pleos.navigation.kakao.KakaoNavigationProvider
import com.moagauge.pleos.network.NetworkMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

enum class DrivePhase { IDLE, SEARCHING, ROUTING, STARTING, GUIDING }
data class DriveState(
    val ready: Boolean = false,
    val initializing: Boolean = false,
    val internetAvailable: Boolean = false,
    val phase: DrivePhase = DrivePhase.IDLE,
    val results: List<NavigationDestination> = emptyList(),
    val searchQuery: String = "",
    val route: NavigationRoute? = null,
    val routeDestination: NavigationDestination? = null,
    val routePreference: NavigationRoutePreference = NavigationRoutePreference.RECOMMENDED,
    val routeAlternatives: List<NavigationRouteAlternative> = emptyList(),
    val message: String? = null,
    val provider: NavigationProviderId = NavigationProviderId.KAKAO,
    val suggestedDestinationPending: Boolean = false,
)

class DriveViewModel(application: Application) : AndroidViewModel(application) {
    val kakao = KakaoNavigationProvider(application)
    // Only the registry knows concrete SDKs; common state and operations stay provider neutral.
    private val providers: Map<NavigationProviderId, NavigationProvider> = mapOf(kakao.id to kakao)
    private val preferences = application.getSharedPreferences("navigation", 0)
    private val places = NavigationPlacesStore(application)
    val placesState = places.state
    private val session = RouteSessionPolicy()
    private val networkMonitor = NetworkMonitor(application)
    private val mutableState = MutableStateFlow(DriveState(internetAvailable = networkMonitor.isOnline()))
    val state = mutableState.asStateFlow()
    val location = NavigationLocation(application)
    private val gate = PleosNavigationGate(application) {
        stopGuidance("다른 내비게이션이 활성화되어 안내를 종료했어요.")
    }
    val policy = gate.policy
    val progress get() = engine.progress
    private val engine get() = providers.getValue(session.selectedProvider)
    private var operation: Job? = null
    private val initializationRecovery = NavigationInitializationRecovery(
        scope = viewModelScope,
        initialize = { engine.initialize() },
        initialOnline = mutableState.value.internetAvailable,
    )
    private val suggestedRequests = SuggestedDestinationRequests()
    private var suggestedOperation: Job? = null
    private var suggestedTimeout: Job? = null
    private var visible = false
    private var searchRevision = 0L
    // Comparison identity is independent of card selection: sibling results remain valid.
    private var routeRevision = 0L

    init {
        val saved = NavigationProviderId.entries.find { it.name == preferences.getString("provider", null) }
        if (saved != null && providers.containsKey(saved)) session.select(saved)
        session.selectRoutePreference(NavigationRoutePreference.fromStored(preferences.getString("route_preference", null)))
        mutableState.update { it.copy(provider = session.selectedProvider, routePreference = session.routePreference) }
        viewModelScope.launch {
            initializationRecovery.state.collect { connection ->
                mutableState.update { current ->
                    current.copy(ready = connection.ready, initializing = connection.initializing,
                        internetAvailable = connection.online,
                        message = when {
                            // Network changes must not replace route/guidance messages or state.
                            current.ready -> current.message
                            connection.ready -> null
                            !connection.online -> "인터넷 연결을 기다리고 있어요. 연결되면 지도를 자동으로 준비해요."
                            connection.failure != null -> failure(connection.failure, "카카오내비에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.")
                            connection.initializing -> null
                            else -> current.message
                        })
                }
            }
        }
        viewModelScope.launch {
            networkMonitor.online.collect { online ->
                if (!online) initializationRecovery.setOnline(false)
                initializationRecovery.updateAvailability(visible, location.hasPrecisePermission())
                if (online) initializationRecovery.setOnline(true)
            }
        }
        viewModelScope.launch {
            policy.collect { restrictions ->
                if (!restrictions.searchAllowed) cancelSearch()
                if (!restrictions.searchAllowed && (state.value.suggestedDestinationPending ||
                    state.value.phase in listOf(DrivePhase.SEARCHING, DrivePhase.ROUTING))) {
                    clearSuggestedDestination()
                    operation?.cancel()
                    retireRouteComparison()
                    session.invalidate()
                    mutableState.update { it.copy(phase = DrivePhase.IDLE, results = emptyList(), message = "검색과 경로 설정은 주차 후 이용해 주세요.") }
                }
            }
        }
    }

    fun setVisible(active: Boolean) {
        if (visible == active) {
            initializationRecovery.updateAvailability(active, location.hasPrecisePermission())
            if (!active) cancelSuggestedDestination()
            return
        }
        visible = active
        session.setForeground(active)
        if (active) {
            engine.foreground(true)
            location.start()
            initializationRecovery.updateAvailability(true, location.hasPrecisePermission())
        } else {
            initializationRecovery.updateAvailability(false, location.hasPrecisePermission())
            cancelSearch()
            stopGuidance(if (state.value.phase in listOf(DrivePhase.STARTING, DrivePhase.GUIDING)) "화면을 벗어나 안내를 종료했어요." else null)
            location.stop()
            engine.foreground(false)
        }
    }

    fun initialize() {
        val permissionGranted = location.hasPrecisePermission()
        initializationRecovery.updateAvailability(visible, permissionGranted)
        if (visible && permissionGranted) location.start()
        initializationRecovery.retry()
    }

    fun search(query: String) {
        if (!visible || !state.value.ready || !policy.value.searchAllowed || query.isBlank()) return
        val acceptedQuery = query.trim().take(100)
        stopGuidance()
        engine.clearPreparedRoutes()
        val token = session.token()
        val revision = ++searchRevision
        val provider = engine
        places.addRecentQuery(acceptedQuery)
        mutableState.update { it.copy(phase = DrivePhase.SEARCHING, searchQuery = acceptedQuery,
            message = null, route = null, routeDestination = null, routeAlternatives = emptyList(), results = emptyList()) }
        operation = viewModelScope.launch {
            try {
                val result = provider.search(acceptedQuery, location.currentPoint())
                if (acceptsSearch(token, revision, acceptedQuery)) mutableState.update {
                    it.copy(phase = DrivePhase.IDLE, results = result, message = if (result.isEmpty()) "검색 결과가 없어요." else null)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (acceptsSearch(token, revision, acceptedQuery)) mutableState.update { it.copy(phase = DrivePhase.IDLE, message = failure(error, "목적지를 검색하지 못했어요.")) } }
        }
    }

    /** Editing invalidates the request even if a provider delivers a late, uncancellable reply. */
    fun updateSearchQuery(query: String) {
        if (!visible || !policy.value.searchAllowed || state.value.phase in listOf(DrivePhase.ROUTING, DrivePhase.STARTING, DrivePhase.GUIDING)) return
        val next = query.take(100)
        if (next == state.value.searchQuery) return
        cancelPendingSearch()
        mutableState.update { it.copy(searchQuery = next, results = emptyList(), message = null) }
    }

    /** Dismissal clears only search UI/request state, never an already prepared route or guidance. */
    fun cancelSearch() {
        cancelPendingSearch()
        mutableState.update { it.copy(searchQuery = "", results = emptyList(),
            message = if (it.phase == DrivePhase.IDLE) null else it.message) }
    }

    private fun cancelPendingSearch() {
        searchRevision++
        if (state.value.phase == DrivePhase.SEARCHING && !state.value.suggestedDestinationPending) {
            operation?.cancel(); operation = null
            session.invalidate()
            mutableState.update { it.copy(phase = DrivePhase.IDLE) }
        }
    }

    private fun acceptsSearch(token: Long, revision: Long, query: String) =
        acceptsOperation(token) && searchRevision == revision && state.value.searchQuery == query

    fun savePlace(slot: SavedPlaceSlot, destination: NavigationDestination) {
        if (!visible || !policy.value.searchAllowed) return
        places.savePlace(slot, destination)
        showMessage("${slot.label} 위치를 저장했어요.")
    }

    fun removePlace(slot: SavedPlaceSlot) {
        if (!visible || !policy.value.searchAllowed) return
        places.removePlace(slot)
        showMessage("${slot.label} 위치를 삭제했어요.")
    }

    fun removeRecentQuery(query: String) {
        if (visible && policy.value.searchAllowed) places.removeRecentQuery(query)
    }

    fun clearRecentQueries() {
        if (visible && policy.value.searchAllowed) places.clearRecentQueries()
    }

    fun prepare(destination: NavigationDestination) {
        if (!visible || !policy.value.searchAllowed || !state.value.ready) return
        stopGuidance()
        engine.clearPreparedRoutes()
        val preference = session.routePreference
        val revision = ++routeRevision
        mutableState.update { it.copy(route = null, routeDestination = destination, routePreference = preference,
            routeAlternatives = pendingRouteAlternatives(), phase = DrivePhase.ROUTING,
            message = null, searchQuery = "", results = emptyList()) }
        val origin = location.currentPoint()
        if (origin == null) {
            val message = "현재 위치를 확인한 뒤 경로를 계산할 수 있어요. GPS 수신 상태를 확인해 주세요."
            retireRouteComparison(message)
            mutableState.update { it.copy(phase = DrivePhase.IDLE, message = message) }
            return
        }
        val provider = engine
        operation = viewModelScope.launch {
            try {
                loadRouteAlternatives(provider, origin, destination, preference).collect { result ->
                    if (acceptsComparison(revision, destination)) mutableState.update { current ->
                        val alternatives = current.routeAlternatives.map { if (it.preference == result.preference) result else it }
                        current.copy(routeAlternatives = alternatives,
                            route = alternatives.firstOrNull { it.preference == current.routePreference }?.route,
                            phase = if (alternatives.any { it.loading }) DrivePhase.ROUTING else DrivePhase.IDLE)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (acceptsComparison(revision, destination)) {
                    val message = failure(error, "경로를 계산하지 못했어요.")
                    retireRouteComparison(message)
                    mutableState.update { it.copy(phase = DrivePhase.IDLE, message = message) }
                }
            }
        }
    }

    private fun acceptsComparison(revision: Long, destination: NavigationDestination) =
        visible && policy.value.searchAllowed && revision == routeRevision &&
            state.value.routeDestination == destination && state.value.phase in listOf(DrivePhase.IDLE, DrivePhase.ROUTING)

    /** Stop pending rows without dropping completed native routes used by preview/guidance. */
    private fun retireRouteComparison(message: String = "경로 계산이 중단됐어요. 다시 시도해 주세요.") {
        routeRevision++
        mutableState.update { current -> current.copy(routeAlternatives = current.routeAlternatives.map {
            if (it.loading) it.copy(loading = false, message = message) else it
        }) }
    }

    fun selectRoutePreference(preference: NavigationRoutePreference) {
        val current = state.value
        if (!visible || !policy.value.searchAllowed || !current.ready || current.suggestedDestinationPending ||
            current.phase in listOf(DrivePhase.STARTING, DrivePhase.GUIDING) || preference == session.routePreference) return
        session.selectRoutePreference(preference)
        preferences.edit().putString("route_preference", preference.name).apply()
        mutableState.update { it.copy(routePreference = preference,
            route = it.routeAlternatives.firstOrNull { option -> option.preference == preference }?.route,
            message = null) }
        if (com.moagauge.pleos.BuildConfig.DEBUG) android.util.Log.d("PleosRouteOptions", "selected cachedPreference=$preference ready=${state.value.route != null}")
    }

    fun retryRoute(preference: NavigationRoutePreference = state.value.routePreference) {
        if (state.value.phase != DrivePhase.IDLE || state.value.suggestedDestinationPending) return
        selectRoutePreference(preference)
        state.value.routeDestination?.let(::prepare)
    }

    /** Called before the Drive screen opens; only prepares a route, never starts guidance. */
    fun requestSuggestedDestination(query: String) {
        requestSuggestedDestination(query, null)
    }

    /** Preserve a budget candidate's exact entrance coordinate instead of searching its name again. */
    fun requestDestination(destination: NavigationDestination) {
        requestSuggestedDestination(destination.name, destination)
    }

    private fun requestSuggestedDestination(query: String, exactDestination: NavigationDestination?) {
        val trimmed = query.trim().take(100)
        if (trimmed.isBlank()) return
        if (!policy.value.searchAllowed) {
            showMessage("경로는 주차 후 확인해 주세요.")
            return
        }
        if (exactDestination == null && suggestedRequests.active?.query == trimmed) return
        stopGuidance()
        val request = suggestedRequests.begin(trimmed, SystemClock.elapsedRealtime())
        val provider = engine
        engine.clearPreparedRoutes()
        mutableState.update { it.copy(suggestedDestinationPending = true, route = null, routeDestination = null, routeAlternatives = emptyList(), results = emptyList(),
            phase = DrivePhase.IDLE, message = "목적지의 경로를 준비하고 있어요.") }
        suggestedTimeout = viewModelScope.launch {
            delay(suggestedRequests.timeoutMs)
            if (suggestedRequests.isCurrent(request)) {
                finishSuggestedFailure(request, "경로 준비 시간이 초과됐어요. 위치·연결 상태를 확인하고 다시 선택해 주세요.")
                suggestedOperation?.cancel()
                suggestedOperation = null
            }
        }
        suggestedOperation = viewModelScope.launch {
            try {
                withTimeout(suggestedRequests.timeoutMs) {
                    var initializationRequested = false
                    while (true) {
                        if (!suggestedRequests.accepts(request, SystemClock.elapsedRealtime())) return@withTimeout
                        if (!policy.value.searchAllowed) throw NavigationFailure("경로는 주차 후 확인해 주세요.")
                        if (visible && location.hasPrecisePermission() && !state.value.ready && !initializationRequested) {
                            initializationRequested = true
                            initialize()
                        }
                        val waiting = when {
                            !visible -> "내비게이션 화면을 열고 있어요."
                            !location.hasPrecisePermission() -> "정확한 위치 접근을 허용하면 경로를 준비해요."
                            !state.value.ready -> "내비게이션 연결을 기다리고 있어요."
                            location.currentPoint() == null -> "현재 위치를 확인하고 있어요. GPS 수신 상태를 확인해 주세요."
                            else -> null
                        }
                        if (waiting == null) break
                        mutableState.update { it.copy(message = waiting) }
                        delay(300)
                    }
                    mutableState.update { it.copy(phase = DrivePhase.SEARCHING, message = "목적지 ‘$trimmed’을 찾고 있어요.") }
                    val results = if (exactDestination == null) provider.search(trimmed, location.currentPoint()) else emptyList()
                    if (!acceptsSuggested(request)) return@withTimeout
                    val destination = exactDestination ?: chooseSuggestedDestination(trimmed, results)
                        ?: throw NavigationFailure("목적지를 찾지 못했어요. 목적지 검색에서 다시 확인해 주세요.")
                    suggestedRequests.finish(request)
                    suggestedTimeout?.cancel(); suggestedTimeout = null
                    // Hand off to the same comparison flow as a search result. Detach this
                    // coroutine first so prepare/stopGuidance cannot cancel its own handoff.
                    suggestedOperation = null
                    mutableState.update { it.copy(phase = DrivePhase.IDLE, suggestedDestinationPending = false, message = null) }
                    prepare(destination)
                }
                // A provider that does not cooperate with coroutine cancellation must not leave a pending UI behind.
                if (suggestedRequests.isCurrent(request)) finishSuggestedFailure(request, "경로 준비 시간이 초과됐어요. 위치·연결 상태를 확인하고 다시 선택해 주세요.")
            } catch (_: TimeoutCancellationException) {
                finishSuggestedFailure(request, "경로 준비 시간이 초과됐어요. 위치·연결 상태를 확인하고 다시 선택해 주세요.")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                finishSuggestedFailure(request, failure(error, "목적지의 경로를 준비하지 못했어요. 목적지 검색으로 다시 확인해 주세요."))
            }
        }
    }

    fun cancelSuggestedDestination() {
        if (!state.value.suggestedDestinationPending) return
        clearSuggestedDestination()
        showMessage("경로 준비를 취소했어요.")
    }

    private fun clearSuggestedDestination() {
        suggestedRequests.cancel()
        suggestedOperation?.cancel()
        suggestedOperation = null
        suggestedTimeout?.cancel()
        suggestedTimeout = null
        if (state.value.suggestedDestinationPending) mutableState.update {
            it.copy(suggestedDestinationPending = false, phase = DrivePhase.IDLE)
        }
    }

    private fun acceptsSuggested(request: SuggestedDestinationRequest) = visible && policy.value.searchAllowed &&
        suggestedRequests.accepts(request, SystemClock.elapsedRealtime())

    private fun finishSuggestedFailure(request: SuggestedDestinationRequest, message: String) {
        if (!suggestedRequests.isCurrent(request)) return
        suggestedRequests.finish(request)
        suggestedTimeout?.cancel(); suggestedTimeout = null
        mutableState.update { it.copy(suggestedDestinationPending = false, phase = DrivePhase.IDLE,
            route = null, results = emptyList(), message = message) }
    }

    private fun acceptsOperation(token: Long) = visible && policy.value.searchAllowed && session.token() == token

    fun startGuidance(preference: NavigationRoutePreference = state.value.routePreference) {
        val current = state.value
        val route = current.routeAlternatives.firstOrNull { it.preference == preference }?.route ?: return
        if (!visible || current.suggestedDestinationPending ||
            current.phase !in listOf(DrivePhase.IDLE, DrivePhase.ROUTING) || !policy.value.searchAllowed ||
            route.preference != preference || route.provider != session.selectedProvider || route.destination != current.routeDestination) return
        if (location.currentPoint() == null) { showMessage("현재 위치를 다시 확인한 후 안내를 시작해 주세요."); return }
        selectRoutePreference(preference)
        operation?.cancel(); operation = null
        retireRouteComparison()
        session.invalidate()
        val token = session.token()
        operation = viewModelScope.launch {
            mutableState.update { it.copy(phase = DrivePhase.STARTING, message = "카카오내비로 안내를 시작합니다.") }
            try {
                if (!gate.acquire() || !session.grantFocus(token)) throw NavigationFailure("차량의 내비게이션 사용 권한을 얻지 못했어요. 다시 시도해 주세요.")
                if (!session.mayGuide(token)) return@launch
                engine.start(route) { viewModelScope.launch { stopGuidance("경로 안내를 종료했어요.") } }
                if (session.mayGuide(token)) mutableState.update { it.copy(phase = DrivePhase.GUIDING, message = null) }
                else engine.stop()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                engine.stop(); gate.release(); session.invalidate()
                mutableState.update { it.copy(phase = DrivePhase.IDLE, message = failure(error, "길 안내를 시작하지 못했어요.")) }
            }
        }
    }

    fun stopGuidance(message: String? = null) {
        searchRevision++
        clearSuggestedDestination()
        operation?.cancel(); operation = null
        retireRouteComparison()
        session.invalidate()
        engine.stop()
        gate.release()
        mutableState.update { it.copy(phase = DrivePhase.IDLE, message = message ?: it.message) }
    }

    fun clearRoute() {
        stopGuidance()
        engine.clearPreparedRoutes()
        mutableState.update { it.copy(route = null, routeDestination = null, routeAlternatives = emptyList(), results = emptyList(), message = null) }
    }
    fun showMessage(message: String) { mutableState.update { it.copy(message = message) } }
    fun dismissMessage() { mutableState.update { it.copy(message = null) } }
    fun surfaceError(message: String) { stopGuidance(message) }

    fun selectProvider(id: NavigationProviderId) {
        if (!policy.value.settingsAllowed || !providers.containsKey(id)) return
        if (id == session.selectedProvider) return
        stopGuidance("내비게이션을 ${id.label}(으)로 변경했어요.")
        if (session.select(id)) {
            preferences.edit().putString("provider", id.name).apply()
            mutableState.update { DriveState(provider = id, routePreference = session.routePreference,
                internetAvailable = initializationRecovery.state.value.online) }
            initializationRecovery.resetProvider()
            initialize()
        }
    }

    private fun failure(error: Exception, fallback: String) = (error as? NavigationFailure)?.userMessage ?: fallback
    override fun onCleared() {
        stopGuidance(); location.stop(); gate.close(); engine.foreground(false)
    }
}
