package com.moagauge.pleos.trips

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/** Editable assumptions, never a live/local fuel-price feed or a charging bill. */
data class TripCostBasis(
    val gasolineWonPerLiter: Double = 1700.0,
    val benchmarkKmPerLiter: Double = 12.0,
    val electricityWonPerKwh: Double = 300.0,
) {
    val isValid: Boolean get() = listOf(gasolineWonPerLiter, benchmarkKmPerLiter, electricityWonPerKwh)
        .all { it.isFinite() && it > 0.0 }
}

data class TripCostSummary(
    val recordCount: Int = 0,
    val measuredCount: Int = 0,
    val totalDistanceKm: Double = 0.0,
    val measuredDistanceKm: Double = 0.0,
    val measuredEnergyKwh: Double? = null,
    val efficiencyKmPerKwh: Double? = null,
    val electricityCostWon: Double? = null,
    val gasolineCostWon: Double? = null,
    val savingsWon: Double? = null,
) {
    fun efficiencyChangePercent(previous: TripCostSummary?): Double? {
        val now = efficiencyKmPerKwh ?: return null
        val before = previous?.efficiencyKmPerKwh?.takeIf { it > 0.0 } ?: return null
        return ((now / before - 1.0) * 100.0).takeIf { it.isFinite() }
    }
}

object TripCostCalculator {
    /** Do not combine known energy with distance from an unknown or interrupted segment. */
    fun isMeasured(record: TripRecord): Boolean =
        record.status == TripStatus.COMPLETED && record.endedAtMs != null && !record.hasDataGap &&
            record.distanceMeters.isFinite() && record.distanceMeters >= 100.0 &&
            record.consumedWh?.let { it.isFinite() && it >= 50.0 } == true

    fun calculate(records: List<TripRecord>, basis: TripCostBasis = TripCostBasis()): TripCostSummary {
        val measured = records.filter(::isMeasured)
        val distanceKm = measured.sumOf { it.distanceMeters / 1000.0 }
        val energyKwh = measured.sumOf { it.consumedWh!! / 1000.0 }
        val valid = measured.isNotEmpty() && distanceKm.isFinite() && energyKwh.isFinite() && energyKwh > 0.0
        val electricity = if (valid && basis.isValid) (energyKwh * basis.electricityWonPerKwh).finite() else null
        val gasoline = if (valid && basis.isValid) (distanceKm / basis.benchmarkKmPerLiter * basis.gasolineWonPerLiter).finite() else null
        return TripCostSummary(
            recordCount = records.size,
            measuredCount = if (valid) measured.size else 0,
            totalDistanceKm = records.sumOf { it.distanceMeters.takeIf { meters -> meters.isFinite() && meters >= 0.0 }?.div(1000.0) ?: 0.0 }
                .takeIf { it.isFinite() } ?: 0.0,
            measuredDistanceKm = if (valid) distanceKm else 0.0,
            measuredEnergyKwh = energyKwh.takeIf { valid },
            efficiencyKmPerKwh = if (valid) (distanceKm / energyKwh).finite() else null,
            electricityCostWon = electricity,
            gasolineCostWon = gasoline,
            savingsWon = if (gasoline != null && electricity != null) (gasoline - electricity).finite() else null,
        )
    }

    private fun Double.finite() = takeIf { it.isFinite() }
}

object TripCostFormat {
    fun basisLabel(basis: TripCostBasis): String =
        "비교 기준 · 휘발유 ${exact(basis.gasolineWonPerLiter)}원/L · " +
            "평균 연비 ${exact(basis.benchmarkKmPerLiter)} km/L · 전력 ${exact(basis.electricityWonPerKwh)}원/kWh"

    fun exact(value: Double): String = if (value.isFinite()) BigDecimal.valueOf(value).stripTrailingZeros().toPlainString() else "—"

    fun won(value: Double?): String = value?.takeIf { it.isFinite() }?.let {
        NumberFormat.getIntegerInstance(Locale.KOREAN).format(BigDecimal.valueOf(it).setScale(0, RoundingMode.HALF_UP)) + "원"
    } ?: "—"

    fun decimal(value: Double?, places: Int = 1): String = value?.takeIf { it.isFinite() }
        ?.let { String.format(Locale.KOREAN, "%.${places}f", it) } ?: "—"
}
