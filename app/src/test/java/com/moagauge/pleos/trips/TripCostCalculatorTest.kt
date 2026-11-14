package com.moagauge.pleos.trips

import org.junit.Assert.*
import org.junit.Test

class TripCostCalculatorTest {
    private fun trip(distance: Double = 12_000.0, wh: Double? = 2_000.0) = TripRecord(
        id = "trip", startedAtMs = 1_000, endedAtMs = 601_000,
        startDate = "2026-11-05", zoneId = "Asia/Seoul", durationMs = 600_000,
        distanceMeters = distance, consumedWh = wh, batteryStartPercent = 80,
        batteryEndPercent = 77, status = TripStatus.COMPLETED,
    )

    @Test fun convertsMetersAndWhToCostsAndEfficiency() {
        val result = TripCostCalculator.calculate(listOf(trip()))
        assertEquals(12.0, result.measuredDistanceKm, .000001)
        assertEquals(2.0, result.measuredEnergyKwh!!, .000001)
        assertEquals(6.0, result.efficiencyKmPerKwh!!, .000001)
        assertEquals(600.0, result.electricityCostWon!!, .000001)
        assertEquals(1700.0, result.gasolineCostWon!!, .000001)
        assertEquals(1100.0, result.savingsWon!!, .000001)
    }

    @Test fun missingZeroNegativeAndNonfiniteEnergyCannotBecomeFreeTravel() {
        listOf(null, 0.0, -50.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { wh ->
            val result = TripCostCalculator.calculate(listOf(trip(wh = wh)))
            assertEquals(0, result.measuredCount)
            assertNull(result.electricityCostWon)
            assertNull(result.gasolineCostWon)
            assertNull(result.savingsWon)
            assertNull(result.efficiencyKmPerKwh)
        }
    }

    @Test fun shortAndInvalidDistanceCannotProduceComparison() {
        listOf(0.0, -1.0, 99.9, Double.NaN, Double.POSITIVE_INFINITY).forEach { distance ->
            assertNull(TripCostCalculator.calculate(listOf(trip(distance))).savingsWon)
        }
        assertNull(TripCostCalculator.calculate(listOf(trip(wh = 49.9))).savingsWon)
        assertNotNull(TripCostCalculator.calculate(listOf(trip(100.0, 50.0))).savingsWon)
    }

    @Test fun interruptedGappedAndActiveRecordsAreExcludedFromComparison() {
        val records = listOf(trip().copy(status = TripStatus.INTERRUPTED),
            trip().copy(hasDataGap = true), trip().copy(status = TripStatus.RECORDING),
            trip().copy(status = TripStatus.PARKING), trip().copy(endedAtMs = null))
        val result = TripCostCalculator.calculate(records)
        assertEquals(0, result.measuredCount)
        assertNull(result.savingsWon)
    }

    @Test fun mixedDayUsesIdenticalMeasuredSubsetAndWeightedEfficiency() {
        val result = TripCostCalculator.calculate(listOf(trip(), trip(6_000.0, 2_000.0), trip(100_000.0, null)))
        assertEquals(3, result.recordCount)
        assertEquals(2, result.measuredCount)
        assertEquals(118.0, result.totalDistanceKm, .000001)
        assertEquals(18.0, result.measuredDistanceKm, .000001)
        assertEquals(4.5, result.efficiencyKmPerKwh!!, .000001)
        assertEquals(2550.0, result.gasolineCostWon!!, .000001)
        assertEquals(1200.0, result.electricityCostWon!!, .000001)
        assertEquals(1350.0, result.savingsWon!!, .000001)
    }

    @Test fun higherElectricityCostPreservesNegativeSavings() {
        val result = TripCostCalculator.calculate(listOf(trip()), TripCostBasis(electricityWonPerKwh = 1000.0))
        assertEquals(-300.0, result.savingsWon!!, .000001)
    }

    @Test fun invalidBasisDoesNotCreateMisleadingCosts() {
        val invalid = listOf(TripCostBasis(gasolineWonPerLiter = 0.0), TripCostBasis(benchmarkKmPerLiter = -1.0),
            TripCostBasis(electricityWonPerKwh = Double.NaN), TripCostBasis(electricityWonPerKwh = 0.0))
        invalid.forEach { basis ->
            assertFalse(basis.isValid)
            assertNull(TripCostCalculator.calculate(listOf(trip()), basis).savingsWon)
        }
    }

    @Test fun absentOrInvalidHistoryDoesNotClaimAnEfficiencyImprovement() {
        val current = TripCostCalculator.calculate(listOf(trip()))
        assertNull(current.efficiencyChangePercent(null))
        assertNull(current.efficiencyChangePercent(TripCostCalculator.calculate(listOf(trip(wh = null)))))
        val previous = TripCostCalculator.calculate(listOf(trip(6_000.0, 2_000.0)))
        assertEquals(100.0, current.efficiencyChangePercent(previous)!!, .000001)
        assertEquals(-50.0, previous.efficiencyChangePercent(current)!!, .000001)
    }

    @Test fun emptyDayAndOverflowDoNotExposeNonfiniteNumbers() {
        val empty = TripCostCalculator.calculate(emptyList())
        assertEquals(0, empty.recordCount)
        assertNull(empty.savingsWon)
        val overflow = TripCostCalculator.calculate(listOf(trip(Double.MAX_VALUE, 50.0)),
            TripCostBasis(gasolineWonPerLiter = Double.MAX_VALUE))
        assertNull(overflow.savingsWon)
    }

    @Test fun wonFormattingRoundsHalfUpWithoutLosingNegativeDirection() {
        assertEquals("1,101원", TripCostFormat.won(1100.5))
        assertEquals("-1,101원", TripCostFormat.won(-1100.5))
        assertEquals("—", TripCostFormat.won(null))
        assertEquals("—", TripCostFormat.won(Double.NaN))
    }

    @Test fun comparisonAssumptionsPreserveEnteredPrecisionIncludingSubWonPrices() {
        val text = TripCostFormat.basisLabel(TripCostBasis(1700.05, 12.04, 300.49))
        assertTrue(text.contains("1700.05원/L"))
        assertTrue(text.contains("12.04 km/L"))
        assertTrue(text.contains("300.49원/kWh"))
        assertTrue(TripCostFormat.basisLabel(TripCostBasis(electricityWonPerKwh = 0.1)).contains("0.1원/kWh"))
    }

    @Test fun realShortDayExampleUsesOneOfEightMeasuredTrips() {
        val records = listOf(trip(442.058, 100.0)) + List(7) { trip(20.0, 0.0) }
        val result = TripCostCalculator.calculate(records)
        assertEquals(1, result.measuredCount)
        assertEquals(8, result.recordCount)
        assertEquals(4.42058, result.efficiencyKmPerKwh!!, .000001)
        assertEquals("63원", TripCostFormat.won(result.gasolineCostWon))
        assertEquals("30원", TripCostFormat.won(result.electricityCostWon))
        assertEquals("33원", TripCostFormat.won(result.savingsWon))
    }
}
