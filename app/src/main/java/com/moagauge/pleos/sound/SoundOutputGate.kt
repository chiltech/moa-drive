package com.moagauge.pleos.sound

/** Serializes the PCM writer with transport changes from the vehicle/focus callbacks. */
internal class SoundOutputGate(private val preview: Boolean) {
    interface Sink {
        fun volume(value: Float)
        fun pause()
        fun flush()
        fun play()
        fun write(samples: ShortArray, offset: Int, count: Int): Int
    }
    data class Block(val epoch: Long)
    enum class MotionChange { NONE, STARTED, STOPPED }
    private var sink: Sink? = null
    private var running = true
    private var focusPaused = false
    private var moving = false
    private var playing = false
    @Volatile var epoch: Long = 0
        private set
    private val mayPlay get() = running && !focusPaused && (preview || moving)

    @Synchronized fun attach(output: Sink) {
        sink = output
        playing = false
        if (!mayPlay) silence(flush = !preview || !running)
    }

    @Synchronized fun detach(output: Sink) {
        if (sink === output) { sink = null; playing = false }
    }

    @Synchronized fun updateSpeed(speedKmh: Double): MotionChange {
        if (preview || !running) return MotionChange.NONE
        val next = speedKmh.isFinite() && speedKmh > 0.0 && speedKmh <= 360.0
        if (moving == next) return MotionChange.NONE
        moving = next
        if (!next) {
            epoch++
            // A new zero-filled block cannot silence nonzero PCM that is already queued.
            silence(flush = true)
        }
        return if (next) MotionChange.STARTED else MotionChange.STOPPED
    }

    @Synchronized fun beginBlock(): Block? = if (mayPlay) Block(epoch) else null

    /** null rejects a stale/paused block; zero means the nonblocking device queue is full. */
    @Synchronized fun write(block: Block, samples: ShortArray, offset: Int, count: Int): Int? {
        if (!mayPlay || block.epoch != epoch) return null
        val output = sink ?: return null
        start(output)
        return output.write(samples, offset, count)
    }

    @Synchronized fun pauseForFocus() {
        focusPaused = true
        if (!preview) epoch++
        silence(flush = !preview)
    }

    @Synchronized fun resumeFocus() { focusPaused = false }

    /** Only a preview may resume queued PCM without preparing a fresh motion-validated block. */
    @Synchronized fun resumePreview() {
        if (preview && mayPlay) sink?.let(::start)
    }

    @Synchronized fun stop() {
        running = false
        epoch++
        silence(flush = true)
    }

    private fun start(output: Sink) {
        if (!playing) {
            output.volume(1f)
            output.play()
            playing = true
        }
    }

    private fun silence(flush: Boolean) {
        playing = false
        sink?.let { output ->
            // Muting first closes the mixer path while pause/flush discard outstanding PCM.
            try { output.volume(0f) }
            finally {
                try { output.pause() }
                finally { if (flush) output.flush() }
            }
        }
    }
}
