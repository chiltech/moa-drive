package com.moadrive.pleos.ui.trips

import com.moadrive.pleos.trips.*
import org.junit.Assert.*
import org.junit.Test

class TripReceiptModelTest {
    private fun record(wh: Double?) = TripRecord("one", 0L, 600_000L, "1970-01-01", "Asia/Seoul",
        600_000L, 12_000.0, wh, 80, 77, TripStatus.COMPLETED)

    @Test fun saveRestoreKeepsSelectedTripAmountsAndAssumptions() {
        val receipt = tripReceipt(listOf(record(2000.0)), TripCostBasis(1700.05, 12.04, 300.49), "선택한 주행")
        assertEquals(receipt, restoreReceiptSnapshot(receipt.savedState()))
        assertTrue(receipt.notes.first().contains("12.04 km/L"))
        assertTrue(receipt.notes.first().contains("300.49원/kWh"))
        assertTrue(receipt.rows.any { it.value == "601원" })
    }

    @Test fun missingAndZeroEnergyReceiptsDoNotClaimFreeTrips() {
        listOf(null, 0.0).forEach { wh ->
            val receipt = tripReceipt(listOf(record(wh)), TripCostBasis(), "선택한 주행")
            assertEquals(3, receipt.rows.count { it.value == "—" })
            assertFalse(receipt.rows.any { it.value == "0원" })
            assertTrue(receipt.rows.any { it.value.contains("비용 비교 제외") })
        }
    }

    @Test fun expensivePowerReceiptLabelsAnAdditionalCost() {
        val receipt = tripReceipt(listOf(record(2000.0)), TripCostBasis(electricityWonPerKwh = 1000.0), "선택한 주행")
        assertTrue(receipt.rows.any { it.label.contains("추가 비용") && it.value == "300원" })
        assertFalse(receipt.rows.any { it.label.contains("예상 절감") })
    }

    @Test fun costCountUpEndsAtTheAlreadyRoundedReceiptAmount() {
        val receipt = tripReceipt(listOf(record(2000.0)), TripCostBasis(electricityWonPerKwh = 300.49), "선택한 주행")
        val counter = ReceiptCostCounter(receipt.rows.first { it.label == "예상 전력 비용" }.value)

        assertTrue(counter.canAnimate)
        assertEquals("0원", counter.textAt(0f))
        assertEquals("300원", counter.textAt(.5f))
        assertEquals("601원", counter.textAt(1f))
    }

    @Test fun costCountUpDoesNotLoseLargeWonAmountsToFloatingPointOrLongOverflow() {
        val counter = ReceiptCostCounter("12,345,678,901,234,567,890,123원")

        assertEquals("6,172,839,450,617,283,945,061원", counter.textAt(.5f))
        assertEquals("12,345,678,901,234,567,890,123원", counter.textAt(1f))
    }

    @Test fun unavailableOrNonPositiveCostsNeverAnimateAsFreeTrips() {
        listOf("—", "0원", "-10원", "비용 미확인").forEach { cost ->
            val counter = ReceiptCostCounter(cost)
            assertFalse(counter.canAnimate)
            assertEquals(cost, counter.textAt(0f))
            assertEquals(cost, counter.textAt(.5f))
            assertEquals(cost, counter.textAt(1f))
        }
    }

    @Test fun costCountUpClampsEndpointsWithoutOvershooting() {
        val counter = ReceiptCostCounter("1,234원")

        assertEquals("0원", counter.textAt(-1f))
        assertEquals("1,234원", counter.textAt(2f))
        assertEquals("1,234원", counter.textAt(Float.NaN))
    }

    @Test fun malformedSavedSnapshotsAreIgnored() {
        assertNull(restoreReceiptSnapshot(emptyList()))
        assertNull(restoreReceiptSnapshot(listOf("1", "title", "label", "file", "999999999", "2")))
        assertNull(restoreReceiptSnapshot(listOf("2", "title", "label", "file", "0", "0")))
        assertNull(restoreReceiptSnapshot(listOf("1", "title", "label", "file", "1", "0")))
    }
}
