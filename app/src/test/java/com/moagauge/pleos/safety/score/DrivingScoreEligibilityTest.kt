package com.moagauge.pleos.safety.score

import org.junit.Assert.*
import org.junit.Test

class DrivingScoreEligibilityTest {
    private fun record(id: String = "trip-1", meters: Double = 600.0): DrivingScoreRecord {
        val durationMs = maxOf(60_000L, (meters * 100.0).toLong()) // Ten measured meters per second.
        return DrivingScoreRecord(
            tripId = id, startedAtMs = 1_000, endedAtMs = 1_000 + durationMs, startDate = "2026-11-13",
            tripDurationMs = durationMs, tripDistanceMeters = meters,
            observedDurationMs = durationMs, observedDistanceMeters = meters,
            speedLimitObservedMs = 0, accelerationEvents = 0, brakingEvents = 0, overspeedEvents = 0,
            completed = true, tripHasDataGap = false,
        )
    }

    @Test fun noDataHasNoScoreAndCannotMeetReference() {
        val result = DrivingScoreEligibility.evaluate(emptyList())
        assertNull(result.score)
        assertEquals(0, result.scoredTrips)
        assertEquals(0.0, result.distanceKm, .001)
        assertFalse(result.meetsReference)
        assertNull(result.pointsRemaining)
        assertEquals(500.0, result.kilometersRemaining, .001)
    }

    @Test fun penaltiesAreAppliedAndClampedWithoutIntegerOverflow() {
        assertEquals(82, record().copy(accelerationEvents = 1, brakingEvents = 2, overspeedEvents = 1).score)
        assertEquals(0, record().copy(brakingEvents = Int.MAX_VALUE).score)
        assertNull(record().copy(accelerationEvents = -1).score)
    }

    @Test fun partialShortInterruptedAndGapRecordsCannotEarnScores() {
        listOf(
            record().copy(completed = false), record().copy(tripHasDataGap = true),
            record().copy(endedAtMs = null), record().copy(endedAtMs = 60_999),
            record().copy(tripDurationMs = 59_000, observedDurationMs = 59_000),
            record().copy(tripDistanceMeters = 499.0, observedDistanceMeters = 499.0),
            record().copy(observedDurationMs = 47_999), record().copy(observedDistanceMeters = 479.0),
            record().copy(observedDurationMs = 72_001), record().copy(observedDistanceMeters = 720.01),
            record().copy(observedDistanceMeters = Double.NaN), record().copy(speedLimitObservedMs = 60_001),
            record().copy(tripDistanceMeters = 6_000.0, observedDistanceMeters = 6_000.0),
        ).forEach { assertNull(it.toString(), it.score) }
        assertEquals(100, record().copy(observedDurationMs = 48_000, observedDistanceMeters = 480.0).score)
    }

    @Test fun eligibilityUsesMeasuredDistanceAndWeightedActualScoresOnly() {
        val first = record("one", 400_000.0).copy(brakingEvents = 2)
        val second = record("two", 100_000.0).copy(brakingEvents = 10)
        val incomplete = record("partial", 9_000_000.0).copy(completed = false)
        val result = DrivingScoreEligibility.evaluate(listOf(first, second, incomplete))
        assertEquals(82, result.score)
        assertEquals(500.0, result.distanceKm, .001)
        assertEquals(2, result.scoredTrips)
        assertTrue(result.meetsReference)
    }

    @Test fun duplicateTripCannotDoubleQualifyingDistance() {
        val entry = record(meters = 250_000.0)
        val result = DrivingScoreEligibility.evaluate(listOf(entry, entry))
        assertEquals(250.0, result.distanceKm, .001)
        assertFalse(result.meetsReference)
        assertEquals(250.0, result.kilometersRemaining, .001)
    }

    @Test fun onlyObservedDistanceCountsWhenJourneyCoverageIsSufficient() {
        val entry = record(meters = 500_000.0).copy(observedDistanceMeters = 400_000.0)
        val result = DrivingScoreEligibility.evaluate(listOf(entry))
        assertEquals(400.0, result.distanceKm, .001)
        assertFalse(result.meetsReference)
        assertEquals(100.0, result.kilometersRemaining, .001)
    }

    @Test fun referenceCanBeAdjustedWithoutClaimingOfficialQualification() {
        val result = DrivingScoreEligibility.evaluate(listOf(record(meters = 100_000.0)),
            minimumScore = 90, minimumDistanceKm = 100.0)
        assertTrue(result.meetsReference)
        val insufficient = DrivingScoreEligibility.evaluate(listOf(record().copy(brakingEvents = 5)))
        assertEquals(5, insufficient.pointsRemaining)
        assertEquals(499.4, insufficient.kilometersRemaining, .001)
    }

    @Test fun previewScenariosDoNotChangeActualEmptyEligibility() {
        assertTrue(DrivingScorePreview.READY.meetsReference)
        assertFalse(DrivingScorePreview.MORE_DISTANCE.meetsReference)
        assertFalse(DrivingScoreEligibility.evaluate(emptyList()).meetsReference)
        assertNull(DrivingScoreEligibility.evaluate(emptyList()).score)
    }
}
