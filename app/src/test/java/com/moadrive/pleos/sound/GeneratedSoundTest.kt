package com.moadrive.pleos.sound

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/** Known PCM tones separate playback-rate changes from changes already present in a source. */
class GeneratedSoundTest {
    private val generated = SoundProfile.entries.filter { it.kind == SoundKind.GENERATED }

    @Test fun generatedProfilesPlayTheirOwnProvidedSamplesRatherThanAnElectronicFallback() {
        val heard = generated.mapIndexed { index, profile ->
            val sourceHz = 200.0 + index * 50.0
            val renderer = SoundSynthesizer(recordings = mapOf(profile to tone(sourceHz)))
            val pcm = steady(renderer, profile, speed = 40.0)
            val hz = crossings(pcm)
            assertTrue("$profile must preserve its supplied tone at 1x: $hz Hz", abs(hz - sourceHz) <= 1.0)
            assertTrue("$profile should be audible", rms(pcm) > 2_000)
            val silent = SoundSynthesizer(recordings = mapOf(profile to RecordedLoop(FloatArray(48_000))))
            assertTrue("$profile must not add a replacement synth", steady(silent, profile, 40.0).all { it == 0.toShort() })
            hz
        }
        assertEquals("Different provided tones must stay distinct", generated.size, heard.distinct().size)
    }

    @Test fun aMissingGeneratedSampleFailsBeforeAdvancingTheEngineOrWritingPcm() {
        for (profile in generated) {
            val renderer = SoundSynthesizer()
            renderer.parameters = SoundParameters(profile, 80.0, "D", .5)
            val before = renderer.engineState
            val pcm = ShortArray(960) { 123 }
            assertThrows(IllegalStateException::class.java) { renderer.render(pcm) }
            assertEquals("A rejected sample must not advance the engine", before, renderer.engineState)
            assertTrue("A rejected sample must not partially write PCM", pcm.all { it == 123.toShort() })
        }
    }

    @Test fun everySampledProfileKeepsSourcePitchAtEveryMovingSpeedAndGear() {
        for (profile in SoundProfile.entries.filter { it.recordingAsset != null }) {
            for ((speed, gear) in listOf(1.0 to "1", 20.0 to "R", 80.0 to "D", 180.0 to "6")) {
                val renderer = SoundSynthesizer(recordings = mapOf(profile to tone(400.0)))
                val hz = crossings(steady(renderer, profile, speed, gear))
                assertTrue("$profile at $speed/$gear must stay at 400Hz, got $hz", hz in 399..401)
            }
        }
    }

    @Test fun sourceSampleRateIsResampledWithoutChangingTheOriginalPitch() {
        for (profile in SoundProfile.entries.filter { it.recordingAsset != null }) {
            for (outputRate in listOf(16_000, 44_100, 48_000)) {
                val sourceRate = 24_000
                val source = RecordedLoop(FloatArray(sourceRate) {
                    (.6 * sin(2.0 * PI * 400.0 * it / sourceRate)).toFloat()
                }, sourceRate)
                val renderer = SoundSynthesizer(sampleRate = outputRate, recordings = mapOf(profile to source))
                renderer.parameters = SoundParameters(profile, 80.0, "D", .7)
                renderer.render(ShortArray(outputRate))
                val hz = crossings(ShortArray(outputRate).also(renderer::render))
                assertTrue("$profile resampling to $outputRate must keep 400Hz, got $hz", hz in 399..401)
            }
        }
    }

    @Test fun generatedSamplesKeepTheAudibleTorqueReleaseDuringAutomaticShifts() {
        for (profile in generated) {
            val renderer = SoundSynthesizer(recordings = mapOf(profile to tone(400.0)))
            val normal = mutableListOf<Double>()
            val shifting = mutableListOf<Double>()
            repeat(300) { tick ->
                renderer.parameters = SoundParameters(profile, tick / 299.0 * 100.0, "D", .7)
                val pcm = ShortArray(480).also(renderer::render)
                val state = renderer.engineState
                if (tick > 60 && state.load > .6) {
                    if (state.shiftCut > .7) shifting += rms(pcm)
                    else if (state.shiftCut == 0.0) normal += rms(pcm)
                }
            }
            assertTrue("$profile needs actual automatic shifts", shifting.size >= 3)
            assertTrue("$profile shift should release torque audibly", shifting.average() < normal.average() * .8)
        }
    }

    @Test fun generatedLoopPitchAndPhaseDoNotDependOnAudioBufferSize() {
        for (profile in generated) {
            fun renderer() = SoundSynthesizer(recordings = mapOf(profile to tone(300.0))).apply {
                parameters = SoundParameters(profile, 48.0, "D", .6)
            }
            val expected = ShortArray(96_000).also(renderer()::render)
            val segmented = renderer()
            val actual = ShortArray(96_000)
            for (offset in actual.indices step 960) {
                ShortArray(960).also(segmented::render).copyInto(actual, offset)
            }
            assertArrayEquals(profile.name, expected, actual)
            assertTrue("$profile output headroom", actual.all { abs(it.toInt()) <= 14_745 })
        }
    }

    private fun steady(renderer: SoundSynthesizer, profile: SoundProfile, speed: Double, gear: String = "1"): ShortArray {
        renderer.parameters = SoundParameters(profile, speed, gear, .7)
        renderer.render(ShortArray(48_000))
        return ShortArray(48_000).also(renderer::render)
    }

    private fun tone(hz: Double) = RecordedLoop(FloatArray(48_000) {
        (.6 * sin(2.0 * PI * hz * it / 48_000)).toFloat()
    })

    private fun crossings(samples: ShortArray) =
        (1 until samples.size).count { samples[it - 1] <= 0 && samples[it] > 0 }

    private fun rms(samples: ShortArray) = sqrt(samples.sumOf { it.toDouble() * it } / samples.size)
}
