package com.moagauge.pleos.trips

import java.time.LocalDate
import java.time.ZoneId

/** View-only sample records. No repository or recording service receives this list. */
internal fun tripSampleRecords(anchorDate: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<TripRecord> =
    (0..6).flatMap { dayOffset ->
        val date = anchorDate.minusDays(dayOffset.toLong())
        listOf(8, 18).mapIndexed { tripIndex, hour ->
            val distance = (if (tripIndex == 0) 24_600.0 else 18_200.0) + dayOffset * 210.0
            val efficiency = (if (tripIndex == 0) 5.8 else 6.2) + ((dayOffset + 1) % 3 - 1) * .15
            val start = date.atTime(hour, if (tripIndex == 0) dayOffset else 10 + dayOffset)
                .atZone(zone).toInstant().toEpochMilli()
            val duration = ((if (tripIndex == 0) 42 else 31) + dayOffset % 4) * 60_000L
            val batteryStart = 82 - tripIndex * 8
            TripRecord(
                id = "sample-trip-$date-$tripIndex",
                startedAtMs = start,
                endedAtMs = start + duration,
                startDate = date.toString(),
                zoneId = zone.id,
                durationMs = duration,
                distanceMeters = distance,
                consumedWh = distance / efficiency,
                batteryStartPercent = batteryStart,
                batteryEndPercent = batteryStart - if (tripIndex == 0) 6 else 4,
                status = TripStatus.COMPLETED,
            )
        }
    }
