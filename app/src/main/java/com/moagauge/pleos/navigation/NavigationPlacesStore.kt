package com.moagauge.pleos.navigation

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Private on-vehicle storage; no location data is sent outside the navigation provider. */
class NavigationPlacesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("navigation_places", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(read())
    val state = mutableState.asStateFlow()

    fun addRecentQuery(query: String) = update { NavigationPlacesRules.addRecentQuery(it, query) }
    fun removeRecentQuery(query: String) = update { NavigationPlacesRules.removeRecentQuery(it, query) }
    fun clearRecentQueries() = update { it.copy(recentQueries = emptyList()) }
    fun savePlace(slot: SavedPlaceSlot, destination: NavigationDestination) = update { NavigationPlacesRules.savePlace(it, slot, destination) }
    fun removePlace(slot: SavedPlaceSlot) = update { it.copy(savedPlaces = it.savedPlaces - slot) }

    @Synchronized
    private fun update(transform: (NavigationPlacesState) -> NavigationPlacesState) {
        val next = NavigationPlacesRules.normalized(transform(mutableState.value))
        if (next == mutableState.value) return
        val saved = JSONObject()
        next.savedPlaces.forEach { (slot, destination) ->
            saved.put(slot.name, JSONObject().put("id", destination.id).put("name", destination.name)
                .put("address", destination.address).put("latitude", destination.point.latitude).put("longitude", destination.point.longitude))
        }
        val data = JSONObject().put("version", 1).put("recent", JSONArray(next.recentQueries)).put("saved", saved)
        prefs.edit().putString("places_v1", data.toString()).apply()
        mutableState.value = next
    }

    private fun read(): NavigationPlacesState = runCatching {
        val raw = prefs.getString("places_v1", null) ?: return NavigationPlacesState()
        if (raw.length > 64_000) return NavigationPlacesState()
        val data = JSONObject(raw)
        if (data.optInt("version", 1) != 1) return NavigationPlacesState()
        val recentJson = data.optJSONArray("recent")
        val recent = if (recentJson == null) emptyList() else (0 until minOf(recentJson.length(), 200))
            .mapNotNull { index -> (recentJson.opt(index) as? String)?.takeIf(String::isNotBlank) }
        val savedJson = data.optJSONObject("saved")
        val saved = SavedPlaceSlot.entries.mapNotNull { slot ->
            val entry = savedJson?.optJSONObject(slot.name) ?: return@mapNotNull null
            runCatching {
                NavigationDestination(entry.getString("id"), entry.getString("name"), entry.optString("address", ""),
                    GeoPoint(entry.getDouble("latitude"), entry.getDouble("longitude")))
            }.getOrNull()?.let(NavigationPlacesRules::validPlace)?.let { slot to it }
        }.toMap()
        NavigationPlacesRules.normalized(NavigationPlacesState(recent, saved))
    }.getOrDefault(NavigationPlacesState())
}
