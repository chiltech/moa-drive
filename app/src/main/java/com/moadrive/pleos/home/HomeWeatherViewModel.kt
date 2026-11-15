package com.moadrive.pleos.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moadrive.pleos.navigation.NavigationLocation
import com.moadrive.pleos.safety.AreaForecast
import com.moadrive.pleos.safety.OpenMeteoWeatherProvider
import com.moadrive.pleos.safety.WeatherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A Home-owned current-location feed, isolated from Safety's selectable destination. */
class HomeWeatherViewModel(application: Application) : AndroidViewModel(application) {
    private val location = NavigationLocation(application)
    private val provider: WeatherProvider = OpenMeteoWeatherProvider()
    private val mutableState = MutableStateFlow(HomeWeatherState())
    val state = mutableState.asStateFlow()
    private var visible = false
    private var autoRefresh = true
    private var forecast: AreaForecast? = null
    private var operation: Job? = null
    private var ticker: Job? = null
    private var generation = 0L
    private var lastAttemptMs: Long? = null
    private var requestFailed = false

    init {
        viewModelScope.launch {
            location.location.collect {
                if (visible) { render(); if (autoRefresh) request() }
            }
        }
    }

    /** Root supplies RESUMED && Home selected. Calling again rechecks permission changes. */
    fun setVisible(active: Boolean) {
        visible = active
        if (active) {
            location.start()
            render()
            if (autoRefresh) request()
            if (ticker?.isActive != true) ticker = viewModelScope.launch {
                while (isActive) {
                    delay(10_000L)
                    render()
                    if (autoRefresh) request()
                }
            }
        } else {
            location.stop()
            ticker?.cancel(); ticker = null
            cancelRequest()
            render()
        }
    }

    fun setAutoRefresh(enabled: Boolean) {
        if (autoRefresh == enabled) return
        autoRefresh = enabled
        if (!enabled) cancelRequest() else if (visible) request()
        render()
    }

    fun refresh() {
        if (!visible) return
        location.start()
        request(force = true)
    }

    private fun request(force: Boolean = false) {
        if (!visible || !location.hasPrecisePermission()) return
        val point = location.currentPoint() ?: return
        val now = System.currentTimeMillis()
        if (!force && homeWeatherState(forecast, point, now).scene != HomeWeatherScene.NEUTRAL) return
        if (!force && operation?.isActive == true) return
        if (!force && lastAttemptMs?.let { now - it in 0 until 30_000L } == true) return
        cancelRequest()
        val token = generation
        lastAttemptMs = now
        requestFailed = false
        operation = viewModelScope.launch {
            try {
                val result = provider.forecast(point)
                if (visible && token == generation) forecast = result
            } catch (_: TimeoutCancellationException) {
                if (token == generation) requestFailed = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (token == generation) requestFailed = true }
            finally {
                if (token == generation) { operation = null; render() }
            }
        }
        render()
    }

    private fun cancelRequest() {
        generation++
        operation?.cancel()
        operation = null
    }

    private fun render() {
        val next = homeWeatherState(forecast, location.currentPoint(), System.currentTimeMillis(),
            preciseLocationGranted = location.hasPrecisePermission(), loading = operation?.isActive == true)
        mutableState.value = if (next.scene == HomeWeatherScene.NEUTRAL && requestFailed &&
            location.currentPoint() != null && location.hasPrecisePermission()) {
            next.copy(detail = "날씨 연결을 확인해 주세요")
        } else next
    }

    override fun onCleared() { setVisible(false) }
}
