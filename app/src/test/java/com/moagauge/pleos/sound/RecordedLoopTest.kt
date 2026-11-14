package com.moagauge.pleos.sound

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class RecordedLoopTest {
    @Test fun decodesSignedLittleEndianPcmWithoutLosingHeadroom() {
        val pcm = ShortArray(100).apply {
            this[0] = Short.MIN_VALUE
            this[1] = -16384
            this[2] = 0
            this[3] = Short.MAX_VALUE
        }
        val loop = RecordedLoop.readWav(ByteArrayInputStream(wav(pcm)))
        assertEquals(-1.0, loop.next(1.0), 0.0)
        assertEquals(-.5, loop.next(1.0), 0.0)
        assertEquals(0.0, loop.next(1.0), 0.0)
        assertEquals(32767.0 / 32768.0, loop.next(1.0), 0.0)
    }

    @Test fun handlesUnknownOddSizedChunksExtendedFormatAndShortReads() {
        val data = riff(
            chunk("JUNK", byteArrayOf(1, 2, 3)),
            chunk("fmt ", format() + byteArrayOf(0, 0)),
            chunk("LIST", byteArrayOf(4, 5)),
            chunk("data", pcm(ShortArray(100) { 8192 })),
        )
        val stream = object : ByteArrayInputStream(data) {
            override fun read(b: ByteArray, off: Int, len: Int): Int =
                super.read(b, off, minOf(len, 3))
        }
        val loop = RecordedLoop.readWav(stream)
        repeat(400) { assertEquals(.25, loop.next(1.0), 0.0) }
    }

    @Test fun rejectsUnsupportedOrInconsistentPcmFormats() {
        val formats = listOf(
            format(encoding = 3), format(channels = 2), format(bits = 8),
            format(sampleRate = 0), format(blockAlign = 4), format(byteRate = 1),
        )
        for (fmt in formats) {
            rejects(riff(chunk("fmt ", fmt), chunk("data", pcm(ShortArray(10)))))
        }
    }

    @Test fun rejectsMalformedMissingDuplicateAndTruncatedChunks() {
        val valid = wav(ShortArray(100))
        rejects(valid.copyOf(11))
        rejects(valid.copyOf(valid.size - 1))
        rejects(valid.copyOf().apply { this[0] = 'X'.code.toByte() })
        rejects(valid.copyOf().apply { this[8] = 'X'.code.toByte() })
        rejects(riff(chunk("fmt ", format())))
        rejects(riff(chunk("data", pcm(ShortArray(10)))))
        rejects(riff(chunk("fmt ", format()), chunk("fmt ", format()), chunk("data", pcm(ShortArray(10)))))
        rejects(riff(chunk("fmt ", format()), chunk("data", byteArrayOf(1, 2, 3))))
        rejects(riff(chunk("fmt ", format()), chunk("data", byteArrayOf())))
        rejects(riff(chunk("fmt ", format()), "data".toByteArray() + byteArrayOf(-1, -1, -1, 127)))
        rejects(valid.copyOf().apply { this[4] = 0; this[5] = 0; this[6] = 0; this[7] = 0 })
    }

    @Test fun interpolatesFractionalPlaybackRates() {
        val samples = FloatArray(100).apply { this[0] = -.5f; this[1] = 0f; this[2] = .5f }
        val loop = RecordedLoop(samples)
        assertEquals(-.5, loop.next(.5), 0.0)
        assertEquals(-.25, loop.next(.5), 0.0)
        assertEquals(0.0, loop.next(.5), 0.0)
        assertEquals(.25, loop.next(.5), 0.0)
    }

    @Test fun crossfadeRemovesTheBoundaryClickAcrossManyWraps() {
        val loop = RecordedLoop(FloatArray(4_800) { -.8f + 1.6f * it / 4_799 })
        var previous = loop.next(1.0)
        var largestStep = 0.0
        repeat(30_000) {
            val sample = loop.next(1.0)
            largestStep = maxOf(largestStep, abs(sample - previous))
            assertTrue(sample in -1.0..1.0)
            previous = sample
        }
        assertTrue("A hard wrap would jump by 1.6: $largestStep", largestStep < .004)
    }

    @Test fun increasingPlaybackRateRaisesTheRecordedPitch() {
        val samples = FloatArray(48_000) { (.6 * sin(2 * PI * 400 * it / 48_000)).toFloat() }
        fun positiveCrossings(rate: Double): Int {
            val loop = RecordedLoop(samples)
            var previous = loop.next(rate)
            var crossings = 0
            repeat(19_999) {
                val current = loop.next(rate)
                if (previous <= 0 && current > 0) crossings++
                previous = current
            }
            return crossings
        }
        assertTrue(positiveCrossings(1.0) in 166..167)
        assertTrue(positiveCrossings(2.0) in 333..334)
    }

    @Test fun invalidRatesAreSilentAndDoNotCorruptPlaybackPosition() {
        val samples = FloatArray(100) { it / 100f }
        val loop = RecordedLoop(samples)
        val reference = RecordedLoop(samples)
        for (rate in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0, 0.0)) {
            assertEquals(0.0, loop.next(rate), 0.0)
        }
        repeat(10) { assertEquals(reference.next(.75), loop.next(.75), 0.0) }
        repeat(100) { assertTrue(loop.next(1e100).isFinite()) }
    }

    @Test fun keepsAnOwnedNormalizedCopyOfTheRecording() {
        val source = FloatArray(100) { .4f }
        val loop = RecordedLoop(source)
        source.fill(Float.NaN)
        assertEquals(.4, loop.next(1.0), .000001)
        for (invalid in listOf(floatArrayOf(), floatArrayOf(0f), floatArrayOf(Float.NaN, 0f), floatArrayOf(1.1f, 0f))) {
            assertThrows(IllegalArgumentException::class.java) { RecordedLoop(invalid) }
        }
        assertThrows(IllegalArgumentException::class.java) { RecordedLoop(FloatArray(10), 0) }
    }

    private fun rejects(bytes: ByteArray) {
        assertThrows(IllegalArgumentException::class.java) {
            RecordedLoop.readWav(ByteArrayInputStream(bytes))
        }
    }

    private fun wav(samples: ShortArray): ByteArray =
        riff(chunk("fmt ", format()), chunk("data", pcm(samples)))

    private fun format(
        encoding: Int = 1, channels: Int = 1, sampleRate: Int = 48_000,
        byteRate: Int = 96_000, blockAlign: Int = 2, bits: Int = 16,
    ): ByteArray = le16(encoding) + le16(channels) + le32(sampleRate) +
        le32(byteRate) + le16(blockAlign) + le16(bits)

    private fun pcm(samples: ShortArray): ByteArray = ByteArrayOutputStream().apply {
        samples.forEach { write(le16(it.toInt())) }
    }.toByteArray()

    private fun riff(vararg chunks: ByteArray): ByteArray {
        val body = ByteArrayOutputStream().apply { chunks.forEach(::write) }.toByteArray()
        return "RIFF".toByteArray() + le32(body.size + 4) + "WAVE".toByteArray() + body
    }

    private fun chunk(name: String, data: ByteArray): ByteArray =
        name.toByteArray() + le32(data.size) + data + if (data.size % 2 == 1) byteArrayOf(0) else byteArrayOf()

    private fun le16(value: Int) = byteArrayOf(value.toByte(), (value ushr 8).toByte())
    private fun le32(value: Int) = byteArrayOf(value.toByte(), (value ushr 8).toByte(), (value ushr 16).toByte(), (value ushr 24).toByte())
}
