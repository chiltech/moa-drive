package com.moadrive.pleos.vehicle

import org.junit.Assert.*
import org.junit.Test

class VehicleTelemetryTest {
    @Test fun speedConvertsMetersPerSecondIncludingReverse() {
        assertEquals(72, VehicleValues.speedKmh(20f))
        assertEquals(36, VehicleValues.speedKmh(-10f))
        assertEquals(0, VehicleValues.speedKmh(0f))
        assertNull(VehicleValues.speedKmh(Float.NaN))
        assertNull(VehicleValues.speedKmh(Float.POSITIVE_INFINITY))
    }
    @Test fun unknownIsDistinctFromZero() {
        assertNull(VehicleValues.batteryPercent(null))
        assertNull(VehicleValues.batteryPercent(101))
        assertNull(VehicleValues.rangeKm(-1f))
        assertNull(VehicleValues.efficiency(-1f))
        assertNull(VehicleValues.efficiency(Float.NaN))
        assertEquals(0, VehicleValues.batteryPercent(0))
        assertEquals(420, VehicleValues.rangeKm(420_000f))
        assertEquals(0.0, VehicleValues.efficiency(0f)!!, 0.0)
        assertEquals(5.8, VehicleValues.efficiency(5.8f)!!, 0.00001)
    }
    @Test fun aaosGearBitValuesAreMapped() {
        assertEquals(listOf("P", "R", "N", "D"), listOf(4, 2, 1, 8).map(VehicleValues::gear))
        assertNull(VehicleValues.gear(0))
        assertNull(VehicleValues.gear(3))
    }
    @Test fun stoppedOrOlderSessionCannotRestoreStaleTelemetry() {
        val session = TelemetrySession()
        val first = session.start()
        assertTrue(session.update(first) { it.copy(speedKmh = 60) })
        session.stop()
        assertNull(session.value.speedKmh)
        assertFalse(session.update(first) { it.copy(speedKmh = 80) })
        val second = session.start()
        assertFalse(session.update(first) { it.copy(batteryPercent = 65) })
        assertTrue(session.update(second) { it.copy(batteryPercent = 80) })
        assertEquals(80, session.value.batteryPercent)
        assertNull(session.value.speedKmh)
    }
}
