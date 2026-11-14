package com.moagauge.pleos.insurance

import com.moagauge.pleos.trips.TripRecord
import com.moagauge.pleos.trips.TripStatus
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class InsuranceMileageTest {
    private fun trip(id: String, date: String = "2026-11-04", distance: Double = 1000.0,
                     status: TripStatus = TripStatus.COMPLETED, gap: Boolean = false) =
        TripRecord(id, 1L, 1001L, date, "Asia/Seoul", 1000, distance, 100.0, 80, 79, status, gap)

    @Test fun aggregatesCompletedUniqueRecordsByRecordedDepartureMonth() {
        val record = trip("one", distance = 2500.0)
        val result = InsuranceMileage.from(listOf(record, record, trip("old", "2026-10-31", 1000.0),
            trip("live", status = TripStatus.RECORDING)), LocalDate.parse("2026-11-04"))
        assertEquals(3.5, result.totalKm, .0001)
        assertEquals(2.5, result.monthKm, .0001)
        assertEquals(2, result.tripCount)
        assertEquals(0, result.incompleteCount)
    }

    @Test fun incompleteRecordsAreCountedSeparatelyAndInvalidDistanceIsExcluded() {
        val result = InsuranceMileage.from(listOf(trip("gap", gap = true), trip("interrupted", status = TripStatus.INTERRUPTED),
            trip("negative", distance = -1.0), trip("nan", distance = Double.NaN)), LocalDate.parse("2026-11-04"))
        assertEquals(2.0, result.totalKm, .0001)
        assertEquals(2, result.tripCount)
        assertEquals(2, result.incompleteCount)
    }

    @Test fun invalidStoredDateDoesNotCrashOrEnterMonthlyTotal() {
        val result = InsuranceMileage.from(listOf(trip("date", date = "bad")), LocalDate.parse("2026-11-04"))
        assertEquals(1.0, result.totalKm, .0001)
        assertEquals(0.0, result.monthKm, .0001)
    }
}
