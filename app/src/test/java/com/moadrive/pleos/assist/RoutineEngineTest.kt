package com.moadrive.pleos.assist

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.DayOfWeek

class RoutineEngineTest {
    private val engine = RoutineEngine()
    private fun at(value: String) = LocalDateTime.parse(value)
    private fun observation(value: String, scenario: RoutineScenario = RoutineScenario.COMMUTE) =
        RoutineObservation(scenario, at(value))

    @Test fun demoWeekdayExamplesLearnAllThreeRoutinesFromDistinctPastDays() {
        RoutineScenario.entries.forEach { scenario ->
            val suggestion = engine.suggest(DemoRoutines.observations(LocalDate.of(2026, 11, 4)), at("2026-11-04T00:00").withHour(scenario.hour))
            assertEquals(scenario, suggestion?.scenario)
            assertTrue((suggestion?.sampleCount ?: 0) >= 3)
            assertEquals(scenario.hour * 60, suggestion?.minuteOfDay)
        }
    }

    @Test fun rollingSeedHistoryUsesDistinctPastWeekdaysAcrossYearAndWeekendBoundaries() {
        listOf(LocalDate.of(2027, 1, 1), LocalDate.of(2028, 1, 3), LocalDate.of(2027, 5, 8)).forEach { date ->
            val records = DemoRoutines.observations(date)
            assertTrue(records.all { it.departedAt.toLocalDate() < date })
            assertTrue(records.all { it.departedAt.toLocalDate() >= date.minusDays(45) })
            assertTrue(records.none { it.departedAt.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) })
            assertEquals(5, records.filter { it.scenario == RoutineScenario.SCHOOL_PICKUP }
                .map { it.departedAt.toLocalDate() }.distinct().size)
            if (date.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) {
                RoutineScenario.entries.forEach { scenario ->
                    assertEquals(scenario, engine.suggest(records, date.atTime(scenario.hour, 0))?.scenario)
                }
            }
        }
    }

    @Test fun careSpeechIncludesTheScenarioQuestionAndSafetyWishWithoutChangingDestination() {
        RoutineScenario.entries.forEach { scenario ->
            assertTrue(scenario.careMessage.contains("오늘도 안전운전하세요"))
            assertTrue(scenario.speechText.contains(scenario.question))
            assertTrue(scenario.speechText.contains(scenario.careMessage))
            assertTrue(scenario.destinationQuery.isNotBlank())
        }
        assertTrue(RoutineScenario.COMMUTE.careMessage.contains("아침"))
        assertTrue(RoutineScenario.SCHOOL_PICKUP.careMessage.contains("아이"))
        assertTrue(RoutineScenario.RETURN_HOME.careMessage.contains("수고"))
    }

    @Test fun minimumSupportUsesDifferentDaysNotRepeatedSamples() {
        val duplicate = observation("2026-11-02T08:00")
        val records = List(8) { duplicate } + observation("2026-11-03T08:00")
        assertNull(engine.suggest(records, at("2026-11-04T08:00")))
    }

    @Test fun usesDominantTimeClusterAndMedianWithoutOutlierSkew() {
        val records = listOf("2026-10-26T07:55", "2026-10-27T08:00", "2026-10-28T08:04", "2026-10-29T08:05",
            "2026-10-30T12:00").map { observation(it) }
        val suggestion = engine.suggest(records, at("2026-11-04T08:10"))
        assertEquals(RoutineScenario.COMMUTE, suggestion?.scenario)
        assertEquals(4, suggestion?.sampleCount)
        assertEquals(482, suggestion?.minuteOfDay)
        assertNull(engine.suggest(records, at("2026-11-04T12:00")))
    }

    @Test fun doesNotProposeOnWeekendOrOutsideThirtyMinuteWindow() {
        val records = listOf("2026-10-26T08:00", "2026-10-27T08:00", "2026-10-28T08:00").map { observation(it) }
        assertNotNull(engine.suggest(records, at("2026-11-04T08:30")))
        assertNull(engine.suggest(records, at("2026-11-04T08:31")))
        assertNull(engine.suggest(records, at("2026-11-07T08:00")))
    }

    @Test fun ignoresFutureSameDayWeekendAndExpiredObservations() {
        val records = listOf("2026-08-03T08:00", "2026-08-04T08:00", "2026-08-05T08:00",
            "2026-10-31T08:00", "2026-11-01T08:00", "2026-11-04T07:59", "2026-11-05T08:00", "2026-11-06T08:00")
            .map { observation(it) }
        assertNull(engine.suggest(records, at("2026-11-04T08:00")))
    }

    @Test fun equallySupportedDifferentDestinationsAtSameTimeAreAmbiguous() {
        val records = listOf("2026-10-26T08:00", "2026-10-27T08:00", "2026-10-28T08:00").flatMap {
            listOf(observation(it), observation(it, RoutineScenario.SCHOOL_PICKUP))
        }
        assertNull(engine.suggest(records, at("2026-11-04T08:00")))
    }
}
