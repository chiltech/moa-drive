package com.moadrive.pleos.sound

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** A mono recording with fractional-speed playback and an overlapping loop boundary. */
internal class RecordedLoop(samples: FloatArray, val sampleRate: Int = 48_000) {
    private val recording: FloatArray
    private val fadeFrames: Int
    private val loopFrames: Int
    private var position = 0.0

    init {
        require(sampleRate in 1..192_000) { "Invalid recording sample rate" }
        require(samples.size >= 2) { "Recording needs at least two samples" }
        require(samples.all { it.isFinite() && it in -1f..1f }) { "Recording must be normalized PCM" }
        recording = samples.copyOf()
        fadeFrames = minOf((sampleRate * .060).toInt(), samples.size / 4)
        loopFrames = samples.size - fadeFrames
    }

    /** [rate] is source frames per output frame. The caller smooths its playback-rate changes. */
    fun next(rate: Double): Double {
        if (!rate.isFinite() || rate <= 0.0) return 0.0

        var value = interpolate(position)
        if (fadeFrames > 0 && position >= loopFrames) {
            val headPosition = position - loopFrames
            val mix = headPosition / fadeFrames
            // Complementary linear gains preserve headroom, including correlated source audio.
            value = value * (1.0 - mix) + interpolate(headPosition) * mix
        }

        position += rate
        if (position >= recording.size) {
            // The head has already played during the overlap; resume after that head segment.
            position = fadeFrames + (position - recording.size) % loopFrames
        }
        return value
    }

    private fun interpolate(frame: Double): Double {
        val first = frame.toInt()
        val second = minOf(first + 1, recording.lastIndex)
        val fraction = frame - first
        return recording[first].toDouble() * (1.0 - fraction) + recording[second] * fraction
    }

    companion object {
        private const val MAX_WAV_BYTES = 16 * 1024 * 1024

        /** Decodes RIFF/WAVE PCM16 mono. The caller owns and closes [input]. */
        fun readWav(input: InputStream): RecordedLoop {
            val bytes = readBounded(input)
            require(bytes.size >= 12 && tag(bytes, 0) == "RIFF" && tag(bytes, 8) == "WAVE") {
                "Expected a RIFF/WAVE file"
            }
            require(unsigned32(bytes, 4) + 8L == bytes.size.toLong()) { "Invalid or truncated RIFF size" }

            var sampleRate = 0
            var dataOffset = -1
            var dataSize = 0
            var offset = 12
            while (offset < bytes.size) {
                require(bytes.size - offset >= 8) { "Truncated WAV chunk header" }
                val name = tag(bytes, offset)
                val size = unsigned32(bytes, offset + 4)
                val start = offset + 8
                val end = start.toLong() + size
                val next = end + (size and 1L)
                require(next <= bytes.size.toLong()) { "Truncated WAV chunk" }
                when (name) {
                    "fmt " -> {
                        require(sampleRate == 0 && size >= 16) { "Missing or duplicate PCM format" }
                        require(unsigned16(bytes, start) == 1 && unsigned16(bytes, start + 2) == 1 &&
                            unsigned16(bytes, start + 14) == 16) { "Only PCM16 mono WAV is supported" }
                        val rate = unsigned32(bytes, start + 4)
                        require(rate in 1L..192_000L) { "Invalid WAV sample rate" }
                        require(unsigned16(bytes, start + 12) == 2 &&
                            unsigned32(bytes, start + 8) == rate * 2) { "Inconsistent PCM frame size" }
                        sampleRate = rate.toInt()
                    }
                    "data" -> {
                        require(dataOffset == -1 && size >= 4 && size % 2L == 0L) { "Invalid PCM data chunk" }
                        dataOffset = start
                        dataSize = size.toInt()
                    }
                }
                offset = next.toInt()
            }
            require(sampleRate > 0 && dataOffset >= 0) { "WAV needs format and audio data" }
            val samples = FloatArray(dataSize / 2) { index ->
                unsigned16(bytes, dataOffset + index * 2).toShort().toFloat() / 32768f
            }
            return RecordedLoop(samples, sampleRate)
        }

        private fun readBounded(input: InputStream): ByteArray {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8_192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) {
                    val byte = input.read()
                    if (byte < 0) break
                    require(output.size() < MAX_WAV_BYTES) { "WAV is too large" }
                    output.write(byte)
                } else {
                    require(count <= MAX_WAV_BYTES - output.size()) { "WAV is too large" }
                    output.write(buffer, 0, count)
                }
            }
            return output.toByteArray()
        }

        private fun tag(bytes: ByteArray, offset: Int): String = String(bytes, offset, 4, Charsets.US_ASCII)

        private fun unsigned16(bytes: ByteArray, offset: Int): Int =
            (bytes[offset].toInt() and 255) or ((bytes[offset + 1].toInt() and 255) shl 8)

        private fun unsigned32(bytes: ByteArray, offset: Int): Long =
            unsigned16(bytes, offset).toLong() or (unsigned16(bytes, offset + 2).toLong() shl 16)
    }
}
