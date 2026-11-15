package com.moadrive.pleos.safety.score

import com.moadrive.pleos.trips.TripRecord
import com.moadrive.pleos.trips.TripStatus
import org.junit.Assert.*
import org.junit.Test

class DrivingScoreEngineTest {
    private val origin = 1_800_000_000_000L

    private fun trip(seconds: Long = 60, meters: Double = 600.0, status: TripStatus = TripStatus.RECORDING,
        id: String = "trip-1", gap: Boolean = false) = TripRecord(
        id, origin, if (status == TripStatus.COMPLETED || status == TripStatus.INTERRUPTED) origin + seconds * 1_000 else null,
        "2027-01-15", "Asia/Seoul", seconds * 1_000, meters, null, null, null, status, gap,
    )

    private fun feed(engine: DrivingScoreEngine, ms: Long, speed: Int?, limit: Int? = null,
        limitTime: Long? = if (limit == null) null else ms, now: Long = ms, activeTrip: TripRecord = trip()) {
        engine.observe(activeTrip, speed, ms, now, limit, limitTime, origin + now)
    }

    @Test fun oldTripsAndUnavailableSpeedDoNotReceiveScores() {
        val engine = DrivingScoreEngine()
        assertNull(engine.snapshot(trip(status = TripStatus.COMPLETED)))
        feed(engine, 0, null)
        feed(engine, 1_000, 36, now = 4_001)
        assertNull(engine.snapshot(trip(status = TripStatus.COMPLETED)))
    }

    @Test fun completeMeasuredJourneyHasRealDurationDistanceAndScore() {
        val engine = DrivingScoreEngine()
        (0L..60L).forEach { feed(engine, it * 1_000, 36) }
        val record = requireNotNull(engine.snapshot(trip(status = TripStatus.COMPLETED)))
        assertEquals(60_000L, record.observedDurationMs)
        assertEquals(600.0, record.observedDistanceMeters, .001)
        assertEquals(100, record.score)
        assertFalse(record.hasSpeedLimitObservation)
        assertEquals(0L, record.speedLimitObservedMs)
    }

    @Test fun duplicateBackwardsAndNoisySubsecondSamplesDoNotInflateDistance() {
        val engine = DrivingScoreEngine()
        feed(engine, 0, 36)
        feed(engine, 100, 108)
        feed(engine, 1_000, 36)
        feed(engine, 1_000, 108)
        feed(engine, 500, 108)
        feed(engine, 2_000, 36)
        val record = requireNotNull(engine.snapshot(trip()))
        assertEquals(2_000L, record.observedDurationMs)
        assertEquals(20.0, record.observedDistanceMeters, .001)
        assertEquals(0, record.accelerationEvents)
        assertEquals(0, record.brakingEvents)
    }

    @Test fun gapsAndUnavailableSpeedNeverBridgeDistanceOrAcceleration() {
        val engine = DrivingScoreEngine()
        feed(engine, 0, 36)
        feed(engine, 1_000, 36)
        feed(engine, 10_000, 144)
        feed(engine, 11_000, 144)
        feed(engine, 12_000, null)
        feed(engine, 13_000, 36)
        feed(engine, 14_000, 36)
        val record = requireNotNull(engine.snapshot(trip()))
        assertEquals(3_000L, record.observedDurationMs)
        assertEquals(60.0, record.observedDistanceMeters, .001)
        assertEquals(0, record.accelerationEvents)
        assertEquals(0, record.brakingEvents)
        assertNull(record.score)
    }

    @Test fun continuousAccelerationCountsOnceAndRearmsAfterTwoNormalSeconds() {
        val engine = DrivingScoreEngine()
        listOf(0, 12, 24, 36, 48, 48, 48, 60, 72).forEachIndexed { i, speed ->
            feed(engine, i * 1_000L, speed)
        }
        assertEquals(2, engine.snapshot(trip())?.accelerationEvents)
    }

    @Test fun singleHalfSecondSpikeDoesNotCountAsHarshAcceleration() {
        val engine = DrivingScoreEngine()
        feed(engine, 0, 0)
        feed(engine, 500, 10)
        feed(engine, 1_000, 10)
        assertEquals(0, engine.snapshot(trip())?.accelerationEvents)
    }

    @Test fun continuousBrakingCountsOnceWithoutCountingEachFrame() {
        val engine = DrivingScoreEngine()
        listOf(90, 72, 54, 36, 18, 0).forEachIndexed { i, speed -> feed(engine, i * 1_000L, speed) }
        assertEquals(1, engine.snapshot(trip())?.brakingEvents)
    }

    @Test fun overspeedRequiresFreshLimitsAndThreeContinuousSeconds() {
        val engine = DrivingScoreEngine()
        (0L..6L).forEach { feed(engine, it * 1_000, 80, 60) }
        val record = requireNotNull(engine.snapshot(trip()))
        assertEquals(1, record.overspeedEvents)
        assertEquals(6_000L, record.speedLimitObservedMs)
        assertTrue(record.hasSpeedLimitObservation)
        val missing = DrivingScoreEngine()
        (0L..6L).forEach { feed(missing, it * 1_000, 80, 60, limitTime = 0L) }
        assertEquals(3_000L, missing.snapshot(trip())?.speedLimitObservedMs)
        assertEquals(1, missing.snapshot(trip())?.overspeedEvents)
        val stale = DrivingScoreEngine()
        (10L..16L).forEach { feed(stale, it * 1_000, 80, 60, limitTime = 0L) }
        assertEquals(0, stale.snapshot(trip())?.overspeedEvents)
        assertFalse(requireNotNull(stale.snapshot(trip())).hasSpeedLimitObservation)
    }

    @Test fun limitChangeAndSignalLossCannotCompleteAnOverspeedEvent() {
        val engine = DrivingScoreEngine()
        feed(engine, 0, 80, 60)
        feed(engine, 1_000, 80, 60)
        feed(engine, 2_000, 80, 70)
        feed(engine, 3_000, 80, 70)
        feed(engine, 4_000, 80)
        assertEquals(0, engine.snapshot(trip())?.overspeedEvents)
    }

    @Test fun repeatedOverspeedAfterTwoNormalSecondsCountsAnotherEvent() {
        val engine = DrivingScoreEngine()
        (0L..4L).forEach { feed(engine, it * 1_000, 80, 60) }
        feed(engine, 5_000, 60, 60)
        feed(engine, 6_000, 60, 60)
        (7L..11L).forEach { feed(engine, it * 1_000, 80, 60) }
        assertEquals(2, engine.snapshot(trip())?.overspeedEvents)
    }

    @Test fun parkingGraceAndOtherJourneySamplesAreNotAdded() {
        val engine = DrivingScoreEngine()
        feed(engine, 0, 36)
        feed(engine, 1_000, 36)
        feed(engine, 2_000, 36, activeTrip = trip(status = TripStatus.PARKING))
        feed(engine, 3_000, 36, activeTrip = trip(id = "different"))
        feed(engine, 4_000, 36)
        feed(engine, 5_000, 36)
        assertEquals(2_000L, engine.snapshot(trip())?.observedDurationMs)
        assertNull(engine.snapshot(trip(id = "different")))
    }

    @Test fun restoredCheckpointKeepsTotalsWithoutBridgingProcessGap() {
        val first = DrivingScoreEngine()
        feed(first, 0, 36)
        feed(first, 1_000, 36)
        val restored = DrivingScoreEngine(requireNotNull(first.snapshot(trip())))
        feed(restored, 20_000, 36)
        feed(restored, 21_000, 36)
        assertEquals(2_000L, restored.snapshot(trip())?.observedDurationMs)
        assertEquals(20.0, requireNotNull(restored.snapshot(trip())).observedDistanceMeters, .001)
    }

    @Test fun samplePredatingTripCannotBeAssignedToThatJourney() {
        val engine = DrivingScoreEngine()
        val laterTrip = trip().copy(startedAtMs = origin + 5_000)
        feed(engine, 4_000, 36, now = 5_000, activeTrip = laterTrip)
        assertNull(engine.snapshot(laterTrip))
        feed(engine, 5_000, 36, activeTrip = laterTrip)
        feed(engine, 6_000, 36, activeTrip = laterTrip)
        assertEquals(1_000L, engine.snapshot(laterTrip)?.observedDurationMs)
    }

    @Test fun explicitVisibilityBreakNeverInterpolatesHiddenTime() {
        val engine = DrivingScoreEngine()
        feed(engine, 0, 36)
        feed(engine, 1_000, 36)
        engine.breakContinuity()
        feed(engine, 2_000, 36)
        feed(engine, 3_000, 36)
        assertEquals(2_000L, engine.snapshot(trip())?.observedDurationMs)
    }

    @Test fun interruptedTripKeepsMeasuredHistoryWithoutScore() {
        val engine = DrivingScoreEngine()
        (0L..60L).forEach { feed(engine, it * 1_000, 36) }
        val record = requireNotNull(engine.snapshot(trip(status = TripStatus.INTERRUPTED)))
        assertEquals(origin + 60_000, record.endedAtMs)
        assertFalse(record.completed)
        assertNull(record.score)
        assertEquals(600.0, record.observedDistanceMeters, .001)
    }
}
