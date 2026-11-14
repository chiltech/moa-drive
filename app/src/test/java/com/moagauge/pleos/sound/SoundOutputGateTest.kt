package com.moagauge.pleos.sound

import org.junit.Assert.*
import org.junit.Test

class SoundOutputGateTest {
    @Test fun zeroSpeedDiscardsAlreadyQueuedSoundAndCannotRestartUntilMotion() {
        val sink = QueuedOutput()
        val gate = SoundOutputGate(preview = false)
        gate.attach(sink)
        gate.updateSpeed(50.0)
        gate.write(gate.beginBlock()!!, shortArrayOf(1111, 1111, 1111), 0, 3)
        gate.updateSpeed(0.0)
        assertArrayEquals(shortArrayOf(0, 0, 0), sink.consume(3))
        assertNull(gate.beginBlock())
        gate.resumeFocus()
        gate.resumePreview()
        assertNull(gate.beginBlock())
        assertArrayEquals(shortArrayOf(0, 0), sink.consume(2))
        gate.updateSpeed(25.0)
        gate.write(gate.beginBlock()!!, shortArrayOf(2222, 2222), 0, 2)
        assertArrayEquals(shortArrayOf(2222, 2222), sink.consume(2))
    }

    @Test fun quickStopAndDepartureRejectsRenderedFramesFromBeforeTheStop() {
        val sink = QueuedOutput()
        val gate = SoundOutputGate(preview = false)
        gate.attach(sink)
        gate.updateSpeed(50.0)
        val oldBlock = gate.beginBlock()!!
        gate.updateSpeed(0.0)
        gate.updateSpeed(10.0)
        assertNull(gate.write(oldBlock, shortArrayOf(1111), 0, 1))
        val nextBlock = gate.beginBlock()!!
        assertNotEquals(oldBlock.epoch, nextBlock.epoch)
        gate.write(nextBlock, shortArrayOf(2222), 0, 1)
        assertArrayEquals(shortArrayOf(2222), sink.consume(1))
    }

    @Test fun partialBlockCannotRefillTheQueueAfterAStopAndResume() {
        val sink = QueuedOutput(capacity = 2)
        val gate = SoundOutputGate(preview = false)
        gate.attach(sink)
        gate.updateSpeed(60.0)
        val block = gate.beginBlock()!!
        val old = shortArrayOf(1111, 1112, 1113, 1114)
        assertEquals(2, gate.write(block, old, 0, old.size))
        gate.updateSpeed(0.0)
        gate.updateSpeed(1.0)
        assertNull(gate.write(block, old, 2, 2))
        gate.write(gate.beginBlock()!!, shortArrayOf(2221, 2222), 0, 2)
        assertArrayEquals(shortArrayOf(2221, 2222), sink.consume(2))
    }

    @Test fun startingAtRestNeverPlaysAndAttachingAfterStopCannotResurrectAudio() {
        val gate = SoundOutputGate(preview = false)
        val sink = QueuedOutput()
        gate.attach(sink)
        gate.updateSpeed(0.0)
        assertNull(gate.beginBlock())
        gate.stop()
        val later = QueuedOutput()
        gate.attach(later)
        gate.updateSpeed(80.0)
        gate.resumeFocus()
        gate.resumePreview()
        assertNull(gate.beginBlock())
        assertArrayEquals(shortArrayOf(0, 0), later.consume(2))
    }

    @Test fun stoppingBeforeFocusReturnsKeepsTransportSilent() {
        val gate = SoundOutputGate(preview = false)
        val sink = QueuedOutput()
        gate.attach(sink); gate.updateSpeed(40.0)
        val oldBlock = gate.beginBlock()!!
        gate.write(oldBlock, shortArrayOf(1000, 1000), 0, 2)
        gate.pauseForFocus()
        gate.updateSpeed(0.0)
        gate.resumeFocus()
        assertNull(gate.beginBlock())
        assertNull(gate.write(oldBlock, shortArrayOf(1000), 0, 1))
        assertArrayEquals(shortArrayOf(0, 0), sink.consume(2))
    }

    @Test fun focusLossInvalidatesLivePartialFramesEvenIfTheVehicleKeepsMoving() {
        val gate = SoundOutputGate(preview = false)
        val sink = QueuedOutput()
        gate.attach(sink); gate.updateSpeed(40.0)
        val oldBlock = gate.beginBlock()!!
        gate.write(oldBlock, shortArrayOf(1000, 1000), 0, 2)
        gate.pauseForFocus()
        assertNull(gate.beginBlock())
        gate.resumeFocus()
        assertNull(gate.write(oldBlock, shortArrayOf(1000), 0, 1))
        gate.write(gate.beginBlock()!!, shortArrayOf(2000), 0, 1)
        assertArrayEquals(shortArrayOf(2000), sink.consume(1))
    }

    @Test fun previewRetainsItsQueuedFramesAcrossFocusAndIgnoresVehicleStandstill() {
        val gate = SoundOutputGate(preview = true)
        val sink = QueuedOutput(capacity = 2)
        gate.attach(sink); gate.updateSpeed(0.0)
        val block = gate.beginBlock()!!
        val samples = shortArrayOf(101, 102, 103, 104)
        assertEquals(2, gate.write(block, samples, 0, 4))
        gate.pauseForFocus()
        assertArrayEquals(shortArrayOf(0, 0), sink.consume(2))
        gate.updateSpeed(40.0); gate.updateSpeed(0.0)
        gate.resumeFocus(); gate.resumePreview()
        assertArrayEquals(shortArrayOf(101, 102), sink.consume(2))
        assertEquals(2, gate.write(block, samples, 2, 2))
        assertArrayEquals(shortArrayOf(103, 104), sink.consume(2))
    }

    @Test fun explicitStopRejectsLateWritesAndLateFocusGainForBothModes() {
        for (preview in listOf(false, true)) {
            val gate = SoundOutputGate(preview)
            val sink = QueuedOutput()
            gate.attach(sink); gate.updateSpeed(20.0)
            val block = gate.beginBlock()!!
            gate.write(block, shortArrayOf(1000), 0, 1)
            gate.stop(); gate.resumeFocus(); gate.updateSpeed(30.0)
            assertNull(gate.write(block, shortArrayOf(2000), 0, 1))
            gate.resumePreview()
            assertArrayEquals(shortArrayOf(0, 0), sink.consume(2))
        }
    }

    /** Audio hardware model: consuming a paused/muted sink is quiet; flush requires pause. */
    private class QueuedOutput(private val capacity: Int = 32) : SoundOutputGate.Sink {
        private val queue = ArrayDeque<Short>()
        private var playing = false
        private var volume = 1f
        override fun volume(value: Float) { volume = value }
        override fun pause() { playing = false }
        override fun flush() { check(!playing); queue.clear() }
        override fun play() { playing = true }
        override fun write(samples: ShortArray, offset: Int, count: Int): Int {
            val accepted = minOf(count, capacity - queue.size)
            repeat(accepted) { queue.addLast(samples[offset + it]) }
            return accepted
        }
        fun consume(count: Int): ShortArray = ShortArray(count) {
            if (!playing) 0 else {
                val value = queue.removeFirstOrNull() ?: 0
                (value * volume).toInt().toShort()
            }
        }
    }
}
