package com.moadrive.pleos.vehicle

import org.junit.Assert.*
import org.junit.Test

class SessionEfficiencyCalculatorTest {
    private fun tick(c: SessionEfficiencyCalculator, seconds: Int, speed: Float, wh: Float) =
        c.sample(speed, wh, seconds * 1_000_000_000L)

    @Test fun integratesVehicleSpeedAndUsesMeasuredBatteryEnergy() {
        val c = SessionEfficiencyCalculator()
        assertNull(tick(c, 0, 20f, 50_000f))
        for (t in 1..30) tick(c, t, 20f, 50_000f - t * (100f / 30f))
        assertEquals(6.0, c.value!!, 0.0001) // 600 m / 100 Wh
        for (t in 31..60) tick(c, t, 20f, 49_900f - (t - 30) * (200f / 30f))
        assertEquals(4.0, c.value!!, 0.0001) // 1,200 m / 300 Wh
    }
    @Test fun reverseAndAccelerationUseSpeedMagnitudeAndTrapezoid() {
        val c = SessionEfficiencyCalculator()
        tick(c, 0, -10f, 50_000f)
        for (t in 1..10) tick(c, t, -10f, 49_950f)
        assertEquals(2.0, c.value!!, 0.0001)
        tick(c, 11, -20f, 49_950f)
        assertEquals(2.3, c.value!!, 0.0001) // previous 100 m + 15 m
    }
    @Test fun missingDataGapAndChargingDiscardOldBaseline() {
        val c = SessionEfficiencyCalculator()
        tick(c, 0, 20f, 50_000f)
        for (t in 1..10) tick(c, t, 20f, 49_900f)
        assertNotNull(c.value)
        assertNull(c.sample(Float.NaN, 49_900f, 11_000_000_000L))
        assertNull(tick(c, 12, 20f, 49_900f))
        for (t in 13..22) tick(c, t, 20f, 49_800f)
        assertNotNull(c.value)
        assertNull(tick(c, 26, 20f, 49_800f)) // >3-second gap
        for (t in 27..36) tick(c, t, 20f, 49_700f)
        assertNull(tick(c, 37, 20f, 50_000f)) // charge/reset exceeds baseline
        c.reset(); assertNull(c.value)
    }
    @Test fun thresholdsZeroConsumptionAndRegenerationAreHandled() {
        val c = SessionEfficiencyCalculator()
        tick(c, 0, 20f, 50_000f)
        for (t in 1..10) assertNull(tick(c, t, 20f, 50_000f))
        assertNull(tick(c, 11, 20f, 49_990f)) // <50 Wh
        assertEquals(2.4, tick(c, 12, 20f, 49_900f)!!, 0.0001)
        assertEquals(5.2, tick(c, 13, 20f, 49_950f)!!, 0.0001) // regen below baseline
        assertEquals(5.2, tick(c, 12, 20f, 49_800f)!!, 0.0001) // late sample ignored
    }
}
