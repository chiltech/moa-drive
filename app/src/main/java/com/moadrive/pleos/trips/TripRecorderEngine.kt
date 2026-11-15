package com.moadrive.pleos.trips

import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A single-owner, Android-independent recorder. Feed samples, including unavailable readings,
 * regularly so loss of telemetry can be detected without inventing speed or elapsed distance.
 */
class TripRecorderEngine(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
    private val zoneProvider: () -> ZoneId = { zoneId },
) {
    private data class Motion(val sample: TripSample, val speed: Double, val gear: String) {
        val isStarting get() = (gear == "D" || gear == "R" || gear in MANUAL_GEARS) && speed >= START_SPEED_MPS
        val isParked get() = gear == "P" && speed <= PARK_SPEED_MPS
    }

    private data class Parking(val sinceMs: Long, val record: TripRecord)

    private class Journey(val first: Motion, val date: String, val zone: String) {
        var id = ""
        var previousMotion: Motion? = first
        var lastTrusted: Motion = first
        var distanceMeters = 0.0
        var netConsumedWh = 0.0
        var energyKnown = validEnergy(first.sample.batteryWh) != null
        var lastEnergyWh = validEnergy(first.sample.batteryWh)
        var lastEnergyTimeMs = first.sample.monotonicMs
        var stationaryMinimumWh: Double? = null
        val batteryStart = validSoc(first.sample.batteryPercent)
        var batteryEnd = batteryStart
        var hasGap = false
        var ignitionOn = first.sample.ignitionOn
        var parking: Parking? = null

        fun record(status: TripStatus) = TripRecord(
            id = id,
            startedAtMs = first.sample.wallTimeMs,
            endedAtMs = null,
            startDate = date,
            zoneId = zone,
            durationMs = lastTrusted.sample.monotonicMs - first.sample.monotonicMs,
            distanceMeters = distanceMeters,
            consumedWh = netConsumedWh.takeIf { energyKnown && it.isFinite() && it >= 0.0 },
            batteryStartPercent = batteryStart,
            batteryEndPercent = batteryEnd,
            status = status,
            hasDataGap = hasGap,
        )
    }

    private var candidate: Journey? = null
    private var journey: Journey? = null
    private var lastAcceptedMs: Long? = null

    val current: TripRecord?
        get() = journey?.let { active ->
            active.parking?.record?.copy(status = TripStatus.PARKING, hasDataGap = active.hasGap)
                ?: active.record(TripStatus.RECORDING)
        }

    /** Returns each finished record exactly once; active snapshots are available through [current]. */
    fun accept(sample: TripSample): List<TripRecord> {
        if (sample.monotonicMs < 0 || lastAcceptedMs?.let { sample.monotonicMs <= it } == true) return emptyList()
        lastAcceptedMs = sample.monotonicMs
        val motion = motion(sample)
        val finished = mutableListOf<TripRecord>()
        val active = journey

        // A returning valid reading must not bridge a minute of silence into the previous trip.
        if (active != null && sample.monotonicMs - active.lastTrusted.sample.monotonicMs >= SIGNAL_LOSS_MS) {
            active.hasGap = true
            finish(TripStatus.INTERRUPTED)?.let(finished::add)
        }

        val ongoing = journey
        if (ongoing == null) {
            detectStart(motion)
            return finished
        }

        val ignitionTurnedOff = ongoing.ignitionOn == true && sample.ignitionOn == false
        if (sample.ignitionOn != null) ongoing.ignitionOn = sample.ignitionOn

        if (motion == null) {
            ongoing.hasGap = true
            ongoing.previousMotion = null
            ongoing.parking = null
            if (validEnergy(sample.batteryWh) == null) ongoing.energyKnown = false
        } else {
            val gap = sample.monotonicMs - ongoing.lastTrusted.sample.monotonicMs > MAX_INTERVAL_MS
            if (gap) {
                ongoing.hasGap = true
                ongoing.parking = null
            }
            accumulate(ongoing, motion)
            if (motion.isParked) {
                val parking = ongoing.parking
                if (parking == null) {
                    ongoing.parking = Parking(sample.monotonicMs, ongoing.record(TripStatus.PARKING))
                } else if (sample.monotonicMs - parking.sinceMs >= PARK_CONFIRM_MS) {
                    finish(TripStatus.COMPLETED)?.let(finished::add)
                    return finished
                }
            } else {
                ongoing.parking = null
            }
        }

        if (ignitionTurnedOff) finish(TripStatus.COMPLETED)?.let(finished::add)
        return finished
    }

    /** Stops at the latest trusted sample, never at a later wall-clock time. */
    fun interrupt(): TripRecord? {
        candidate = null
        return finish(TripStatus.INTERRUPTED)
    }

    private fun detectStart(motion: Motion?) {
        if (motion == null || !motion.isStarting) {
            candidate = null
            return
        }
        val pending = candidate
        if (pending == null || motion.sample.monotonicMs - pending.lastTrusted.sample.monotonicMs > MAX_INTERVAL_MS) {
            val departureZone = zoneProvider()
            candidate = Journey(
                motion,
                Instant.ofEpochMilli(motion.sample.wallTimeMs).atZone(departureZone).toLocalDate().toString(),
                departureZone.id,
            )
            return
        }
        accumulate(pending, motion)
        if (motion.sample.ignitionOn != null) pending.ignitionOn = motion.sample.ignitionOn
        if (motion.sample.monotonicMs - pending.first.sample.monotonicMs >= START_CONFIRM_MS) {
            pending.id = idProvider()
            journey = pending
            candidate = null
        }
    }

    private fun accumulate(active: Journey, next: Motion) {
        val previous = active.previousMotion
        if (previous != null) {
            val elapsedMs = next.sample.monotonicMs - previous.sample.monotonicMs
            if (elapsedMs in 1..MAX_INTERVAL_MS) {
                active.distanceMeters += (previous.speed + next.speed) / 2.0 * elapsedMs / 1_000.0
            }
        }

        val energy = validEnergy(next.sample.batteryWh)
        val previousEnergy = active.lastEnergyWh
        if (energy == null || previousEnergy == null) {
            active.energyKnown = false
        } else {
            val gain = energy - previousEnergy
            val seconds = (next.sample.monotonicMs - active.lastEnergyTimeMs) / 1_000.0
            // Compare with the lowest stationary reading so slow charging cannot evade a per-tick limit.
            val stationaryMinimum = active.stationaryMinimumWh
            val stationaryGain = next.speed <= PARK_SPEED_MPS && stationaryMinimum != null && energy - stationaryMinimum > 5.0
            // Large jumps in either direction indicate reset/charging, not plausible battery use.
            val implausibleDrop = -gain > max(500.0, seconds * 300.0)
            if (gain > max(50.0, seconds * 100.0) || stationaryGain || implausibleDrop) active.energyKnown = false
            active.netConsumedWh -= gain
        }
        active.stationaryMinimumWh = if (next.speed <= PARK_SPEED_MPS && energy != null) {
            min(active.stationaryMinimumWh ?: energy, energy)
        } else {
            null
        }
        active.lastEnergyWh = energy
        active.lastEnergyTimeMs = next.sample.monotonicMs
        active.batteryEnd = validSoc(next.sample.batteryPercent)
        active.previousMotion = next
        active.lastTrusted = next
    }

    private fun finish(status: TripStatus): TripRecord? {
        val active = journey ?: return null
        val record = if (status == TripStatus.COMPLETED) {
            active.parking?.record ?: active.record(status)
        } else {
            active.record(status)
        }
        journey = null
        candidate = null
        return record.copy(
            endedAtMs = record.startedAtMs + record.durationMs,
            status = status,
            hasDataGap = active.hasGap,
        )
    }

    private fun motion(sample: TripSample): Motion? {
        val speed = sample.speedMps?.takeIf { it.isFinite() && abs(it) <= 100.0 }?.let(::abs) ?: return null
        val gear = sample.gear?.takeIf { it == "D" || it == "R" || it == "P" || it == "N" || it in MANUAL_GEARS } ?: return null
        return Motion(sample, speed, gear)
    }

    private companion object {
        const val START_SPEED_MPS = 3.0 / 3.6
        const val PARK_SPEED_MPS = 0.5
        const val START_CONFIRM_MS = 2_000L
        const val PARK_CONFIRM_MS = 30_000L
        const val MAX_INTERVAL_MS = 3_000L
        const val SIGNAL_LOSS_MS = 60_000L
        val MANUAL_GEARS = (1..9).map(Int::toString).toSet()
        fun validEnergy(value: Double?) = value?.takeIf { it.isFinite() && it >= 0.0 }
        fun validSoc(value: Int?) = value?.takeIf { it in 0..100 }
    }
}
