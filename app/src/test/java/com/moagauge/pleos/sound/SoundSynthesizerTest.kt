package com.moagauge.pleos.sound

import com.moagauge.pleos.vehicle.VehicleTelemetry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

internal fun recordingBank(): Map<SoundProfile, RecordedLoop> = SoundProfile.entries
    .filter { it.recordingAsset != null }.associateWith { profile ->
        val asset = File("src/main/assets/${profile.recordingAsset}")
        asset.inputStream().use(RecordedLoop::readWav)
    }

internal fun soundRenderer() = SoundSynthesizer(recordings = recordingBank())

class SoundSynthesizerTest {
    @Test fun actualPackagedRecordingsAndOriginalVoicesAreAudibleWithoutClipping() {
        for (profile in SoundProfile.entries) {
            val renderer = soundRenderer()
            for (speed in listOf(45.0, 120.0, 360.0)) {
                renderer.parameters = SoundParameters(profile, speed, "D", 1.0)
                renderer.render(ShortArray(24_000))
                val samples = ShortArray(48_000).also(renderer::render)
                assertTrue("$profile at $speed clips", samples.all { abs(it.toInt()) <= 14_745 })
                assertTrue("$profile at $speed is inaudible", rms(samples) > 2_000)
            }
        }
    }

    @Test fun missingRecordingFailsRatherThanImpersonatingItWithSynthesis() {
        val renderer = SoundSynthesizer()
        renderer.parameters = SoundParameters(SoundProfile.STUDEBAKER, 20.0, "D", .5)
        assertThrows(IllegalStateException::class.java) { renderer.render(ShortArray(960)) }
    }

    @Test fun generatedSelectionsPlayDistinctPackagedAudio() {
        val outputs = SoundProfile.entries.filter { it.kind == SoundKind.GENERATED }.associateWith { profile ->
            val renderer = soundRenderer()
            renderer.parameters = SoundParameters(profile, 40.0, "D", .7)
            renderer.render(ShortArray(24_000))
            ShortArray(48_000).also(renderer::render)
        }.entries.toList()
        for (left in outputs.indices) for (right in left + 1 until outputs.size) {
            val a = outputs[left].value
            val b = outputs[right].value
            val correlation = abs(a.indices.sumOf { a[it].toDouble() * b[it] }) /
                sqrt(a.sumOf { it.toDouble() * it } * b.sumOf { it.toDouble() * it })
            assertTrue("${outputs[left].key} and ${outputs[right].key} must not be the same source at a different volume",
                correlation < .98)
        }
    }

    @Test fun recordedLoopsKeepAudibleTorqueDipsDuringShifts() {
        val renderer = soundRenderer()
        val normal = mutableListOf<Double>()
        val shifting = mutableListOf<Double>()
        repeat(300) { tick ->
            renderer.parameters = SoundParameters(SoundProfile.RENAULT, tick / 299.0 * 100.0, "D", .7)
            val pcm = ShortArray(480).also(renderer::render)
            val state = renderer.engineState
            if (tick > 60 && state.load > .6) {
                if (state.shiftCut > .7) shifting += rms(pcm)
                else if (state.shiftCut == 0.0) normal += rms(pcm)
            }
        }
        assertTrue(shifting.size >= 3)
        assertTrue("recorded PCM should dip during shift", shifting.average() < normal.average() * .8)
    }

    @Test fun zeroSpeedIsCompletelySilentInitiallyAndImmediatelyAfterMotionForEveryProfile() {
        for (profile in SoundProfile.entries) {
            val renderer = soundRenderer()
            renderer.parameters = SoundParameters(profile, 0.0, "D", 1.0)
            assertTrue("$profile at rest", ShortArray(4_800).also(renderer::render).all { it == 0.toShort() })
            renderer.parameters = renderer.parameters.copy(speedKmh = 60.0)
            assertTrue("$profile moving", ShortArray(48_000).also(renderer::render).any { it != 0.toShort() })
            renderer.parameters = renderer.parameters.copy(speedKmh = 0.0)
            assertTrue("$profile must stop from the first PCM frame", ShortArray(960).also(renderer::render).all { it == 0.toShort() })
        }
    }

    @Test fun settledOutputGrowsWithSpeedAcrossEveryPackagedAndOriginalProfile() {
        for (profile in SoundProfile.entries) {
            val levels = listOf(1.0, 5.0, 20.0, 40.0, 80.0).map { speed ->
                val renderer = soundRenderer()
                renderer.parameters = SoundParameters(profile, speed, "1", 1.0)
                renderer.render(ShortArray(96_000))
                rms(ShortArray(48_000).also(renderer::render))
            }
            assertTrue("$profile should start gently but audibly: $levels", levels.first() in 100.0..1_500.0)
            assertTrue("$profile speed levels must increase: $levels", levels.zipWithNext().all { (a, b) -> b > a * 1.10 })
        }
    }

    @Test fun largeSpeedStepFadesInInsteadOfJumpingToTheConfiguredMaximum() {
        val profile = SoundProfile.ELECTRIC_PROPULSION
        val renderer = SoundSynthesizer(recordings = mapOf(profile to RecordedLoop(FloatArray(48_000) { .6f })))
        renderer.parameters = SoundParameters(profile, 5.0, "1", 1.0)
        val previous = ShortArray(48_000).also(renderer::render).last().toInt()
        renderer.parameters = renderer.parameters.copy(speedKmh = 80.0)
        val first = ShortArray(960).also(renderer::render)
        renderer.render(ShortArray(24_000))
        val settled = ShortArray(4_800).also(renderer::render)
        assertTrue(abs(first.first().toInt() - previous) < 30)
        assertTrue("A 20ms step should still be fading", rms(first) < rms(settled) * .55)
        assertTrue("The configured maximum is reached after the fade", rms(settled) > previous * 3.5)
    }

    @Test fun invalidAndMutedInputsAreImmediatelySilentAfterPlayback() {
        for (profile in SoundProfile.entries) {
            val renderer = soundRenderer()
            val valid = SoundParameters(profile, 40.0, "D", .5)
            renderer.parameters = valid
            renderer.render(ShortArray(4_800))
            listOf(valid.copy(speedKmh = Double.NaN), valid.copy(speedKmh = -1.0),
                valid.copy(speedKmh = 361.0), valid.copy(gear = "?"),
                valid.copy(volume = Double.NaN), valid.copy(volume = 0.0),
                valid.copy(ducking = 0.0)).forEach {
                renderer.parameters = it
                val samples = ShortArray(960) { 100 }
                renderer.render(samples)
                assertTrue(samples.all { sample -> sample == 0.toShort() })
            }
        }
    }

    @Test fun duckingReducesEveryVoiceWithoutChangingItsWaveform() {
        for (profile in SoundProfile.entries) {
            fun output(duck: Double): ShortArray {
                val renderer = soundRenderer()
                renderer.parameters = SoundParameters(profile, 60.0, "D", .5, duck)
                renderer.render(ShortArray(24_000))
                return ShortArray(48_000).also(renderer::render)
            }
            val normal = output(1.0)
            val ducked = output(.2)
            assertTrue("$profile duck ratio", rms(ducked) / rms(normal) in .19.. .21)
            assertTrue(normal.indices.all { abs(ducked[it] - normal[it] * .2) <= 1 })
        }
    }

    @Test fun splittingIntoAudioBuffersPreservesEveryVoiceAndLoopPhase() {
        for (profile in SoundProfile.entries) {
            val p = SoundParameters(profile, 30.0, "D", .4)
            val full = soundRenderer().apply { parameters = p }
            val segmented = soundRenderer().apply { parameters = p }
            val expected = ShortArray(96_000).also(full::render)
            val actual = (1..100).flatMap { ShortArray(960).also(segmented::render).toList() }.toShortArray()
            assertArrayEquals(profile.name, expected, actual)
        }
    }

    @Test fun profileChangesCrossfadeInsteadOfJumpingAtTheBoundary() {
        val renderer = soundRenderer()
        renderer.parameters = SoundParameters(SoundProfile.AURORA, 40.0, "D", .7)
        var previous = ShortArray(24_000).also(renderer::render).last().toInt()
        for (profile in SoundProfile.entries.reversed()) {
            renderer.parameters = renderer.parameters.copy(profile = profile)
            val pcm = ShortArray(960).also(renderer::render)
            assertTrue("$profile abrupt profile boundary", abs(pcm.first().toInt() - previous) < 1_500)
            previous = pcm.last().toInt()
        }
    }

    @Test fun livePlaybackRequiresValidVehicleSignals() {
        val valid = VehicleTelemetry(active = true, speedKmh = 50, gear = "D", ignitionOn = true)
        assertTrue(valid.supportsSound())
        assertTrue(valid.copy(speedKmh = 0, gear = "P", ignitionOn = null).supportsSound())
        assertFalse(valid.copy(active = false).supportsSound())
        assertFalse(valid.copy(speedKmh = null).supportsSound())
        assertFalse(valid.copy(speedKmh = 361).supportsSound())
        assertFalse(valid.copy(gear = null).supportsSound())
        assertFalse(valid.copy(ignitionOn = false).supportsSound())
    }

    private fun rms(samples: ShortArray): Double = sqrt(samples.sumOf { it.toDouble() * it } / samples.size)
}
