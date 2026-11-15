package com.moadrive.pleos.budget

import com.moadrive.pleos.navigation.GeoPoint
import org.junit.Assert.*
import org.junit.Test

class BatteryBudgetCalculatorTest {
    private val vehicle = BatteryBudgetVehicle(80, 320)
    private val preferences = BatteryBudgetPreferences(90, 30)
    private val origin = GeoPoint(37.5665, 126.9780)

    @Test fun usesBothActualRouteLegsAndTwentyPercentConsumptionMargin() {
        val result = BatteryBudgetCalculator.estimate(vehicle, preferences,
            BatteryBudgetLeg(20_000, 1_200), BatteryBudgetLeg(30_000, 1_800))!!
        assertEquals(50_000L, result.distanceMeters)
        assertEquals(3_000L, result.travelSeconds)
        assertEquals(4_200L, result.totalSeconds)
        assertEquals(15.0, result.consumedPercent, .0001)
        assertEquals(65.0, result.returnPercent, .0001)
        assertTrue(result.feasible)
    }

    @Test fun includesVisitTimeAndRequiresBothTimeAndReserve() {
        val tooSlow = BatteryBudgetCalculator.estimate(vehicle, preferences,
            BatteryBudgetLeg(20_000, 2_100), BatteryBudgetLeg(20_000, 2_101))!!
        assertFalse(tooSlow.withinTime)
        assertTrue(tooSlow.withinReserve)
        assertFalse(tooSlow.feasible)
        val lowReserve = BatteryBudgetCalculator.estimate(vehicle, preferences,
            BatteryBudgetLeg(100_000, 900), BatteryBudgetLeg(100_000, 900))!!
        assertEquals(20.0, lowReserve.returnPercent, .0001)
        assertTrue(lowReserve.withinTime)
        assertFalse(lowReserve.withinReserve)
        assertFalse(lowReserve.feasible)
    }

    @Test fun exactReserveAndTimeBoundaryAreAccepted() {
        val result = BatteryBudgetCalculator.estimate(BatteryBudgetVehicle(90, 360),
            BatteryBudgetPreferences(90, 30), BatteryBudgetLeg(100_000, 2_100), BatteryBudgetLeg(100_000, 2_100))!!
        assertEquals(30.0, result.returnPercent, .0001)
        assertEquals(5_400L, result.totalSeconds)
        assertTrue(result.feasible)
    }

    @Test fun rejectsUnknownInvalidSignalsAndRoutesRatherThanInventingCapacity() {
        val leg = BatteryBudgetLeg(1_000, 100)
        for (invalid in listOf(BatteryBudgetVehicle(0, 320), BatteryBudgetVehicle(101, 320),
                BatteryBudgetVehicle(80, 0), BatteryBudgetVehicle(80, -1), BatteryBudgetVehicle(80, 2_001))) {
            assertNull(BatteryBudgetCalculator.estimate(invalid, preferences, leg, leg))
        }
        for (invalid in listOf(BatteryBudgetLeg(0, 100), BatteryBudgetLeg(-1, 100), BatteryBudgetLeg(100, 0))) {
            assertNull(BatteryBudgetCalculator.estimate(vehicle, preferences, invalid, leg))
        }
        assertNull(BatteryBudgetCalculator.estimate(vehicle, BatteryBudgetPreferences(0, 30), leg, leg))
        assertNull(BatteryBudgetCalculator.estimate(vehicle, BatteryBudgetPreferences(90, -1), leg, leg))
    }

    @Test fun largeLegsDoNotOverflowOrProduceAFeasibleNegativeConsumption() {
        val result = BatteryBudgetCalculator.estimate(vehicle, preferences,
            BatteryBudgetLeg(Int.MAX_VALUE, Int.MAX_VALUE), BatteryBudgetLeg(Int.MAX_VALUE, Int.MAX_VALUE))!!
        assertEquals(4_294_967_294L, result.distanceMeters)
        assertEquals(4_294_968_494L, result.totalSeconds)
        assertTrue(result.consumedPercent > 100)
        assertEquals(0.0, result.returnPercent, .0001)
        assertFalse(result.feasible)
    }

    @Test fun resultExpiresAndCannotSurviveChangedSignalsOrPreferences() {
        val snapshot = BatteryBudgetSnapshot(preferences, vehicle, origin, 1_000L)
        assertTrue(BatteryBudgetCalculator.isCurrent(snapshot, preferences, vehicle, origin, 300_999L))
        assertFalse(BatteryBudgetCalculator.isCurrent(snapshot, preferences, vehicle, origin, 301_000L))
        assertFalse(BatteryBudgetCalculator.isCurrent(snapshot, preferences, vehicle, origin, 999L))
        assertFalse(BatteryBudgetCalculator.isCurrent(snapshot, preferences, vehicle.copy(batteryPercent = 79), origin, 2_000L))
        assertFalse(BatteryBudgetCalculator.isCurrent(snapshot, preferences, vehicle.copy(rangeKm = 319), origin, 2_000L))
        assertFalse(BatteryBudgetCalculator.isCurrent(snapshot, preferences.copy(reservePercent = 40), vehicle, origin, 2_000L))
        assertFalse(BatteryBudgetCalculator.isCurrent(snapshot, preferences, vehicle, null, 2_000L))
    }

    @Test fun originDriftIsBoundedToTwoHundredMetres() {
        val snapshot = BatteryBudgetSnapshot(preferences, vehicle, origin, 0L)
        fun north(meters: Double) = GeoPoint(origin.latitude + Math.toDegrees(meters / 6_371_000.0), origin.longitude)
        assertTrue(BatteryBudgetCalculator.isCurrent(snapshot, preferences, vehicle, north(199.9), 1L))
        assertFalse(BatteryBudgetCalculator.isCurrent(snapshot, preferences, vehicle, north(200.1), 1L))
        // The previous approximately 390m tolerance example must now force recalculation.
        assertFalse(BatteryBudgetCalculator.isCurrent(snapshot, preferences, vehicle, GeoPoint(37.57, 126.978), 1L))
        assertFalse(BatteryBudgetCalculator.isCurrent(snapshot, preferences, vehicle, GeoPoint(37.59, 126.978), 1L))
    }
}
