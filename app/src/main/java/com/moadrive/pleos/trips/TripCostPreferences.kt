package com.moadrive.pleos.trips

import android.content.Context

/** Separate preferences leave recording settings and stored trip records untouched. */
class TripCostPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("trip_cost_comparison", Context.MODE_PRIVATE)

    fun load(): TripCostBasis {
        val defaults = TripCostBasis()
        fun read(key: String, fallback: Double): Double = runCatching { preferences.getString(key, null)?.toDoubleOrNull() }
            .getOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: fallback
        return TripCostBasis(
            read("gasoline_won_per_liter", defaults.gasolineWonPerLiter),
            read("benchmark_km_per_liter", defaults.benchmarkKmPerLiter),
            read("electricity_won_per_kwh", defaults.electricityWonPerKwh),
        )
    }

    fun save(basis: TripCostBasis) {
        require(basis.isValid)
        preferences.edit()
            .putString("gasoline_won_per_liter", basis.gasolineWonPerLiter.toString())
            .putString("benchmark_km_per_liter", basis.benchmarkKmPerLiter.toString())
            .putString("electricity_won_per_kwh", basis.electricityWonPerKwh.toString())
            .apply()
    }
}
