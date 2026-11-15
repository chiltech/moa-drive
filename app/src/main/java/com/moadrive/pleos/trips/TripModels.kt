package com.moadrive.pleos.trips

enum class TripStatus { RECORDING, PARKING, COMPLETED, INTERRUPTED }

data class TripSample(
    val wallTimeMs: Long,
    val monotonicMs: Long,
    val speedMps: Double?,
    val gear: String?,
    val batteryWh: Double?,
    val batteryPercent: Int?,
    val ignitionOn: Boolean? = null,
)

data class TripRecord(
    val id: String,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val startDate: String,
    val zoneId: String,
    val durationMs: Long,
    val distanceMeters: Double,
    val consumedWh: Double?,
    val batteryStartPercent: Int?,
    val batteryEndPercent: Int?,
    val status: TripStatus,
    val hasDataGap: Boolean = false,
) {
    /** Meters / Wh is numerically equal to km / kWh. Small totals amplify sensor noise. */
    val averageEfficiencyKmPerKwh: Double?
        get() = consumedWh?.takeIf {
            it.isFinite() && it >= 50.0 && distanceMeters.isFinite() &&
                distanceMeters >= 100.0 && !hasDataGap
        }?.let { distanceMeters / it }

    val isActive: Boolean
        get() = status == TripStatus.RECORDING || status == TripStatus.PARKING
}
