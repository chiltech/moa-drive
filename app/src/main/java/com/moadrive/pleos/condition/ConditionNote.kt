package com.moadrive.pleos.condition

import com.moadrive.pleos.passport.PassportVisit
import com.moadrive.pleos.trips.TripRecord
import com.moadrive.pleos.trips.TripStatus
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.min

enum class ConditionNoteStatus { NO_TRIP, UNMEASURED, NEED_HISTORY, READY }
enum class ConditionMatchBasis { DISTANCE_TIME, LOCATION_DISTANCE_TIME }

data class ConditionComparison(
    val currentWhPerKm: Double,
    val baselineWhPerKm: Double,
    val energyChangePercent: Double,
    val currentMinutesPer10Km: Double,
    val baselineMinutesPer10Km: Double,
    val timeChangePercent: Double,
    val currentEfficiencyKmPerKwh: Double,
    val baselineEfficiencyKmPerKwh: Double,
    val currentBatteryUsedPoints: Double?,
    /** Median consumption per km, converted to the selected trip's distance. */
    val baselineBatteryUsedPoints: Double?,
    val baselineTripIds: List<String>,
    val basis: ConditionMatchBasis,
)

data class ConditionNote(
    val target: TripRecord?,
    val status: ConditionNoteStatus,
    val eligibleCount: Int,
    val matchedCount: Int,
    val comparison: ConditionComparison? = null,
    val minimumComparisons: Int = 3,
)

/** Read-only comparisons; historical weather and mechanical diagnoses are not inferred. */
object ConditionNoteCalculator {
    const val MINIMUM_COMPARISONS = 3

    fun isMeasured(record: TripRecord): Boolean =
        record.status == TripStatus.COMPLETED && !record.hasDataGap &&
            record.durationMs > 0 && record.endedAtMs?.let { ended ->
                runCatching { Math.subtractExact(ended, record.startedAtMs) }.getOrNull() == record.durationMs
            } == true &&
            record.distanceMeters.isFinite() && record.distanceMeters >= 1_000.0 &&
            record.consumedWh?.let { it.isFinite() && it >= 50.0 } == true &&
            record.averageEfficiencyKmPerKwh?.let { it.isFinite() && it > 0.0 } == true &&
            minuteOfDay(record) != null

    fun analyze(records: List<TripRecord>, visits: List<PassportVisit>, selectedTripId: String? = null): ConditionNote {
        val unique = records.distinctBy { it.id }.sortedByDescending { it.startedAtMs }
        val eligible = unique.filter(::isMeasured)
        val target = unique.firstOrNull { it.id == selectedTripId && !it.isActive }
            ?: eligible.firstOrNull() ?: unique.firstOrNull { !it.isActive }
            ?: return ConditionNote(null, ConditionNoteStatus.NO_TRIP, eligible.size, 0)
        if (!isMeasured(target)) return ConditionNote(target, ConditionNoteStatus.UNMEASURED, eligible.size, 0)
        val byTrip = visits.groupBy { it.tripId }
        val targetEndpoints = endpoints(target, byTrip[target.id].orEmpty())
        val targetTime = minuteOfDay(target)!!
        val prior = eligible.filter { candidate ->
            candidate.id != target.id && candidate.startedAtMs < target.startedAtMs &&
                candidate.endedAtMs!! <= target.startedAtMs &&
                candidate.distanceMeters >= target.distanceMeters * .8 &&
                candidate.distanceMeters <= target.distanceMeters * 1.2 &&
                circularMinuteDistance(targetTime, minuteOfDay(candidate)!!) <= 120 &&
                endpoints(candidate, byTrip[candidate.id].orEmpty()).let { other ->
                    targetEndpoints == null || other == null || other == targetEndpoints
                }
        }
        if (prior.size < MINIMUM_COMPARISONS) {
            return ConditionNote(target, ConditionNoteStatus.NEED_HISTORY, eligible.size, prior.size)
        }
        val currentWh = target.consumedWh!! / (target.distanceMeters / 1_000.0)
        val baselineWh = median(prior.map { it.consumedWh!! / (it.distanceMeters / 1_000.0) })
        val currentTime = minutesPer10Km(target)
        val baselineTime = median(prior.map(::minutesPer10Km))
        val batteryRates = prior.mapNotNull { record -> batteryUse(record)?.div(record.distanceMeters / 1_000.0) }
        val locationMatched = targetEndpoints != null && prior.all {
            endpoints(it, byTrip[it.id].orEmpty()) == targetEndpoints
        }
        return ConditionNote(target, ConditionNoteStatus.READY, eligible.size, prior.size,
            ConditionComparison(
                currentWh, baselineWh, (currentWh / baselineWh - 1.0) * 100.0,
                currentTime, baselineTime, (currentTime / baselineTime - 1.0) * 100.0,
                1_000.0 / currentWh, 1_000.0 / baselineWh,
                batteryUse(target),
                if (batteryRates.size >= MINIMUM_COMPARISONS) median(batteryRates) * (target.distanceMeters / 1_000.0) else null,
                prior.map { it.id },
                if (locationMatched) ConditionMatchBasis.LOCATION_DISTANCE_TIME else ConditionMatchBasis.DISTANCE_TIME,
            ))
    }

    private fun minuteOfDay(record: TripRecord): Int? = runCatching {
        val local = Instant.ofEpochMilli(record.startedAtMs).atZone(ZoneId.of(record.zoneId))
        local.hour * 60 + local.minute
    }.getOrNull()

    private fun circularMinuteDistance(a: Int, b: Int): Int = abs(a - b).let { min(it, 1440 - it) }
    private fun minutesPer10Km(record: TripRecord) = record.durationMs / 60_000.0 / (record.distanceMeters / 10_000.0)
    private fun batteryUse(record: TripRecord): Double? {
        val before = record.batteryStartPercent?.takeIf { it in 0..100 } ?: return null
        val after = record.batteryEndPercent?.takeIf { it in 0..100 && it <= before } ?: return null
        return (before - after).toDouble()
    }
    private fun endpoints(record: TripRecord, visits: List<PassportVisit>): Pair<String, String>? {
        val ended = record.endedAtMs ?: return null
        val ordered = visits.filter { it.visitedAtMs in record.startedAtMs..ended }.sortedBy { it.visitedAtMs }
        val first = ordered.firstOrNull() ?: return null
        val last = ordered.lastOrNull() ?: return null
        // Persisted cells are first visits, not a full route trace. Require actual
        // observations near both boundaries before using them as endpoint evidence.
        if (first.visitedAtMs - record.startedAtMs > 120_000L || ended - last.visitedAtMs > 120_000L ||
            first.visitedAtMs >= last.visitedAtMs || first.cell.id == last.cell.id) return null
        return first.cell.id to last.cell.id
    }
    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) sorted[middle - 1] / 2.0 + sorted[middle] / 2.0 else sorted[middle]
    }
}
