package com.moadrive.pleos.sound

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

internal enum class ElectronicTone { AURORA, PULSE, PRISM }

/** Original procedural EV textures; no recordings, noise source, or note/alert sequencer. */
internal class ElectronicVoice(private val sampleRate: Int = 48_000) {
    init { require(sampleRate >= 16_000) }

    private val radiansPerHz = 2.0 * PI / sampleRate
    private val pitchResponse = 1.0 - exp(-1.0 / (sampleRate * .09))
    private val textureResponse = 1.0 - exp(-1.0 / (sampleRate * .045))
    private val voices = Array(ElectronicTone.entries.size) { VoiceState() }

    /** Audio-thread operation: persistent independent phases, bounded controls, no allocations. */
    fun next(tone: ElectronicTone, state: EngineSoundFrame, speedKmh: Double): Double {
        val voice = voices[tone.ordinal]
        voice.speed += (bounded(speedKmh, 0.0, 360.0, voice.speed) - voice.speed) * pitchResponse
        voice.rpm += (bounded(state.rpm, 600.0, 6_000.0, voice.rpm) - voice.rpm) * pitchResponse
        voice.load += (bounded(state.load, 0.0, 1.0, voice.load) - voice.load) * textureResponse
        voice.braking += (bounded(state.braking, 0.0, 1.0, voice.braking) - voice.braking) * textureResponse
        voice.shift += (bounded(state.shiftCut, 0.0, 1.0, voice.shift) - voice.shift) * textureResponse
        // A shift releases upper texture smoothly. The renderer applies the actual torque/volume dip.
        val drive = voice.load * (1.0 - .78 * voice.shift)
        return when (tone) {
            ElectronicTone.AURORA -> aurora(voice, drive)
            ElectronicTone.PULSE -> pulse(voice, drive)
            ElectronicTone.PRISM -> prism(voice, drive)
        }
    }

    private fun aurora(v: VoiceState, drive: Double): Double {
        // A low harmonic bed, a gently detuned unison, and an open fifth form a warm moving chord.
        // There is no triggered envelope: motion comes from slow beating and continuous pitch glide.
        val hz = 104.0 + v.speed * .80 + v.rpm * .020
        v.a = advance(v.a, hz)
        v.b = advance(v.b, hz * 1.003 + .13)
        v.c = advance(v.c, hz * 1.498)
        v.d = advance(v.d, .13 + v.speed * .0005)
        val breath = sin(v.d)
        val upper = .35 * (1.0 - .78 * v.shift) + .65 * drive
        val regen = .025 * v.braking
        return .53 * sin(v.a) +
            .16 * sin(v.b + .05 * breath) +
            (.12 - regen) * sin(v.c) +
            (.12 * upper + regen) * sin(2.0 * v.a + .08 * breath) +
            .07 * upper * sin(3.0 * v.a + .20 * v.braking)
    }

    private fun pulse(v: VoiceState, drive: Double): Double {
        // A bass rotor with smooth amplitude cycles, rather than discrete pulses or combustion noise.
        val hz = 42.0 + v.speed * .38 + v.rpm * .005
        v.a = advance(v.a, hz)
        v.b = advance(v.b, 1.65 + v.speed * .045)
        v.c = advance(v.c, hz * .5)
        val rotor = sin(v.c)
        val depth = .26 + .07 * drive - .04 * v.braking
        val envelope = 1.0 - depth + depth * sin(v.b)
        val motor = .75 * sin(v.a + (.22 * (1.0 - .78 * v.shift) + .22 * drive + .16 * v.braking) * rotor) +
            (.10 + .10 * drive) * sin(2.0 * v.a + .12 * rotor) +
            (.025 + .05 * drive + .03 * v.braking) * sin(3.0 * v.a)
        // The sum of all carrier amplitudes is <= 1.055, so this retains strict peak headroom.
        return .94 * envelope * motor
    }

    private fun prism(v: VoiceState, drive: Double): Double {
        // Continuous inharmonic phase modulation gives a glassy spectrum without bell strikes,
        // alternating notes, or a warning-style amplitude pattern. The low anchor keeps it a drive bed.
        val hz = 236.0 + v.speed * 1.70 + v.rpm * .040
        v.a = advance(v.a, hz)
        v.b = advance(v.b, hz * 1.41421356237)
        v.c = advance(v.c, hz * 2.003)
        v.d = advance(v.d, hz * 2.756)
        v.e = advance(v.e, hz * .5)
        v.f = advance(v.f, .17 + v.speed * .0008)
        val shimmer = sin(v.f)
        // At the highest accepted speed/RPM the strongest FM sidebands remain below 8 kHz.
        val index = .48 * (1.0 - .55 * v.shift) + .60 * drive + .18 * v.braking
        return .52 * sin(v.a + index * sin(v.b)) +
            .21 * sin(v.c + (.22 + .20 * drive) * sin(v.b + .10 * shimmer)) +
            .13 * sin(v.d + .12 * shimmer + .32 * v.braking) +
            .10 * sin(v.e)
    }

    private fun advance(phase: Double, hz: Double): Double {
        val next = phase + hz * radiansPerHz
        return if (next >= TWO_PI) next - TWO_PI else next
    }

    private fun bounded(value: Double, minimum: Double, maximum: Double, fallback: Double): Double =
        if (value.isFinite()) value.coerceIn(minimum, maximum) else fallback

    private class VoiceState {
        var speed = 0.0
        var rpm = 900.0
        var load = .18
        var braking = 0.0
        var shift = 0.0
        var a = 0.0
        var b = 0.0
        var c = 0.0
        var d = 0.0
        var e = 0.0
        var f = 0.0
    }

    private companion object { const val TWO_PI = 2.0 * PI }
}
