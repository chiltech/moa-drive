package com.moagauge.pleos.safety

import com.moagauge.pleos.navigation.GeoPoint
import kotlin.math.*

const val WEATHER_FRESH_MS = 15 * 60 * 1000L
private const val EARTH_RADIUS_METERS = 6_371_000.0
private const val HOUR_MS = 3_600_000L

enum class SafetyTarget { CURRENT, DESTINATION }
enum class WeatherAttention(val label: String) {
    UNKNOWN("확인 필요"), ROUTINE("날씨 살펴보기"), CAUTION("주의해서 출발해요"), HIGH("날씨 주의가 필요해요")
}

/** Null is unavailable, never a zero-valued observation. All units are metric. */
data class WeatherReading(
    val timeMs: Long,
    val weatherCode: Int?,
    val temperatureC: Double?,
    val precipitationMm: Double?,
    val snowfallCm: Double?,
    val windMps: Double?,
    val gustMps: Double?,
    val visibilityMeters: Double?,
    val precipitationProbability: Double?,
) {
    val complete: Boolean get() = weatherCode != null && temperatureC != null &&
        precipitationMm != null && snowfallCm != null && windMps != null && visibilityMeters != null
    val hourlyComplete: Boolean get() = complete && gustMps != null && precipitationProbability != null
}

data class WeatherSamplePoint(val label: String, val point: GeoPoint)
data class WeatherSample(val label: String, val point: GeoPoint, val current: WeatherReading?, val hours: List<WeatherReading>)
data class AreaForecast(val center: GeoPoint, val fetchedAtMs: Long, val samples: List<WeatherSample>) {
    val centerSample get() = samples.firstOrNull()
    fun isFresh(nowMs: Long): Boolean = nowMs - fetchedAtMs in 0..WEATHER_FRESH_MS &&
        samples.size == 5 && samples.all { sample ->
            sample.current?.let { nowMs - it.timeMs in -300_000L..5_400_000L } == true
        }
}

data class WeatherNotice(val key: String, val title: String, val detail: String, val high: Boolean = false)
data class WeatherAssessment(val attention: WeatherAttention, val summary: String,
                             val notices: List<WeatherNotice>, val complete: Boolean)

data class SafetyState(
    val target: SafetyTarget = SafetyTarget.CURRENT,
    val destinationName: String? = null,
    val preciseLocationGranted: Boolean = false,
    val locationReady: Boolean = false,
    val loading: Boolean = false,
    val forecast: AreaForecast? = null,
    val message: String? = null,
    val nowMs: Long = System.currentTimeMillis(),
) {
    val targetLabel: String get() = if (target == SafetyTarget.CURRENT) "현재 위치" else destinationName ?: "목적지"
    val assessment: WeatherAssessment get() = assessWeather(forecast, nowMs)
}

/** Geodesic samples: not an exhaustive geofence weather/incident feed. */
fun weatherSamplePoints(center: GeoPoint): List<WeatherSamplePoint> = listOf(
    WeatherSamplePoint("중심", center),
    WeatherSamplePoint("북쪽 10km", destinationPoint(center, 0.0)),
    WeatherSamplePoint("동쪽 10km", destinationPoint(center, 90.0)),
    WeatherSamplePoint("남쪽 10km", destinationPoint(center, 180.0)),
    WeatherSamplePoint("서쪽 10km", destinationPoint(center, 270.0)),
)

private fun destinationPoint(origin: GeoPoint, bearing: Double): GeoPoint {
    val d = 10_000.0 / EARTH_RADIUS_METERS
    val b = Math.toRadians(bearing)
    val latitude = Math.toRadians(origin.latitude)
    val longitude = Math.toRadians(origin.longitude)
    val lat = asin((sin(latitude) * cos(d) + cos(latitude) * sin(d) * cos(b)).coerceIn(-1.0, 1.0))
    val lon = longitude + atan2(sin(b) * sin(d) * cos(latitude), cos(d) - sin(latitude) * sin(lat))
    return GeoPoint(Math.toDegrees(lat), ((Math.toDegrees(lon) + 540.0) % 360.0) - 180.0)
}

fun weatherDistanceMeters(a: GeoPoint, b: GeoPoint): Double {
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val dlat = lat2 - lat1
    val dlon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dlat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dlon / 2).pow(2)
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

fun isDepartureForecast(forecast: AreaForecast?, position: GeoPoint?, nowMs: Long): Boolean =
    forecast != null && position != null && forecast.isFresh(nowMs) && weatherDistanceMeters(forecast.center, position) <= 5_000.0

fun assessWeather(forecast: AreaForecast?, nowMs: Long): WeatherAssessment {
    if (forecast == null || !forecast.isFresh(nowMs)) return WeatherAssessment(
        WeatherAttention.UNKNOWN, if (forecast == null) "날씨 정보를 확인해 주세요" else "예보를 새로 확인해 주세요", emptyList(), false)
    val readings = forecast.samples.flatMap { sample -> listOfNotNull(sample.current) +
        sample.hours.filter { it.timeMs in (nowMs - HOUR_MS)..(nowMs + 6 * HOUR_MS) } }
    // forecast_hours=6 starts at the current hour. A short, repeated, shifted or
    // partially populated series cannot support a "no signals" summary.
    val currentHour = (nowMs / HOUR_MS) * HOUR_MS
    val expectedHours = (0 until 6).map { currentHour + it * HOUR_MS }.toSet()
    val complete = forecast.samples.all { sample -> sample.current?.complete == true &&
        sample.hours.size == 6 && sample.hours.map { it.timeMs }.toSet() == expectedHours &&
        sample.hours.all { it.hourlyComplete } }
    val notices = buildList {
        if (readings.any { it.weatherCode in setOf(95, 96, 99) })
            add(WeatherNotice("thunder", "뇌우 예보", "시야와 노면 상태를 살피고 필요하면 출발을 늦춰 주세요.", true))
        if (readings.any { (it.snowfallCm ?: 0.0) > 0 || it.weatherCode in setOf(71, 73, 75, 77, 85, 86) })
            add(WeatherNotice("snow", "눈·미끄럼 주의", "급가속과 급제동을 줄이고 차간 거리를 넉넉히 유지해 주세요.", true))
        if (readings.any { (it.precipitationMm ?: 0.0) >= .1 || (it.precipitationProbability ?: 0.0) >= 60 ||
                it.weatherCode in setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82) })
            add(WeatherNotice("rain", "강수·젖은 노면 주의", "주변 또는 6시간 이내 강수 신호가 있어요. 노면과 제동 거리를 살펴 주세요."))
        if (readings.any { (it.visibilityMeters ?: Double.MAX_VALUE) < 1_000 || it.weatherCode in setOf(45, 48) })
            add(WeatherNotice("fog", "시야 저하 주의", "안개나 낮은 시정이 예상돼요. 속도를 줄이고 전방을 살펴 주세요.", true))
        if (readings.any { (it.windMps ?: 0.0) >= 10 || (it.gustMps ?: 0.0) >= 15 })
            add(WeatherNotice("wind", "강한 바람 주의", "교량과 개방된 도로에서 돌풍과 횡풍에 유의해 주세요."))
        if (readings.any { it.weatherCode in setOf(56, 57, 66, 67) ||
                (it.temperatureC != null && it.temperatureC <= 2 && ((it.precipitationMm ?: 0.0) > 0 || (it.snowfallCm ?: 0.0) > 0)) })
            add(WeatherNotice("ice", "결빙 가능성", "기온과 강수 조건에 따른 안내예요. 실제 노면 결빙 여부는 확인할 수 없어요.", true))
    }
    val attention = when {
        notices.any { it.high } -> WeatherAttention.HIGH
        notices.isNotEmpty() -> WeatherAttention.CAUTION
        !complete -> WeatherAttention.UNKNOWN
        else -> WeatherAttention.ROUTINE
    }
    val summary = when {
        notices.isNotEmpty() -> notices.take(2).joinToString(" · ") { it.title }
        !complete -> "일부 예보를 확인할 수 없어요"
        else -> "뚜렷한 기상 주의 신호는 없어요"
    }
    return WeatherAssessment(attention, summary, notices, complete)
}

fun weatherDescription(code: Int?): String = when (code) {
    0 -> "맑음"
    1, 2 -> "구름 조금"
    3 -> "흐림"
    45, 48 -> "안개"
    51, 53, 55 -> "이슬비"
    56, 57, 66, 67 -> "어는 비"
    61, 63, 65 -> "비"
    71, 73, 75, 77 -> "눈"
    80, 81, 82 -> "소나기"
    85, 86 -> "눈 소나기"
    95, 96, 99 -> "뇌우"
    else -> "정보 없음"
}
