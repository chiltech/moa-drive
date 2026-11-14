package com.moagauge.pleos.budget

import com.moagauge.pleos.navigation.GeoPoint
import kotlin.math.*

/** Estimates from the vehicle's remaining-range signal, without assuming a battery pack capacity. */
object BatteryBudgetCalculator {
    const val CONSUMPTION_MARGIN = 1.20
    const val RESULT_TTL_MS = 5 * 60_000L
    // NavigationLocation accepts fixes within 100m accuracy. A 200m drift budget
    // absorbs measurement variance without reusing a route from a distant street.
    const val MAX_ORIGIN_DRIFT_METERS = 200.0

    fun estimate(
        vehicle: BatteryBudgetVehicle,
        preferences: BatteryBudgetPreferences,
        outgoing: BatteryBudgetLeg,
        returning: BatteryBudgetLeg,
    ): BatteryBudgetEstimate? {
        if (!vehicle.valid || preferences.totalMinutes !in listOf(60, 90, 120, 180) ||
            preferences.reservePercent !in listOf(20, 30, 40, 50) || preferences.visitMinutes != 20 ||
            outgoing.distanceMeters <= 0 || returning.distanceMeters <= 0 ||
            outgoing.durationSeconds <= 0 || returning.durationSeconds <= 0) return null
        // Use Long before adding: route providers can report large values or malformed extremes.
        val distance = outgoing.distanceMeters.toLong() + returning.distanceMeters.toLong()
        val travel = outgoing.durationSeconds.toLong() + returning.durationSeconds.toLong()
        val total = travel + preferences.visitMinutes * 60L
        val consumed = vehicle.batteryPercent * (distance.toDouble() / (vehicle.rangeKm * 1_000.0)) * CONSUMPTION_MARGIN
        val remaining = vehicle.batteryPercent - consumed
        return BatteryBudgetEstimate(distance, travel, total, consumed, remaining.coerceAtLeast(0.0),
            total <= preferences.totalMinutes * 60L, remaining + 1e-9 >= preferences.reservePercent)
    }

    fun isCurrent(
        snapshot: BatteryBudgetSnapshot,
        preferences: BatteryBudgetPreferences,
        vehicle: BatteryBudgetVehicle?,
        origin: GeoPoint?,
        nowElapsedMs: Long,
    ): Boolean = vehicle?.valid == true && vehicle == snapshot.vehicle && preferences == snapshot.preferences &&
        origin != null && nowElapsedMs - snapshot.createdAtElapsedMs in 0 until RESULT_TTL_MS &&
        distanceMeters(snapshot.origin, origin) <= MAX_ORIGIN_DRIFT_METERS

    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val lat = Math.toRadians(b.latitude - a.latitude)
        val lon = Math.toRadians(b.longitude - a.longitude)
        val sinHalf = sin(lat / 2).pow(2) + cos(Math.toRadians(a.latitude)) *
            cos(Math.toRadians(b.latitude)) * sin(lon / 2).pow(2)
        return 6_371_000.0 * 2 * asin(sqrt(sinHalf.coerceIn(0.0, 1.0)))
    }
}
