package com.moagauge.pleos.budget

import com.moagauge.pleos.navigation.GeoPoint
import com.moagauge.pleos.navigation.NavigationDestination

data class BatteryBudgetPreferences(
    val totalMinutes: Int = 90,
    val reservePercent: Int = 30,
    val visitMinutes: Int = 20,
)

data class BatteryBudgetVehicle(val batteryPercent: Int, val rangeKm: Int) {
    val valid get() = batteryPercent in 1..100 && rangeKm in 1..2_000
}

data class BatteryBudgetLeg(val distanceMeters: Int, val durationSeconds: Int)

data class BatteryBudgetEstimate(
    val distanceMeters: Long,
    val travelSeconds: Long,
    val totalSeconds: Long,
    val consumedPercent: Double,
    val returnPercent: Double,
    val withinTime: Boolean,
    val withinReserve: Boolean,
) {
    val feasible get() = withinTime && withinReserve
}

data class BatteryBudgetCandidate(
    val cityName: String,
    val province: String,
    val destination: NavigationDestination,
    val estimate: BatteryBudgetEstimate,
)

data class BatteryBudgetSnapshot(
    val preferences: BatteryBudgetPreferences,
    val vehicle: BatteryBudgetVehicle,
    val origin: GeoPoint,
    val createdAtElapsedMs: Long,
)

data class BatteryBudgetState(
    val preferences: BatteryBudgetPreferences = BatteryBudgetPreferences(),
    val batteryPercent: Int? = null,
    val rangeKm: Int? = null,
    val locationReady: Boolean = false,
    val canCalculate: Boolean = false,
    val loading: Boolean = false,
    val isPreview: Boolean = false,
    val candidates: List<BatteryBudgetCandidate> = emptyList(),
    val message: String? = "나들이 시간과 귀가할 때 남길 배터리를 정해 보세요.",
    val calculatedAtMs: Long? = null,
    val failedCandidates: Int = 0,
)
