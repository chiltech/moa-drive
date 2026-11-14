package com.moagauge.pleos.care.pet

import java.time.LocalDate
import kotlin.math.roundToInt

enum class PetSpecies(val label: String, val activityLabel: String) {
    DOG("강아지", "산책"), CAT("고양이", "놀이·활동");

    companion object {
        /** Existing profiles and records predate species selection and describe dogs. */
        fun fromStored(value: String?): PetSpecies = entries.firstOrNull { it.name == value } ?: DOG
    }
}

data class PetProfile(
    val name: String = "",
    val ageYears: Double? = null,
    val weightKg: Double? = null,
    val usualMealGrams: Double? = null,
    // Keep the storage-facing name for existing data; cats use this as play/activity minutes.
    val usualWalkMinutes: Int? = null,
    val species: PetSpecies = PetSpecies.DOG,
) {
    val displayName: String get() = name.ifBlank { "우리 ${species.label}" }
}

data class PetDailyRecord(
    val date: LocalDate,
    val walkMinutes: Int? = null,
    val mealGrams: Double? = null,
    val conditionNote: String = "",
    val species: PetSpecies = PetSpecies.DOG,
)

data class PetCareData(
    val profile: PetProfile = PetProfile(),
    val records: List<PetDailyRecord> = emptyList(),
    val profiles: Map<PetSpecies, PetProfile> = emptyMap(),
) {
    fun profileFor(species: PetSpecies): PetProfile =
        if (profile.species == species) profile else profiles[species] ?: PetProfile(species = species)

    fun withProfile(updated: PetProfile): PetCareData = copy(
        profile = updated,
        profiles = profiles + (profile.species to profile) + (updated.species to updated),
    )

    fun recordFor(date: LocalDate): PetDailyRecord? = records.firstOrNull { it.date == date && it.species == profile.species }
    fun withRecord(record: PetDailyRecord): PetCareData = copy(
        records = (records.filterNot { it.date == record.date && it.species == record.species } + record).sortedByDescending { it.date }.take(90),
    )
    fun displayData(sampleMode: Boolean, today: LocalDate, sampleSpecies: PetSpecies = profile.species): PetCareData =
        if (sampleMode) sample(today, sampleSpecies) else this

    companion object {
        fun sample(today: LocalDate, species: PetSpecies = PetSpecies.DOG) = if (species == PetSpecies.CAT) PetCareData(
            profile = PetProfile("나비", 4.0, 4.1, 60.0, 20, PetSpecies.CAT),
            records = listOf(
                PetDailyRecord(today, 12, 50.0, "장난감 놀이 중 평소보다 일찍 쉬었어요.", PetSpecies.CAT),
                PetDailyRecord(today.minusDays(1), 20, 60.0, "", PetSpecies.CAT),
                PetDailyRecord(today.minusDays(2), 18, 60.0, "", PetSpecies.CAT),
            ),
        ) else PetCareData(
            profile = PetProfile("보리", 3.0, 6.2, 150.0, 40),
            records = listOf(
                PetDailyRecord(today, 25, 120.0, "평소보다 산책 중 쉬는 시간이 길었어요."),
                PetDailyRecord(today.minusDays(1), 40, 150.0, ""),
                PetDailyRecord(today.minusDays(2), 35, 150.0, ""),
            ),
        )
    }
}

data class PetValidation<T>(val value: T? = null, val error: String? = null)

data class PetProfileInput(
    val name: String = "",
    val ageYears: String = "",
    val weightKg: String = "",
    val usualMealGrams: String = "",
    val usualWalkMinutes: String = "",
    val species: PetSpecies = PetSpecies.DOG,
) {
    fun validate(): PetValidation<PetProfile> {
        if (name.trim().length > 24) return PetValidation(error = "이름은 24자 이내로 입력해 주세요.")
        val age = ageYears.optionalDouble()
        if (ageYears.isNotBlank() && (age == null || age !in 0.0..40.0))
            return PetValidation(error = "나이는 0~40세 사이로 입력해 주세요.")
        val weight = weightKg.optionalDouble()
        if (weightKg.isNotBlank() && (weight == null || weight !in 0.1..150.0))
            return PetValidation(error = "몸무게는 0.1~150kg 사이로 입력해 주세요.")
        val meal = usualMealGrams.optionalDouble()
        if (usualMealGrams.isNotBlank() && (meal == null || meal !in 1.0..3000.0))
            return PetValidation(error = "평소 하루 식사량은 1~3,000g 사이로 입력해 주세요.")
        val walk = usualWalkMinutes.trim().toIntOrNull()
        if (usualWalkMinutes.isNotBlank() && (walk == null || walk !in 1..1440))
            return PetValidation(error = "평소 하루 ${species.activityLabel}은 1~1,440분 사이로 입력해 주세요.")
        return PetValidation(PetProfile(name.trim(), age, weight, meal, walk, species))
    }

    companion object {
        fun from(profile: PetProfile) = PetProfileInput(profile.name, profile.ageYears?.petNumber().orEmpty(),
            profile.weightKg?.petNumber().orEmpty(), profile.usualMealGrams?.petNumber().orEmpty(),
            profile.usualWalkMinutes?.toString().orEmpty(), profile.species)
    }
}

data class PetRecordInput(val walkMinutes: String = "", val mealGrams: String = "", val conditionNote: String = "") {
    fun validate(date: LocalDate, species: PetSpecies = PetSpecies.DOG): PetValidation<PetDailyRecord> {
        val walk = walkMinutes.trim().toIntOrNull()
        if (walkMinutes.isNotBlank() && (walk == null || walk !in 0..1440))
            return PetValidation(error = "${species.activityLabel} 시간은 0~1,440분 사이의 정수로 입력해 주세요.")
        val meal = mealGrams.optionalDouble()
        if (mealGrams.isNotBlank() && (meal == null || meal !in 0.0..3000.0))
            return PetValidation(error = "하루 식사량은 0~3,000g 사이로 입력해 주세요.")
        if (conditionNote.trim().length > 500) return PetValidation(error = "상태 메모는 500자 이내로 입력해 주세요.")
        if (walk == null && meal == null && conditionNote.isBlank())
            return PetValidation(error = "${species.activityLabel}, 식사, 상태 중 한 가지 이상을 기록해 주세요.")
        return PetValidation(PetDailyRecord(date, walk, meal, conditionNote.trim(), species))
    }

    companion object {
        fun from(record: PetDailyRecord?) = PetRecordInput(record?.walkMinutes?.toString().orEmpty(),
            record?.mealGrams?.petNumber().orEmpty(), record?.conditionNote.orEmpty())
    }
}

enum class PetSuggestionKind { ACTIVITY, MEAL, CONDITION }
enum class PetSuggestionSource { USER_RECORD, MISSING_RECORD }

data class PetSuggestion(
    val kind: PetSuggestionKind,
    val title: String,
    val description: String,
    val source: PetSuggestionSource,
    val baselinePercent: Int? = null,
)

/** Summarizes caregiver entries only; no sensor inference, diagnosis, or ideal-dose calculation. */
object PetCareGuidance {
    fun forDay(data: PetCareData, day: LocalDate): List<PetSuggestion> {
        val record = data.recordFor(day)
        val species = data.profile.species
        val activity = species.activityLabel
        val walk = record?.walkMinutes
        val meal = record?.mealGrams
        val walkBaseline = data.profile.usualWalkMinutes?.takeIf { it > 0 }
        val mealBaseline = data.profile.usualMealGrams?.takeIf { it.isFinite() && it > 0 }
        val mealPercent = if (meal != null && mealBaseline != null) (meal / mealBaseline * 100).roundToInt() else null
        return listOf(
            if (walk == null) PetSuggestion(PetSuggestionKind.ACTIVITY, "오늘 ${activity}을 남겨 주세요",
                "시간을 기록하면 보호자가 정한 평소 ${activity}과 나란히 볼 수 있어요.", PetSuggestionSource.MISSING_RECORD)
            else PetSuggestion(PetSuggestionKind.ACTIVITY,
                if (walkBaseline != null) "${activity} ${walk}분 · 평소 ${walkBaseline}분" else "오늘 ${activity} ${walk}분",
                if (species == PetSpecies.CAT) {
                    if (walkBaseline != null && walk < walkBaseline) "평소보다 짧게 기록했어요. 다음 놀이도 ${data.profile.displayName}의 관심과 컨디션을 살피며 준비해요."
                    else "오늘의 놀이·활동 시간을 남겼어요. 편안히 쉬는 시간도 함께 살펴봐요."
                } else if (walkBaseline != null && walk < walkBaseline) "평소보다 짧게 기록했어요. 다음 산책은 오늘의 컨디션과 날씨를 먼저 살펴봐요."
                else "오늘의 산책 시간을 남겼어요. 다음 외출도 ${data.profile.displayName}의 컨디션에 맞춰 준비해요.",
                PetSuggestionSource.USER_RECORD),
            if (meal == null) PetSuggestion(PetSuggestionKind.MEAL, "오늘 식사량은 아직 비어 있어요",
                "실제로 먹은 양을 기록해 주세요. 적정 급여량을 자동으로 정하지 않아요.", PetSuggestionSource.MISSING_RECORD)
            else PetSuggestion(PetSuggestionKind.MEAL,
                if (mealPercent != null) "평소 식사량의 ${mealPercent}% 기록" else "오늘 식사 ${meal.petNumber()}g",
                if (mealPercent != null) "보호자가 정한 평소 ${mealBaseline!!.petNumber()}g과 비교했어요. 변화가 이어지는지 다음 기록도 함께 살펴봐요."
                else "평소 하루 식사량을 프로필에 입력하면 오늘 기록과 비교할 수 있어요.",
                PetSuggestionSource.USER_RECORD, mealPercent),
            if (record?.conditionNote.isNullOrBlank()) PetSuggestion(PetSuggestionKind.CONDITION, "작은 변화도 메모로 남겨요",
                "활동, 식욕, 배변 등 직접 살핀 변화를 남겨 주세요. 빈 기록을 정상 상태로 해석하지 않아요.", PetSuggestionSource.MISSING_RECORD)
            else PetSuggestion(PetSuggestionKind.CONDITION, "기록한 변화를 함께 살펴봐요",
                "“${record!!.conditionNote}”\n걱정되는 변화가 있다면 이 기록을 수의사 상담 때 보여 주세요.", PetSuggestionSource.USER_RECORD),
        )
    }
}

enum class PetPlaceSearch { CLINIC, PHARMACY, HOTEL, GROOMING, OUTING, RESTAURANT, STAY }

/** Search intents only: the returned query does not confirm a venue's animal policy. */
fun PetSpecies.searchQuery(place: PetPlaceSearch): String = when (place) {
    PetPlaceSearch.CLINIC -> "동물병원"
    PetPlaceSearch.PHARMACY -> "동물약국"
    PetPlaceSearch.HOTEL -> if (this == PetSpecies.CAT) "고양이 호텔" else "반려견 호텔"
    PetPlaceSearch.GROOMING -> if (this == PetSpecies.CAT) "고양이 미용" else "애견 미용"
    PetPlaceSearch.OUTING -> if (this == PetSpecies.CAT) "고양이 동반 카페" else "반려견 동반 공원"
    PetPlaceSearch.RESTAURANT -> if (this == PetSpecies.CAT) "고양이 동반 식당" else "반려견 동반 식당"
    PetPlaceSearch.STAY -> if (this == PetSpecies.CAT) "고양이 동반 숙소" else "반려견 동반 숙소"
}

fun Double.petNumber(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()
private fun String.optionalDouble(): Double? = trim().toDoubleOrNull()?.takeIf { it.isFinite() }
