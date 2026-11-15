package com.moadrive.pleos.safety

import com.moadrive.pleos.navigation.GeoPoint
import org.junit.Assert.*
import org.junit.Test

class WeatherRulesTest {
    private val now = 1_800_000_000_000L
    private fun reading(code: Int? = 0, temperature: Double? = 15.0,
                        rain: Double? = 0.0, snow: Double? = 0.0,
                        wind: Double? = 2.0, gust: Double? = 3.0,
                        visibility: Double? = 20000.0) = WeatherReading(
        now, code, temperature, rain, snow, wind, gust, visibility, 0.0)
    private fun forecast(value: WeatherReading = reading()) = AreaForecast(
        GeoPoint(37.5665, 126.978), now,
        weatherSamplePoints(GeoPoint(37.5665, 126.978)).map {
            WeatherSample(it.label, it.point, value, (0 until 6).map { hour -> value.copy(timeMs = now + hour * 3_600_000L) })
        })

    @Test fun cardinalSamplesRemainTenKilometersFromCenterIncludingDateLine() {
        listOf(GeoPoint(37.5665,126.978), GeoPoint(0.0,179.99), GeoPoint(89.99,1.0)).forEach { center ->
            val samples = weatherSamplePoints(center)
            assertEquals(5, samples.size)
            assertEquals(center, samples.first().point)
            samples.drop(1).forEach { assertEquals(10_000.0, weatherDistanceMeters(center, it.point), .01) }
        }
    }

    @Test fun missingDataNeverProducesClearAssessment() {
        assertEquals(WeatherAttention.UNKNOWN, assessWeather(null, now).attention)
        val assessment = assessWeather(forecast(reading(code = null, visibility = null)), now)
        assertEquals(WeatherAttention.UNKNOWN, assessment.attention)
        assertFalse(assessment.complete)
    }

    @Test fun oldOrFutureForecastIsUnavailableForDeparture() {
        assertFalse(forecast().isFresh(now + WEATHER_FRESH_MS + 1))
        assertFalse(forecast().copy(fetchedAtMs = now + 1).isFresh(now))
        val oldReading = reading().copy(timeMs = now - 2 * 60 * 60 * 1000)
        assertFalse(forecast(oldReading).isFresh(now))
        assertEquals(WeatherAttention.UNKNOWN, assessWeather(forecast(), now + WEATHER_FRESH_MS + 1).attention)
    }

    @Test fun normalWeatherIsDescribedAsNoDistinctSignalsRatherThanSafe() {
        val result = assessWeather(forecast(), now)
        assertEquals(WeatherAttention.ROUTINE, result.attention)
        assertTrue(result.complete)
        assertFalse(result.summary.contains("안전"))
    }

    @Test fun rainSnowFogWindAndFreezingEachProduceWarnings() {
        val conditions = listOf(
            reading(code = 61, rain = 1.0) to "rain",
            reading(code = 71, snow = 0.2) to "snow",
            reading(code = 45, visibility = 500.0) to "fog",
            reading(gust = 19.0) to "wind",
            reading(temperature = -2.0, rain = 0.2) to "ice",
        )
        conditions.forEach { (value, key) ->
            assertTrue("Missing $key", assessWeather(forecast(value), now).notices.any { it.key == key })
        }
    }

    @Test fun nextHourOrPeripheralHazardIsIncluded() {
        val data = forecast()
        val sample = data.samples.last().copy(hours = listOf(reading(code = 95)))
        val result = assessWeather(data.copy(samples = data.samples.dropLast(1) + sample), now)
        assertEquals(WeatherAttention.HIGH, result.attention)
        assertTrue(result.notices.any { it.key == "thunder" })
        assertFalse(result.complete)
    }

    @Test fun partialWeatherCanStillWarnButCannotClaimCompleteness() {
        val result = assessWeather(forecast(reading(code = 71, visibility = null)), now)
        assertFalse(result.complete)
        assertTrue(result.notices.any { it.key == "snow" })
    }

    @Test fun farawayOrStaleWeatherCannotBeDepartureForecast() {
        val data = forecast()
        assertTrue(isDepartureForecast(data, data.center, now))
        assertFalse(isDepartureForecast(data, GeoPoint(35.1796, 129.0756), now))
        assertFalse(isDepartureForecast(data, null, now))
        assertFalse(isDepartureForecast(data, data.center, now + WEATHER_FRESH_MS + 1))
    }

    @Test fun shortHourlyForecastCannotImplyFullHorizon() {
        val data = forecast()
        listOf(0, 1, 5).forEach { count ->
            val result = assessWeather(data.copy(samples = data.samples.map { it.copy(hours = it.hours.take(count)) }), now)
            assertFalse("Accepted only $count hours", result.complete)
            assertEquals(WeatherAttention.UNKNOWN, result.attention)
        }
    }

    @Test fun duplicateOrNoncontiguousHoursCannotImplyFullHorizon() {
        val data = forecast()
        val duplicate = data.samples.map { it.copy(hours = List(6) { reading() }) }
        val gap = data.samples.map { it.copy(hours = it.hours.dropLast(1) + reading().copy(timeMs = now + 6 * 3_600_000L)) }
        listOf(duplicate, gap).forEach { samples ->
            val result = assessWeather(data.copy(samples = samples), now)
            assertFalse(result.complete)
            assertEquals(WeatherAttention.UNKNOWN, result.attention)
        }
    }

    @Test fun hourlyGustAndPrecipitationProbabilityMustBePresent() {
        val data = forecast()
        listOf(reading().copy(gustMps = null), reading().copy(precipitationProbability = null)).forEach { incomplete ->
            val first = data.samples.first().copy(hours = listOf(incomplete) + data.samples.first().hours.drop(1))
            val result = assessWeather(data.copy(samples = listOf(first) + data.samples.drop(1)), now)
            assertFalse(result.complete)
            assertEquals(WeatherAttention.UNKNOWN, result.attention)
        }
    }

    @Test fun freshCurrentValuesDoNotMakeAnOldHourlyHorizonComplete() {
        val nextHour = now + 3_600_000L
        val data = forecast().let { original -> original.copy(fetchedAtMs = nextHour,
            samples = original.samples.map { it.copy(current = it.current!!.copy(timeMs = nextHour)) }) }
        assertTrue(data.isFresh(nextHour))
        val result = assessWeather(data, nextHour)
        assertFalse(result.complete)
        assertEquals(WeatherAttention.UNKNOWN, result.attention)
    }
}
