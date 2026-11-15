package com.moadrive.pleos.assist

import com.moadrive.pleos.navigation.GeoPoint
import com.moadrive.pleos.vehicle.VehicleTelemetry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class AssistSessionTest {
    private val parked = VehicleTelemetry(active = true, speedKmh = 0, gear = "P", ignitionOn = true)
    private val moving = parked.copy(speedKmh = 2, gear = "D")
    private val origin = GeoPoint(37.5665, 126.978)

    @Test fun parkedClockEnteringRoutineWindowProducesSuggestionWithoutAnotherBoarding() = runTest {
        var clock = LocalDateTime.of(2026, 11, 4, 7, 29)
        val session = AssistSession(backgroundScope, localNow = { clock })
        session.setVisible(true); session.updateContext(parked, origin, true)
        assertNull(session.state.value.suggestion)
        clock = clock.plusMinutes(1)
        session.updateContext(parked, origin, true)
        assertEquals(RoutineScenario.COMMUTE, session.state.value.suggestion?.scenario)
        session.dismissRoutine()
        clock = clock.withHour(15).withMinute(0)
        session.updateContext(parked, origin, true)
        assertEquals(RoutineScenario.SCHOOL_PICKUP, session.state.value.suggestion?.scenario)
        session.dismissRoutine()
        clock = clock.withHour(18)
        session.updateContext(parked, origin, true)
        assertEquals(RoutineScenario.RETURN_HOME, session.state.value.suggestion?.scenario)
    }

    @Test fun demoRoutinesRemainAvailableOnFutureWeekdaysAndRearmOnANewLocalDate() = runTest {
        var clock = LocalDateTime.of(2027, 5, 3, 8, 0)
        val session = AssistSession(backgroundScope, localNow = { clock })
        session.setVisible(true); session.updateContext(parked, origin, true)
        assertEquals(RoutineScenario.COMMUTE, session.state.value.suggestion?.scenario)
        session.dismissRoutine()
        clock = clock.plusDays(1)
        session.updateContext(parked, origin, true)
        assertEquals(RoutineScenario.COMMUTE, session.state.value.suggestion?.scenario)
    }

    @Test fun movingDismissesRoutineEvenIfBrowsingPermissionIsStale() = runTest {
        val session = AssistSession(backgroundScope, localNow = { LocalDateTime.of(2026, 11, 4, 8, 0) })
        session.setVisible(true); session.updateContext(parked, origin, true)
        assertNotNull(session.state.value.suggestion)
        session.updateContext(moving, origin, true)
        assertNull(session.state.value.suggestion)
        session.updateContext(parked, origin, true)
        assertNull(session.state.value.suggestion)
    }

    @Test fun presentationIdSurvivesGpsUpdatesAndChangesOnlyForANewPresentation() = runTest {
        val session = AssistSession(backgroundScope, localNow = { LocalDateTime.of(2026, 11, 4, 8, 0) })
        session.setVisible(true); session.updateContext(parked, origin, true)
        val first = session.state.value.suggestionId
        assertNotNull(first)
        repeat(3) { session.updateContext(parked, origin.copy(latitude = 37.57), true) }
        assertEquals(first, session.state.value.suggestionId)
        session.dismissRoutine()
        assertNull(session.state.value.suggestionId)
        session.simulateRoutine(RoutineScenario.COMMUTE)
        val second = session.state.value.suggestionId
        assertNotNull(second)
        assertNotEquals(first, second)
        session.resetDemo(); session.simulateRoutine(RoutineScenario.COMMUTE)
        assertNotEquals(second, session.state.value.suggestionId)
        runCurrent(); advanceTimeBy(60_001); runCurrent()
        assertNull(session.state.value.suggestionId)
    }

    @Test fun disabledRoutineReevaluatesOnEnableButNeverRepeatsAlreadyPresentedScenario() = runTest {
        val session = AssistSession(backgroundScope, localNow = { LocalDateTime.of(2026, 11, 4, 8, 0) })
        session.setOptions(false, true)
        session.setVisible(true); session.updateContext(parked, origin, true)
        assertNull(session.state.value.suggestion)
        session.setOptions(true, true); session.updateContext(parked, origin, true)
        assertNotNull(session.state.value.suggestion)
        session.setOptions(false, true)
        assertNull(session.state.value.suggestionId)
        session.setOptions(true, true); session.updateContext(parked, origin, true)
        assertNull(session.state.value.suggestion)
    }

    @Test fun sameMinuteParkedReentryCanPresentUnseenScenarioAndWeekendManualPreviewStillWorks() = runTest {
        var clock = LocalDateTime.of(2027, 5, 3, 8, 0)
        val session = AssistSession(backgroundScope, localNow = { clock })
        session.setVisible(true); session.updateContext(moving, origin, true)
        assertNull(session.state.value.suggestion)
        session.updateContext(parked, origin, true)
        assertEquals(RoutineScenario.COMMUTE, session.state.value.suggestion?.scenario)
        session.dismissRoutine()
        clock = LocalDateTime.of(2027, 5, 8, 15, 0)
        session.updateContext(parked, origin, true)
        assertNull(session.state.value.suggestion)
        session.simulateRoutine(RoutineScenario.SCHOOL_PICKUP)
        assertEquals(RoutineScenario.SCHOOL_PICKUP, session.state.value.suggestion?.scenario)
    }

    @Test fun boardingSuggestsOncePerDayAndNeverWhileMoving() = runTest {
        val session = AssistSession(backgroundScope, localNow = { LocalDateTime.of(2026, 11, 4, 8, 0) })
        session.setVisible(true)
        session.updateContext(moving, origin, false)
        assertNull(session.state.value.suggestion)
        session.updateContext(parked, origin, true)
        assertEquals(RoutineScenario.COMMUTE, session.state.value.suggestion?.scenario)
        session.dismissRoutine()
        session.setVisible(false); session.setVisible(true)
        session.updateContext(parked, origin, true)
        assertNull(session.state.value.suggestion)
    }

    @Test fun armedIncidentWaitsForActualMovementAndDoesNotRepeat() = runTest {
        val session = AssistSession(backgroundScope, nowMs = { 1000L })
        session.setVisible(true); session.updateContext(parked, origin, true)
        session.armIncident()
        assertTrue(session.state.value.incidentDemoArmed)
        assertNull(session.state.value.nearbyIncident)
        session.updateContext(moving, null, false)
        assertNull(session.state.value.nearbyIncident)
        session.updateContext(moving, origin, false)
        assertNotNull(session.state.value.nearbyIncident)
        runCurrent(); advanceTimeBy(12_001); runCurrent()
        assertNull(session.state.value.nearbyIncident)
        session.updateContext(moving, origin, false)
        assertNull(session.state.value.nearbyIncident)
        assertNotNull(session.state.value.lastIncident)
    }

    @Test fun dismissedIncidentKeepsHistoryAndDoesNotRepeat() = runTest {
        val session = AssistSession(backgroundScope, nowMs = { 1000L })
        session.setVisible(true); session.updateContext(parked, origin, true)
        session.armIncident(); session.updateContext(moving, origin, false)
        runCurrent()
        val alertedState = session.state.value
        assertNotNull(alertedState.nearbyIncident)

        session.dismissIncident()
        assertEquals(alertedState.copy(nearbyIncident = null), session.state.value)
        repeat(3) { session.updateContext(moving, origin, false) }
        assertNull(session.state.value.nearbyIncident)
        advanceTimeBy(12_001); runCurrent()
        assertEquals(alertedState.copy(nearbyIncident = null), session.state.value)
    }

    @Test fun newArmedIncidentAndVirtualDemoCanAlertAfterDismissal() = runTest {
        val session = AssistSession(backgroundScope, nowMs = { 1000L + testScheduler.currentTime })
        session.setVisible(true); session.updateContext(parked, origin, true)
        session.armIncident(); session.updateContext(moving, origin, false)
        val firstIncident = session.state.value.nearbyIncident!!.incident
        session.dismissIncident()

        advanceTimeBy(1)
        session.updateContext(parked, origin, true); session.armIncident()
        session.updateContext(moving, origin, false)
        val secondIncident = session.state.value.nearbyIncident!!.incident
        assertNotEquals(firstIncident.id, secondIncident.id)
        assertEquals(secondIncident, session.state.value.lastIncident?.incident)
        session.dismissIncident()

        session.updateContext(parked, origin, true); session.playIncidentDemo()
        runCurrent(); advanceTimeBy(3_001); runCurrent()
        val demoIncident = session.state.value.nearbyIncident!!
        assertNotEquals(secondIncident.id, demoIncident.incident.id)
        assertEquals(8_000.0, demoIncident.distanceMeters, 1.0)
        assertEquals(demoIncident, session.state.value.lastIncident)
    }

    @Test fun virtualEntryIsOutsideFirstAndBackgroundCancelsPendingAlert() = runTest {
        val session = AssistSession(backgroundScope, nowMs = { 1000L })
        session.setVisible(true); session.updateContext(parked, null, true)
        session.playIncidentDemo(); runCurrent()
        assertEquals(12_000.0, session.state.value.demoDistanceMeters!!, 1.0)
        assertNull(session.state.value.nearbyIncident)
        session.setVisible(false)
        advanceTimeBy(5_000); runCurrent()
        assertNull(session.state.value.nearbyIncident)
        assertFalse(session.state.value.incidentDemoArmed)
    }

    @Test fun virtualEntryWarnsAtEightKmAndMovingCannotStartDemo() = runTest {
        val session = AssistSession(backgroundScope, nowMs = { 1000L })
        session.setVisible(true); session.updateContext(parked, null, true)
        session.playIncidentDemo(); runCurrent(); advanceTimeBy(3_001); runCurrent()
        assertEquals(8_000.0, session.state.value.nearbyIncident!!.distanceMeters, 1.0)
        session.resetDemo(); session.updateContext(moving, origin, false)
        session.simulateRoutine(RoutineScenario.SCHOOL_PICKUP)
        session.playIncidentDemo(); session.armIncident()
        assertNull(session.state.value.suggestion)
        assertFalse(session.state.value.incidentDemoArmed)
        assertNull(session.state.value.nearbyIncident)
    }

    @Test fun ignitionOffAndDisabledFeaturesCannotProducePrompts() = runTest {
        val session = AssistSession(backgroundScope, localNow = { LocalDateTime.of(2026, 11, 4, 8, 0) })
        session.setVisible(true); session.updateContext(parked.copy(ignitionOn = false), origin, true)
        assertNull(session.state.value.suggestion)
        session.simulateRoutine(RoutineScenario.COMMUTE)
        assertNull(session.state.value.suggestion)
        session.setOptions(false, false); session.updateContext(parked, origin, true)
        session.simulateRoutine(RoutineScenario.COMMUTE); session.armIncident(); session.playIncidentDemo()
        assertNull(session.state.value.suggestion)
        assertFalse(session.state.value.incidentDemoArmed)
        advanceTimeBy(4_000); runCurrent()
        assertNull(session.state.value.nearbyIncident)
    }
}
