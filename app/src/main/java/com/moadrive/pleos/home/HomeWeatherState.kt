package com.moadrive.pleos.home

import com.moadrive.pleos.navigation.GeoPoint
import com.moadrive.pleos.safety.AreaForecast
import com.moadrive.pleos.safety.WEATHER_FRESH_MS
import com.moadrive.pleos.safety.weatherDescription
import com.moadrive.pleos.safety.weatherDistanceMeters

enum class HomeWeatherScene { NEUTRAL, CLEAR, CLOUDY, FOG, RAIN, SNOW }

fun homeWeatherGreeting(scene: HomeWeatherScene): String = when (scene) {
    HomeWeatherScene.CLEAR -> "햇살 좋은 날,\n기분 좋게 출발해요"
    HomeWeatherScene.CLOUDY -> "구름 낀 하늘에도,\n여유 있게 운전하세요"
    HomeWeatherScene.RAIN -> "비 오는 길,\n여유 있게 운전하세요"
    HomeWeatherScene.SNOW -> "눈 오는 길,\n천천히 안전하게"
    HomeWeatherScene.FOG -> "안개 낀 길,\n앞차와 거리를 두세요"
    else -> "오늘, 어디로 떠날까요?"
}

data class HomeWeatherState(
    val scene: HomeWeatherScene = HomeWeatherScene.NEUTRAL,
    val temperatureC: Double? = null,
    val conditionLabel: String = "날씨 확인 대기",
    val detail: String = "현재 위치 날씨",
    val loading: Boolean = false,
    val observedAtMs: Long? = null,
)

/** Home represents this position's current observation, never an area warning or future forecast. */
fun homeWeatherState(
    forecast: AreaForecast?,
    currentPoint: GeoPoint?,
    nowMs: Long,
    preciseLocationGranted: Boolean = true,
    loading: Boolean = false,
): HomeWeatherState {
    fun unavailable(detail: String) = HomeWeatherState(
        conditionLabel = if (loading) "날씨 확인 중" else "날씨 정보 없음", detail = detail, loading = loading)
    if (!preciseLocationGranted) return unavailable("위치 권한이 필요해요")
    if (currentPoint == null) return unavailable("현재 위치를 확인하고 있어요")
    if (forecast == null) return unavailable("현재 위치 날씨")
    if (weatherDistanceMeters(forecast.center, currentPoint) > 5_000.0) return unavailable("현재 위치 날씨를 확인 중이에요")
    val sample = forecast.centerSample ?: return unavailable("현재 위치 날씨")
    if (weatherDistanceMeters(sample.point, forecast.center) > 1_000.0) return unavailable("현재 위치 날씨")
    val reading = sample.current ?: return unavailable("현재 관측 정보가 없어요")
    if (nowMs - forecast.fetchedAtMs !in 0..WEATHER_FRESH_MS ||
        nowMs - reading.timeMs !in -300_000L..1_800_000L) return unavailable("날씨 정보를 새로 확인해 주세요")
    val temperature = reading.temperatureC?.takeIf { it.isFinite() && it in -100.0..65.0 }
        ?: return unavailable("현재 관측 정보가 없어요")
    val scene = when {
        reading.weatherCode == null -> HomeWeatherScene.NEUTRAL
        (reading.snowfallCm ?: 0.0) > 0 || reading.weatherCode in setOf(71, 73, 75, 77, 85, 86) -> HomeWeatherScene.SNOW
        (reading.precipitationMm ?: 0.0) > 0 || reading.weatherCode in setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99) -> HomeWeatherScene.RAIN
        reading.weatherCode == 0 -> HomeWeatherScene.CLEAR
        reading.weatherCode in setOf(1, 2, 3) -> HomeWeatherScene.CLOUDY
        reading.weatherCode in setOf(45, 48) -> HomeWeatherScene.FOG
        else -> HomeWeatherScene.NEUTRAL
    }
    if (scene == HomeWeatherScene.NEUTRAL) return unavailable("현재 관측 정보가 없어요")
    val description = when {
        scene == HomeWeatherScene.SNOW && reading.weatherCode !in setOf(71, 73, 75, 77, 85, 86) -> "눈"
        scene == HomeWeatherScene.RAIN && reading.weatherCode in setOf(0, 1, 2, 3, 45, 48) -> "비"
        else -> weatherDescription(reading.weatherCode)
    }
    return HomeWeatherState(scene, temperature, description, "현재 위치 날씨", loading, reading.timeMs)
}

/** Decorative weather runs only while the home screen is visible in the foreground. */
fun shouldAnimateHomeWeather(
    appResumed: Boolean,
    homeVisible: Boolean,
): Boolean = appResumed && homeVisible
