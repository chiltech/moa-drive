package com.moagauge.pleos.sound

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt

data class SoundParameters(
    val profile: SoundProfile,
    val speedKmh: Double,
    val gear: String,
    val volume: Double,
    val ducking: Double = 1.0,
) {
    internal val valid get() = speedKmh.isFinite() && speedKmh in 0.0..360.0 && gear.isSoundGear() &&
        volume.isFinite() && ducking.isFinite()
}

/** One foreground PCM path for generated loops, electronic voices and real recordings. */
internal class SoundSynthesizer(
    private val sampleRate: Int = SAMPLE_RATE,
    private val recordings: Map<SoundProfile, RecordedLoop> = emptyMap(),
) {
    var parameters = SoundParameters(SoundProfile.AURORA, 0.0, "P", 0.0)
    private val engine = EngineSoundModel()
    internal val engineState get() = engine.state
    private val electronic = ElectronicVoice(sampleRate)
    private var gain = 0.0
    private var torqueGain = 1.0
    private var controlRemaining = 0
    private val controlFrames = (sampleRate / 200).coerceAtLeast(1)
    private val profiles = SoundProfile.entries.toTypedArray()
    private val weights = DoubleArray(profiles.size)
    private val bodies = DoubleArray(profiles.size)
    private var bodyRamp = 1.0 - exp(-2.0 * PI * 2_200.0 / sampleRate)
    private var started = false
    private val gainRamp = 1.0 - exp(-1.0 / (sampleRate * .120))
    private val torqueRamp = 1.0 - exp(-1.0 / (sampleRate * .008))
    private val blendRamp = 1.0 - exp(-1.0 / (sampleRate * .020))

    init { require(sampleRate >= 16_000) }

    fun render(samples: ShortArray) {
        val p = parameters
        if (!p.valid) {
            samples.fill(0)
            gain = 0.0
            torqueGain = 1.0
            engine.step(Double.NaN, "?", .005)
            controlRemaining = 0
            return
        }
        check(p.profile.recordingAsset == null || recordings.containsKey(p.profile)) {
            "Recording unavailable: ${p.profile.name}"
        }
        if (!started) {
            weights[p.profile.ordinal] = 1.0
            started = true
        }
        // Stop without a residual fade when the vehicle reaches zero; departure fades in again.
        val muted = p.speedKmh == 0.0 || p.volume <= 0.0 || p.ducking <= 0.0
        if (muted) gain = 0.0
        val volume = p.volume.coerceIn(0.0, 1.0) * p.ducking.coerceIn(0.0, 1.0) *
            SoundSpeedEnvelope.gainFor(p.speedKmh) * .45
        for (index in samples.indices) {
            // Persist the 5 ms control clock across buffers and preview updates.
            if (controlRemaining == 0) {
                engine.step(p.speedKmh, p.gear, controlFrames.toDouble() / sampleRate)
                val cutoff = 2_200.0 + engine.state.load * 3_000.0 - engine.state.braking * 900.0
                bodyRamp = 1.0 - exp(-2.0 * PI * cutoff / sampleRate)
                controlRemaining = controlFrames
            }
            controlRemaining--
            val state = engine.state
            val torque = (.92 + .08 * state.load) * (1.0 - .72 * state.shiftCut) * (1.0 - .10 * state.braking)
            // Road-speed volume fades gradually; the shorter shift gesture keeps its own response.
            gain += (volume - gain) * gainRamp
            torqueGain += (torque - torqueGain) * torqueRamp
            var wave = 0.0
            for (id in profiles.indices) {
                val profile = profiles[id]
                val target = if (profile == p.profile) 1.0 else 0.0
                weights[id] += (target - weights[id]) * blendRamp
                if (weights[id] < .00001 && target == 0.0) {
                    weights[id] = 0.0
                    continue
                }
                val voice = when (profile) {
                    SoundProfile.AURORA -> electronic.next(ElectronicTone.AURORA, state, p.speedKmh)
                    SoundProfile.PULSE -> electronic.next(ElectronicTone.PULSE, state, p.speedKmh)
                    SoundProfile.PRISM -> electronic.next(ElectronicTone.PRISM, state, p.speedKmh)
                    else -> {
                        val recording = checkNotNull(recordings[profile])
                        // Every sampled voice stays at source speed; only device sample-rate conversion remains.
                        val raw = recording.next(recording.sampleRate.toDouble() / sampleRate)
                        // A small load-responsive body filter keeps the original mechanical texture.
                        bodies[id] += (raw - bodies[id]) * bodyRamp
                        .8 * raw + .2 * bodies[id]
                    }
                }
                wave += voice * weights[id]
            }
            samples[index] = if (muted) 0 else
                (wave * gain * torqueGain * Short.MAX_VALUE).roundToInt().coerceIn(-14_745, 14_745).toShort()
        }
    }

    companion object { const val SAMPLE_RATE = 48_000 }
}
