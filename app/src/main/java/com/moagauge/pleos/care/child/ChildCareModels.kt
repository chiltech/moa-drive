package com.moagauge.pleos.care.child

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import com.moagauge.pleos.care.school.SchoolInfo
import com.moagauge.pleos.care.school.isValidSchool
import com.moagauge.pleos.care.school.SchoolMealResult

enum class ChildGender(val label: String) { UNSPECIFIED("선택 안 함"), GIRL("여아"), BOY("남아") }

data class ChildProfile(
    val name: String,
    val age: Int,
    val gender: ChildGender = ChildGender.UNSPECIFIED,
    val school: String = "",
    val schoolInfo: SchoolInfo? = null,
) {
    /** Legacy text remains readable, but only an identified school may be queried. */
    val linkedSchool: SchoolInfo? get() = schoolInfo?.takeIf { it.isValidSchool() }
}

data class ChildSchoolDay(
    val date: LocalDate,
    val classes: String = "",
    val meal: String = "",
    val isSample: Boolean = false,
)

enum class ChildReminderStatus { UPCOMING, TODAY, OVERDUE, COMPLETE }

data class ChildVaccinationReminder(
    val id: String,
    val title: String,
    val date: LocalDate,
    val note: String = "",
    val completedOn: LocalDate? = null,
    val notificationsEnabled: Boolean = true,
) {
    fun status(today: LocalDate): ChildReminderStatus = when {
        completedOn != null -> ChildReminderStatus.COMPLETE
        date.isBefore(today) -> ChildReminderStatus.OVERDUE
        date == today -> ChildReminderStatus.TODAY
        else -> ChildReminderStatus.UPCOMING
    }
}

data class ChildCareData(
    val profile: ChildProfile? = null,
    val schoolDay: ChildSchoolDay? = null,
    val reminders: List<ChildVaccinationReminder> = emptyList(),
) {
    fun schoolDayFor(date: LocalDate): ChildSchoolDay? = schoolDay?.takeIf { it.date == date }
}

data class ChildConversationPrompt(val label: String, val question: String, val isSample: Boolean = false)

fun validateChildProfile(name: String, age: String, school: String): String? = when {
    name.trim().isEmpty() -> "아이의 이름이나 애칭을 입력해 주세요."
    name.trim().length > 24 -> "이름은 24자 이내로 입력해 주세요."
    age.toIntOrNull() !in 0..18 -> "나이는 0~18세의 숫자로 입력해 주세요."
    school.trim().length > 100 -> "학교 이름은 100자 이내로 입력해 주세요."
    else -> null
}

/** ISO local dates are intentionally not converted through UTC timestamps. */
fun parseChildReminderDate(value: String): LocalDate? = value.trim()
    .takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    ?.takeIf { it.year in 2000..2100 }

fun childLocalDate(instant: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    instant.atZone(zone).toLocalDate()

fun List<ChildVaccinationReminder>.sortedChildReminders(): List<ChildVaccinationReminder> =
    sortedWith(compareBy<ChildVaccinationReminder> { it.completedOn != null }.thenBy { it.date }.thenBy { it.title })

fun sampleChildSchoolDay(today: LocalDate) = ChildSchoolDay(today, "미술 · 나만의 동물 그리기", "카레와 과일", isSample = true)

/** Only the selected school's matching-day published meal may replace a personal meal note. */
fun childConversationSchoolDay(today: LocalDate, entered: ChildSchoolDay?, published: SchoolMealResult?): ChildSchoolDay? {
    val current = entered?.takeIf { it.date == today && !it.isSample }
    val meals = published?.takeIf { it.date == today }?.meals.orEmpty()
    val meal = meals.firstOrNull { it.mealType.contains("중식") } ?: meals.firstOrNull()
    val allergenNumbers = Regex("\\(\\s*\\d+(?:[\\s.,]+\\d+)*[\\s.,]*\\)")
    val menu = meal?.menu?.lineSequence()?.map { dish ->
        dish.replace(allergenNumbers, "").replace(Regex("\\s+"), " ").trim()
    }?.filter(String::isNotBlank)?.take(2)?.joinToString(", ").orEmpty()
    if (menu.isBlank()) return current
    return ChildSchoolDay(today, current?.classes.orEmpty(), menu.take(160))
}

/** Small, local templates: these prompts make no claim to a school or language-model connection. */
fun childConversationPrompts(profile: ChildProfile?, day: ChildSchoolDay?): List<ChildConversationPrompt> {
    val name = profile?.name?.takeIf { it.isNotBlank() } ?: "우리 아이"
    val young = profile?.age?.let { it < 6 } == true
    val sample = day?.isSample == true
    val classes = day?.classes?.trim().orEmpty()
    val meal = day?.meal?.trim().orEmpty()
    return listOf(
        ChildConversationPrompt(if (classes.isEmpty()) "오늘의 발견" else "수업 이야기", when {
            classes.isNotEmpty() && young -> "$name, 오늘 ‘$classes’에서 했던 놀이를 같이 해볼까?"
            classes.isNotEmpty() -> "$name, 오늘 ‘$classes’에서 가장 기억에 남는 건 뭐였어?"
            young -> "$name, 오늘 가장 재미있었던 놀이를 알려줄래?"
            else -> "$name, 오늘 새롭게 알게 된 것 하나를 알려줄래?"
        }, sample),
        ChildConversationPrompt(if (meal.isEmpty()) "맛있는 이야기" else "급식 이야기", if (meal.isNotEmpty())
            "$name, 오늘 먹은 ‘$meal’ 중에 가장 마음에 든 맛은 뭐였어?"
        else "$name, 오늘 먹은 것 중에 또 먹고 싶은 건 뭐야?", sample),
        ChildConversationPrompt("마음 나누기", if (young)
            "$name, 오늘 기분을 색깔로 고른다면 무슨 색일까?"
        else "$name, 오늘 웃었던 순간이나 이야기하고 싶은 일이 있어?", sample),
    )
}
