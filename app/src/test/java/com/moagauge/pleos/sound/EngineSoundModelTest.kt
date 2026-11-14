package com.moagauge.pleos.sound

import org.junit.Assert.*
import org.junit.Test

class EngineSoundModelTest {
    @Test fun accelerationRaisesRpmBetweenThreeAudibleUpshifts() {
        val model = EngineSoundModel()
        val frames = (0..1_000).map { tick ->
            model.step((tick * .125).coerceAtMost(100.0), "D", .005)
        }
        val shifts = (1 until frames.size).filter { frames[it].gear > frames[it - 1].gear && frames[it - 1].gear > 0 }
        assertEquals("0–100 km/h should include three upshifts", 3, shifts.size)
        assertTrue("engine revs must rise before the first shift", frames[200].rpm > frames[60].rpm + 1_000.0)
        for (index in shifts) {
            val after = frames.subList(index, minOf(index + 40, frames.size))
            assertTrue("upshift must audibly lower RPM", after.minOf { it.rpm } < frames[index - 1].rpm * .88)
            assertTrue("upshift must interrupt engine torque", after.maxOf { it.shiftCut } > .4)
        }
        assertTrue("acceleration must load the engine", frames.maxOf { it.load } > .65)
    }

    @Test fun thresholdNoiseDoesNotHuntBetweenGears() {
        val model = EngineSoundModel()
        repeat(600) { model.step(40.0, "D", .005) }
        assertEquals(2, model.state.gear)
        val gears = (0 until 1_600).map { tick ->
            // Rounded 10 Hz readings hover around the first upshift threshold.
            model.step(if ((tick / 20) % 2 == 0) 35.0 else 37.0, "D", .005).gear
        }
        assertTrue("hysteresis must keep second gear engaged", gears.all { it == 2 })
        assertEquals("a settled engine must finish its shift cut", 0.0, model.state.shiftCut, .001)
    }

    @Test fun brakingDownshiftsAndReturnsToIdleAtRest() {
        val model = EngineSoundModel()
        repeat(800) { model.step(100.0, "D", .005) }
        val frames = (0..1_000).map { tick -> model.step((100.0 - tick * .125).coerceAtLeast(0.0), "D", .005) }
        assertTrue("deceleration should downshift", frames.zipWithNext().any { (a, b) -> b.gear < a.gear })
        assertTrue("deceleration should use the overrun tone", frames.maxOf { it.braking } > .5)
        assertEquals(1, model.state.gear)
        assertTrue("resting engine should settle to idle", model.state.rpm in 850.0..1_300.0)
    }

    @Test fun selectorControlsNeutralReverseAndNumberedGears() {
        val model = EngineSoundModel()
        for (selector in listOf("P", "N")) {
            repeat(200) { model.step(100.0, selector, .005) }
            assertEquals(0, model.state.gear)
            assertEquals(900.0, model.state.rpm, .5)
            assertEquals(0.0, model.state.shiftCut, 0.0)
        }
        repeat(400) { model.step(120.0, "R", .005) }
        assertEquals(-1, model.state.gear)
        assertTrue("reverse revs should remain restrained", model.state.rpm in 900.0..3_500.0)
        for (gear in 1..9) {
            repeat(200) { model.step(45.0, gear.toString(), .005) }
            assertEquals("an explicit gear must not be replaced by a virtual one", gear, model.state.gear)
        }
    }

    @Test fun malformedSignalsCannotPoisonLaterEngineFrames() {
        val model = EngineSoundModel()
        for (speed in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -5.0, 1.0e200, 360.0)) {
            for (dt in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 0.0, .005, 1.0e200)) {
                assertBounded(model.step(speed, "D", dt))
            }
        }
        assertBounded(model.step(50.0, "?", .005))
        repeat(400) { assertBounded(model.step(50.0, "D", .005)) }
        assertTrue("valid driving must recover after malformed input", model.state.rpm > 1_500.0)
    }

    @Test fun nonPositiveOrNonFiniteTimeDoesNotAdvanceTheEngine() {
        val model = EngineSoundModel()
        repeat(200) { model.step(20.0, "D", .005) }
        val before = model.state
        for (dt in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertEquals(before, model.step(100.0, "D", dt))
        }
    }

    private fun assertBounded(frame: EngineSoundFrame) {
        assertTrue(frame.rpm.isFinite() && frame.rpm in 850.0..7_200.0)
        assertTrue(frame.gear in -1..9)
        assertTrue(frame.load.isFinite() && frame.load in 0.0..1.0)
        assertTrue(frame.shiftCut.isFinite() && frame.shiftCut in 0.0..1.0)
        assertTrue(frame.braking.isFinite() && frame.braking in 0.0..1.0)
    }
}
