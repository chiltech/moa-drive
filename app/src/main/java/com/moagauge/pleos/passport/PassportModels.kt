package com.moagauge.pleos.passport

import kotlin.math.floor
import kotlin.math.abs

/** A stale or invalid powertrain value cannot turn GPS drift into a visited area. */
fun isPassportMotion(speedMps: Double?, gear: String?): Boolean =
    speedMps != null && speedMps.isFinite() && abs(speedMps) in (3.0 / 3.6)..100.0 &&
        (gear == "D" || gear == "R" || gear == "N" || (gear?.length == 1 && gear[0] in '1'..'9'))

data class PassportFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val wallTimeMs: Long,
    val monotonicMs: Long,
)

data class PassportCell(
    val id: String,
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    val centerLatitude: Double get() = (south + north) / 2
    val centerLongitude: Double get() = (west + east) / 2
    val title: String get() = "탐험 구역 ${id.removePrefix("g1:").replace(":", " · ")}"

    companion object {
        /** Geographic 0.01° cells, roughly 1km across in Korea; not administrative borders. */
        fun from(latitude: Double, longitude: Double): PassportCell? {
            if (!latitude.isFinite() || !longitude.isFinite() ||
                latitude < -90.0 || latitude >= 90.0 || longitude < -180.0 || longitude >= 180.0
            ) return null
            val row = floor(latitude * 100.0).toInt()
            val column = floor(longitude * 100.0).toInt()
            return PassportCell("g1:$row:$column", row / 100.0, column / 100.0,
                (row + 1) / 100.0, (column + 1) / 100.0)
        }
    }
}

data class PassportVisit(
    val tripId: String,
    val cell: PassportCell,
    val visitedAtMs: Long,
    val placeName: String? = null,
)
