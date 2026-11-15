package com.moadrive.pleos.vehicle

import org.junit.Assert.*
import org.junit.Test

class SpeedFreshnessTest {
    @Test fun speedExpiresEvenWhenItsConnectionRemainsActive() {
        val sample = VehicleTelemetry(active = true, speedKmh = 80, speedObservedElapsedMs = 10_000L)
        assertEquals(80, sample.freshSpeedKmh(10_000L))
        assertEquals(80, sample.freshSpeedKmh(13_000L))
        assertNull(sample.freshSpeedKmh(13_001L))
        assertTrue(sample.active)
    }

    @Test fun missingInactiveInvalidAndFutureMeasurementsNeverProduceWarnings() {
        val sample = VehicleTelemetry(active = true, speedKmh = 80, speedObservedElapsedMs = 10_000L)
        assertNull(sample.copy(active = false).freshSpeedKmh(10_000L))
        assertNull(sample.copy(speedKmh = null).freshSpeedKmh(10_000L))
        assertNull(sample.copy(speedKmh = -1).freshSpeedKmh(10_000L))
        assertNull(sample.copy(speedKmh = 301).freshSpeedKmh(10_000L))
        assertNull(sample.copy(speedObservedElapsedMs = null).freshSpeedKmh(10_000L))
        assertNull(sample.copy(speedObservedElapsedMs = -1).freshSpeedKmh(10_000L))
        assertNull(sample.freshSpeedKmh(9_999L))
        assertNull(sample.freshSpeedKmh(10_000L, maxAgeMs = -1))
    }

    @Test fun repeatedEqualValuesRenewFreshnessAndZeroIsAValidSpeed() {
        val original = VehicleTelemetry(active = true, speedKmh = 80, speedObservedElapsedMs = 10_000L)
        val repeated = original.copy(speedObservedElapsedMs = 13_000L)
        assertNull(original.freshSpeedKmh(15_000L))
        assertEquals(80, repeated.freshSpeedKmh(15_000L))
        assertEquals(0, repeated.copy(speedKmh = 0).freshSpeedKmh(15_000L))
        assertEquals(300, repeated.copy(speedKmh = 300).freshSpeedKmh(15_000L))
    }

    @Test fun customExpiryAndLongClockBoundariesDoNotOverflow() {
        val sample = VehicleTelemetry(active = true, speedKmh = 50, speedObservedElapsedMs = 0L)
        assertEquals(50, sample.freshSpeedKmh(0L, maxAgeMs = 0L))
        assertNull(sample.freshSpeedKmh(1L, maxAgeMs = 0L))
        assertNull(sample.freshSpeedKmh(Long.MAX_VALUE))
        assertNull(sample.freshSpeedKmh(Long.MIN_VALUE))
        assertEquals(50, sample.copy(speedObservedElapsedMs = Long.MAX_VALUE).freshSpeedKmh(Long.MAX_VALUE))
    }

    @Test fun restartingSessionCannotCarryAnOldObservationIntoNewMeasurements() {
        val session = TelemetrySession()
        val first = session.start()
        session.update(first) { it.copy(speedKmh = 80, speedObservedElapsedMs = 10_000L) }
        assertEquals(80, session.value.freshSpeedKmh(10_001L))
        session.stop()
        assertNull(session.value.speedObservedElapsedMs)
        val second = session.start()
        assertNull(session.value.freshSpeedKmh(10_001L))
        assertFalse(session.update(first) { it.copy(speedKmh = 80, speedObservedElapsedMs = 10_001L) })
        session.update(second) { it.copy(speedKmh = 60, speedObservedElapsedMs = 10_002L) }
        assertEquals(60, session.value.freshSpeedKmh(10_003L))
    }
}
