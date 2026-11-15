package com.moadrive.pleos.safety.score

import kotlin.math.min
import kotlin.math.roundToInt

/** MOA reference screening only. This has no insurer connection and never returns a discount rate. */
object DrivingScoreEligibility {
    fun evaluate(
        records: List<DrivingScoreRecord>,
        minimumScore: Int = 80,
        minimumDistanceKm: Double = 500.0,
    ): DrivingScoreEligibilityResult {
        require(minimumScore in 0..100)
        require(minimumDistanceKm.isFinite() && minimumDistanceKm >= 0.0)
        // A later checkpoint replaces the same trip; repeated records never create extra distance.
        val measured = records.associateBy { it.tripId }.values.mapNotNull { record ->
            record.score?.let { score -> score to min(record.observedDistanceMeters, record.tripDistanceMeters) }
        }
        val distanceMeters = measured.sumOf { it.second }
        val distanceKm = distanceMeters / 1_000.0
        val score = if (distanceMeters > 0.0 && distanceMeters.isFinite())
            (measured.sumOf { it.first * (it.second / distanceMeters) }).roundToInt().coerceIn(0, 100) else null
        return DrivingScoreEligibilityResult(
            score = score,
            distanceKm = distanceKm.takeIf { it.isFinite() } ?: 0.0,
            scoredTrips = measured.size,
            meetsReference = score != null && score >= minimumScore && distanceKm.isFinite() && distanceKm >= minimumDistanceKm,
            pointsRemaining = score?.let { (minimumScore - it).coerceAtLeast(0) },
            kilometersRemaining = (minimumDistanceKm - (distanceKm.takeIf { it.isFinite() } ?: 0.0)).coerceAtLeast(0.0),
        )
    }
}
