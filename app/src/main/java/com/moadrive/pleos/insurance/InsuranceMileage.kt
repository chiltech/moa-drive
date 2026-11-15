package com.moadrive.pleos.insurance

import com.moadrive.pleos.trips.TripRecord
import com.moadrive.pleos.trips.TripStatus
import java.time.LocalDate
import java.time.YearMonth

data class InsuranceMileage(val totalKm: Double, val monthKm: Double, val tripCount: Int, val incompleteCount: Int) {
    companion object {
        fun from(records: List<TripRecord>, today: LocalDate = LocalDate.now()): InsuranceMileage {
            val ended = records.filter { !it.isActive && it.distanceMeters.isFinite() && it.distanceMeters >= 0.0 }
                .sortedByDescending { it.endedAtMs ?: it.startedAtMs }.distinctBy { it.id }
            val month = YearMonth.from(today)
            return InsuranceMileage(
                totalKm = ended.sumOf { it.distanceMeters } / 1000.0,
                monthKm = ended.filter { runCatching { YearMonth.from(LocalDate.parse(it.startDate)) == month }.getOrDefault(false) }
                    .sumOf { it.distanceMeters } / 1000.0,
                tripCount = ended.size,
                incompleteCount = ended.count { it.hasDataGap || it.status == TripStatus.INTERRUPTED },
            )
        }
    }
}
