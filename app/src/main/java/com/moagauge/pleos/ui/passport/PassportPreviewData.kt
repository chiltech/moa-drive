package com.moagauge.pleos.ui.passport

import com.moagauge.pleos.passport.PassportCell
import com.moagauge.pleos.passport.PassportVisit
import com.moagauge.pleos.trips.TripRecord
import com.moagauge.pleos.trips.TripStatus
import java.time.LocalDate
import java.time.ZoneId

internal data class PassportPreviewData(val trips: List<TripRecord>, val visits: List<PassportVisit>)

/** In-memory display fixtures only. No recorder or passport store ever receives these rows. */
internal fun passportPreviewData(): PassportPreviewData {
    val zone = ZoneId.of("Asia/Seoul")
    val today = LocalDate.now(zone)
    val destinations = listOf(
        Triple("서울특별시", 37.5665, 126.9780),
        Triple("수원시", 37.2636, 127.0286),
        Triple("용인시", 37.2411, 127.1776),
        Triple("춘천시", 37.8813, 127.7298),
        Triple("강릉시", 37.7519, 128.8761),
        Triple("속초시", 38.175159, 128.517112),
    )
    val trips = destinations.mapIndexed { index, _ ->
        val day = today.minusDays((destinations.size - index).toLong() * 3)
        val start = day.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val duration = (45L + index * 12) * 60_000
        val distance = (24.0 + index * 18.0) * 1_000
        TripRecord("passport-preview-$index", start, start + duration, day.toString(), zone.id,
            duration, distance, distance / 6.2, 90, 86 - index * 3, TripStatus.COMPLETED)
    }
    val visits = destinations.mapIndexed { index, (name, latitude, longitude) ->
        PassportVisit(trips[index].id, requireNotNull(PassportCell.from(latitude, longitude)),
            requireNotNull(trips[index].endedAtMs), name)
    }
    return PassportPreviewData(trips, visits)
}
