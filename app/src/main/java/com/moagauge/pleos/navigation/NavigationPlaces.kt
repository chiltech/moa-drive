package com.moagauge.pleos.navigation

enum class SavedPlaceSlot(val label: String) { HOME("집"), WORK("회사") }

data class NavigationPlacesState(
    val recentQueries: List<String> = emptyList(),
    val savedPlaces: Map<SavedPlaceSlot, NavigationDestination> = emptyMap(),
)

/** Bounded, provider-independent data. The native provider restores a POI from saved coordinates. */
object NavigationPlacesRules {
    const val MAX_RECENT_QUERIES = 20
    private val spaces = Regex("[\\s\\u00a0]+")
    fun normalizeQuery(query: String): String = query.trim().replace(spaces, " ").take(120)

    fun addRecentQuery(state: NavigationPlacesState, query: String): NavigationPlacesState {
        val clean = normalizeQuery(query)
        if (clean.isBlank()) return state
        return state.copy(recentQueries = (listOf(clean) + state.recentQueries.map(::normalizeQuery)
            .filter { it.isNotBlank() && !it.equals(clean, ignoreCase = true) })
            .distinctBy { it.lowercase(java.util.Locale.ROOT) }.take(MAX_RECENT_QUERIES))
    }

    fun removeRecentQuery(state: NavigationPlacesState, query: String): NavigationPlacesState {
        val clean = normalizeQuery(query)
        return state.copy(recentQueries = state.recentQueries.filterNot { normalizeQuery(it).equals(clean, ignoreCase = true) })
    }

    fun savePlace(state: NavigationPlacesState, slot: SavedPlaceSlot, destination: NavigationDestination): NavigationPlacesState {
        val place = validPlace(destination) ?: return state
        return state.copy(savedPlaces = state.savedPlaces + (slot to place))
    }

    fun validPlace(destination: NavigationDestination): NavigationDestination? {
        if (destination.id.isBlank() || destination.id.length > 512 || destination.name.isBlank()) return null
        return destination.copy(name = destination.name.trim().take(160), address = destination.address.trim().take(300))
    }

    fun normalized(state: NavigationPlacesState): NavigationPlacesState {
        val recent = state.recentQueries.asReversed().fold(NavigationPlacesState()) { current, query -> addRecentQuery(current, query) }.recentQueries
        val saved = state.savedPlaces.mapNotNull { (slot, destination) -> validPlace(destination)?.let { slot to it } }.toMap()
        return NavigationPlacesState(recent, saved)
    }
}
