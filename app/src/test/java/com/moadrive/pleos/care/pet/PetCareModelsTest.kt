package com.moadrive.pleos.care.pet

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class PetCareModelsTest {
    private val today = LocalDate.of(2026, 11, 13)

    @Test fun optionalProfileNumbersStayUnknownRatherThanBecomingZero() {
        val result = PetProfileInput(name = "  모아  ").validate()
        assertNull(result.error)
        assertEquals("모아", result.value?.name)
        assertNull(result.value?.ageYears)
        assertNull(result.value?.weightKg)
        assertNull(result.value?.usualMealGrams)
    }

    @Test fun invalidAmountsCannotBecomeStoredProfileValues() {
        listOf("NaN", "Infinity", "-1", "0", "3001").forEach { amount ->
            assertNull(PetProfileInput(name = "모아", usualMealGrams = amount).validate().value)
        }
        assertNull(PetProfileInput(name = "모아", weightKg = "NaN").validate().value)
        assertNull(PetProfileInput(name = "모아", usualWalkMinutes = "1441").validate().value)
    }

    @Test fun zeroWalkAndMealAreExplicitRecordsWhileBlanksAreMissing() {
        val empty = PetRecordInput().validate(today)
        assertNull(empty.value)
        val recorded = PetRecordInput(walkMinutes = "0", mealGrams = "0").validate(today)
        assertNull(recorded.error)
        assertEquals(0, recorded.value?.walkMinutes)
        assertEquals(0.0, recorded.value?.mealGrams)
        assertNull(PetRecordInput(conditionNote = "활동 변화 메모").validate(today).value?.walkMinutes)
    }

    @Test fun malformedOrOutOfRangeDailyValuesAreRejected() {
        listOf("-1", "1441", "1.5", "NaN").forEach {
            assertNull(PetRecordInput(walkMinutes = it).validate(today).value)
        }
        assertNull(PetRecordInput(mealGrams = "Infinity").validate(today).value)
        assertNull(PetRecordInput(conditionNote = "가".repeat(501)).validate(today).value)
    }

    @Test fun yesterdayDoesNotMasqueradeAsTodaysObservation() {
        val state = PetCareData(records = listOf(PetDailyRecord(today.minusDays(1), 35, 100.0, "")))
        assertNull(state.recordFor(today))
        val guidance = PetCareGuidance.forDay(state, today)
        assertTrue(guidance.all { it.source == PetSuggestionSource.MISSING_RECORD })
    }

    @Test fun mealComparisonUsesTheUsersBaseline() {
        val data = PetCareData(PetProfile(name = "모아", usualMealGrams = 200.0),
            listOf(PetDailyRecord(today, mealGrams = 150.0)))
        val meal = PetCareGuidance.forDay(data, today).first { it.kind == PetSuggestionKind.MEAL }
        assertEquals(75, meal.baselinePercent)
        assertEquals(PetSuggestionSource.USER_RECORD, meal.source)
    }

    @Test fun missingBaselineDoesNotInferAnIdealAmount() {
        val data = PetCareData(records = listOf(PetDailyRecord(today, mealGrams = 150.0)))
        val meal = PetCareGuidance.forDay(data, today).first { it.kind == PetSuggestionKind.MEAL }
        assertNull(meal.baselinePercent)
        assertEquals(PetSuggestionSource.USER_RECORD, meal.source)
    }

    @Test fun updatingTodaysRecordPreservesOtherDaysAndReplacesSameDay() {
        val earlier = PetDailyRecord(today.minusDays(1), walkMinutes = 20)
        val old = PetDailyRecord(today, walkMinutes = 10)
        val data = PetCareData(records = listOf(earlier, old))
            .withRecord(PetDailyRecord(today, walkMinutes = 30))
        assertEquals(2, data.records.size)
        assertEquals(30, data.recordFor(today)?.walkMinutes)
        assertEquals(20, data.recordFor(today.minusDays(1))?.walkMinutes)
    }

    @Test fun sampleDataIsExplicitAndDoesNotMutateActualRecords() {
        val actual = PetCareData(PetProfile(name = "우리 강아지"))
        val shown = actual.displayData(sampleMode = true, today)
        assertTrue(shown.records.isNotEmpty())
        assertTrue(actual.records.isEmpty())
        assertEquals(actual, actual.displayData(sampleMode = false, today))
    }

    @Test fun legacySpeciesValuesKeepExistingDogRecords() {
        assertEquals(PetSpecies.DOG, PetSpecies.fromStored(null))
        assertEquals(PetSpecies.DOG, PetSpecies.fromStored(""))
        assertEquals(PetSpecies.DOG, PetSpecies.fromStored("unexpected"))
        assertEquals(PetSpecies.CAT, PetSpecies.fromStored("CAT"))
        assertEquals(PetSpecies.DOG, PetProfileInput().validate().value?.species)
    }

    @Test fun catProfileSurvivesEditingAndDailyValidation() {
        val input = PetProfileInput(name = "나비", usualWalkMinutes = "15", species = PetSpecies.CAT)
        val profile = input.validate().value!!
        assertEquals(PetSpecies.CAT, PetProfileInput.from(profile).validate().value?.species)
        val record = PetRecordInput(walkMinutes = "10").validate(today, profile.species).value!!
        assertEquals(PetSpecies.CAT, record.species)
        assertEquals(10, record.walkMinutes)
    }

    @Test fun changingToCatDoesNotReuseADogWalkAsCatActivity() {
        val dogRecord = PetDailyRecord(today, walkMinutes = 40)
        val catProfile = PetProfile(species = PetSpecies.CAT)
        val data = PetCareData(catProfile, listOf(dogRecord))
        assertNull(data.recordFor(today))
        assertEquals(PetSuggestionSource.MISSING_RECORD,
            PetCareGuidance.forDay(data, today).first { it.kind == PetSuggestionKind.ACTIVITY }.source)
        val withCat = data.withRecord(PetDailyRecord(today, walkMinutes = 10, species = PetSpecies.CAT))
        assertEquals(2, withCat.records.size)
        assertEquals(10, withCat.recordFor(today)?.walkMinutes)
        assertEquals(40, withCat.copy(profile = PetProfile()).recordFor(today)?.walkMinutes)
    }

    @Test fun catActivityGuidanceDoesNotSuggestADogWalk() {
        val data = PetCareData(PetProfile(usualWalkMinutes = 20, species = PetSpecies.CAT),
            listOf(PetDailyRecord(today, walkMinutes = 10, species = PetSpecies.CAT)))
        val activity = PetCareGuidance.forDay(data, today).first { it.kind == PetSuggestionKind.ACTIVITY }
        assertEquals(PetSuggestionSource.USER_RECORD, activity.source)
        assertTrue(activity.title.contains("놀이·활동"))
        assertFalse(activity.description.contains("산책"))
        assertFalse(activity.description.contains("외출"))
    }

    @Test fun catSearchesDoNotSendDogOnlyQueries() {
        assertEquals("동물병원", PetSpecies.CAT.searchQuery(PetPlaceSearch.CLINIC))
        assertEquals("동물약국", PetSpecies.CAT.searchQuery(PetPlaceSearch.PHARMACY))
        assertEquals("고양이 호텔", PetSpecies.CAT.searchQuery(PetPlaceSearch.HOTEL))
        assertEquals("고양이 미용", PetSpecies.CAT.searchQuery(PetPlaceSearch.GROOMING))
        assertEquals("고양이 동반 카페", PetSpecies.CAT.searchQuery(PetPlaceSearch.OUTING))
        assertEquals("고양이 동반 식당", PetSpecies.CAT.searchQuery(PetPlaceSearch.RESTAURANT))
        assertEquals("고양이 동반 숙소", PetSpecies.CAT.searchQuery(PetPlaceSearch.STAY))
        assertEquals("반려견 동반 공원", PetSpecies.DOG.searchQuery(PetPlaceSearch.OUTING))
    }

    @Test fun catSampleSelectionDoesNotModifyTheSavedDogProfileOrRecords() {
        val actual = PetCareData(PetProfile(name = "보리"), listOf(PetDailyRecord(today, walkMinutes = 35)))
        val shown = actual.displayData(true, today, sampleSpecies = PetSpecies.CAT)
        assertEquals(PetSpecies.CAT, shown.profile.species)
        assertTrue(shown.records.all { it.species == PetSpecies.CAT })
        assertFalse(shown.recordFor(today)!!.conditionNote.contains("산책"))
        assertEquals(PetSpecies.DOG, actual.profile.species)
        assertEquals(35, actual.recordFor(today)?.walkMinutes)
        assertEquals(actual, actual.displayData(false, today, sampleSpecies = PetSpecies.CAT))
    }

    @Test fun switchingDogCatDogRestoresEachProfileBaselineAndRecords() {
        val dog = PetProfile("보리", 3.0, 6.2, 150.0, 40)
        val dogRecord = PetDailyRecord(today, 40, 150.0)
        val original = PetCareData(dog, listOf(dogRecord))
        val firstCat = original.profileFor(PetSpecies.CAT)
        assertEquals(PetSpecies.CAT, firstCat.species)
        assertEquals("", firstCat.name)
        assertNull(firstCat.usualMealGrams)
        assertNull(firstCat.usualWalkMinutes)

        val cat = PetProfile("나비", 4.0, 4.1, 60.0, 20, PetSpecies.CAT)
        val catData = original.withProfile(cat)
            .withRecord(PetDailyRecord(today, 20, 60.0, species = PetSpecies.CAT))
        val restored = catData.withProfile(catData.profileFor(PetSpecies.DOG))
        assertEquals(dog, restored.profile)
        assertEquals(dogRecord, restored.recordFor(today))
        assertEquals(100, PetCareGuidance.forDay(restored, today)
            .first { it.kind == PetSuggestionKind.MEAL }.baselinePercent)
        assertEquals(cat, restored.profileFor(PetSpecies.CAT))
        assertEquals(2, restored.records.size)
    }
}
