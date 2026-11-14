package com.moagauge.pleos.safety.score

/** Local observations for one real trip. A missing score means insufficient or invalid coverage. */
data class DrivingScoreRecord(
    val tripId: String,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val startDate: String,
    val tripDurationMs: Long,
    val tripDistanceMeters: Double,
    val observedDurationMs: Long = 0L,
    val observedDistanceMeters: Double = 0.0,
    val speedLimitObservedMs: Long = 0L,
    val accelerationEvents: Int = 0,
    val brakingEvents: Int = 0,
    val overspeedEvents: Int = 0,
    val completed: Boolean = false,
    val tripHasDataGap: Boolean = false,
) {
    val durationCoverage: Double
        get() = if (tripDurationMs > 0L && observedDurationMs >= 0L)
            (observedDurationMs.toDouble() / tripDurationMs).coerceIn(0.0, 1.0) else 0.0

    val distanceCoverage: Double
        get() = if (tripDistanceMeters.isFinite() && tripDistanceMeters > 0.0 &&
            observedDistanceMeters.isFinite() && observedDistanceMeters >= 0.0)
            (observedDistanceMeters / tripDistanceMeters).coerceIn(0.0, 1.0) else 0.0

    val hasSpeedLimitObservation: Boolean get() = speedLimitObservedMs > 0L

    internal val validMeasurements: Boolean
        get() = tripId.isNotBlank() && startedAtMs >= 0L &&
            (endedAtMs == null || endedAtMs >= startedAtMs) &&
            tripDurationMs >= 0L && tripDistanceMeters.isFinite() && tripDistanceMeters >= 0.0 &&
            observedDurationMs >= 0L && observedDistanceMeters.isFinite() && observedDistanceMeters >= 0.0 &&
            speedLimitObservedMs in 0L..observedDurationMs &&
            accelerationEvents >= 0 && brakingEvents >= 0 && overspeedEvents >= 0 &&
            // Even a corrupt checkpoint cannot turn impossible distance into eligibility.
            observedDistanceMeters <= observedDurationMs.toDouble() * (300.0 / 3_600.0) + 1.0

    val isScoreReady: Boolean
        get() = validMeasurements && completed && endedAtMs != null && !tripHasDataGap &&
            endedAtMs - startedAtMs == tripDurationMs &&
            tripDurationMs >= 60_000L && tripDistanceMeters >= 500.0 &&
            durationCoverage >= .8 && distanceCoverage >= .8 &&
            observedDurationMs.toDouble() <= tripDurationMs.toDouble() * 1.2 &&
            observedDistanceMeters <= tripDistanceMeters * 1.2

    /** MOA reference only: 100 − acceleration×3 − braking×5 − observed overspeed×5. */
    val score: Int?
        get() = if (!isScoreReady) null else
            (100L - accelerationEvents.toLong() * 3L - brakingEvents.toLong() * 5L -
                overspeedEvents.toLong() * 5L).coerceIn(0L, 100L).toInt()
}

data class DrivingScoreEligibilityResult(
    val score: Int?,
    val distanceKm: Double,
    val scoredTrips: Int,
    val meetsReference: Boolean,
    val pointsRemaining: Int?,
    val kilometersRemaining: Double,
)

/** Presentation-only examples deliberately have no trip identifier or conversion to stored records. */
data class DrivingScorePreview private constructor(val score: Int, val distanceKm: Double) {
    val meetsReference: Boolean get() = score >= 80 && distanceKm >= 500.0

    companion object {
        val READY = DrivingScorePreview(92, 620.0)
        val MORE_DISTANCE = DrivingScorePreview(92, 140.0)
    }
}
