package com.moagauge.pleos.sound

import kotlin.math.sqrt

/** Shared fraction of the user's chosen volume; the renderer smooths positive-speed changes. */
object SoundSpeedEnvelope {
    fun gainFor(speedKmh: Double): Double {
        if (!speedKmh.isFinite() || speedKmh !in 0.0..360.0) return 0.0
        return sqrt((speedKmh / 80.0).coerceIn(0.0, 1.0))
    }
}
