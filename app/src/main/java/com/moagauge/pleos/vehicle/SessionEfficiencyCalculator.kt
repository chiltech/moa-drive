package com.moagauge.pleos.vehicle

import kotlin.math.abs

/**
 * Estimated foreground-session efficiency when the vehicle does not supply trip statistics.
 * Integrates measured vehicle speed against monotonic sample time and divides by net battery use.
 * A missing reading or a gap longer than 3 seconds starts a new observation window.
 */
class SessionEfficiencyCalculator {
    private data class Sample(val speed: Double, val energy: Double, val nanos: Long)
    private var previous: Sample? = null
    private var baselineWh = 0.0
    private var distanceMeters = 0.0
    var value: Double? = null
        private set

    fun sample(speedMetersPerSecond: Float?, energyWh: Float?, timestampNanos: Long): Double? {
        val speed = speedMetersPerSecond?.takeIf { it.isFinite() && abs(it) <= 100f }?.let { abs(it.toDouble()) }
        val energy = energyWh?.takeIf { it.isFinite() && it >= 0f }?.toDouble()
        if (speed == null || energy == null || timestampNanos < 0) { reset(); return null }
        val next = Sample(speed, energy, timestampNanos)
        val old = previous
        if (old != null && timestampNanos <= old.nanos) return value
        if (old == null || timestampNanos - old.nanos > 3_000_000_000L || energy > baselineWh + 50.0) {
            previous = next; baselineWh = energy; distanceMeters = 0.0; value = null
            return null
        }
        val seconds = (timestampNanos - old.nanos) / 1_000_000_000.0
        distanceMeters += (old.speed + speed) / 2.0 * seconds
        previous = next
        val consumedWh = baselineWh - energy
        // meters / Wh is numerically equal to km / kWh. Thresholds suppress sensor noise.
        value = if (distanceMeters >= 100.0 && consumedWh >= 50.0) distanceMeters / consumedWh else null
        return value
    }

    fun reset() { previous = null; baselineWh = 0.0; distanceMeters = 0.0; value = null }
}
