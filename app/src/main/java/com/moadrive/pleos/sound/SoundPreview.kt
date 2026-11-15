package com.moadrive.pleos.sound

/** Preview parameters advance with PCM frames, independently of vehicle telemetry. */
internal object SoundPreview {
    const val TOTAL_FRAMES = 5L * SoundSynthesizer.SAMPLE_RATE

    fun parametersAt(frame: Long, current: SoundParameters): SoundParameters {
        val ms = frame.coerceIn(0L, TOTAL_FRAMES) * 1_000.0 / SoundSynthesizer.SAMPLE_RATE
        val speed = when {
            ms < 300.0 -> 0.0
            ms < 3_000.0 -> 100.0 * smooth((ms - 300.0) / 2_700.0)
            ms < 4_700.0 -> 100.0 * (1.0 - smooth((ms - 3_000.0) / 1_700.0))
            else -> 0.0
        }
        // Leave a short silent tail so the renderer's gain ramp settles before track release.
        val fade = ((4_800.0 - ms) / 200.0).coerceIn(0.0, 1.0)
        return current.copy(speedKmh = speed, gear = "D", volume = current.volume * fade)
    }

    private fun smooth(t: Double): Double = t * t * (3.0 - 2.0 * t)
}
