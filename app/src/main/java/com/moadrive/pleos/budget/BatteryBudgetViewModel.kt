package com.moadrive.pleos.budget

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moadrive.pleos.navigation.GeoPoint
import com.moadrive.pleos.navigation.NavigationDestination
import com.moadrive.pleos.navigation.NavigationFailure
import com.moadrive.pleos.navigation.NavigationLocation
import com.moadrive.pleos.navigation.NavigationRoutePreference
import com.moadrive.pleos.navigation.chooseSuggestedDestination
import com.moadrive.pleos.navigation.kakao.KakaoNavigationProvider
import com.moadrive.pleos.passport.CityBoundaryPoint
import com.moadrive.pleos.passport.PassportCity
import com.moadrive.pleos.passport.PassportCityAssets
import com.moadrive.pleos.passport.PassportVisit
import com.moadrive.pleos.vehicle.VehicleTelemetry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Owns only query work. It never changes Kakao's global guidance lifecycle or vehicle state. */
class BatteryBudgetViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(BatteryBudgetState())
    val state = mutableState.asStateFlow()
    private val location = NavigationLocation(application)
    private var telemetry = VehicleTelemetry()
    private var visits = emptyList<PassportVisit>()
    private var visible = false
    private var browsingAllowed = false
    private var generation = 0L
    private var operation: Job? = null
    private var ticker: Job? = null
    private var snapshot: BatteryBudgetSnapshot? = null
    private var unavailableReason: String? = null
    private var preferencesBeforePreview: BatteryBudgetPreferences? = null

    init {
        viewModelScope.launch { location.location.collect { refreshState() } }
    }

    fun updateVehicle(value: VehicleTelemetry) { telemetry = value; refreshState() }

    fun updateVisits(value: List<PassportVisit>) {
        if (visits == value) return
        visits = value.toList()
        if (!state.value.isPreview && (state.value.loading || state.value.candidates.isNotEmpty()))
            invalidate("방문 기록이 바뀌었어요. 새 코스를 계산해 주세요.")
        refreshState()
    }

    fun setVisible(value: Boolean) {
        if (visible == value) { if (value) refreshLocation(); return }
        visible = value
        ticker?.cancel(); ticker = null
        if (value) {
            refreshLocation()
            ticker = viewModelScope.launch { while (isActive) { delay(1_000L); refreshState() } }
        } else {
            location.stop()
            invalidate("다시 열었을 때 현재 위치로 코스를 계산해 주세요.")
            restorePreviewPreferences()
        }
        refreshState()
    }

    fun setBrowsingAllowed(value: Boolean) {
        browsingAllowed = value
        if (!value) {
            location.stop()
            invalidate("코스 설정은 주차 후 이용해 주세요.")
            restorePreviewPreferences()
        } else if (visible) refreshLocation()
        refreshState()
    }

    fun refreshLocation() {
        if (visible && browsingAllowed) location.start()
        refreshState()
    }

    fun updatePreferences(value: BatteryBudgetPreferences) {
        if (!browsingAllowed || !visible) return
        val normalized = BatteryBudgetPreferences(
            value.totalMinutes.takeIf { it in listOf(60, 90, 120, 180) } ?: 90,
            value.reservePercent.takeIf { it in listOf(20, 30, 40, 50) } ?: 30,
            20,
        )
        if (normalized == state.value.preferences) return
        val wasPreview = state.value.isPreview
        invalidate("조건이 바뀌었어요. 왕복 코스를 다시 계산해 주세요.")
        mutableState.update { it.copy(preferences = normalized) }
        if (wasPreview) preview() else refreshState()
    }

    fun calculate() {
        refreshState()
        if (!state.value.canCalculate) return
        val vehicle = vehicle() ?: return
        val origin = location.currentPoint() ?: return
        invalidate(null)
        val request = ++generation
        val inputs = BatteryBudgetSnapshot(state.value.preferences, vehicle, origin, SystemClock.elapsedRealtime())
        snapshot = inputs
        mutableState.update { it.copy(loading = true, canCalculate = false, isPreview = false,
            candidates = emptyList(), failedCandidates = 0, calculatedAtMs = null,
            message = "가까운 미방문 도시의 왕복 경로를 확인하고 있어요.") }
        operation = viewModelScope.launch {
            // This provider owns an isolated route cache. foreground(false)/stop() would stop
            // GLOBAL guidance, so this read-only query owner never calls either method.
            val provider = KakaoNavigationProvider(getApplication())
            try {
                withTimeout(240_000L) {
                    val atlas = PassportCityAssets.load(getApplication())
                    val cities = withContext(Dispatchers.Default) {
                        val excluded = atlas.visited(visits).map { it.city.id }.toSet() +
                            listOfNotNull(atlas.resolve(origin.latitude, origin.longitude)?.id)
                        atlas.cities.filter { it.kind == "city" && it.id !in excluded }
                            .sortedWith(compareBy<PassportCity> {
                                BatteryBudgetCalculator.distanceMeters(origin, GeoPoint(it.center.latitude, it.center.longitude))
                            }.thenBy { it.id }).take(3)
                    }
                    if (!accepts(request, inputs)) return@withTimeout
                    if (cities.isEmpty()) {
                        mutableState.update { it.copy(loading = false,
                            message = "아직 방문하지 않은 추천 도시가 없어요.") }
                        return@withTimeout
                    }
                    provider.initialize()
                    val found = mutableListOf<BatteryBudgetCandidate>()
                    var failures = 0
                    for (city in cities) {
                        if (!accepts(request, inputs)) return@withTimeout
                        mutableState.update { it.copy(message = "${city.name} 왕복 경로를 확인하고 있어요.") }
                        try {
                            val candidate = withTimeout(65_000L) {
                                val choices = provider.search(city.destinationQuery, origin)
                                val hallName = city.name.replace(" ", "") + "청"
                                val cityHalls = choices.filter { result ->
                                    result.name.replace(" ", "").startsWith(hallName) &&
                                        city.polygons.any { it.contains(CityBoundaryPoint(result.point.latitude, result.point.longitude)) }
                                }
                                val destination = chooseSuggestedDestination(hallName, cityHalls)
                                    ?: throw NavigationFailure("도시의 시청 위치를 확인하지 못했어요.")
                                provider.clearPreparedRoutes()
                                val outgoing = provider.prepareRoute(origin, destination, NavigationRoutePreference.RECOMMENDED)
                                val returnPoint = NavigationDestination("budget-return-$request", "출발 위치", "이 코스를 계산한 출발 위치", origin)
                                val returning = provider.prepareRoute(destination.point, returnPoint, NavigationRoutePreference.RECOMMENDED)
                                val estimate = BatteryBudgetCalculator.estimate(vehicle, inputs.preferences,
                                    BatteryBudgetLeg(outgoing.distanceMeters, outgoing.durationSeconds),
                                    BatteryBudgetLeg(returning.distanceMeters, returning.durationSeconds))
                                    ?: throw NavigationFailure("왕복 경로의 거리와 시간을 확인하지 못했어요.")
                                BatteryBudgetCandidate(city.name, city.province, destination, estimate)
                            }
                            if (!accepts(request, inputs)) return@withTimeout
                            found += candidate
                        } catch (_: TimeoutCancellationException) {
                            if (!accepts(request, inputs)) return@withTimeout
                            failures++
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) {
                            if (!accepts(request, inputs)) return@withTimeout
                            failures++
                        } finally { provider.clearPreparedRoutes() }
                        if (!accepts(request, inputs)) return@withTimeout
                        mutableState.update { it.copy(candidates = found.toList(), failedCandidates = failures) }
                    }
                    if (!accepts(request, inputs)) return@withTimeout
                    val qualifying = found.count { it.estimate.feasible }
                    val message = when {
                        found.isEmpty() -> "왕복 경로를 가져오지 못했어요. 인터넷 연결을 확인하고 다시 계산해 주세요."
                        qualifying == 0 -> "조건에 맞는 코스가 없어요. 시간을 늘리거나 귀가 잔량 조건을 조정해 보세요."
                        else -> "${qualifying}개 코스가 현재 시간과 배터리 조건에 맞아요."
                    } + if (failures > 0 && found.isNotEmpty()) " ${failures}곳은 경로를 확인하지 못했어요." else ""
                    mutableState.update { it.copy(loading = false, candidates = found.sortedByDescending { c -> c.estimate.feasible },
                        message = message, calculatedAtMs = if (found.isEmpty()) null else System.currentTimeMillis(),
                        failedCandidates = failures) }
                }
            } catch (_: TimeoutCancellationException) {
                if (request == generation) invalidate("경로 준비 시간이 초과됐어요. 연결 상태를 확인하고 다시 계산해 주세요.")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (request == generation) invalidate((error as? NavigationFailure)?.userMessage
                    ?: "코스를 준비하지 못했어요. 연결 상태를 확인하고 다시 계산해 주세요.")
            } finally {
                provider.clearPreparedRoutes()
                if (request == generation) { operation = null; refreshState() }
            }
        }
    }

    fun preview() {
        if (!visible || !browsingAllowed) return
        if (!state.value.isPreview) preferencesBeforePreview = state.value.preferences
        invalidate(null)
        val sampleVehicle = BatteryBudgetVehicle(80, 320)
        val sample = listOf(
            Triple("구리시", BatteryBudgetLeg(22_400, 1_500), BatteryBudgetLeg(25_100, 1_650)),
            Triple("과천시", BatteryBudgetLeg(28_200, 1_800), BatteryBudgetLeg(30_800, 2_100)),
            Triple("고양시", BatteryBudgetLeg(48_500, 2_400), BatteryBudgetLeg(52_700, 2_700)),
        ).mapIndexed { index, (name, outgoing, returning) ->
            BatteryBudgetCandidate(name, "경기도", NavigationDestination("budget-preview-$index", "${name}청", "체험용 목적지",
                GeoPoint(37.5665, 126.9780)), BatteryBudgetCalculator.estimate(sampleVehicle, state.value.preferences, outgoing, returning)!!)
        }
        mutableState.update { it.copy(isPreview = true, batteryPercent = 80, rangeKm = 320,
            canCalculate = false, loading = false, candidates = sample,
            message = "기능 체험 · 배터리 80%, 주행가능거리 320km를 가정한 계산이에요. 실제 차량·경로 정보와 다릅니다.",
            calculatedAtMs = null, failedCandidates = 0) }
    }

    fun exitPreview() {
        if (!visible || !browsingAllowed) return
        invalidate("현재 차량과 위치로 코스를 계산해 보세요.")
        restorePreviewPreferences()
        refreshState()
    }

    private fun restorePreviewPreferences() {
        val original = preferencesBeforePreview
        preferencesBeforePreview = null
        mutableState.update { it.copy(isPreview = false, preferences = original ?: it.preferences) }
    }

    /** Always revalidate at the actual click; Compose may still display a previous frame. */
    fun canNavigate(candidate: BatteryBudgetCandidate): Boolean {
        refreshState()
        val current = state.value
        return visible && browsingAllowed && !current.isPreview && !current.loading && candidate.estimate.feasible &&
            candidate in current.candidates && snapshot?.let {
                BatteryBudgetCalculator.isCurrent(it, current.preferences, vehicle(), location.currentPoint(), SystemClock.elapsedRealtime())
            } == true
    }

    private fun vehicle(): BatteryBudgetVehicle? = if (telemetry.active) {
        telemetry.batteryPercent?.let { soc -> telemetry.rangeKm?.let { range -> BatteryBudgetVehicle(soc, range).takeIf { it.valid } } }
    } else null

    private fun accepts(request: Long, inputs: BatteryBudgetSnapshot): Boolean =
        request == generation && visible && browsingAllowed && !state.value.isPreview &&
            BatteryBudgetCalculator.isCurrent(inputs, state.value.preferences, vehicle(), location.currentPoint(), SystemClock.elapsedRealtime())

    private fun refreshState() {
        val current = state.value
        val point = location.currentPoint()
        if (!current.isPreview && snapshot?.let {
                !BatteryBudgetCalculator.isCurrent(it, current.preferences, vehicle(), point, SystemClock.elapsedRealtime())
            } == true) invalidate("위치·배터리 정보가 바뀌었거나 계산 시간이 지났어요. 현재 조건으로 다시 계산해 주세요.")
        val reason = when {
            !visible -> "화면을 열고 현재 위치를 확인해 주세요."
            !browsingAllowed -> "코스 설정은 주차 후 이용해 주세요."
            vehicle() == null -> "차량의 배터리 잔량과 주행가능거리를 확인해야 계산할 수 있어요."
            !location.hasPrecisePermission() -> "정확한 위치 권한을 허용해 주세요."
            point == null -> "현재 위치를 확인하고 있어요. 위치 수신 상태를 확인해 주세요."
            else -> null
        }
        val previousReason = unavailableReason
        unavailableReason = reason
        mutableState.update {
            it.copy(batteryPercent = if (it.isPreview) it.batteryPercent else telemetry.batteryPercent.takeIf { telemetry.active },
                rangeKm = if (it.isPreview) it.rangeKm else telemetry.rangeKm.takeIf { telemetry.active },
                locationReady = point != null, canCalculate = reason == null && !it.loading && !it.isPreview,
                message = if (it.isPreview || it.loading) it.message else reason ?:
                    if (it.message == previousReason) "나들이 시간과 귀가할 때 남길 배터리를 정해 보세요." else it.message)
        }
    }

    private fun invalidate(message: String?) {
        generation++
        operation?.cancel(); operation = null
        snapshot = null
        mutableState.update { it.copy(loading = false, canCalculate = false, candidates = emptyList(),
            message = message, calculatedAtMs = null, failedCandidates = 0) }
    }

    override fun onCleared() {
        generation++
        operation?.cancel()
        ticker?.cancel()
        location.stop()
        super.onCleared()
    }
}
