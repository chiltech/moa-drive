package com.moadrive.pleos.assist

import com.moadrive.pleos.navigation.GeoPoint
import com.moadrive.pleos.navigation.NavigationSectionPhase
import com.moadrive.pleos.navigation.NavigationSpeedCamera
import com.moadrive.pleos.safety.*
import com.moadrive.pleos.vehicle.VehicleTelemetry
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class AiGuideSessionTest {
    private var now = 1_800_000L
    private var elapsed = 10_000L
    private val parked get() = VehicleTelemetry(active = true, gear = "P", speedKmh = 0,
        ignitionOn = true, speedObservedElapsedMs = elapsed)
    private val moving get() = parked.copy(gear = "D", speedKmh = 75)
    private fun session() = AiGuideSession(nowMs = { now }, nowElapsedMs = { elapsed },
        localNow = { LocalDateTime.of(2026, 11, 9, 8, 0) }).also { it.setVisible(true) }
    private fun advance(ms: Long) { now += ms; elapsed += ms }
    private fun forecast(stale: Boolean = false): AreaForecast {
        val at = if (stale) now - WEATHER_FRESH_MS - 1 else now
        val hour = now / 3_600_000L * 3_600_000L
        fun reading(time: Long) = WeatherReading(time, 61, 12.0, 1.0, 0.0, 2.0, 3.0, 10_000.0, 80.0)
        return AreaForecast(GeoPoint(37.56, 126.97), at, (0..4).map {
            WeatherSample("지점 $it", GeoPoint(37.56, 126.97), reading(now),
                (0..5).map { n -> reading(hour + n * 3_600_000L) })
        })
    }

    private fun clearForecast(complete: Boolean = true): AreaForecast {
        fun clear(reading: WeatherReading) = reading.copy(weatherCode = 0, precipitationMm = 0.0,
            precipitationProbability = 0.0)
        return forecast().let { forecast -> forecast.copy(samples = forecast.samples.map { sample ->
            sample.copy(current = sample.current?.let(::clear),
                hours = sample.hours.map(::clear).let { if (complete) it else it.dropLast(1) })
        }) }
    }

    @Test fun structuredBriefingUsesObservedNoticeWithoutReplacingSpokenGuidance() {
        val s = session()
        val data = forecast()
        s.updateContext(AiGuideContext(parked, true, data))
        val message = s.state.value.message!!
        val briefing = message.briefing!!
        val notice = assessWeather(data, now).notices.first()

        assertEquals(AiGuideKind.BOARDING, message.kind)
        assertEquals(AiBriefingStatus.CAUTION, briefing.status)
        assertEquals(notice.title, briefing.weatherTitle)
        assertEquals(notice.detail, briefing.weatherDetail)
        assertTrue(message.body.startsWith(briefing.greeting))
        assertTrue(message.body.contains(notice.title))
        assertTrue(message.body.contains(notice.detail))
        assertEquals(message.body, message.speech)
        assertEquals(3, briefing.checks.size)
    }

    @Test fun missingStaleAndIncompleteStructuredWeatherNeverClaimsRoutineConditions() {
        listOf(null, forecast(stale = true), clearForecast(complete = false)).forEach { forecast ->
            val s = session()
            s.updateContext(AiGuideContext(parked, true, forecast))
            s.dismiss()
            s.requestBriefing()
            val message = s.state.value.message!!

            assertEquals(AiGuideKind.BRIEFING, message.kind)
            assertEquals(AiBriefingStatus.UNKNOWN, message.briefing!!.status)
            assertFalse(message.briefing!!.weatherTitle.contains("뚜렷한"))
            assertEquals(message.body, message.speech)
        }
    }

    @Test fun completeClearForecastHasRoutineSummaryButDisabledWeatherDoesNotUseIt() {
        val s = session()
        val context = AiGuideContext(parked, true, clearForecast())
        s.updateContext(context)
        assertEquals(AiBriefingStatus.ROUTINE, s.state.value.message!!.briefing!!.status)
        assertTrue(s.state.value.message!!.briefing!!.weatherTitle.contains("주의 신호"))

        s.dismiss()
        s.updateContext(context.copy(options = AiGuideOptions(departureWeather = false)))
        s.requestBriefing()
        val disabled = s.state.value.message!!
        assertEquals(AiBriefingStatus.DISABLED, disabled.briefing!!.status)
        assertFalse(disabled.body.contains("예보"))
        assertEquals(disabled.body, disabled.speech)
    }

    @Test fun waitsForWeatherThenGreetsOnceAcrossVisibilityAndContextChanges() {
        val s = session()
        val context = AiGuideContext(parked, true)
        s.updateContext(context)
        assertNull(s.state.value.message)
        advance(5_000); s.updateContext(context.copy(telemetry = parked, forecast = forecast()))
        val first = s.state.value.message!!
        assertEquals(AiGuideKind.BOARDING, first.kind)
        assertEquals("오늘도 안전운전하세요", first.title)
        assertTrue(first.body.contains("좋은 아침이에요"))
        assertTrue(first.body.contains("오늘도 안전운전하세요"))
        assertTrue(first.speech!!.contains("오늘도 안전운전하세요"))
        assertTrue(first.body.contains("강수"))
        assertNotNull(first.speech)
        s.dismiss(); s.setVisible(false); advance(10_000); s.setVisible(true)
        s.updateContext(context.copy(telemetry = parked, forecast = forecast()))
        assertNull(s.state.value.message)
        assertEquals(first, s.state.value.lastBriefing)
    }

    @Test fun unavailableAndStaleWeatherNeverBecomeAClaimOfSafeConditions() {
        val s = session()
        s.updateContext(AiGuideContext(parked, true, forecast(stale = true)))
        advance(8_000); s.tick()
        assertEquals(AiGuideKind.BOARDING, s.state.value.message?.kind)
        assertTrue(s.state.value.message!!.body.contains("날씨 정보를 아직"))
        assertFalse(s.state.value.message!!.body.contains("뚜렷한 기상"))
    }

    @Test fun noGreetingWithoutVisibleActiveParkedVehicleAndRearmOnlyOnObservedIgnitionOff() {
        val s = session()
        s.updateContext(AiGuideContext(moving, false, forecast())); advance(10_000); s.tick()
        assertNull(s.state.value.message)
        s.updateContext(AiGuideContext(parked.copy(active = false), true, forecast()))
        assertNull(s.state.value.message)
        s.updateContext(AiGuideContext(parked, true, forecast()))
        val first = s.state.value.message!!.id
        s.dismiss(); s.updateContext(AiGuideContext(parked.copy(active = false), false))
        s.updateContext(AiGuideContext(parked, true, forecast()))
        assertNull(s.state.value.message)
        s.updateContext(AiGuideContext(parked.copy(ignitionOn = false), true, forecast()))
        s.updateContext(AiGuideContext(parked, true, forecast()))
        assertNotEquals(first, s.state.value.message?.id)
        assertEquals(AiGuideKind.BOARDING, s.state.value.message?.kind)
    }

    @Test fun manualBriefingRequiresParkedVehicleAndHonorsVoiceOption() {
        val s = session()
        s.updateContext(AiGuideContext(moving, false, forecast()))
        s.requestBriefing(); assertNull(s.state.value.message)
        s.updateContext(AiGuideContext(parked, true, forecast(), options = AiGuideOptions(voiceEnabled = false)))
        s.dismiss(); s.requestBriefing()
        assertEquals(AiGuideKind.BRIEFING, s.state.value.message?.kind)
        assertNull(s.state.value.message?.speech)
    }

    @Test fun speedWarningRespectsRangeMobileAndFeatureSwitches() {
        val s = session()
        val camera = NavigationSpeedCamera(60, 650, "이동식 단속", isMobile = true)
        val context = AiGuideContext(moving, false, guiding = true, camera = camera,
            options = AiGuideOptions(warningDistanceMeters = 500))
        s.updateContext(context); assertNull(s.state.value.message)
        s.updateContext(context.copy(options = AiGuideOptions(includeMobileCameras = false)))
        assertNull(s.state.value.message)
        s.updateContext(context.copy(options = AiGuideOptions(speedWarnings = false)))
        assertNull(s.state.value.message)
        s.updateContext(context.copy(options = AiGuideOptions()))
        assertEquals(AiGuideKind.OVERSPEED, s.state.value.message?.kind)
        assertNull(s.state.value.message?.speech)
        assertNull(s.state.value.message?.briefing)
    }

    @Test fun staleSpeedCannotProduceOrKeepOverspeedAndUnknownDistanceCannotWarn() {
        val s = session()
        val context = AiGuideContext(moving, false, guiding = true,
            camera = NavigationSpeedCamera(60, 300, "과속 단속"))
        s.updateContext(context)
        assertEquals(AiGuideKind.OVERSPEED, s.state.value.message?.kind)
        advance(3_001); s.tick()
        assertNotEquals(AiGuideKind.OVERSPEED, s.state.value.message?.kind)
        s.updateContext(context.copy(camera = NavigationSpeedCamera(60, null, "과속 단속")))
        assertNull(s.state.value.message)
    }

    @Test fun warningExpiresWithoutRepeatedCallbackSpamButNewEncounterCanWarn() {
        val s = session()
        val context = AiGuideContext(moving, false, guiding = true,
            camera = NavigationSpeedCamera(60, 300, "과속 단속"))
        s.updateContext(context)
        val first = s.state.value.message!!.id
        advance(1_000); s.updateContext(context.copy(telemetry = moving))
        assertEquals(first, s.state.value.message?.id)
        advance(20_000); s.updateContext(context.copy(telemetry = moving))
        assertNull(s.state.value.message)
        s.updateContext(context.copy(camera = null)); advance(15_000)
        s.updateContext(context.copy(telemetry = moving))
        assertEquals(AiGuideKind.OVERSPEED, s.state.value.message?.kind)
        assertNotEquals(first, s.state.value.message?.id)
    }

    @Test fun sectionAverageCanWarnAndExitNeverClaimsSafePassage() {
        val s = session()
        val context = AiGuideContext(parked.copy(gear = "D", speedKmh = 50), false, guiding = true,
            camera = NavigationSpeedCamera(60, null, "구간 단속", 70, sectionPhase = NavigationSectionPhase.ACTIVE))
        s.updateContext(context)
        assertEquals(AiGuideKind.OVERSPEED, s.state.value.message?.kind)
        assertTrue(s.state.value.message!!.body.contains("평균"))
        advance(20_000)
        s.updateContext(context.copy(camera = NavigationSpeedCamera(60, 80, "구간 단속 종료 지점", sectionPhase = NavigationSectionPhase.EXIT)))
        assertEquals(AiGuideKind.SECTION, s.state.value.message?.kind)
        assertFalse(s.state.value.message!!.body.contains("안전하게 통과"))
    }

    @Test fun incidentPreemptsBriefingOnceAndValidatesDistanceAndExpiry() {
        val s = session()
        val incident = NearbyIncident(RoadIncident("incident1", "가상 사고", GeoPoint(37.56,126.97),now,now+60_000),8_000.0)
        s.updateContext(AiGuideContext(parked, true, forecast()))
        s.updateContext(AiGuideContext(moving, false, incident = incident.copy(distanceMeters = 10_001.0)))
        assertNotEquals(AiGuideKind.INCIDENT, s.state.value.message?.kind)
        s.updateContext(AiGuideContext(moving, false, incident = incident))
        assertEquals(AiGuideKind.INCIDENT, s.state.value.message?.kind)
        assertTrue(s.state.value.message!!.body.contains("체험"))
        s.dismiss(); s.updateContext(AiGuideContext(moving, false, incident = incident))
        assertNull(s.state.value.message)
        s.updateContext(AiGuideContext(moving, false, incident = incident.copy(incident = incident.incident.copy(id = "expired", expiresAtMs = now))))
        assertNull(s.state.value.message)
    }

    @Test fun hiddenOrDisabledGuideClearsMessageAndDoesNotSpeakOnReturn() {
        val s = session()
        val context = AiGuideContext(parked, true, forecast())
        s.updateContext(context); assertNotNull(s.state.value.message?.speech)
        s.setVisible(false); assertNull(s.state.value.message)
        s.updateContext(context); assertNull(s.state.value.message)
        s.setVisible(true); assertNull(s.state.value.message)
        s.updateContext(context.copy(options = AiGuideOptions(enabled = false)))
        s.requestBriefing(); assertNull(s.state.value.message)
    }

    @Test fun routineAbsorbsBoardingAndManualBriefingUntilActualIgnitionOff() {
        val s = session()
        val routine = RoutineSuggestion(RoutineScenario.COMMUTE, 5, 480)
        val context = AiGuideContext(parked, true, forecast(), routineSuggestion = routine)
        s.updateContext(context)
        assertNull(s.state.value.message)
        s.requestBriefing(); assertNull(s.state.value.message)
        s.updateContext(context.copy(routineSuggestion = null))
        advance(10_000); s.tick()
        assertNull(s.state.value.message)
        s.setVisible(false); s.setVisible(true)
        s.updateContext(context.copy(telemetry = parked.copy(active = false), routineSuggestion = null))
        s.updateContext(context.copy(telemetry = parked, routineSuggestion = null))
        assertNull(s.state.value.message)
        s.updateContext(context.copy(telemetry = parked.copy(ignitionOn = false), routineSuggestion = null))
        s.updateContext(context.copy(telemetry = parked, forecast = forecast(), routineSuggestion = null))
        assertEquals(AiGuideKind.BOARDING, s.state.value.message?.kind)
    }

    @Test fun arrivingRoutineDismissesOnlyWarmBriefingsAndPreservesIncidentAndCameraWarnings() {
        val s = session()
        val routine = RoutineSuggestion(RoutineScenario.COMMUTE, 5, 480)
        val context = AiGuideContext(parked, true, forecast())
        s.updateContext(context)
        assertEquals(AiGuideKind.BOARDING, s.state.value.message?.kind)
        s.updateContext(context.copy(routineSuggestion = routine))
        assertNull(s.state.value.message)
        val incident = NearbyIncident(RoadIncident("care-incident", "사고", GeoPoint(37.56,126.97), now, now+60_000), 8_000.0)
        s.updateContext(context.copy(routineSuggestion = routine, incident = incident))
        assertEquals(AiGuideKind.INCIDENT, s.state.value.message?.kind)
        val incidentId = s.state.value.message?.id
        s.updateContext(context.copy(routineSuggestion = routine, incident = incident))
        assertEquals(incidentId, s.state.value.message?.id)
        s.updateContext(context.copy(telemetry = moving, browsingAllowed = false, guiding = true,
            routineSuggestion = routine, camera = NavigationSpeedCamera(60, 300, "과속 단속")))
        assertEquals(AiGuideKind.OVERSPEED, s.state.value.message?.kind)
    }

    @Test fun boardingCareChangesWithTimeOfDayAndRetainsUnknownWeatherCaution() {
        listOf(8 to "좋은 아침", 15 to "오후", 18 to "수고", 23 to "늦은 시간").forEach { (hour, greeting) ->
            val s = AiGuideSession(nowMs = { now }, nowElapsedMs = { elapsed },
                localNow = { LocalDateTime.of(2027, 5, 3, hour, 0) })
            s.setVisible(true); s.updateContext(AiGuideContext(parked, true))
            advance(8_000); s.tick()
            val message = s.state.value.message!!
            assertEquals("오늘도 안전운전하세요", message.title)
            assertTrue(message.body.contains(greeting))
            assertTrue(message.body.contains("오늘도 안전운전하세요"))
            assertTrue(message.body.contains("날씨 정보를 아직"))
            assertEquals(message.body, message.speech)
        }
    }
}
