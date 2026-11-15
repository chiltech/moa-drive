package com.moadrive.pleos.home

import org.junit.Assert.*
import org.junit.Test

class HomeWeatherModeTest {
    @Test fun `actual mode always returns the latest supplied observation unchanged`() {
        val first = HomeWeatherState(HomeWeatherScene.CLEAR, 8.0, "맑음", observedAtMs = 100L)
        val latest = HomeWeatherState(HomeWeatherScene.RAIN, 6.0, "비", observedAtMs = 200L)

        assertSame(first, HomeWeatherMode.ACTUAL.stateFor(first))
        HomeWeatherMode.SNOW.stateFor(first)
        assertSame(latest, HomeWeatherMode.ACTUAL.stateFor(latest))
    }

    @Test fun `returning to actual preserves unavailable or loading weather rather than a sample`() {
        val unavailable = HomeWeatherState(conditionLabel = "날씨 정보 없음", detail = "위치 권한이 필요해요")
        val loading = HomeWeatherState(conditionLabel = "날씨 확인 중", loading = true)

        HomeWeatherMode.RAIN.stateFor(unavailable)
        listOf(unavailable, loading).forEach { actual ->
            assertSame(actual, HomeWeatherMode.ACTUAL.stateFor(actual))
            assertNull(HomeWeatherMode.ACTUAL.stateFor(actual).temperatureC)
        }
    }

    @Test fun `samples are marked and never inherit or modify live observation metadata`() {
        val actual = HomeWeatherState(HomeWeatherScene.FOG, 4.0, "안개", "현재 위치 날씨",
            loading = true, observedAtMs = 300L)
        val original = actual.copy()
        val expected = mapOf(
            HomeWeatherMode.CLEAR to (HomeWeatherScene.CLEAR to 22.0),
            HomeWeatherMode.CLOUDY to (HomeWeatherScene.CLOUDY to 18.0),
            HomeWeatherMode.RAIN to (HomeWeatherScene.RAIN to 16.0),
            HomeWeatherMode.SNOW to (HomeWeatherScene.SNOW to -3.0),
        )

        expected.forEach { (mode, values) ->
            val sample = mode.stateFor(actual)
            assertEquals(values.first, sample.scene)
            assertEquals(values.second, sample.temperatureC!!, 0.0)
            assertEquals("샘플 날씨", sample.detail)
            assertNull(sample.observedAtMs)
            assertFalse(sample.loading)
            assertEquals(original, actual)
        }
    }
}
