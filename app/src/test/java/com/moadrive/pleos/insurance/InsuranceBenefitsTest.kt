package com.moadrive.pleos.insurance

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class InsuranceBenefitsTest {
    @Test fun mileageThresholdIncludesExactlyTenThousandAndCapsProgressAboveIt() {
        assertEquals(MileageDiscountRange.WITHIN_LIMIT, InsuranceAnnualMileage.from(0.0).range)
        assertEquals(1.0, InsuranceAnnualMileage.from(9999.0).remainingKm!!, .001)
        assertEquals(MileageDiscountRange.WITHIN_LIMIT, InsuranceAnnualMileage.from(10000.0).range)
        val over = InsuranceAnnualMileage.from(10000.5)
        assertEquals(MileageDiscountRange.OVER_LIMIT, over.range)
        assertEquals(1f, over.progress, .001f)
        assertEquals(.5, over.excessKm!!, .001)
    }
    @Test fun missingNegativeOrNonFiniteMileageNeverLooksEligible() {
        listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach {
            val mileage = InsuranceAnnualMileage.from(it)
            assertEquals(MileageDiscountRange.UNKNOWN, mileage.range)
            assertNull(mileage.distanceKm)
            assertNull(mileage.remainingKm)
            assertEquals(0f, mileage.progress, .001f)
        }
    }
    @Test fun eachReceiptConsumesSharedPoolOnceAndPreservesIndividualUsage() {
        val initial = InsuranceRoadsideUsage(6, mapOf(RoadsideService.ROADSIDE to 1, RoadsideService.BATTERY to 1))
        val after = initial.consume(RoadsideService.BATTERY, "DEMO-ONE")!!
        assertEquals(3, after.used)
        assertEquals(3, after.remaining)
        assertEquals(2, after.usedFor(RoadsideService.BATTERY))
        assertEquals(1, after.usedFor(RoadsideService.ROADSIDE))
        assertEquals(after, after.consume(RoadsideService.BATTERY, "DEMO-ONE"))
    }
    @Test fun exhaustedPoolRejectsNewReceiptAndNeverGoesNegative() {
        val full = InsuranceRoadsideUsage(1, mapOf(RoadsideService.TIRE to 1))
        assertEquals(0, full.remaining)
        assertNull(full.consume(RoadsideService.BATTERY, "DEMO-TWO"))
        assertEquals(0, InsuranceRoadsideUsage(0).remaining)
    }
    @Test fun invalidQuotaCountsAreRejectedInsteadOfPresentedAsRealAllowances() {
        listOf({ InsuranceRoadsideUsage(-1) },
            { InsuranceRoadsideUsage(6, mapOf(RoadsideService.TOW to -1)) },
            { InsuranceRoadsideUsage(2, mapOf(RoadsideService.TOW to 2, RoadsideService.TIRE to 1)) }).forEach { bad ->
            try { bad(); fail("invalid quota accepted") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun storageKeyChangesWithProviderAndContractPeriod() {
        val first = InsuranceContract(InsuranceProvider.DB, "자동차보험", LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-12-31"), emptyList(), ContractSource.DEMO, 1)
        assertNotEquals(first.usageStorageKey(), first.copy(provider = InsuranceProvider.KB).usageStorageKey())
        assertNotEquals(first.usageStorageKey(), first.copy(startDate = LocalDate.parse("2027-01-01"),
            endDate = LocalDate.parse("2027-12-31")).usageStorageKey())
    }

    @Test fun savedUsageRoundTripKeepsCountsAndReceiptIdempotency() {
        val initial = InsuranceRoadsideUsage(6, mapOf(RoadsideService.ROADSIDE to 1, RoadsideService.BATTERY to 1))
        val used = initial.consume(RoadsideService.TOW, "DEMO-A/B|C")!!
        val restored = InsuranceUsageCodec.decode(InsuranceUsageCodec.encode(used))!!
        assertEquals(used.used, restored.used)
        assertEquals(used.remaining, restored.remaining)
        assertEquals(used.usedFor(RoadsideService.TOW), restored.usedFor(RoadsideService.TOW))
        assertEquals(restored, restored.consume(RoadsideService.TOW, "DEMO-A/B|C"))
    }

    @Test fun malformedSavedUsageIsRejectedWithoutInventingOrReducingCounts() {
        listOf(null, "", "future|6|1,1,0,0|", "1|6|-1,1,0,0|", "1|6|6,1,0,0|",
            "1|6|1,1,0|", "1|x|1,1,0,0|", "1|6|1,1,0,0|%%%", "x".repeat(20_000)).forEach {
            assertNull(InsuranceUsageCodec.decode(it))
        }
    }
}
