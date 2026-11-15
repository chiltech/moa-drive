package com.moadrive.pleos.sound

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class SoundPreviewTest {
    @Test fun samplePreviewChangesVolumeWhileKeepingTheSourceAtOneTimesSpeed() {
        for (profile in SoundProfile.entries.filter { it.recordingAsset != null }) {
            val source = RecordedLoop(FloatArray(48_000) { (.6 * sin(2 * PI * 400 * it / 48_000)).toFloat() })
            val renderer = SoundSynthesizer(recordings = mapOf(profile to source))
            val pcm = renderPreview(SoundParameters(profile, 0.0, "P", 1.0), renderer)
            fun region(startMs: Int, endMs: Int) = pcm.sliceArray(startMs * 48 until endMs * 48)
            fun rms(samples: ShortArray) = sqrt(samples.sumOf { it.toDouble() * it } / samples.size)
            for (startMs in listOf(700, 2_500, 4_000)) {
                val region = region(startMs, startMs + 500)
                val crossings = (1 until region.size).count { region[it - 1] <= 0 && region[it] > 0 }
                assertTrue("$profile preview must keep source pitch, got $crossings cycles/0.5s", crossings in 199..201)
            }
            assertTrue("$profile gets louder as speed rises", rms(region(2_500, 3_000)) > rms(region(700, 1_200)) * 1.5)
            assertTrue("$profile gets quieter while slowing", rms(region(2_500, 3_000)) > rms(region(4_000, 4_500)) * 1.5)
        }
    }

    @Test fun everyPackagedSamplePreviewPlaysAndFinishesQuietly() {
        for (profile in SoundProfile.entries.filter { it.recordingAsset != null }) {
            val pcm = renderPreview(SoundParameters(profile, 0.0, "P", 1.0))
            assertTrue("$profile must wait silently at zero speed", pcm.take(14_400).all { it == 0.toShort() })
            assertTrue("$profile must actually play", pcm.sliceArray(48_000..96_000).any { abs(it.toInt()) > 1_000 })
            assertTrue("$profile should fade before the track ends", pcm.takeLast(480).all { abs(it.toInt()) < 100 })
        }
    }

    @Test fun previewRetainsMuteProfileAndDuckingAcrossTheWholeSequence() {
        val p = SoundParameters(SoundProfile.PULSE, 0.0, "P", 0.0, .2)
        assertTrue(renderPreview(p).all { it == 0.toShort() })
        for (frame in listOf(-1L, 0L, 48_000L, 144_000L, 240_000L, Long.MAX_VALUE)) {
            val actual = SoundPreview.parametersAt(frame, p.copy(volume = .35))
            assertEquals(p.profile, actual.profile)
            assertEquals(.2, actual.ducking, 0.0)
            assertTrue(actual.volume in 0.0.. .35)
            assertTrue(actual.speedKmh in 0.0..120.0)
        }
    }

    private fun renderPreview(parameters: SoundParameters, renderer: SoundSynthesizer = soundRenderer()): ShortArray {
        val output = ShortArray(240_000) // Five seconds of 48 kHz PCM, regardless of wall-clock pauses.
        for (offset in output.indices step 960) {
            renderer.parameters = SoundPreview.parametersAt(offset.toLong(), parameters)
            val block = ShortArray(minOf(960, output.size - offset))
            renderer.render(block)
            block.copyInto(output, offset)
        }
        return output
    }
}
