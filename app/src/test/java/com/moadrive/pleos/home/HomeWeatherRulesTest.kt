package com.moadrive.pleos.home

import com.moadrive.pleos.navigation.GeoPoint
import com.moadrive.pleos.safety.AreaForecast
import com.moadrive.pleos.safety.WeatherReading
import com.moadrive.pleos.safety.WeatherSample
import org.junit.Assert.*
import org.junit.Test

class HomeWeatherRulesTest {
    private val now = 1_800_000_000_000L
    private val here = GeoPoint(37.5665, 126.9780)
    private fun reading(code: Int?, time: Long = now) = WeatherReading(time, code, 12.0,
        0.0, 0.0, 2.0, null, 10_000.0, null)
    private fun forecast(current: WeatherReading?, hours: List<WeatherReading> = emptyList(),
        fetchedAt: Long = now, center: GeoPoint = here) =
        AreaForecast(center, fetchedAt, listOf(WeatherSample("중심", center, current, hours)))

    @Test fun `current rain and snow use their own scene`() {
        listOf(51, 56, 61, 67, 80, 95, 99).forEach { code ->
            assertEquals(HomeWeatherScene.RAIN, homeWeatherState(forecast(reading(code)), here, now).scene)
        }
        listOf(71, 73, 75, 77, 85, 86).forEach { code ->
            assertEquals(HomeWeatherScene.SNOW, homeWeatherState(forecast(reading(code)), here, now).scene)
        }
    }

    @Test fun `future snow and neighboring rain cannot replace current clear weather`() {
        val current = forecast(reading(0), listOf(reading(75, now + 3_600_000)))
        val area = current.copy(samples = current.samples + WeatherSample("북쪽 10km",
            GeoPoint(37.6565, 126.9780), reading(65), emptyList()))
        assertEquals(HomeWeatherScene.CLEAR, homeWeatherState(area, here, now).scene)
        assertEquals(homeWeatherGreeting(HomeWeatherScene.CLEAR),
            homeWeatherGreeting(homeWeatherState(area, here, now).scene))
    }

    @Test fun `observed rain and snow show caution only while the observation is valid`() {
        listOf(61 to HomeWeatherScene.RAIN, 75 to HomeWeatherScene.SNOW).forEach { (code, scene) ->
            val data = forecast(reading(code))
            val greeting = homeWeatherGreeting(homeWeatherState(data, here, now).scene)
            assertEquals(homeWeatherGreeting(scene), greeting)
            assertNotEquals(homeWeatherGreeting(HomeWeatherScene.NEUTRAL), greeting)
            assertEquals(homeWeatherGreeting(HomeWeatherScene.NEUTRAL),
                homeWeatherGreeting(homeWeatherState(data, here, now + 900_001).scene))
            assertEquals(homeWeatherGreeting(HomeWeatherScene.NEUTRAL),
                homeWeatherGreeting(homeWeatherState(data, here, now, preciseLocationGranted = false).scene))
        }
    }

    @Test fun `cloud and fog are never described as sunny`() {
        assertEquals(HomeWeatherScene.CLOUDY, homeWeatherState(forecast(reading(3)), here, now).scene)
        assertEquals(HomeWeatherScene.FOG, homeWeatherState(forecast(reading(45)), here, now).scene)
    }

    @Test fun `missing position or permission produces neutral scene without temperature`() {
        val data = forecast(reading(0))
        listOf(homeWeatherState(data, null, now), homeWeatherState(data, here, now, preciseLocationGranted = false),
            homeWeatherState(null, here, now), homeWeatherState(forecast(null), here, now),
            homeWeatherState(forecast(reading(null)), here, now)).forEach { state ->
            assertEquals(HomeWeatherScene.NEUTRAL, state.scene)
            assertNull(state.temperatureC)
        }
    }

    @Test fun `stale fetch and stale observation expire independently`() {
        assertEquals(HomeWeatherScene.NEUTRAL,
            homeWeatherState(forecast(reading(61), fetchedAt = now - 900_001), here, now).scene)
        assertEquals(HomeWeatherScene.NEUTRAL,
            homeWeatherState(forecast(reading(61, now - 1_800_001)), here, now).scene)
        assertEquals(HomeWeatherScene.RAIN,
            homeWeatherState(forecast(reading(61, now - 1_800_000), fetchedAt = now - 900_000), here, now).scene)
    }

    @Test fun `future observation and destination weather are rejected`() {
        assertEquals(HomeWeatherScene.NEUTRAL,
            homeWeatherState(forecast(reading(0, now + 300_001)), here, now).scene)
        assertEquals(HomeWeatherScene.NEUTRAL,
            homeWeatherState(forecast(reading(0), center = GeoPoint(35.1796, 129.0756)), here, now).scene)
    }

    @Test fun `snowfall takes priority over rain and invalid temperature is unavailable`() {
        assertEquals(HomeWeatherScene.SNOW,
            homeWeatherState(forecast(reading(61).copy(snowfallCm = 0.2, precipitationMm = 1.0)), here, now).scene)
        assertEquals(HomeWeatherScene.NEUTRAL,
            homeWeatherState(forecast(reading(0).copy(temperatureC = Double.NaN)), here, now).scene)
    }

    @Test fun `weather motion pauses when home is hidden or app is not resumed`() {
        assertTrue(shouldAnimateHomeWeather(true, true))
        assertFalse(shouldAnimateHomeWeather(false, true))
        assertFalse(shouldAnimateHomeWeather(true, false))
        assertFalse(shouldAnimateHomeWeather(false, false))
    }
}
