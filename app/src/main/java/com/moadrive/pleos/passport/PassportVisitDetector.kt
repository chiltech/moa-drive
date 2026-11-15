package com.moadrive.pleos.passport

import kotlin.math.*

/** Awards only cells backed by two trustworthy moving fixes from one automatic trip. */
class PassportVisitDetector {
    private data class Candidate(val cell: PassportCell, val first: PassportFix)
    private var activeTrip: String? = null
    private var lastAccepted: PassportFix? = null
    private var latestSampleMs: Long? = null
    private var candidate: Candidate? = null
    private val awarded = mutableSetOf<String>()

    fun accept(tripId: String?, moving: Boolean, fix: PassportFix, nowMonotonicMs: Long): PassportVisit? {
        if (tripId != activeTrip) {
            activeTrip = tripId
            awarded.clear()
            reset()
        }
        if (tripId.isNullOrBlank() || !moving) {
            reset()
            return null
        }
        if (fix.monotonicMs < 0 || nowMonotonicMs < fix.monotonicMs ||
            nowMonotonicMs - fix.monotonicMs > MAX_AGE_MS
        ) {
            candidate = null
            return null
        }
        if (latestSampleMs?.let { fix.monotonicMs <= it } == true) return null
        latestSampleMs = fix.monotonicMs
        val cell = PassportCell.from(fix.latitude, fix.longitude)
        if (cell == null || !fix.accuracyMeters.isFinite() || fix.accuracyMeters < 0 ||
            fix.accuracyMeters > MAX_ACCURACY_METERS || fix.wallTimeMs < 0
        ) {
            candidate = null
            return null
        }
        val previous = lastAccepted
        if (previous != null) {
            val deltaMs = fix.monotonicMs - previous.monotonicMs
            if (deltaMs > MAX_CONFIRMATION_MS) {
                // A new observed sequence may start after a gap; unobserved cells are never filled.
                candidate = null
            }
            if (distanceMeters(previous, fix) >
                MAX_SPEED_MPS * deltaMs / 1000.0 + previous.accuracyMeters + fix.accuracyMeters
            ) {
                candidate = null
                return null // An outlier must never become the next distance anchor.
            }
        }
        lastAccepted = fix
        if (!insideAccuracyCircle(cell, fix)) {
            candidate = null
            return null
        }
        if (cell.id in awarded) {
            candidate = null
            return null
        }
        val pending = candidate
        if (pending == null || pending.cell.id != cell.id ||
            fix.monotonicMs - pending.first.monotonicMs > MAX_CONFIRMATION_MS
        ) {
            candidate = Candidate(cell, fix)
            return null
        }
        if (fix.monotonicMs - pending.first.monotonicMs < MIN_CONFIRMATION_MS) return null
        awarded.add(cell.id)
        candidate = null
        return PassportVisit(tripId, cell, pending.first.wallTimeMs)
    }

    /** Permission/parking gaps break confirmation without re-awarding cells from this trip. */
    fun reset() {
        candidate = null
        lastAccepted = null
        latestSampleMs = null
    }

    private fun insideAccuracyCircle(cell: PassportCell, fix: PassportFix): Boolean {
        val latMeters = EARTH_RADIUS_METERS * PI / 180.0
        val lonMeters = latMeters * cos(Math.toRadians(fix.latitude))
        val edgeDistance = minOf((fix.latitude - cell.south) * latMeters,
            (cell.north - fix.latitude) * latMeters, (fix.longitude - cell.west) * lonMeters,
            (cell.east - fix.longitude) * lonMeters)
        return edgeDistance > fix.accuracyMeters
    }

    private fun distanceMeters(a: PassportFix, b: PassportFix): Double {
        val latDelta = Math.toRadians(b.latitude - a.latitude)
        val lonDelta = Math.toRadians(b.longitude - a.longitude)
        val h = sin(latDelta / 2).pow(2) + cos(Math.toRadians(a.latitude)) *
            cos(Math.toRadians(b.latitude)) * sin(lonDelta / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_008.8
        const val MIN_CONFIRMATION_MS = 2_000L
        const val MAX_CONFIRMATION_MS = 15_000L
        const val MAX_AGE_MS = 10_000L
        const val MAX_ACCURACY_METERS = 50.0
        const val MAX_SPEED_MPS = 250.0 / 3.6
    }
}
