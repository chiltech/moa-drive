package com.moadrive.pleos.sound

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class ElectronicVoiceTest {
    @Test fun everyVoiceHasUsefulLevelAndBoundedFiniteSamplesAcrossTheDrivingRange() {
        for (tone in ElectronicTone.entries) {
            for ((speed, frame) in listOf(
                0.0 to frame(rpm = 900.0, load = .18),
                55.0 to frame(),
                360.0 to frame(rpm = 5_600.0, load = 1.0),
            )) {
                val samples = render(tone, frame, speed)
                assertTrue("$tone at $speed must stay finite and normalized",
                    samples.all { it.isFinite() && abs(it) <= 1.0 })
                assertTrue("$tone at $speed must remain audible: ${rms(samples)}",
                    rms(samples) in .28.. .55)
                assertTrue("$tone must be bipolar", samples.min() < -.2 && samples.max() > .2)
                assertTrue("$tone must not accumulate DC", abs(samples.average()) < .01)
            }
        }
    }

    @Test fun prismIsSpectrallyBrightAndPulseIsDeepRatherThanThreeSimilarWaveforms() {
        val aurora = render(ElectronicTone.AURORA)
        val pulse = render(ElectronicTone.PULSE)
        val prism = render(ElectronicTone.PRISM)
        // Derivative energy measures spectral brightness without relying on exact oscillator pitches.
        val warmBrightness = brightness(aurora)
        val deepBrightness = brightness(pulse)
        val crystalBrightness = brightness(prism)
        assertTrue("Aurora must have more upper movement than the bass motor", warmBrightness > deepBrightness * 1.35)
        assertTrue("Prism must be clearly brighter than the warm layers", crystalBrightness > warmBrightness * 2.0)
    }

    @Test fun pulseHasASustainedRhythmicEnvelopeThatAuroraDoesNot() {
        val pulse = blockLevels(render(ElectronicTone.PULSE, count = 96_000))
        val aurora = blockLevels(render(ElectronicTone.AURORA, count = 96_000))
        assertTrue("Pulse needs an audible repeating motor swell", variation(pulse) > .17)
        assertTrue("Pulse must stay sustained between swells", pulse.min() > .14)
        assertTrue("Aurora must glide rather than inherit the motor rhythm",
            variation(pulse) > variation(aurora) * 1.5)
    }

    @Test fun speedRaisesPitchEvenWhenTheVirtualRpmStaysConstant() {
        for (tone in ElectronicTone.entries) {
            val slow = render(tone, frame(rpm = 2_000.0), speed = 10.0)
            val fast = render(tone, frame(rpm = 2_000.0), speed = 120.0)
            assertTrue("$tone must respond to road speed", crossings(fast) > crossings(slow) * 1.3)
        }
    }

    @Test fun rpmStillChangesMotorPitchAtTheSameRoadSpeed() {
        for (tone in ElectronicTone.entries) {
            val low = render(tone, frame(rpm = 1_000.0), speed = 40.0)
            val high = render(tone, frame(rpm = 5_000.0), speed = 40.0)
            assertTrue("$tone must react to virtual motor RPM", crossings(high) > crossings(low) * 1.15)
        }
    }

    @Test fun abruptTelemetryAndShiftChangesDoNotResetPhaseOrClick() {
        val changed = frame(rpm = 5_600.0, load = 1.0, shiftCut = .85, braking = 1.0)
        for (tone in ElectronicTone.entries) {
            val steady = ElectronicVoice()
            val moving = ElectronicVoice()
            repeat(12_001) {
                steady.next(tone, frame(), 45.0)
                moving.next(tone, frame(), 45.0)
            }
            assertEquals("$tone must smooth the control transition", steady.next(tone, frame(), 45.0),
                moving.next(tone, changed, 200.0), .005)
            var difference = 0.0
            repeat(24_000) {
                difference += abs(steady.next(tone, frame(), 45.0) - moving.next(tone, changed, 200.0))
            }
            assertTrue("$tone must eventually follow the new telemetry", difference / 24_000 > .1)
        }
    }

    @Test fun loadBrakingAndShiftEachChangeTextureWithoutSilencingTheVoice() {
        for (tone in ElectronicTone.entries) {
            val base = render(tone, frame(load = .2))
            for (changed in listOf(frame(load = 1.0), frame(load = .2, braking = 1.0),
                frame(load = .2, shiftCut = .85))) {
                val samples = render(tone, changed)
                val difference = sqrt(samples.indices.sumOf { (samples[it] - base[it]) * (samples[it] - base[it]) } / samples.size)
                assertTrue("$tone must respond to $changed: RMS difference=$difference", difference > .025)
                assertTrue("The parent renderer owns the shift volume dip", rms(samples) > .20)
            }
        }
    }

    @Test fun tonesKeepIndependentContinuousPhasesWhenInterleaved() {
        for (tone in ElectronicTone.entries) {
            val isolated = ElectronicVoice()
            val interleaved = ElectronicVoice()
            repeat(5_000) {
                assertEquals(isolated.next(tone, frame(), 60.0), interleaved.next(tone, frame(), 60.0), 0.0)
                for (other in ElectronicTone.entries) {
                    if (other != tone) interleaved.next(other, frame(rpm = 1_000.0), 10.0)
                }
            }
        }
    }

    @Test fun invalidControlsCannotPoisonFutureSamples() {
        for (tone in ElectronicTone.entries) {
            val voice = ElectronicVoice()
            repeat(2_000) { voice.next(tone, frame(), 45.0) }
            val invalid = EngineSoundFrame(Double.NaN, 0, Double.POSITIVE_INFINITY, Double.NaN, Double.NEGATIVE_INFINITY)
            repeat(1_000) { assertTrue(voice.next(tone, invalid, Double.NaN).isFinite()) }
            repeat(1_000) { assertTrue(voice.next(tone, frame(), 45.0).isFinite()) }
        }
    }

    private fun frame(rpm: Double = 2_800.0, load: Double = .65, shiftCut: Double = 0.0, braking: Double = 0.0) =
        EngineSoundFrame(rpm, 3, load, shiftCut, braking)

    private fun render(tone: ElectronicTone, frame: EngineSoundFrame = frame(), speed: Double = 55.0,
        count: Int = 48_000): DoubleArray {
        val voice = ElectronicVoice()
        repeat(48_000) { voice.next(tone, frame, speed) }
        return DoubleArray(count) { voice.next(tone, frame, speed) }
    }

    private fun rms(samples: DoubleArray): Double = sqrt(samples.sumOf { it * it } / samples.size)
    private fun brightness(samples: DoubleArray): Double =
        sqrt((1 until samples.size).sumOf { (samples[it] - samples[it - 1]) * (samples[it] - samples[it - 1]) } / samples.size) / rms(samples)
    private fun crossings(samples: DoubleArray): Int =
        (1 until samples.size).count { samples[it - 1] <= 0.0 && samples[it] > 0.0 }
    private fun blockLevels(samples: DoubleArray): DoubleArray = DoubleArray(samples.size / 2_400) { block ->
        sqrt((block * 2_400 until (block + 1) * 2_400).sumOf { samples[it] * samples[it] } / 2_400)
    }
    private fun variation(values: DoubleArray): Double {
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size) / mean
    }
}
