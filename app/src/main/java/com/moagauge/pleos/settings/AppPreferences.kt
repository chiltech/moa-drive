package com.moagauge.pleos.settings

enum class AppearanceMode(val label: String) { LIGHT("라이트"), DARK("다크"), SYSTEM("시스템 설정") }
enum class MapAppearance(val label: String) { FOLLOW_APP("앱 테마에 맞춤"), DAY("주간 지도"), NIGHT("야간 지도") }

enum class NavigationCardScale(val label: String, val factor: Float) {
    SMALL("작게", .85f), STANDARD("기본", 1f), LARGE("크게", 1.15f),
}
enum class NavigationCardBackground(val label: String) { FOLLOW_THEME("앱 테마"), WHITE("화이트"), DARK("다크") }
enum class NavigationCardAccent(val label: String) { BLUE("블루"), GREEN("그린"), ORANGE("오렌지") }
data class NavigationLayerPreferences(
    val cardScale: NavigationCardScale = NavigationCardScale.STANDARD,
    val background: NavigationCardBackground = NavigationCardBackground.FOLLOW_THEME,
    val opacity: Float = .94f,
    val showTraffic: Boolean = true,
    val showSpeed: Boolean = true,
    val showRestArea: Boolean = true,
    val accent: NavigationCardAccent = NavigationCardAccent.BLUE,
) {
    fun normalized() = copy(opacity = if (opacity.isFinite()) opacity.coerceIn(.70f, 1f) else .94f)
}

data class AppPreferences(
    val appearance: AppearanceMode = AppearanceMode.LIGHT,
    val mapAppearance: MapAppearance = MapAppearance.FOLLOW_APP,
    val speedWarnings: Boolean = true,
    val voiceGuidance: Boolean = true,
    val showVehicle: Boolean = true,
    val showCctv: Boolean = true,
    val showDrivingMetrics: Boolean = true,
    val routineSuggestions: Boolean = true,
    val incidentWarnings: Boolean = true,
    val departureWeather: Boolean = true,
    val aiGuideEnabled: Boolean = true,
    val aiGuideVoice: Boolean = true,
    val keepScreenOn: Boolean = true,
    val navigationLayers: NavigationLayerPreferences = NavigationLayerPreferences(),
    val showIntro: Boolean = true,
    val autoLaunchOnBoarding: Boolean = false,
    val speedWarningFlash: Boolean = true,
    val speedWarningDistanceMeters: Int = 1000,
    val includeMobileCameras: Boolean = true,
    val map3dEnabled: Boolean = false,
) {
    fun normalized() = copy(
        navigationLayers = navigationLayers.normalized(),
        speedWarningDistanceMeters = if (speedWarningDistanceMeters == 500) 500 else 1000,
    )
}
