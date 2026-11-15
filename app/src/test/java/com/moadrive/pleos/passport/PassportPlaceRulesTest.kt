package com.moadrive.pleos.passport

import com.moadrive.pleos.navigation.GeoPoint
import com.moadrive.pleos.navigation.NavigationDestination
import org.junit.Assert.*
import org.junit.Test

class PassportPlaceRulesTest {
    private val city = PassportCity("test", "테스트시", "테스트도", "city", CityBoundaryPoint(37.5, 127.5),
        listOf(CityBoundaryPolygon(listOf(CityBoundaryPoint(37.0, 127.0), CityBoundaryPoint(37.0, 128.0),
            CityBoundaryPoint(38.0, 128.0), CityBoundaryPoint(38.0, 127.0)))))
    private fun place(id: String, name: String, latitude: Double = 37.5, longitude: Double = 127.5) =
        NavigationDestination(id, name, "테스트시 실제 주소", GeoPoint(latitude, longitude))

    @Test fun acceptsOnlyActualPlacesInsideTheRecommendedCity() {
        val entrance = place("entrance", "문화유산 공원", 37.61, 127.41)
        val selected = PassportPlaceRules.select(city, mapOf(PassportPlaceKind.ATTRACTION to listOf(
            place("hall", "테스트시청"), place("station", "테스트시청역"), place("outside", "다른 도시 관광지", 36.8),
            place("center", "동네 행정복지센터"), place("empty", ""), entrance)))
        assertEquals(listOf(entrance), selected.map { it.destination })
        // Exact provider entrance coordinates survive, never a city-center placeholder.
        assertEquals(37.61, selected.single().destination.point.latitude, 0.0)
        assertEquals(127.41, selected.single().destination.point.longitude, 0.0)
    }

    @Test fun mixesTourismAndFoodAndCapsAtThreeWithoutDuplicatePlaces() {
        val attraction = place("a", "문화 공원")
        val food = place("b", "우리 식당")
        val next = place("c", "역사박물관")
        val selected = PassportPlaceRules.select(city, linkedMapOf(
            PassportPlaceKind.ATTRACTION to listOf(attraction, next),
            PassportPlaceKind.RESTAURANT to listOf(food, place("a", "다른 표시명"), place("b2", "우리식당")),
            PassportPlaceKind.PARK to listOf(attraction)))
        assertEquals(listOf("a", "b", "c"), selected.map { it.destination.id })
        assertEquals(PassportPlaceKind.RESTAURANT, selected[1].kind)
    }

    @Test fun noResultsNeverFabricatesCityHallOrCityCenterDestination() {
        assertTrue(PassportPlaceRules.select(city, emptyMap()).isEmpty())
        assertTrue(PassportPlaceRules.select(city, mapOf(PassportPlaceKind.ATTRACTION to listOf(place("hall", "테스트시청")))).isEmpty())
    }

    @Test fun boundaryPointsAndPlacesInPolygonHolesAreExcluded() {
        val cityWithHole = city.copy(polygons = listOf(city.polygons.single().copy(holes = listOf(listOf(
            CityBoundaryPoint(37.4, 127.4), CityBoundaryPoint(37.4, 127.6),
            CityBoundaryPoint(37.6, 127.6), CityBoundaryPoint(37.6, 127.4))))))
        val result = PassportPlaceRules.select(cityWithHole, mapOf(PassportPlaceKind.PARK to listOf(
            place("hole", "외곽 도시 공원"), place("edge", "경계 장소", 37.0), place("inside", "도시 공원", 37.7))))
        assertEquals(listOf("inside"), result.map { it.destination.id })
    }
}
