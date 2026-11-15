package com.moadrive.pleos.care.pet

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Device-local storage. Example records never pass through this store. */
class PetCareStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("pet_care", Context.MODE_PRIVATE)

    fun load(): PetCareData = runCatching {
        val root = JSONObject(preferences.getString("data", null) ?: return PetCareData())
        val profileJson = root.optJSONObject("profile") ?: JSONObject()
        val profile = profileJson.readProfile(PetSpecies.fromStored(profileJson.optString("species")))
        val profilesJson = root.optJSONObject("profiles")
        val profiles = buildMap {
            PetSpecies.entries.forEach { species ->
                profilesJson?.optJSONObject(species.name)?.let { put(species, it.readProfile(species)) }
            }
            // Migrates the former single profile and keeps the active saved profile authoritative.
            put(profile.species, profile)
        }
        val list = root.optJSONArray("records") ?: JSONArray()
        val records = buildList {
            for (index in 0 until list.length()) {
                val item = list.optJSONObject(index) ?: continue
                val date = runCatching { LocalDate.parse(item.optString("date")) }.getOrNull() ?: continue
                val record = PetRecordInput(item.valueString("walkMinutes"), item.valueString("mealGrams"),
                    item.optString("conditionNote")).validate(date, PetSpecies.fromStored(item.optString("species"))).value ?: continue
                add(record)
            }
        }.distinctBy { it.date to it.species }.sortedByDescending { it.date }.take(90)
        PetCareData(profile, records, profiles)
    }.getOrDefault(PetCareData())

    fun save(data: PetCareData) {
        val profiles = JSONObject().apply {
            (data.profiles + (data.profile.species to data.profile)).forEach { (species, profile) ->
                put(species.name, profile.toJson())
            }
        }
        val records = JSONArray().apply {
            data.records.take(90).forEach { record ->
                put(JSONObject().put("date", record.date.toString()).put("walkMinutes", record.walkMinutes)
                    .put("mealGrams", record.mealGrams).put("conditionNote", record.conditionNote).put("species", record.species.name))
            }
        }
        preferences.edit().putString("data", JSONObject().put("profile", data.profile.toJson())
            .put("profiles", profiles).put("records", records).toString()).apply()
    }

    private fun JSONObject.readProfile(species: PetSpecies): PetProfile = PetProfileInput(
        optString("name"), valueString("ageYears"), valueString("weightKg"),
        valueString("usualMealGrams"), valueString("usualWalkMinutes"), species,
    ).validate().value ?: PetProfile(species = species)

    private fun PetProfile.toJson(): JSONObject = JSONObject().put("name", name).put("species", species.name)
        .put("ageYears", ageYears).put("weightKg", weightKg)
        .put("usualMealGrams", usualMealGrams).put("usualWalkMinutes", usualWalkMinutes)

    private fun JSONObject.valueString(key: String): String = if (isNull(key)) "" else optString(key)
}
