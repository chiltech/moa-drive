package com.moagauge.pleos.navigation

import org.junit.Assert.*
import org.junit.Test

class NavigationPlacesRulesTest {
    private fun place(name: String = "회사") = NavigationDestination("kakao-123", name, "서울 중구", GeoPoint(37.56, 126.98))

    @Test fun recentQueriesNormalizeWhitespaceDeduplicateAndMoveToFront() {
        var state = NavigationPlacesState()
        state = NavigationPlacesRules.addRecentQuery(state, "  Seoul   Station ")
        state = NavigationPlacesRules.addRecentQuery(state, "부산역")
        state = NavigationPlacesRules.addRecentQuery(state, "seoul station")
        assertEquals(listOf("seoul station", "부산역"), state.recentQueries)
    }

    @Test fun emptySearchDoesNotEraseHistoryAndOldestFallsOffAtTwenty() {
        var state = NavigationPlacesState()
        repeat(25) { state = NavigationPlacesRules.addRecentQuery(state, "검색 $it") }
        assertEquals(20, state.recentQueries.size)
        assertEquals("검색 24", state.recentQueries.first())
        assertEquals("검색 5", state.recentQueries.last())
        assertEquals(state, NavigationPlacesRules.addRecentQuery(state, " \n\t "))
    }

    @Test fun removeRecentQueryMatchesCaseAndWhitespaceWithoutDeletingSavedPlace() {
        val saved = place()
        val state = NavigationPlacesState(listOf("Seoul Station", "부산역"), mapOf(SavedPlaceSlot.WORK to saved))
        val result = NavigationPlacesRules.removeRecentQuery(state, "  seoul  station ")
        assertEquals(listOf("부산역"), result.recentQueries)
        assertEquals(saved, result.savedPlaces[SavedPlaceSlot.WORK])
    }

    @Test fun savingAndReplacingHomeKeepsWorkAndHistory() {
        val work = place()
        val home = place("집")
        val state = NavigationPlacesState(listOf("검색어"), mapOf(SavedPlaceSlot.WORK to work))
        val saved = NavigationPlacesRules.savePlace(state, SavedPlaceSlot.HOME, home)
        assertEquals(home, saved.savedPlaces[SavedPlaceSlot.HOME])
        assertEquals(work, saved.savedPlaces[SavedPlaceSlot.WORK])
        assertEquals(listOf("검색어"), saved.recentQueries)
        val replacement = home.copy(name = "새 집", point = GeoPoint(35.1, 129.0))
        assertEquals(replacement, NavigationPlacesRules.savePlace(saved, SavedPlaceSlot.HOME, replacement).savedPlaces[SavedPlaceSlot.HOME])
    }

    @Test fun invalidSavedPlaceCannotReplaceValidDestination() {
        val valid = place()
        val state = NavigationPlacesState(savedPlaces = mapOf(SavedPlaceSlot.HOME to valid))
        assertEquals(state, NavigationPlacesRules.savePlace(state, SavedPlaceSlot.HOME, valid.copy(name = " ")))
        assertEquals(state, NavigationPlacesRules.savePlace(state, SavedPlaceSlot.HOME, valid.copy(id = "")))
    }
}
