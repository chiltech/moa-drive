package com.moadrive.pleos.assist

import com.moadrive.pleos.navigation.GeoPoint
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class IncidentGeofenceTest {
    private val origin = GeoPoint(0.0, 0.0)
    private val now = 1_000_000L
    private fun north(meters: Double) = GeoPoint(meters / 6_371_000.0 * 180.0 / PI, 0.0)
    private fun incident(id: String = "accident", distance: Double = 5_000.0) =
        RoadIncident(id, "예시 대형 사고", north(distance), now - 1000, now + 60_000)

    @Test fun enteringTenKilometersAlertsOnceAndDoesNotRepeatAfterReentry() {
        val geofence = IncidentGeofence()
        val accident = incident(distance = 10_000.0)
        assertNull(geofence.evaluate(north(-2_000.0), listOf(accident), now, moving = true))
        assertNotNull(geofence.evaluate(origin, listOf(accident), now, moving = true))
        assertNull(geofence.evaluate(origin, listOf(accident), now + 1, moving = true))
        assertNull(geofence.evaluate(north(-3_000.0), listOf(accident), now + 2, moving = true))
        assertNull(geofence.evaluate(origin, listOf(accident), now + 3, moving = true))
        geofence.reset()
        assertNotNull(geofence.evaluate(origin, listOf(accident), now + 4, moving = true))
    }

    @Test fun justOutsideBoundaryDoesNotAlert() {
        val result = IncidentGeofence().evaluate(origin, listOf(incident(distance = 10_001.0)), now, moving = true)
        assertNull(result)
    }

    @Test fun nearestNewAccidentIsSelectedAndInvalidEventsAreExcluded() {
        val expired = incident("expired", 10.0).copy(expiresAtMs = now)
        val future = incident("future", 20.0).copy(occurredAtMs = now + 1)
        val minor = incident("minor", 30.0).copy(severe = false)
        val malformed = incident("invalid", 40.0).copy(occurredAtMs = now - 100, expiresAtMs = now - 200)
        val geofence = IncidentGeofence()
        val events = listOf(incident("far", 9_000.0), expired, future, minor, malformed, incident("near", 2_000.0))
        assertEquals("near", geofence.evaluate(origin, events, now, true)?.incident?.id)
        assertEquals("far", geofence.evaluate(origin, events, now, true)?.incident?.id)
        assertNull(geofence.evaluate(origin, events, now, true))
    }

    @Test fun missingGpsOrStoppedVehicleDoesNotConsumeEvent() {
        val geofence = IncidentGeofence()
        val events = listOf(incident())
        assertNull(geofence.evaluate(null, events, now, true))
        assertNull(geofence.evaluate(origin, events, now, false))
        assertNotNull(geofence.evaluate(origin, events, now, true))
    }

    @Test fun distancesWorkAcrossDateLineAndCoincidentPoints() {
        assertEquals(0.0, distanceMeters(origin, origin), 0.000001)
        assertEquals(222.39, distanceMeters(GeoPoint(0.0, 179.999), GeoPoint(0.0, -179.999)), 0.1)
        assertEquals(10_000.0, distanceMeters(origin, north(10_000.0)), 0.00001)
    }
}
