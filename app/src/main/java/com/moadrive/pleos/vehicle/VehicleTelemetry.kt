package com.moadrive.pleos.vehicle

import kotlin.math.abs
import kotlin.math.roundToInt

data class VehicleTelemetry(
    val active: Boolean = false,
    val speedKmh: Int? = null,
    val gear: String? = null,
    val batteryPercent: Int? = null,
    val rangeKm: Int? = null,
    val efficiencyKmPerKwh: Double? = null,
    val efficiencyLabel: String = "운행 전비",
    val ignitionOn: Boolean? = null,
    val speedObservedElapsedMs: Long? = null,
) {
    val hasData get() = speedKmh != null || gear != null || batteryPercent != null || rangeKm != null || efficiencyKmPerKwh != null
    val connectionLabel get() = when { !active -> "차량 연결 대기"; hasData -> "차량 데이터"; else -> "차량 정보 확인 중" }
}

/** Overspeed decorations use only a recently observed speed; connection state alone is insufficient. */
fun VehicleTelemetry.freshSpeedKmh(nowElapsedMs: Long, maxAgeMs: Long = 3_000L): Int? {
    val observed = speedObservedElapsedMs ?: return null
    if (!active || observed < 0L || nowElapsedMs < observed || maxAgeMs < 0L || nowElapsedMs - observed > maxAgeMs) return null
    return speedKmh?.takeIf { it in 0..300 }
}

/** Raw, read-only sample for persistent trip accounting; no rounded display values. */
data class VehicleReading(
    val wallTimeMs: Long,
    val monotonicMs: Long,
    val speedMps: Double?,
    val batteryWh: Double?,
    val gear: String?,
    val batteryPercent: Int?,
    val ignitionOn: Boolean?,
)

/** Public-source units are converted once, before any UI consumes them. */
object VehicleValues {
    fun speedKmh(metersPerSecond: Float?): Int? = metersPerSecond?.takeIf { it.isFinite() && abs(it) <= 100f }?.let { (abs(it) * 3.6f).roundToInt() }
    fun batteryPercent(value: Int?): Int? = value?.takeIf { it in 0..100 }
    fun rangeKm(meters: Float?): Int? = meters?.takeIf { it.isFinite() && it in 0f..2_000_000f }?.let { (it / 1000f).roundToInt() }
    fun efficiency(value: Float?): Double? = value?.takeIf { it.isFinite() && it >= 0f }?.toDouble()
    // AAOS VehicleGear bit constants, kept independent of Android for regression tests.
    fun gear(value: Int?): String? = when (value) {
        1 -> "N"; 2 -> "R"; 4 -> "P"; 8 -> "D"
        16 -> "1"; 32 -> "2"; 64 -> "3"; 128 -> "4"; 256 -> "5"; 512 -> "6"; 1024 -> "7"; 2048 -> "8"; 4096 -> "9"
        else -> null
    }
}

/** All calls are serialized by the repository's main-thread boundary. */
class TelemetrySession {
    var value = VehicleTelemetry()
        private set
    private var generation = 0L
    fun start(): Long { generation++; value = VehicleTelemetry(active = true); return generation }
    fun stop() { generation++; value = VehicleTelemetry() }
    fun accepts(token: Long) = value.active && generation == token
    fun update(token: Long, transform: (VehicleTelemetry) -> VehicleTelemetry): Boolean {
        if (!accepts(token)) return false
        value = transform(value)
        return true
    }
}
