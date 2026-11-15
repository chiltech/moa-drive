package com.moadrive.pleos.passport

import org.junit.Assert.*
import org.junit.Test

class PassportVisitDetectorTest {
    @Test fun motionGateRequiresTrustworthySpeedAndNonParkGear() {
        listOf(null, Double.NaN, Double.POSITIVE_INFINITY, 101.0, -101.0, 0.0, 0.8).forEach {
            assertFalse(isPassportMotion(it, "D"))
        }
        listOf(null, "P", "?", "UNKNOWN", "0", "10").forEach {
            assertFalse(isPassportMotion(10.0, it))
        }
        (listOf("D", "R", "N") + (1..9).map(Int::toString)).forEach {
            assertTrue(isPassportMotion(3.0 / 3.6, it))
            assertTrue(isPassportMotion(-10.0, it))
            assertTrue(isPassportMotion(100.0, it))
        }
    }

    private fun fix(t: Long, lat: Double = 37.565, lon: Double = 126.975, accuracy: Double = 5.0) =
        PassportFix(lat, lon, accuracy, 1_700_000_000_000L + t, t)
    private fun PassportVisitDetector.sample(t: Long, trip: String? = "trip-a", moving: Boolean = true,
        lat: Double = 37.565, lon: Double = 126.975, accuracy: Double = 5.0, now: Long = t) =
        accept(trip, moving, fix(t, lat, lon, accuracy), now)

    @Test fun cellHasStableBoundsAndCenter() {
        val cell = PassportCell.from(37.5665, 126.978)!!
        assertEquals("g1:3756:12697", cell.id)
        assertEquals(37.56, cell.south, 0.0000001)
        assertEquals(126.97, cell.west, 0.0000001)
        assertEquals(37.57, cell.north, 0.0000001)
        assertEquals(126.98, cell.east, 0.0000001)
        assertEquals(37.565, cell.centerLatitude, 0.0000001)
        assertEquals(126.975, cell.centerLongitude, 0.0000001)
    }
    @Test fun negativeCoordinatesUseFloorAndInvalidCoordinatesHaveNoCell() {
        assertEquals("g1:-1301:-7711", PassportCell.from(-13.005, -77.105)!!.id)
        listOf(Double.NaN, Double.POSITIVE_INFINITY, 90.0, -90.01).forEach { assertNull(PassportCell.from(it, 127.0)) }
        listOf(Double.NaN, Double.NEGATIVE_INFINITY, 180.0, -180.01).forEach { assertNull(PassportCell.from(37.0, it)) }
    }
    @Test fun confirmsAfterTwoSecondsAndKeepsFirstObservationTime() {
        val detector = PassportVisitDetector()
        assertNull(detector.sample(1000))
        assertNull(detector.sample(2999))
        val visit = detector.sample(3000)!!
        assertEquals("trip-a", visit.tripId)
        assertEquals(fix(1000).wallTimeMs, visit.visitedAtMs)
        assertEquals(PassportCell.from(37.565, 126.975), visit.cell)
    }
    @Test fun duplicatesAndOutOfOrderSamplesCannotConfirm() {
        val detector = PassportVisitDetector()
        assertNull(detector.sample(4000))
        assertNull(detector.sample(4000))
        assertNull(detector.sample(1000))
        assertNull(detector.sample(5000))
        assertNotNull(detector.sample(6000))
    }
    @Test fun stationaryAndMissingTripNeverAwardAVisit() {
        val detector = PassportVisitDetector()
        assertNull(detector.sample(1000, trip = null))
        assertNull(detector.sample(4000, trip = null))
        assertNull(detector.sample(7000, moving = false))
        assertNull(detector.sample(10000, moving = false))
        assertNull(detector.sample(13000))
        assertNotNull(detector.sample(15000))
    }
    @Test fun stationaryIntervalBreaksConfirmation() {
        val detector = PassportVisitDetector()
        assertNull(detector.sample(1000))
        assertNull(detector.sample(2000, moving = false))
        assertNull(detector.sample(3000))
        assertNotNull(detector.sample(5000))
    }
    @Test fun staleFutureAndPoorAccuracyLocationsCannotAward() {
        val bad = listOf(
            fix(1000).copy(accuracyMeters = 50.1), fix(1000).copy(accuracyMeters = -1.0),
            fix(1000).copy(accuracyMeters = Double.NaN), fix(1000).copy(latitude = Double.NaN),
            fix(1000).copy(longitude = 200.0), fix(1000).copy(monotonicMs = -1),
        )
        for (point in bad) {
            val detector = PassportVisitDetector()
            assertNull(detector.accept("trip", true, point, 1000))
            assertNull(detector.accept("trip", true, point.copy(monotonicMs = 4000), 4000))
        }
        val detector = PassportVisitDetector()
        assertNull(detector.sample(1000, now = 11001))
        assertNull(detector.sample(4000, now = 3999))
        assertNull(detector.sample(7000))
        assertNotNull(detector.sample(9000))
    }
    @Test fun accuracyCircleCrossingBoundaryCannotClaimEitherSide() {
        val detector = PassportVisitDetector()
        assertNull(detector.sample(1000, lat = 37.56001))
        assertNull(detector.sample(4000, lat = 37.55999))
        assertNull(detector.sample(7000, lon = 126.97001))
        assertNull(detector.sample(10000, lon = 126.96999))
        assertNull(detector.sample(13000))
        assertNotNull(detector.sample(15000))
    }
    @Test fun gapOverFifteenSecondsRequiresFreshConfirmation() {
        val detector = PassportVisitDetector()
        assertNull(detector.sample(1000))
        assertNull(detector.sample(16001))
        assertNotNull(detector.sample(18001))
    }
    @Test fun continuousFixesConfirmEvenAtFifteenSecondLimit() {
        val detector = PassportVisitDetector()
        assertNull(detector.sample(1000))
        assertNotNull(detector.sample(16000))
    }
    @Test fun teleportDoesNotAwardDestinationOrIntermediateCells() {
        val detector = PassportVisitDetector()
        assertNull(detector.sample(1000))
        assertNotNull(detector.sample(3000))
        assertNull(detector.sample(4000, lat = 37.585))
        assertNull(detector.sample(6000, lat = 37.585))
        assertNull(detector.sample(7000))
    }
    @Test fun crossCountryJumpAfterGpsGapStillCannotAward() {
        val detector = PassportVisitDetector()
        detector.sample(1000)
        assertNotNull(detector.sample(3000))
        assertNull(detector.sample(19001, lat = 35.565))
        assertNull(detector.sample(21001, lat = 35.565))
        assertNull(detector.sample(23001, lat = 35.565))
    }
    @Test fun legitimateTravelAddsOnlyObservedDestination() {
        val detector = PassportVisitDetector()
        assertNull(detector.sample(1000))
        assertNotNull(detector.sample(3000))
        // A new sequence after a GPS gap can establish a new candidate, never fill the gap.
        assertNull(detector.sample(50000, lat = 37.585))
        val visit = detector.sample(52000, lat = 37.585)!!
        assertEquals("g1:3758:12697", visit.cell.id)
        assertNull(detector.sample(55000, lat = 37.585))
    }
    @Test fun eachTripGetsOneVisitPerCellAndResetKeepsDedupe() {
        val detector = PassportVisitDetector()
        detector.sample(1000)
        assertNotNull(detector.sample(3000))
        assertNull(detector.sample(5000))
        detector.reset()
        assertNull(detector.sample(7000))
        assertNull(detector.sample(9000))
        assertNull(detector.sample(10000, trip = "trip-b"))
        assertNotNull(detector.sample(12000, trip = "trip-b"))
    }
    @Test fun tripChangeDoesNotReuseCandidate() {
        val detector = PassportVisitDetector()
        detector.sample(1000)
        assertNull(detector.sample(3000, trip = "trip-b"))
        assertNotNull(detector.sample(5000, trip = "trip-b"))
    }
    @Test fun continuousMovementAcrossBoundaryAwardsBothObservedCellsOnce() {
        val detector = PassportVisitDetector()
        assertNull(detector.sample(1000, lat = 37.5694))
        assertEquals("g1:3756:12697", detector.sample(3000, lat = 37.5694)!!.cell.id)
        assertNull(detector.sample(6000, lat = 37.5706))
        assertEquals("g1:3757:12697", detector.sample(8000, lat = 37.5706)!!.cell.id)
        assertNull(detector.sample(11000, lat = 37.5694))
        assertNull(detector.sample(13000, lat = 37.5694))
    }
    @Test fun qualityLimitsAreInclusiveAndUseMonotonicAge() {
        val detector = PassportVisitDetector()
        assertNull(detector.sample(1000, accuracy = 50.0, now = 11000))
        val visit = detector.accept("trip-a", true,
            fix(3000, accuracy = 50.0).copy(wallTimeMs = 1_600_000_000_000L), 13000)
        assertNotNull(visit)
        assertEquals(fix(1000).wallTimeMs, visit!!.visitedAtMs)
    }
    @Test fun poorAccuracyBetweenGoodFixesBreaksConfirmation() {
        val detector = PassportVisitDetector()
        assertNull(detector.sample(1000))
        assertNull(detector.sample(2000, accuracy = 100.0))
        assertNull(detector.sample(3000))
        assertNotNull(detector.sample(5000))
    }
}
