package com.moagauge.pleos.safety

import com.moagauge.pleos.assist.NearbyIncident
import com.moagauge.pleos.assist.RoadIncident
import com.moagauge.pleos.cctv.CctvCamera
import com.moagauge.pleos.cctv.CctvState
import com.moagauge.pleos.cctv.CctvStatus
import com.moagauge.pleos.navigation.GeoPoint
import org.junit.Assert.*
import org.junit.Test

class TrafficSafetySummaryTest {
    private val now = 1_000_000L
    private val point = GeoPoint(37.5, 127.0)
    private val camera = CctvCamera("one", "고속도로 CCTV", point, null)

    @Test fun failedRefreshKeepsLastProvenNonzeroCatalogInsteadOfTransientEmptyList() {
        val ready = CctvState(status = CctvStatus.READY, cameras = listOf(camera), lastUpdatedMs = now)
        val refreshing = ready.copy(status = CctvStatus.LOADING, cameras = emptyList())
        val pending = trafficCatalogForSummary(refreshing, ready)
        assertEquals(1, currentTrafficSafetySummary(pending, now + 1).cctv.count)
        val failed = trafficCatalogForSummary(CctvState(status = CctvStatus.ERROR), ready)
        val summary = currentTrafficSafetySummary(failed, now + 2)
        assertEquals(1, summary.cctv.count)
        assertEquals(TrafficMetricStatus.CACHED, summary.cctv.status)
        assertEquals("이전 정보", summary.cctv.statusLabel)
    }

    @Test fun transientTimestampCannotInventZeroWithoutSuccessfulCatalog() {
        val loading = CctvState(status = CctvStatus.LOADING, lastUpdatedMs = now)
        assertNull(currentTrafficSafetySummary(trafficCatalogForSummary(loading, CctvState()), now).cctv.count)
        assertNull(currentTrafficSafetySummary(trafficCatalogForSummary(loading, loading), now).cctv.count)
    }

    @Test fun provenEmptyCatalogCanReplacePreviouslyNonzeroCounts() {
        val previous = CctvState(status = CctvStatus.READY, cameras = listOf(camera), lastUpdatedMs = now - 1)
        val empty = CctvState(status = CctvStatus.READY, lastUpdatedMs = now)
        assertEquals(0, currentTrafficSafetySummary(trafficCatalogForSummary(empty, previous), now).cctv.count)
    }

    @Test fun disconnectedSourcesNeverReportZeroIncidentsOrCameras() {
        val summary = currentTrafficSafetySummary(CctvState(), now)
        assertNull(summary.accidents.count)
        assertNull(summary.pedestrians.count)
        assertNull(summary.cctv.count)
        assertEquals(TrafficMetricStatus.UNAVAILABLE, summary.cctv.status)
        assertFalse(summary.demo)
    }

    @Test fun onlySuccessfulTimestampedCatalogCanConfirmZeroCameras() {
        val summary = currentTrafficSafetySummary(CctvState(status = CctvStatus.READY, lastUpdatedMs = now), now)
        assertEquals(0, summary.cctv.count)
        assertEquals(TrafficMetricStatus.FRESH, summary.cctv.status)
        assertNull(summary.accidents.count)
        assertNull(summary.pedestrians.count)
        assertNull(currentTrafficSafetySummary(CctvState(status = CctvStatus.READY), now).cctv.count)
    }

    @Test fun failuresWithoutCatalogNeverMasqueradeAsFreshZero() {
        listOf(CctvStatus.NEEDS_CONFIGURATION, CctvStatus.LOADING, CctvStatus.NO_LOCATION,
            CctvStatus.OFFLINE, CctvStatus.ERROR, CctvStatus.QUOTA_EXCEEDED).forEach { status ->
            val metric = currentTrafficSafetySummary(CctvState(status = status), now).cctv
            assertNull(status.name, metric.count)
            assertEquals(status.name, TrafficMetricStatus.UNAVAILABLE, metric.status)
        }
    }

    @Test fun agingCatalogRetainsCountButLosesFreshStatusAtFiveMinutes() {
        val state = CctvState(status = CctvStatus.READY, cameras = listOf(camera), lastUpdatedMs = now)
        assertEquals(TrafficMetricStatus.FRESH, currentTrafficSafetySummary(state, now + 299_999).cctv.status)
        val stale = currentTrafficSafetySummary(state, now + 300_000).cctv
        assertEquals(1, stale.count)
        assertEquals(TrafficMetricStatus.CACHED, stale.status)
        assertEquals(now, stale.updatedAtMs)
    }

    @Test fun offlineAndRefreshingCatalogsKeepTheirAgeAndCachedCount() {
        listOf(CctvStatus.OFFLINE, CctvStatus.LOADING, CctvStatus.ERROR).forEach { status ->
            val metric = currentTrafficSafetySummary(CctvState(status = status, cameras = listOf(camera),
                lastUpdatedMs = now - 60_000), now).cctv
            assertEquals(1, metric.count)
            assertEquals(TrafficMetricStatus.CACHED, metric.status)
            assertEquals(now - 60_000, metric.updatedAtMs)
        }
        assertEquals(TrafficMetricStatus.CACHED, currentTrafficSafetySummary(CctvState(
            status = CctvStatus.READY, cameras = listOf(camera), lastUpdatedMs = now, networkOnline = false), now).cctv.status)
    }

    @Test fun futureTimestampAndLocationlessCatalogCannotAssertCurrentCounts() {
        assertNull(currentTrafficSafetySummary(CctvState(status = CctvStatus.READY,
            cameras = listOf(camera), lastUpdatedMs = now + 1), now).cctv.count)
        assertNull(currentTrafficSafetySummary(CctvState(status = CctvStatus.NO_LOCATION,
            cameras = listOf(camera), lastUpdatedMs = now), now).cctv.count)
    }

    @Test fun demoCameraStateCannotLeakIntoActualCounts() {
        val demoCamera = camera.copy(isDemo = true)
        val state = CctvState(status = CctvStatus.READY, cameras = listOf(demoCamera), lastUpdatedMs = now)
        assertNull(currentTrafficSafetySummary(state, now).cctv.count)
        assertNull(currentTrafficSafetySummary(state.copy(demo = true), now).cctv.count)
        assertEquals(1, currentTrafficSafetySummary(state.copy(cameras = listOf(camera, camera, demoCamera)), now).cctv.count)
    }

    @Test fun sampleZeroAndNonzeroScenariosAreExplicitlyDemoOnly() {
        val empty = demoTrafficSafetySummary(TrafficDemoScenario.QUIET, null, now)
        assertTrue(empty.demo)
        assertEquals(listOf(0, 0, 0), listOf(empty.accidents.count, empty.pedestrians.count, empty.cctv.count))
        val active = demoTrafficSafetySummary(TrafficDemoScenario.ALERTS, null, now)
        assertTrue(active.demo)
        assertEquals(listOf(1, 2, 3), listOf(active.accidents.count, active.pedestrians.count, active.cctv.count))
        assertTrue(listOf(active.accidents, active.pedestrians, active.cctv).all { it.status == TrafficMetricStatus.DEMO })
    }

    @Test fun activeIncidentOnlyEnrichesDemoAndExpiredOrRealEventsAreIgnored() {
        val event = NearbyIncident(RoadIncident("demo", "예시 사고", point, now - 10_000, now + 10_000), 100.0)
        assertEquals(1, demoTrafficSafetySummary(TrafficDemoScenario.QUIET, event, now).accidents.count)
        assertEquals(0, demoTrafficSafetySummary(TrafficDemoScenario.QUIET, event, now + 10_000).accidents.count)
        assertEquals(0, demoTrafficSafetySummary(TrafficDemoScenario.QUIET, event.copy(
            incident = event.incident.copy(demo = false)), now).accidents.count)
        assertNull(currentTrafficSafetySummary(CctvState(), now).accidents.count)
    }
}
