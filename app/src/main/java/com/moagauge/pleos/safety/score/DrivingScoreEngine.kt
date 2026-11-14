package com.moagauge.pleos.safety.score

import com.moagauge.pleos.trips.TripRecord
import com.moagauge.pleos.trips.TripStatus

/**
 * App-visible observation only. Callers break continuity while hidden or recording is disabled.
 * Restoring totals never restores the last speed, so process gaps cannot become measured distance.
 */
class DrivingScoreEngine(initial: DrivingScoreRecord? = null) {
    private val restored = initial?.takeIf { it.validMeasurements }
    private var tripId: String? = restored?.tripId
    private var tripStartMs: Long? = restored?.startedAtMs
    private var hasObservation = restored != null
    private var durationMs = restored?.observedDurationMs ?: 0L
    private var distanceMeters = restored?.observedDistanceMeters ?: 0.0
    private var limitDurationMs = restored?.speedLimitObservedMs ?: 0L
    private var accelerationEvents = restored?.accelerationEvents ?: 0
    private var brakingEvents = restored?.brakingEvents ?: 0
    private var overspeedEvents = restored?.overspeedEvents ?: 0
    private var previous: Sample? = null
    private var lastAcceptedElapsedMs: Long? = null
    private val acceleration = SustainedEvent(1_000L)
    private val braking = SustainedEvent(1_000L)
    private val overspeed = SustainedEvent(3_000L)

    private data class Sample(val elapsedMs: Long, val speedKmh: Int, val limitKmh: Int?)

    fun observe(
        trip: TripRecord,
        speedKmh: Int?,
        observedElapsedMs: Long?,
        nowElapsedMs: Long,
        speedLimitKmh: Int? = null,
        speedLimitObservedElapsedMs: Long? = null,
        nowMs: Long,
    ) {
        if (trip.status != TripStatus.RECORDING ||
            (tripId != null && (trip.id != tripId || trip.startedAtMs != tripStartMs))) {
            breakContinuity()
            return
        }
        val observed = observedElapsedMs
        if (speedKmh == null || speedKmh !in 0..300 || observed == null || observed < 0L ||
            nowElapsedMs < observed || nowElapsedMs - observed > MAX_INTERVAL_MS || nowMs < 0L) {
            breakContinuity()
            return
        }
        val ageMs = nowElapsedMs - observed
        if (nowMs < ageMs) { breakContinuity(); return }
        val sampleWallMs = nowMs - ageMs
        if (sampleWallMs < trip.startedAtMs || trip.endedAtMs?.let { sampleWallMs > it } == true) {
            breakContinuity()
            return
        }
        // Repeated Compose updates, out-of-order callbacks and high-rate noise cannot add time.
        val accepted = lastAcceptedElapsedMs
        if (accepted != null && (observed <= accepted || observed - accepted < MIN_INTERVAL_MS)) return

        val limit = speedLimitKmh?.takeIf {
            val limitObserved = speedLimitObservedElapsedMs
            it in 1..200 && limitObserved != null && limitObserved >= 0L &&
                limitObserved <= observed && nowElapsedMs >= limitObserved &&
                nowElapsedMs - limitObserved <= MAX_INTERVAL_MS
        }
        val sample = Sample(observed, speedKmh, limit)
        if (tripId == null) {
            tripId = trip.id
            tripStartMs = trip.startedAtMs
        }
        hasObservation = true
        lastAcceptedElapsedMs = observed
        val prior = previous
        previous = sample
        if (prior == null) return
        val deltaMs = observed - prior.elapsedMs
        if (deltaMs !in MIN_INTERVAL_MS..MAX_INTERVAL_MS) {
            resetEvents()
            return
        }
        durationMs = addDuration(durationMs, deltaMs)
        distanceMeters += (prior.speedKmh + speedKmh) / 2.0 / 3.6 * (deltaMs / 1_000.0)
        val accelerationMps2 = (speedKmh - prior.speedKmh) / 3.6 / (deltaMs / 1_000.0)
        if (acceleration.accept(accelerationMps2 >= 3.0, deltaMs)) accelerationEvents = increment(accelerationEvents)
        if (braking.accept(accelerationMps2 <= -3.5, deltaMs)) brakingEvents = increment(brakingEvents)

        // Both endpoints must observe the same actual limit; a newly arrived sign is not retroactive.
        if (limit != null && limit == prior.limitKmh) {
            limitDurationMs = addDuration(limitDurationMs, deltaMs)
            if (overspeed.accept(prior.speedKmh > limit + 5 && speedKmh > limit + 5, deltaMs)) {
                overspeedEvents = increment(overspeedEvents)
            }
        } else overspeed.reset()
    }

    /** Reconcile only this measured journey with its current/final TripRecord; never backfill old trips. */
    fun snapshot(trip: TripRecord): DrivingScoreRecord? {
        if (!hasObservation || trip.id != tripId || trip.startedAtMs != tripStartMs) return null
        return DrivingScoreRecord(
            tripId = trip.id,
            startedAtMs = trip.startedAtMs,
            endedAtMs = trip.endedAtMs,
            startDate = trip.startDate,
            tripDurationMs = trip.durationMs,
            tripDistanceMeters = trip.distanceMeters,
            observedDurationMs = durationMs,
            observedDistanceMeters = distanceMeters,
            speedLimitObservedMs = limitDurationMs,
            accelerationEvents = accelerationEvents,
            brakingEvents = brakingEvents,
            overspeedEvents = overspeedEvents,
            completed = trip.status == TripStatus.COMPLETED,
            tripHasDataGap = trip.hasDataGap,
        )
    }

    fun breakContinuity() {
        previous = null
        // Keep the watermark: a stale repeated timestamp must not start another segment.
        resetEvents()
    }

    private fun resetEvents() {
        acceleration.reset()
        braking.reset()
        overspeed.reset()
    }

    /** Count an episode once. Two seconds below the threshold are required before another episode. */
    private class SustainedEvent(private val sustainedMs: Long) {
        private var qualifyingMs = 0L
        private var normalMs = 0L
        private var counted = false

        fun accept(qualifies: Boolean, durationMs: Long): Boolean {
            if (!qualifies) {
                qualifyingMs = 0L
                normalMs += durationMs
                if (normalMs >= 2_000L) counted = false
                return false
            }
            normalMs = 0L
            if (counted) return false
            qualifyingMs += durationMs
            if (qualifyingMs < sustainedMs) return false
            counted = true
            return true
        }

        fun reset() { qualifyingMs = 0L; normalMs = 0L; counted = false }
    }

    private fun increment(value: Int): Int = if (value == Int.MAX_VALUE) value else value + 1
    private fun addDuration(value: Long, increment: Long): Long =
        if (value > Long.MAX_VALUE - increment) Long.MAX_VALUE else value + increment

    private companion object {
        const val MIN_INTERVAL_MS = 500L
        const val MAX_INTERVAL_MS = 3_000L
    }
}
