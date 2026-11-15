package com.moadrive.pleos.sound

import org.junit.Assert.*
import org.junit.Test

class SoundSpeedEnvelopeTest {
    @Test fun stoppedAndInvalidSpeedNeverRequestAudibleGain() {
        for (speed in listOf(0.0, -0.0, -1.0, 361.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertEquals("$speed must be silent", 0.0, SoundSpeedEnvelope.gainFor(speed), 0.0)
        }
    }

    @Test fun gainStartsGentlyGrowsContinuouslyAndNeverExceedsTheChosenVolume() {
        val gains = (0..800).map { SoundSpeedEnvelope.gainFor(it / 10.0) }
        assertTrue("Low-speed movement must be audible without jumping to full volume", SoundSpeedEnvelope.gainFor(1.0) in .05.. .25)
        assertTrue(gains.all { it.isFinite() && it in 0.0..1.0 })
        assertTrue(gains.zipWithNext().all { (before, after) -> after > before })
        assertTrue(gains.zipWithNext().all { (before, after) -> after - before < .05 })
        assertEquals(1.0, gains.last(), 0.0)
        for (speed in listOf(80.0, 100.0, 200.0, 360.0)) assertEquals(1.0, SoundSpeedEnvelope.gainFor(speed), 0.0)
    }
}
