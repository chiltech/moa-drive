package com.moadrive.pleos.assist

import com.moadrive.pleos.navigation.GeoPoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Straight-line radius, not a route/direction match. Owners supply fresh GPS and clear a session explicitly. */
class IncidentGeofence(private val radiusMeters: Double = 10_000.0) {
    init { require(radiusMeters.isFinite() && radiusMeters > 0.0) }
    private val announced = mutableSetOf<String>()

    fun evaluate(point: GeoPoint?, incidents: List<RoadIncident>, nowMs: Long, moving: Boolean): NearbyIncident? {
        if (point == null || !moving) return null
        val nearest = incidents.asSequence()
            .filter { incident ->
                incident.id.isNotBlank() && incident.severe && incident.id !in announced &&
                    incident.occurredAtMs <= nowMs && nowMs < incident.expiresAtMs &&
                    incident.occurredAtMs < incident.expiresAtMs
            }
            .map { NearbyIncident(it, distanceMeters(point, it.point)) }
            .filter { it.distanceMeters <= radiusMeters + 0.000001 }
            .sortedWith(compareBy<NearbyIncident> { it.distanceMeters }.thenBy { it.incident.id })
            .firstOrNull() ?: return null
        announced.add(nearest.incident.id)
        return nearest
    }

    fun reset() { announced.clear() }
}

/** Haversine distance on a mean-radius Earth, sufficient for this coarse 10 km warning boundary. */
fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
    val latitudeDelta = Math.toRadians(b.latitude - a.latitude)
    val longitudeDelta = Math.toRadians(b.longitude - a.longitude)
    val h = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
        cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) *
        sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
    return 6_371_000.0 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
}
