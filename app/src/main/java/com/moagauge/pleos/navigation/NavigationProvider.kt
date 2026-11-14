package com.moagauge.pleos.navigation

import kotlinx.coroutines.flow.StateFlow

enum class NavigationProviderId(val label: String, val available: Boolean) {
    KAKAO("카카오내비", true), TMAP("티맵", false), NAVER("네이버지도", false),
}

data class GeoPoint(val latitude: Double, val longitude: Double) {
    init { require(latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0) }
}

/** Provider-owned IDs retain the native entrance coordinate without leaking SDK types into UI. */
data class NavigationDestination(val id: String, val name: String, val address: String, val point: GeoPoint)
data class NavigationRoute(
    val id: String,
    val provider: NavigationProviderId,
    val destination: NavigationDestination,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val tollWon: Int? = null,
    val preference: NavigationRoutePreference = NavigationRoutePreference.RECOMMENDED,
)
data class NavigationProgress(
    val remainingMeters: Int? = null,
    val remainingSeconds: Int? = null,
    val maneuver: NavigationManeuver? = null,
    val nextManeuver: NavigationManeuver? = null,
    val traffic: NavigationTraffic? = null,
    val speedCamera: NavigationSpeedCamera? = null,
    val restArea: NavigationRestArea? = null,
)

interface NavigationProvider {
    val id: NavigationProviderId
    val progress: StateFlow<NavigationProgress>
    suspend fun initialize()
    suspend fun search(query: String, origin: GeoPoint?): List<NavigationDestination>
    /** Retire one destination's prepared options before calculating another comparison batch. */
    fun clearPreparedRoutes()
    suspend fun prepareRoute(origin: GeoPoint, destination: NavigationDestination, preference: NavigationRoutePreference): NavigationRoute
    /** Starts only once its native guidance surface is attached. Caller owns AAOS focus. */
    suspend fun start(route: NavigationRoute, onEnded: () -> Unit)
    fun stop()
    fun foreground(active: Boolean)
}

class NavigationFailure(val userMessage: String) : Exception(userMessage)

/** A navigation operation cannot survive leaving the screen, focus loss or provider changes. */
class RouteSessionPolicy {
    private var generation = 0L
    private var foreground = false
    private var ownsFocus = false
    var selectedProvider = NavigationProviderId.KAKAO
        private set
    var routePreference = NavigationRoutePreference.RECOMMENDED
        private set
    fun selectRoutePreference(preference: NavigationRoutePreference) {
        if (routePreference == preference) return
        invalidate()
        routePreference = preference
    }
    fun setForeground(active: Boolean) {
        foreground = active
        if (!active) invalidate()
    }
    fun token(): Long = generation
    fun grantFocus(token: Long): Boolean {
        if (token != generation || !foreground) return false
        ownsFocus = true
        return true
    }
    fun mayGuide(token: Long) = token == generation && foreground && ownsFocus
    fun invalidate() { generation++; ownsFocus = false }
    fun select(provider: NavigationProviderId): Boolean {
        if (!provider.available) return false
        if (provider != selectedProvider) { invalidate(); selectedProvider = provider }
        return true
    }
}
