package com.moagauge.pleos.assist

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs

/** Learns clock-time patterns from explicitly supplied observations; it does not collect or persist trips. */
class RoutineEngine {
    fun suggest(observations: List<RoutineObservation>, boardingAt: LocalDateTime): RoutineSuggestion? {
        val boardingDate = boardingAt.toLocalDate()
        if (!boardingDate.isWeekday()) return null
        val earliestDate = boardingDate.minusDays(45)
        val minute = boardingAt.hour * 60 + boardingAt.minute
        val past = observations.filter {
            val date = it.departedAt.toLocalDate()
            date.isWeekday() && date >= earliestDate && date < boardingDate
        }
        val suggestions = past.groupBy { it.scenario }.mapNotNull { (scenario, samples) ->
            val distinctDayCount = samples.map { it.departedAt.toLocalDate() }.distinct().size
            val clusters = samples.map { it.minute() }.distinct().mapNotNull cluster@{ center ->
                val window = samples.filter { clockDistance(it.minute(), center) <= 30 }
                    .groupBy { it.departedAt.toLocalDate() }.values.map { sameDay ->
                        sameDay.minWith(compareBy<RoutineObservation> { clockDistance(it.minute(), center) }
                            .thenBy { it.departedAt })
                    }
                // A repeated timestamp cannot manufacture confidence; split schedules are not a dominant routine.
                if (window.size < 3 || window.size * 2 <= distinctDayCount) return@cluster null
                val minutes = window.map { center + clockOffset(it.minute(), center) }.sorted()
                val median = if (minutes.size % 2 == 1) minutes[minutes.size / 2]
                    else Math.floorDiv(minutes[minutes.size / 2 - 1] + minutes[minutes.size / 2], 2)
                val typicalMinute = (median + 1440) % 1440
                RoutineSuggestion(scenario, window.size, typicalMinute)
            }
            clusters.filter { clockDistance(it.minuteOfDay, minute) <= 30 }
                .sortedWith(compareByDescending<RoutineSuggestion> { it.sampleCount }
                    .thenBy { clockDistance(it.minuteOfDay, minute) }.thenBy { it.minuteOfDay })
                .firstOrNull()
        }.sortedWith(compareBy<RoutineSuggestion> { clockDistance(it.minuteOfDay, minute) }
            .thenByDescending { it.sampleCount }.thenBy { it.scenario.ordinal })
        val first = suggestions.firstOrNull() ?: return null
        val second = suggestions.getOrNull(1)
        if (second != null && clockDistance(first.minuteOfDay, minute) == clockDistance(second.minuteOfDay, minute)
            && first.sampleCount == second.sampleCount) return null
        return first
    }

    private fun RoutineObservation.minute() = departedAt.hour * 60 + departedAt.minute
    private fun clockOffset(minute: Int, center: Int) = (minute - center + 2160) % 1440 - 720
    private fun clockDistance(a: Int, b: Int) = abs(clockOffset(a, b))
}

private fun LocalDate.isWeekday() = dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY

/** Rolling seeded examples, separate from the user's actual driving history. No trips are collected. */
object DemoRoutines {
    fun observations(referenceDate: LocalDate = LocalDate.now(ZoneId.of("Asia/Seoul"))): List<RoutineObservation> {
        val pastDays = generateSequence(referenceDate.minusDays(1)) { it.minusDays(1) }
            .filter { it.isWeekday() }.take(6).toList()
        val sampleDays = pastDays.take(5).reversed()
        val minuteOffsets = listOf(-5L, 0L, 0L, 4L, 6L)
        return RoutineScenario.entries.flatMap { scenario ->
            minuteOffsets.mapIndexed { index, offset ->
                RoutineObservation(scenario, sampleDays[index].atTime(scenario.hour, 0).plusMinutes(offset))
            }
        } + RoutineObservation(RoutineScenario.COMMUTE, pastDays.last().atTime(11, 0))
    }
}
