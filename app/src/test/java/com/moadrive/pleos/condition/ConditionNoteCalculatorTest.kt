package com.moadrive.pleos.condition

import com.moadrive.pleos.passport.PassportCell
import com.moadrive.pleos.passport.PassportVisit
import com.moadrive.pleos.trips.TripRecord
import com.moadrive.pleos.trips.TripStatus
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class ConditionNoteCalculatorTest {
    private fun trip(day: Int, distance: Double = 10_000.0, wh: Double? = 2_000.0,
        minutes: Int = 30, hour: Int = 8, id: String = "t$day") : TripRecord {
        val start = ZonedDateTime.parse("2026-11-${day.toString().padStart(2, '0')}T${hour.toString().padStart(2, '0')}:00:00+09:00[Asia/Seoul]").toInstant().toEpochMilli()
        return TripRecord(id, start, start + minutes * 60_000L, "2026-11-$day", "Asia/Seoul",
            minutes * 60_000L, distance, wh, 80, 76, TripStatus.COMPLETED)
    }
    private fun visits(t: TripRecord, start: Double = 126.97, end: Double = 127.10) = listOf(
        PassportVisit(t.id, PassportCell.from(37.56, start)!!, t.startedAtMs + 1_000),
        PassportVisit(t.id, PassportCell.from(37.56, end)!!, t.endedAtMs!! - 1_000),
    )

    @Test fun usesMedianEnergyPerDistanceInsteadOfRawEnergyOrMean() {
        val target = trip(9, wh = 2_200.0)
        val prior = listOf(trip(1, distance = 8_000.0, wh = 1_600.0),
            trip(2, distance = 12_000.0, wh = 2_400.0, minutes = 36), trip(3, wh = 5_000.0))
        val note = ConditionNoteCalculator.analyze(prior + target, emptyList())
        assertEquals(ConditionNoteStatus.READY, note.status)
        val comparison = note.comparison!!
        assertEquals(200.0, comparison.baselineWhPerKm, .000001)
        assertEquals(220.0, comparison.currentWhPerKm, .000001)
        assertEquals(10.0, comparison.energyChangePercent, .000001)
        assertEquals(3, note.matchedCount)
    }

    @Test fun neverIncludesTargetFutureOverlappingOrDuplicateRecords() {
        val target = trip(9)
        val records = listOf(trip(1), trip(2), trip(3), trip(3), target,
            trip(10), trip(8).copy(endedAtMs = target.startedAtMs + 1))
        val note = ConditionNoteCalculator.analyze(records, emptyList(), target.id)
        assertEquals(3, note.matchedCount)
        assertEquals(listOf("t3", "t2", "t1"), note.comparison!!.baselineTripIds)
    }

    @Test fun requiresThreeComparablePriorTrips() {
        val note = ConditionNoteCalculator.analyze(listOf(trip(1), trip(2), trip(9)), emptyList())
        assertEquals(ConditionNoteStatus.NEED_HISTORY, note.status)
        assertEquals(2, note.matchedCount)
        assertNull(note.comparison)
    }

    @Test fun excludesUnmeasuredInterruptedGappedAndImpossibleTiming() {
        val base = trip(1)
        val invalid = listOf(base.copy(id="null", consumedWh=null), base.copy(id="zero", consumedWh=0.0),
            base.copy(id="nan", consumedWh=Double.NaN), base.copy(id="inf", distanceMeters=Double.POSITIVE_INFINITY),
            base.copy(id="short", distanceMeters=999.0), base.copy(id="gap", hasDataGap=true),
            base.copy(id="active", status=TripStatus.RECORDING), base.copy(id="interrupted", status=TripStatus.INTERRUPTED),
            base.copy(id="duration", durationMs=0), base.copy(id="reverse", endedAtMs=base.startedAtMs-1),
            base.copy(id="zone", zoneId="invalid-zone"))
        invalid.forEach { assertFalse(it.id, ConditionNoteCalculator.isMeasured(it)) }
        val note = ConditionNoteCalculator.analyze(invalid + trip(9), emptyList())
        assertEquals(1, note.eligibleCount)
        assertEquals(0, note.matchedCount)
    }

    @Test fun rejectsDurationThatDisagreesWithEndpointsAndOverflowingElapsedTime() {
        val valid = trip(1)
        val malformed = listOf(
            valid.copy(durationMs = valid.durationMs - 1),
            valid.copy(endedAtMs = valid.endedAtMs!! + 1),
            valid.copy(startedAtMs = Long.MIN_VALUE, endedAtMs = Long.MAX_VALUE, durationMs = 1),
        )
        assertTrue(ConditionNoteCalculator.isMeasured(valid))
        malformed.forEach { record ->
            assertFalse(ConditionNoteCalculator.isMeasured(record))
            val note = ConditionNoteCalculator.analyze(listOf(record), emptyList())
            assertEquals(ConditionNoteStatus.UNMEASURED, note.status)
            assertNull(note.comparison)
        }
    }

    @Test fun distanceAndCircularDepartureWindowAreInclusive() {
        val target = trip(9, hour=1)
        val records = listOf(trip(1, distance=8_000.0, hour=23), trip(2, distance=12_000.0, hour=3),
            trip(3, hour=1), trip(4, distance=7_999.0, hour=1), trip(5, hour=4), target)
        val note = ConditionNoteCalculator.analyze(records, emptyList())
        assertEquals(3, note.matchedCount)
        assertEquals(ConditionMatchBasis.DISTANCE_TIME, note.comparison!!.basis)
    }

    @Test fun knownApproximateEndpointsMustMatchInTheSameDirection() {
        val target=trip(9); val a=trip(1);val b=trip(2);val c=trip(3);val reversed=trip(4)
        val records=listOf(a,b,c,reversed,target)
        val location=listOf(a,b,c,target).flatMap { visits(it) }+visits(reversed,127.10,126.97)
        val note=ConditionNoteCalculator.analyze(records,location)
        assertEquals(3,note.matchedCount)
        assertEquals(ConditionMatchBasis.LOCATION_DISTANCE_TIME,note.comparison!!.basis)
    }

    @Test fun missingEndpointEvidenceNeverClaimsSameRoute() {
        val records=listOf(trip(1),trip(2),trip(3),trip(9))
        val note=ConditionNoteCalculator.analyze(records,records.take(3).flatMap { visits(it) })
        assertEquals(ConditionMatchBasis.DISTANCE_TIME,note.comparison!!.basis)
        assertEquals(3,note.matchedCount)
    }

    @Test fun sparseOrSimultaneousLocationSamplesDoNotClaimEndpointEvidence() {
        val target=trip(9); val a=trip(1); val b=trip(2); val c=trip(3)
        val sparse = visits(c,127.20,127.30).map { it.copy(visitedAtMs=c.startedAtMs+300_000) }
        val note=ConditionNoteCalculator.analyze(listOf(a,b,c,target),
            listOf(a,b,target).flatMap { visits(it) }+sparse)
        assertEquals(3,note.matchedCount)
        assertEquals(ConditionMatchBasis.DISTANCE_TIME,note.comparison!!.basis)
    }

    @Test fun batteryBaselineNeedsThreeValidSocSamplesAndNormalizesDistance() {
        val records=listOf(trip(1,distance=8_000.0).copy(batteryEndPercent=76),
            trip(2,distance=12_000.0).copy(batteryEndPercent=74),trip(3).copy(batteryEndPercent=75),trip(9))
        val note=ConditionNoteCalculator.analyze(records,emptyList())
        assertEquals(5.0,note.comparison!!.baselineBatteryUsedPoints!!,.00001)
        val insufficient=records.map { if(it.id=="t1") it.copy(batteryEndPercent=null) else it }
        assertNull(ConditionNoteCalculator.analyze(insufficient,emptyList()).comparison!!.baselineBatteryUsedPoints)
    }

    @Test fun invalidBatteryPercentDoesNotSuppressMeasuredEnergyOrInventSocDelta() {
        val records=listOf(trip(1),trip(2),trip(3),trip(9).copy(batteryStartPercent=101))
        val note=ConditionNoteCalculator.analyze(records,emptyList())
        assertNotNull(note.comparison)
        assertNull(note.comparison!!.currentBatteryUsedPoints)
        assertEquals(4.0,note.comparison!!.baselineBatteryUsedPoints!!,.00001)
    }

    @Test fun emptyAndExplicitUnmeasuredSelectionRemainHonest() {
        assertEquals(ConditionNoteStatus.NO_TRIP,ConditionNoteCalculator.analyze(emptyList(),emptyList()).status)
        val bad=trip(9,wh=0.0)
        val records=listOf(trip(1),trip(2),trip(3),bad)
        val note=ConditionNoteCalculator.analyze(records,emptyList(),bad.id)
        assertEquals(bad.id,note.target!!.id)
        assertEquals(ConditionNoteStatus.UNMEASURED,note.status)
        assertNull(note.comparison)
        assertEquals("t3",ConditionNoteCalculator.analyze(records,emptyList()).target!!.id)
    }
}
