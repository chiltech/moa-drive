package com.moagauge.pleos.safety

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moagauge.pleos.navigation.GeoPoint
import com.moagauge.pleos.navigation.NavigationDestination
import com.moagauge.pleos.navigation.NavigationLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SafetyViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("safety", 0)
    private val provider: WeatherProvider = OpenMeteoWeatherProvider()
    private val location = NavigationLocation(application)
    private val mutableState = MutableStateFlow(SafetyState())
    val state = mutableState.asStateFlow()
    // The guide always briefs the current vehicle area, even when the Safety
    // screen is browsing destination weather.
    private val mutableCurrentForecast = MutableStateFlow<AreaForecast?>(null)
    val currentForecast = mutableCurrentForecast.asStateFlow()
    private val mutableAutoRefresh = MutableStateFlow(preferences.getBoolean("auto_refresh", true))
    val autoRefresh = mutableAutoRefresh.asStateFlow()
    private val mutableDepartureMessage = MutableStateFlow<String?>(null)
    val departureMessage = mutableDepartureMessage.asStateFlow()
    private var visible = false
    private var destination: NavigationDestination? = null
    private val cache = mutableMapOf<SafetyTarget, AreaForecast>()
    private val operations = mutableMapOf<SafetyTarget, Job>()
    private val versions = mutableMapOf<SafetyTarget, Long>()
    private val attempts = mutableMapOf<SafetyTarget, Long>()
    private val errors = mutableMapOf<SafetyTarget, String>()
    private var ticker: Job? = null
    private var departureExpiry: Job? = null
    private var activeTripId: String? = null
    private var pendingDepartureUntil = 0L
    private var pendingCurrentRefresh = false

    init {
        viewModelScope.launch {
            location.location.collect {
                if (visible) {
                    render()
                    if (pendingCurrentRefresh && location.currentPoint() != null) {
                        pendingCurrentRefresh = false
                        request(SafetyTarget.CURRENT, true, explicit = true)
                    } else if (autoRefresh.value) request(SafetyTarget.CURRENT, false)
                    maybeAnnounceDeparture()
                }
            }
        }
    }

    /** Owned by app foreground lifecycle, not by the Safety page alone. */
    fun setVisible(active: Boolean) {
        visible = active
        if (active) {
            // Re-entry also rechecks permissions after the system grant screen.
            location.start()
            render()
            if (autoRefresh.value) {
                request(SafetyTarget.CURRENT, false)
                if (state.value.target == SafetyTarget.DESTINATION) request(SafetyTarget.DESTINATION, false)
            }
            if (ticker?.isActive != true) ticker = viewModelScope.launch {
                while (isActive) {
                    delay(10_000)
                    render()
                    if (autoRefresh.value) {
                        request(SafetyTarget.CURRENT, false)
                        if (state.value.target == SafetyTarget.DESTINATION) request(SafetyTarget.DESTINATION, false)
                    }
                    maybeAnnounceDeparture()
                }
            }
        } else {
            location.stop()
            ticker?.cancel(); ticker = null
            SafetyTarget.entries.forEach(::cancelRequest)
            dismissDeparture()
            pendingDepartureUntil = 0L
            pendingCurrentRefresh = false
            render()
        }
    }

    fun setDestination(value: NavigationDestination?) {
        if (destination == value) return
        destination = value
        cancelRequest(SafetyTarget.DESTINATION)
        cache.remove(SafetyTarget.DESTINATION)
        errors.remove(SafetyTarget.DESTINATION)
        attempts.remove(SafetyTarget.DESTINATION)
        if (value == null && state.value.target == SafetyTarget.DESTINATION) mutableState.update { it.copy(target = SafetyTarget.CURRENT) }
        render()
        if (visible && autoRefresh.value && state.value.target == SafetyTarget.DESTINATION) request(SafetyTarget.DESTINATION, false)
    }

    fun selectTarget(target: SafetyTarget) {
        if (target == SafetyTarget.DESTINATION && destination == null) return
        mutableState.update { it.copy(target = target) }
        render()
        request(target, false, explicit = true)
    }

    fun setAutoRefresh(enabled: Boolean) {
        mutableAutoRefresh.value = enabled
        preferences.edit().putBoolean("auto_refresh", enabled).apply()
        if (enabled && visible) {
            request(SafetyTarget.CURRENT, false)
            request(state.value.target, false)
        }
    }

    fun refresh() {
        if (!visible) return
        location.start()
        request(state.value.target, true, explicit = true)
        render()
    }

    fun onTripChanged(tripId: String?) {
        if (tripId == activeTripId) return
        activeTripId = tripId
        dismissDeparture()
        pendingDepartureUntil = 0L
        if (tripId == null || !visible) return
        pendingDepartureUntil = System.currentTimeMillis() + 20_000L
        if (!maybeAnnounceDeparture() && autoRefresh.value) request(SafetyTarget.CURRENT, false)
    }

    fun dismissDeparture() {
        departureExpiry?.cancel(); departureExpiry = null
        mutableDepartureMessage.value = null
    }

    private fun maybeAnnounceDeparture(): Boolean {
        val now = System.currentTimeMillis()
        if (!visible || activeTripId == null || now > pendingDepartureUntil) return false
        val current = cache[SafetyTarget.CURRENT]
        if (!isDepartureForecast(current, location.currentPoint(), now)) return false
        val assessment = assessWeather(current, now)
        mutableDepartureMessage.value = "출발 전 · 현재 위치 주변\n${assessment.summary}"
        pendingDepartureUntil = 0L
        departureExpiry?.cancel()
        departureExpiry = viewModelScope.launch { delay(10_000); mutableDepartureMessage.value = null }
        return true
    }

    private fun point(target: SafetyTarget): GeoPoint? = when (target) {
        SafetyTarget.CURRENT -> location.currentPoint()
        SafetyTarget.DESTINATION -> destination?.point
    }

    private fun request(target: SafetyTarget, force: Boolean, explicit: Boolean = false) {
        if (!visible) return
        val center = point(target)
        if (center == null) {
            if (target == SafetyTarget.CURRENT && explicit) pendingCurrentRefresh = true
            render()
            return
        }
        if (target == SafetyTarget.CURRENT) pendingCurrentRefresh = false
        val now = System.currentTimeMillis()
        val existing = cache[target]
        if (!force && existing != null && existing.isFresh(now) && weatherDistanceMeters(existing.center, center) < 5_000) return
        if (!force && operations[target]?.isActive == true) return
        // Avoid repeated network attempts on every GPS fix after a failure.
        if (!force && !explicit && now - (attempts[target] ?: 0) < 30_000) return
        cancelRequest(target)
        val version = versions[target] ?: 0L
        attempts[target] = now
        errors.remove(target)
        operations[target] = viewModelScope.launch {
            try {
                val result = provider.forecast(center)
                if (visible && versions[target] == version) {
                    cache[target] = result
                    errors.remove(target)
                    maybeAnnounceDeparture()
                }
            } catch (_: TimeoutCancellationException) {
                if (versions[target] == version) errors[target] = "날씨 응답이 늦어지고 있어요. 잠시 후 다시 시도해 주세요."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (versions[target] == version) errors[target] = "날씨를 가져오지 못했어요. 네트워크를 확인하고 다시 시도해 주세요."
            } finally {
                if (versions[target] == version) {
                    operations.remove(target)
                    render()
                }
            }
        }
        render()
    }

    private fun cancelRequest(target: SafetyTarget) {
        versions[target] = (versions[target] ?: 0) + 1
        operations.remove(target)?.cancel()
    }

    private fun render() {
        val now = System.currentTimeMillis()
        mutableCurrentForecast.value = cache[SafetyTarget.CURRENT]?.takeIf {
            visible && isDepartureForecast(it, location.currentPoint(), now)
        }
        val target = state.value.target
        val point = point(target)
        val existing = cache[target]?.takeIf { target != SafetyTarget.CURRENT || point != null && weatherDistanceMeters(it.center, point) < 5_000 }
        val message = errors[target] ?: when {
            target == SafetyTarget.CURRENT && !location.hasPrecisePermission() -> "현재 위치 날씨를 보려면 위치 권한이 필요해요."
            target == SafetyTarget.CURRENT && point == null && pendingCurrentRefresh -> "현재 위치를 확인하면 날씨를 새로 가져올게요. GPS 수신 상태를 확인해 주세요."
            target == SafetyTarget.CURRENT && point == null -> "현재 위치를 확인하고 있어요. GPS 수신 상태를 확인해 주세요."
            target == SafetyTarget.DESTINATION && destination == null -> "주행 메뉴에서 목적지를 먼저 선택해 주세요."
            else -> null
        }
        mutableState.update { it.copy(destinationName = destination?.name,
            preciseLocationGranted = location.hasPrecisePermission(), locationReady = location.currentPoint() != null,
            loading = operations[target]?.isActive == true, forecast = existing, message = message, nowMs = System.currentTimeMillis()) }
    }

    override fun onCleared() { setVisible(false) }
}
