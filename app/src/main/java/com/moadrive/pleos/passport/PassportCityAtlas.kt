package com.moadrive.pleos.passport

import kotlin.math.*

data class CityBoundaryPoint(val latitude: Double, val longitude: Double)
data class CityBoundaryPolygon(val outer: List<CityBoundaryPoint>, val holes: List<List<CityBoundaryPoint>> = emptyList()) {
    val south = outer.minOfOrNull { it.latitude } ?: Double.NaN
    val north = outer.maxOfOrNull { it.latitude } ?: Double.NaN
    val west = outer.minOfOrNull { it.longitude } ?: Double.NaN
    val east = outer.maxOfOrNull { it.longitude } ?: Double.NaN

    /** Strict interiors only: ambiguous boundary points and interior holes grant no visit. */
    fun contains(point: CityBoundaryPoint): Boolean {
        if (point.latitude !in south..north || point.longitude !in west..east) return false
        return relation(outer, point) == 1 && holes.none { relation(it, point) != 0 }
    }
    private fun relation(ring: List<CityBoundaryPoint>, point: CityBoundaryPoint): Int {
        if (ring.size < 3) return 0
        var inside = false
        var previous = ring.last()
        for (current in ring) {
            val cross = (point.longitude - previous.longitude) * (current.latitude - previous.latitude) -
                (point.latitude - previous.latitude) * (current.longitude - previous.longitude)
            if (abs(cross) < 1e-12 && point.latitude in minOf(previous.latitude, current.latitude)..maxOf(previous.latitude, current.latitude) &&
                point.longitude in minOf(previous.longitude, current.longitude)..maxOf(previous.longitude, current.longitude)) return 2
            if ((current.latitude > point.latitude) != (previous.latitude > point.latitude)) {
                val intersection = (previous.longitude - current.longitude) * (point.latitude - current.latitude) /
                    (previous.latitude - current.latitude) + current.longitude
                if (point.longitude < intersection) inside = !inside
            }
            previous = current
        }
        return if (inside) 1 else 0
    }
}
data class PassportCity(val id: String, val name: String, val province: String, val kind: String,
    val center: CityBoundaryPoint, val polygons: List<CityBoundaryPolygon>) {
    val title: String get() = if (name == province) name else "$province $name"
    val destinationQuery: String get() = if (name == province) "${name}청" else "$province ${name}청"
}
data class PassportCityVisit(val city: PassportCity, val visits: List<PassportVisit>) {
    val firstAt: Long get() = visits.minOf { it.visitedAtMs }
    val lastAt: Long get() = visits.maxOf { it.visitedAtMs }
    val tripCount: Int get() = visits.map { it.tripId }.distinct().size
}
class PassportCityAtlas(val cities: List<PassportCity>) {
    fun resolve(latitude: Double, longitude: Double): PassportCity? {
        if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        val point = CityBoundaryPoint(latitude, longitude)
        return cities.filter { city -> city.polygons.any { it.contains(point) } }.singleOrNull()
    }
    /** Existing visit rows remain unchanged; attribution uses the persisted ~1km cell center. */
    fun visited(visits: List<PassportVisit>): List<PassportCityVisit> {
        val cells = visits.distinctBy { it.cell.id }.associate { it.cell.id to resolve(it.cell.centerLatitude, it.cell.centerLongitude) }
        return visits.distinctBy { it.tripId to it.cell.id }.mapNotNull { visit -> cells[visit.cell.id]?.let { it to visit } }
            .groupBy({ it.first }, { it.second }).map { (city, found) -> PassportCityVisit(city, found) }
            .sortedWith(compareByDescending<PassportCityVisit> { it.lastAt }.thenBy { it.city.id })
    }
    fun recommend(visits: List<PassportVisit>, limit: Int = 3): List<PassportCity> {
        if (limit <= 0) return emptyList()
        val visited = visited(visits)
        val visitedIds = visited.map { it.city.id }.toSet()
        val origin = visited.flatMap { it.visits }.maxByOrNull { it.visitedAtMs }?.cell?.let {
            CityBoundaryPoint(it.centerLatitude, it.centerLongitude)
        } ?: cities.firstOrNull { it.id == "11:서울특별시" }?.center ?: cities.minByOrNull { it.id }?.center
        fun distance(city: PassportCity): Double {
            if (origin == null) return 0.0
            val latitudeDelta = Math.toRadians(city.center.latitude - origin.latitude)
            val longitudeDelta = Math.toRadians(city.center.longitude - origin.longitude)
            return sin(latitudeDelta / 2).pow(2) + cos(Math.toRadians(origin.latitude)) *
                cos(Math.toRadians(city.center.latitude)) * sin(longitudeDelta / 2).pow(2)
        }
        return cities.filter { it.kind == "city" && it.id !in visitedIds }
            .sortedWith(compareBy<PassportCity> { distance(it) }.thenBy { it.id }).take(limit)
    }
}
