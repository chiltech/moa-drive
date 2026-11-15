package com.moadrive.pleos.care.child

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import com.moadrive.pleos.care.school.SchoolInfo
import com.moadrive.pleos.care.school.SchoolMeal
import com.moadrive.pleos.care.school.SchoolMealResult

class ChildCareModelsTest {
    @Test fun profileRejectsMissingNameAndOutOfRangeAge() {
        assertNotNull(validateChildProfile("  ", "8", "학교"))
        assertNotNull(validateChildProfile("아이", "-1", "학교"))
        assertNotNull(validateChildProfile("아이", "19", "학교"))
        assertNotNull(validateChildProfile("아이", "eight", "학교"))
        assertNull(validateChildProfile("아이", "0", ""))
        assertNull(validateChildProfile("아이", "18", "학교"))
    }

    @Test fun invalidCalendarDatesAreNotNormalized() {
        assertNull(parseChildReminderDate("2026-02-29"))
        assertNull(parseChildReminderDate("2026-13-01"))
        assertNull(parseChildReminderDate("2026-2-01"))
        assertEquals(LocalDate.of(2028, 2, 29), parseChildReminderDate("2028-02-29"))
    }

    @Test fun reminderStatusUsesCalendarDaysAndCompletionWins() {
        val today = LocalDate.of(2026, 11, 13)
        val reminder = ChildVaccinationReminder("id", "접종 확인", today)
        assertEquals(ChildReminderStatus.TODAY, reminder.status(today))
        assertEquals(ChildReminderStatus.UPCOMING, reminder.copy(date = today.plusDays(1)).status(today))
        assertEquals(ChildReminderStatus.OVERDUE, reminder.copy(date = today.minusDays(1)).status(today))
        assertEquals(ChildReminderStatus.COMPLETE, reminder.copy(completedOn = today).status(today.plusDays(10)))
    }

    @Test fun localDateFollowsUserTimezoneAtMidnightBoundary() {
        val instant = Instant.parse("2026-11-12T16:00:00Z")
        assertEquals(LocalDate.of(2026, 11, 13), childLocalDate(instant, ZoneId.of("Asia/Seoul")))
        assertEquals(LocalDate.of(2026, 11, 12), childLocalDate(instant, ZoneId.of("America/Los_Angeles")))
    }

    @Test fun conversationUsesProvidedClassMealAndName() {
        val profile = ChildProfile("테스트 아이", 8, ChildGender.UNSPECIFIED, "테스트 학교")
        val day = ChildSchoolDay(LocalDate.of(2026, 11, 13), "과학 실험", "카레")
        val prompts = childConversationPrompts(profile, day)
        assertTrue(prompts.any { it.question.contains("과학 실험") })
        assertTrue(prompts.any { it.question.contains("카레") })
        assertTrue(prompts.all { it.question.contains("테스트 아이") })
        assertTrue(prompts.none { it.isSample })
    }

    @Test fun missingSchoolInputsNeverInventClassesOrMeals() {
        val prompts = childConversationPrompts(null, null)
        assertEquals(3, prompts.size)
        assertTrue(prompts.none { it.question.contains("수학") || it.question.contains("카레") })
        assertTrue(prompts.none { it.isSample })
    }

    @Test fun sampleInputsRemainExplicitlySampleAndYoungChildPromptsAdapt() {
        val day = sampleChildSchoolDay(LocalDate.of(2026, 11, 13))
        val prompts = childConversationPrompts(ChildProfile("샘플 아이", 4, ChildGender.UNSPECIFIED, ""), day)
        assertTrue(prompts.all { it.isSample })
        assertTrue(prompts.any { it.question.contains("놀이") })
    }

    @Test fun priorSchoolDayIsNotUsedAsTodaysInformation() {
        val day = ChildSchoolDay(LocalDate.of(2026, 11, 12), "과학", "카레")
        val data = ChildCareData(schoolDay = day)
        assertNull(data.schoolDayFor(LocalDate.of(2026, 11, 13)))
        assertEquals(day, data.schoolDayFor(LocalDate.of(2026, 11, 12)))
    }

    @Test fun incompleteRemindersSortBeforeCompletedAndRespectDate() {
        val today = LocalDate.of(2026, 11, 13)
        val done = ChildVaccinationReminder("done", "완료", today.minusDays(3), completedOn = today)
        val later = ChildVaccinationReminder("later", "다음", today.plusDays(2))
        val due = ChildVaccinationReminder("due", "오늘", today)
        assertEquals(listOf("due", "later", "done"), listOf(done, later, due).sortedChildReminders().map { it.id })
    }

    @Test fun legacySchoolNameAloneCannotBeUsedForLiveMeals() {
        val legacy = ChildProfile("아이", 8, school = "기존 학교 이름")
        assertNull(legacy.linkedSchool)
        val selected = SchoolInfo("B10", "7130180", "테스트 학교", "서울특별시 테스트 주소", "서울특별시")
        assertEquals(selected, legacy.copy(schoolInfo = selected).linkedSchool)
        assertNull(legacy.copy(schoolInfo = selected.copy(schoolCode = "invalid")).linkedSchool)
    }

    @Test fun matchingPublishedLunchPersonalizesConversationWithoutReplacingClasses() {
        val today = LocalDate.of(2026, 11, 13)
        val entered = ChildSchoolDay(today, "과학 실험", "직접 적은 간식")
        val published = SchoolMealResult(today, listOf(
            SchoolMeal("조식", "아침 메뉴", "", ""),
            SchoolMeal("중식", "쌀밥\n된장국(5.6.)\n사과", "600 Kcal", "5, 6"),
        ))
        val result = childConversationSchoolDay(today, entered, published)!!
        assertEquals("과학 실험", result.classes)
        assertEquals("쌀밥, 된장국", result.meal)
        assertFalse(result.meal.contains("아침 메뉴"))
        assertFalse(result.isSample)
    }

    @Test fun anotherDatesMealNeverBecomesTodaysConversation() {
        val today = LocalDate.of(2026, 11, 13)
        val yesterday = SchoolMealResult(today.minusDays(1), listOf(SchoolMeal("중식", "어제 급식", "", "")))
        val entered = ChildSchoolDay(today, "미술", "오늘 식사 메모")
        assertEquals(entered, childConversationSchoolDay(today, entered, yesterday))
        assertNull(childConversationSchoolDay(today, null, yesterday))
    }

    @Test fun conversationRemovesOnlyNumericAllergyParenthesesAndKeepsOriginalMeal() {
        val today = LocalDate.of(2026, 11, 13)
        val rawMenu = "소불고기주먹밥 (1.5.6.13.16.18)\n닭고기쌀국수(순한맛·2인분) (1, 5, 6)\n사과"
        val published = SchoolMealResult(today, listOf(SchoolMeal("중식", rawMenu, "600 Kcal", "1, 5, 6, 13, 16, 18")))
        val result = childConversationSchoolDay(today, null, published)!!
        assertEquals("소불고기주먹밥, 닭고기쌀국수(순한맛·2인분)", result.meal)
        assertEquals(rawMenu, published.meals.single().menu)
        assertEquals("1, 5, 6, 13, 16, 18", published.meals.single().allergyText)
    }
}
