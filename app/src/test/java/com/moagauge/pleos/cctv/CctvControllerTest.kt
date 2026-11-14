package com.moagauge.pleos.cctv

import com.moagauge.pleos.navigation.GeoPoint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CctvControllerTest {
    private val point = GeoPoint(37.5665, 126.978)
    private fun camera(id: String = "one", at: GeoPoint = point) = CctvCamera(id, "교차로", at, "https://cctv.example.org/live")

    @Test fun withoutKeyShowsConfigurationAndDemoNeverInventsVideo() = runTest {
        val controller = CctvController(null, backgroundScope, demoAllowed = true)
        controller.setVisible(true)
        assertEquals(CctvStatus.NEEDS_CONFIGURATION, controller.state.value.status)
        controller.updateLocation(null, true)
        controller.showDemoCameras()
        assertEquals(CctvStatus.NO_LOCATION, controller.state.value.status)
        controller.updateLocation(point, true)
        controller.showDemoCameras()
        assertEquals(3, controller.state.value.cameras.size)
        assertTrue(controller.state.value.demo)
        assertTrue(controller.state.value.cameras.all { it.isDemo && it.streamUrl == null })
        controller.setVisible(false)
        assertFalse(controller.state.value.demo)
        assertTrue(controller.state.value.cameras.isEmpty())
    }

    @Test fun releaseControllerCannotExposeDemoCameras() = runTest {
        val controller = CctvController(null, backgroundScope, demoAllowed = false)
        controller.setVisible(true); controller.updateLocation(point, true); controller.showDemoCameras()
        assertTrue(controller.state.value.cameras.isEmpty())
        assertFalse(controller.state.value.demo)
    }

    @Test fun selectionRequiresForegroundParkedPanelAndMovingClosesIt() = runTest {
        val controller = CctvController(CctvSource { listOf(camera()) }, backgroundScope)
        controller.setVisible(true); controller.updateLocation(point, true); runCurrent()
        controller.openPanel("one")
        assertTrue(controller.state.value.panelOpen)
        assertNull(controller.state.value.selected)
        controller.selectCamera("one")
        assertEquals("one", controller.state.value.selected?.id)
        controller.updateLocation(point, false)
        assertNull(controller.state.value.selected)
        assertFalse(controller.state.value.panelOpen)
        controller.openPanel(); controller.selectCamera("one")
        assertNull(controller.state.value.selected)
    }

    @Test fun lateResponseCannotReturnAfterBackgroundOrMissingGps() = runTest {
        val response = CompletableDeferred<List<CctvCamera>>()
        val source = CctvSource { withContext(NonCancellable) { response.await() } }
        val controller = CctvController(source, backgroundScope)
        controller.setVisible(true); controller.updateLocation(point, true); runCurrent()
        controller.updateLocation(null, true)
        controller.setVisible(false)
        response.complete(listOf(camera())); runCurrent()
        assertTrue(controller.state.value.cameras.isEmpty())
        assertNull(controller.state.value.selected)
    }

    @Test fun mapMarkersDoNotPollButOneKilometerMovementRefreshesWithoutRequestStorm() = runTest {
        var requests = 0
        val source = CctvSource { requests++; listOf(camera(at = it)) }
        val controller = CctvController(source, backgroundScope, nowMs = { testScheduler.currentTime })
        controller.setVisible(true); controller.updateLocation(point, true); runCurrent()
        assertEquals(1, requests)
        repeat(10) { controller.updateLocation(point, true) }; runCurrent()
        assertEquals(1, requests)
        advanceTimeBy(300_001); runCurrent()
        assertEquals(1, requests)
        controller.updateLocation(GeoPoint(37.58, 126.978), true); runCurrent()
        assertEquals(2, requests)
        controller.setVisible(false)
        advanceTimeBy(600_000); runCurrent()
        assertEquals(2, requests)
    }

    @Test fun newerLocationWinsOverUncancellableOlderResponse() = runTest {
        val oldResponse = CompletableDeferred<List<CctvCamera>>()
        val later = GeoPoint(37.60, 126.978)
        val source = CctvSource { origin ->
            if (origin == point) withContext(NonCancellable) { oldResponse.await() } else listOf(camera("new", later))
        }
        val controller = CctvController(source, backgroundScope)
        controller.setVisible(true); controller.updateLocation(point, true); runCurrent()
        controller.updateLocation(later, true); runCurrent()
        oldResponse.complete(listOf(camera("old"))); runCurrent()
        assertEquals(listOf("new"), controller.state.value.cameras.map { it.id })
    }

    @Test fun subKilometerMovementStillRemovesCamerasOutsideCurrentRadius() = runTest {
        val northDelta = 9_900.0 / 6_371_000.0 * 180.0 / Math.PI
        val southDelta = 500.0 / 6_371_000.0 * 180.0 / Math.PI
        val edge = GeoPoint(point.latitude + northDelta, point.longitude)
        var requests = 0
        val controller = CctvController(CctvSource { requests++; listOf(camera(at = edge)) }, backgroundScope)
        controller.setVisible(true); controller.updateLocation(point, true); runCurrent()
        assertEquals(1, controller.state.value.cameras.size)
        controller.updateLocation(GeoPoint(point.latitude - southDelta, point.longitude), true); runCurrent()
        assertEquals(1, requests)
        assertTrue(controller.state.value.cameras.isEmpty())
    }

    @Test fun dismissedVideoIgnoresLatePlayerFailure() = runTest {
        val controller = CctvController(CctvSource { listOf(camera()) }, backgroundScope)
        controller.setVisible(true); controller.updateLocation(point, true); runCurrent()
        controller.openPanel(); controller.selectCamera("one"); runCurrent()
        controller.dismissVideo()

        controller.onStreamError()

        assertEquals(CctvStatus.READY, controller.state.value.status)
        assertNull(controller.state.value.selected)
        assertNull(controller.state.value.message)
    }

    @Test fun offlineLaunchWaitsForInternetThenFetchesWithoutUserRefresh() = runTest {
        var requests = 0
        val controller = CctvController(CctvSource { requests++; listOf(camera()) }, backgroundScope,
            initiallyOnline = false)
        controller.setVisible(true); controller.updateLocation(point, true); runCurrent()
        controller.refresh(); runCurrent()
        assertEquals(0, requests)
        assertEquals(CctvStatus.OFFLINE, controller.state.value.status)

        controller.setNetworkOnline(true); runCurrent()

        assertEquals(1, requests)
        assertEquals(CctvStatus.READY, controller.state.value.status)
        assertEquals(listOf("one"), controller.state.value.cameras.map { it.id })
    }

    @Test fun restoresOpenCameraWithFreshSignedUrlInsideCatalogTtl() = runTest {
        var requests = 0
        val controller = CctvController(CctvSource {
            requests++
            listOf(camera().copy(streamUrl = "https://cctv.example.org/live?signature=$requests"))
        }, backgroundScope, nowMs = { testScheduler.currentTime })
        controller.setVisible(true); controller.updateLocation(point, true); runCurrent()
        controller.openPanel(); controller.selectCamera("one")
        val previousPlayback = controller.state.value.playbackGeneration

        controller.setNetworkOnline(false)
        controller.onStreamError("one", previousPlayback)
        runCurrent()
        assertEquals("one", controller.state.value.selected?.id)
        assertTrue(controller.state.value.reconnecting)
        assertEquals(1, requests)

        controller.setNetworkOnline(true)
        repeat(10) { controller.setNetworkOnline(true); controller.updateLocation(point, true) }
        runCurrent()

        assertEquals(2, requests)
        assertEquals("https://cctv.example.org/live?signature=2", controller.state.value.selected?.streamUrl)
        assertTrue(controller.state.value.playbackGeneration > previousPlayback)
        assertFalse(controller.state.value.reconnecting)
        controller.onStreamError("one", previousPlayback)
        assertEquals(CctvStatus.READY, controller.state.value.status)
        assertEquals("one", controller.state.value.selected?.id)
    }

    @Test fun identicalRefreshedUrlStillRestartsPlayback() = runTest {
        val controller = CctvController(CctvSource { listOf(camera()) }, backgroundScope)
        controller.setVisible(true); controller.updateLocation(point, true); runCurrent()
        controller.openPanel(); controller.selectCamera("one")
        val previousPlayback = controller.state.value.playbackGeneration
        controller.setNetworkOnline(false); controller.setNetworkOnline(true); runCurrent()
        assertEquals(camera(), controller.state.value.selected)
        assertTrue(controller.state.value.playbackGeneration > previousPlayback)
    }

    @Test fun recoveryNeverReopensDismissedClosedHiddenRestrictedOrLocationlessVideo() = runTest {
        val revoke = listOf<(CctvController) -> Unit>(
            { it.dismissVideo() }, { it.closePanel() }, { it.setVisible(false) },
            { it.updateLocation(point, false) }, { it.updateLocation(null, true) },
        )
        revoke.forEachIndexed { index, action ->
            val controller = CctvController(CctvSource { listOf(camera()) }, backgroundScope)
            controller.setVisible(true); controller.updateLocation(point, true); runCurrent()
            controller.openPanel(); controller.selectCamera("one")
            controller.setNetworkOnline(false)
            action(controller)
            controller.setNetworkOnline(true); runCurrent()
            assertNull("Reopened video after revocation $index", controller.state.value.selected)
            assertFalse(controller.state.value.reconnecting)
            controller.setVisible(false)
        }
    }

    @Test fun demoCameraDoesNotFetchOrEnterLiveRecovery() = runTest {
        var requests = 0
        val controller = CctvController(CctvSource { requests++; listOf(camera()) }, backgroundScope,
            demoAllowed = true)
        controller.setVisible(true); controller.updateLocation(point, true); runCurrent()
        controller.showDemoCameras(); controller.openPanel(); controller.selectCamera("demo-north")
        controller.setNetworkOnline(false); controller.setNetworkOnline(true); runCurrent()
        assertEquals(1, requests)
        assertTrue(controller.state.value.selected?.isDemo == true)
        assertFalse(controller.state.value.reconnecting)
    }

    @Test fun transientRecoveryFetchFailureRetriesAndRetainsOpenSelection() = runTest {
        var requests = 0
        val controller = CctvController(CctvSource {
            requests++
            if (requests == 2) throw CctvFailure()
            listOf(camera().copy(streamUrl = "https://cctv.example.org/live?signature=$requests"))
        }, backgroundScope, nowMs = { testScheduler.currentTime })
        controller.setVisible(true); controller.updateLocation(point, true); runCurrent()
        controller.openPanel(); controller.selectCamera("one")
        controller.setNetworkOnline(false); controller.setNetworkOnline(true); runCurrent()
        assertEquals("one", controller.state.value.selected?.id)
        assertTrue(controller.state.value.reconnecting)

        advanceTimeBy(1_001); runCurrent()

        assertEquals(3, requests)
        assertEquals("https://cctv.example.org/live?signature=3", controller.state.value.selected?.streamUrl)
        assertFalse(controller.state.value.reconnecting)
    }

    @Test fun recoveryRetriesAreBoundedAndNewConnectionRearmsThem() = runTest {
        var requests = 0
        val controller = CctvController(CctvSource {
            requests++
            if (requests > 1) throw CctvFailure()
            listOf(camera())
        }, backgroundScope, nowMs = { testScheduler.currentTime })
        controller.setVisible(true); controller.updateLocation(point, true); runCurrent()
        controller.openPanel(); controller.selectCamera("one")
        controller.setNetworkOnline(false); controller.setNetworkOnline(true); runCurrent()
        advanceTimeBy(60_000); runCurrent()
        assertEquals(5, requests)
        assertEquals(CctvStatus.ERROR, controller.state.value.status)
        assertNull(controller.state.value.selected)
        repeat(10) { controller.setNetworkOnline(true) }; runCurrent()
        assertEquals(5, requests)

        controller.setNetworkOnline(false); controller.setNetworkOnline(true); runCurrent()
        assertEquals(6, requests)
        controller.setVisible(false)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(6, requests)
    }

    @Test fun oldUncancellableRecoveryCannotReplaceNewConnectionAndRequestsStaySerial() = runTest {
        val oldResponse = CompletableDeferred<List<CctvCamera>>()
        var requests = 0
        var active = 0
        var maxActive = 0
        val controller = CctvController(CctvSource {
            requests++; active++; maxActive = maxOf(maxActive, active)
            try {
                if (requests == 2) withContext(NonCancellable) { oldResponse.await() }
                else listOf(camera().copy(streamUrl = "https://cctv.example.org/live?signature=$requests"))
            } finally { active-- }
        }, backgroundScope)
        controller.setVisible(true); controller.updateLocation(point, true); runCurrent()
        controller.openPanel(); controller.selectCamera("one")
        controller.setNetworkOnline(false); controller.setNetworkOnline(true); runCurrent()
        controller.setNetworkOnline(false); controller.setNetworkOnline(true); runCurrent()
        oldResponse.complete(listOf(camera().copy(streamUrl = "https://cctv.example.org/obsolete")))
        runCurrent()
        assertEquals(1, maxActive)
        assertEquals(3, requests)
        assertEquals("https://cctv.example.org/live?signature=3", controller.state.value.selected?.streamUrl)
    }

    @Test fun recoveryDropsCameraAbsentFromFreshNearbyCatalog() = runTest {
        var requests = 0
        val controller = CctvController(CctvSource {
            requests++
            if (requests == 1) listOf(camera()) else listOf(camera("other"))
        }, backgroundScope)
        controller.setVisible(true); controller.updateLocation(point, true); runCurrent()
        controller.openPanel(); controller.selectCamera("one")
        controller.setNetworkOnline(false); controller.setNetworkOnline(true); runCurrent()
        assertNull(controller.state.value.selected)
        assertEquals(listOf("other"), controller.state.value.cameras.map { it.id })
    }

    @Test fun playerFailureBeforeNetworkLossKeepsOpenCameraForRecovery() = runTest {
        var requests = 0
        val controller = CctvController(CctvSource {
            requests++
            listOf(camera().copy(streamUrl = "https://cctv.example.org/live?signature=$requests"))
        }, backgroundScope)
        controller.setVisible(true); controller.updateLocation(point, true); runCurrent()
        controller.openPanel(); controller.selectCamera("one"); runCurrent()
        val previousPlayback = controller.state.value.playbackGeneration

        // A player error can arrive before Android stops reporting validated internet.
        controller.onStreamError("one", previousPlayback)
        assertEquals(CctvStatus.ERROR, controller.state.value.status)
        assertEquals("one", controller.state.value.selected?.id)
        controller.setNetworkOnline(false); controller.setNetworkOnline(true); runCurrent()

        assertEquals("https://cctv.example.org/live?signature=3", controller.state.value.selected?.streamUrl)
        assertTrue(controller.state.value.playbackGeneration > previousPlayback)
    }

    @Test fun cameraSelectedWhileCatalogFetchIsPendingUsesItsRefreshedUrl() = runTest {
        var requests = 0
        val nextCatalog = CompletableDeferred<List<CctvCamera>>()
        val controller = CctvController(CctvSource {
            requests++
            if (requests == 1) listOf(camera()) else nextCatalog.await()
        }, backgroundScope, nowMs = { testScheduler.currentTime })
        controller.setVisible(true); controller.updateLocation(point, true); runCurrent()
        controller.openPanel()
        advanceTimeBy(300_001); runCurrent()
        controller.selectCamera("one")
        assertTrue(controller.state.value.reconnecting)
        nextCatalog.complete(listOf(camera().copy(streamUrl = "https://cctv.example.org/fresh")))
        runCurrent()
        assertEquals("https://cctv.example.org/fresh", controller.state.value.selected?.streamUrl)
        assertFalse(controller.state.value.reconnecting)
    }

    @Test fun dismissingDuringPendingRecoveryCannotReopenVideo() = runTest {
        var requests = 0
        val nextCatalog = CompletableDeferred<List<CctvCamera>>()
        val controller = CctvController(CctvSource {
            requests++
            if (requests == 1) listOf(camera()) else nextCatalog.await()
        }, backgroundScope)
        controller.setVisible(true); controller.updateLocation(point, true); runCurrent()
        controller.openPanel(); controller.selectCamera("one")
        controller.setNetworkOnline(false); controller.setNetworkOnline(true); runCurrent()
        controller.dismissVideo()
        nextCatalog.complete(listOf(camera().copy(streamUrl = "https://cctv.example.org/fresh")))
        runCurrent()
        assertNull(controller.state.value.selected)
        assertFalse(controller.state.value.reconnecting)
        assertEquals("https://cctv.example.org/fresh", controller.state.value.cameras.single().streamUrl)
    }

    @Test fun quotaStopsAutomaticRequestsAcrossGpsTimerNetworkAndScreenReturnUntilManualRefresh() = runTest {
        var requests = 0
        val controller = CctvController(CctvSource { location ->
            requests++
            if (requests == 1) throw CctvFailure(CctvFailureKind.MONTHLY_QUOTA_EXCEEDED)
            listOf(camera(at = location))
        }, backgroundScope, nowMs = { testScheduler.currentTime })
        controller.setVisible(true); controller.updateLocation(point, true); runCurrent()
        assertEquals(CctvStatus.QUOTA_EXCEEDED, controller.state.value.status)
        controller.openPanel()
        val moved = GeoPoint(37.60, 126.978)
        repeat(10) { controller.updateLocation(moved, true) }
        advanceTimeBy(600_001); runCurrent()
        controller.setNetworkOnline(false); controller.setNetworkOnline(true); runCurrent()
        controller.setVisible(false); controller.setVisible(true)
        controller.updateLocation(moved, true); runCurrent()
        assertEquals(1, requests)
        assertEquals(CctvStatus.QUOTA_EXCEEDED, controller.state.value.status)

        controller.refresh(); runCurrent()

        assertEquals(2, requests)
        assertEquals(CctvStatus.READY, controller.state.value.status)
        assertEquals(listOf("one"), controller.state.value.cameras.map { it.id })
    }

    @Test fun quotaDuringNetworkRecoveryDoesNotScheduleTransientRetries() = runTest {
        var requests = 0
        val controller = CctvController(CctvSource {
            requests++
            if (requests > 1) throw CctvFailure(CctvFailureKind.MONTHLY_QUOTA_EXCEEDED)
            listOf(camera())
        }, backgroundScope, nowMs = { testScheduler.currentTime })
        controller.setVisible(true); controller.updateLocation(point, true); runCurrent()
        controller.openPanel(); controller.selectCamera("one")
        controller.setNetworkOnline(false); controller.setNetworkOnline(true); runCurrent()
        advanceTimeBy(60_000); runCurrent()
        assertEquals(2, requests)
        assertEquals(CctvStatus.QUOTA_EXCEEDED, controller.state.value.status)
        assertFalse(controller.state.value.reconnecting)
        assertNull(controller.state.value.selected)
    }

    @Test fun manualQuotaRetryWithoutGpsFetchesImmediatelyWhenTheSameGpsReturns() = runTest {
        var requests = 0
        val controller = CctvController(CctvSource {
            requests++
            if (requests == 1) throw CctvFailure(CctvFailureKind.MONTHLY_QUOTA_EXCEEDED)
            listOf(camera())
        }, backgroundScope, nowMs = { testScheduler.currentTime })
        controller.setVisible(true); controller.updateLocation(point, true); runCurrent()
        assertEquals(CctvStatus.QUOTA_EXCEEDED, controller.state.value.status)
        controller.updateLocation(null, true)

        controller.refresh(); runCurrent()
        assertEquals(1, requests)
        assertEquals(CctvStatus.NO_LOCATION, controller.state.value.status)
        controller.updateLocation(point, true); runCurrent()

        assertEquals(2, requests)
        assertEquals(CctvStatus.READY, controller.state.value.status)
        assertEquals(listOf("one"), controller.state.value.cameras.map { it.id })
    }

    @Test fun successfulMarkerCatalogSurvivesScreenReturnWithoutAnotherRequest() = runTest {
        var requests = 0
        val controller = CctvController(CctvSource { requests++; listOf(camera()) }, backgroundScope,
            nowMs = { testScheduler.currentTime })
        controller.setVisible(true); controller.updateLocation(point, true); runCurrent()
        controller.setVisible(false)
        advanceTimeBy(600_001)
        controller.setVisible(true); controller.updateLocation(point, true); runCurrent()
        assertEquals(1, requests)
        assertEquals(CctvStatus.READY, controller.state.value.status)
        assertEquals(listOf("one"), controller.state.value.cameras.map { it.id })
        assertNull(controller.state.value.selected)
        assertFalse(controller.state.value.panelOpen)
    }

    @Test fun cameraIntentWaitsForFreshSignedUrlAndPlayingVideoDoesNotPoll() = runTest {
        var requests = 0
        val fresh = CompletableDeferred<List<CctvCamera>>()
        val controller = CctvController(CctvSource {
            requests++
            if (requests == 1) listOf(camera().copy(streamUrl = "https://cctv.example.org/old"))
            else fresh.await()
        }, backgroundScope, nowMs = { testScheduler.currentTime })
        controller.setVisible(true); controller.updateLocation(point, true); runCurrent()
        controller.openPanel(); controller.selectCamera("one"); runCurrent()
        assertEquals(2, requests)
        assertTrue(controller.state.value.reconnecting)
        repeat(10) { controller.updateLocation(point, true) }; runCurrent()
        assertEquals(2, requests)
        fresh.complete(listOf(camera().copy(streamUrl = "https://cctv.example.org/fresh"))); runCurrent()
        assertEquals("https://cctv.example.org/fresh", controller.state.value.selected?.streamUrl)
        assertFalse(controller.state.value.reconnecting)
        advanceTimeBy(600_001); runCurrent()
        assertEquals(2, requests)
        assertEquals("one", controller.state.value.selected?.id)
    }

    @Test fun dismissingCameraWhileItsFreshUrlIsPendingDoesNotReopenVideo() = runTest {
        var requests = 0
        val fresh = CompletableDeferred<List<CctvCamera>>()
        val controller = CctvController(CctvSource {
            requests++
            if (requests == 1) listOf(camera()) else fresh.await()
        }, backgroundScope)
        controller.setVisible(true); controller.updateLocation(point, true); runCurrent()
        controller.openPanel(); controller.selectCamera("one"); runCurrent()
        assertEquals(2, requests)
        controller.dismissVideo()
        fresh.complete(listOf(camera().copy(streamUrl = "https://cctv.example.org/fresh"))); runCurrent()
        assertNull(controller.state.value.selected)
        assertFalse(controller.state.value.reconnecting)
        assertEquals("https://cctv.example.org/fresh", controller.state.value.cameras.single().streamUrl)
    }

    @Test fun leavingDuringFreshUrlFetchCannotReopenVideoOrReplaceRetainedCatalog() = runTest {
        var requests = 0
        val fresh = CompletableDeferred<List<CctvCamera>>()
        val controller = CctvController(CctvSource {
            requests++
            if (requests == 1) listOf(camera()) else withContext(NonCancellable) { fresh.await() }
        }, backgroundScope)
        controller.setVisible(true); controller.updateLocation(point, true); runCurrent()
        controller.openPanel(); controller.selectCamera("one"); runCurrent()
        controller.setVisible(false)
        fresh.complete(listOf(camera("late"))); runCurrent()
        controller.setVisible(true); controller.updateLocation(point, true); runCurrent()
        assertEquals(2, requests)
        assertEquals(listOf("one"), controller.state.value.cameras.map { it.id })
        assertNull(controller.state.value.selected)
        assertFalse(controller.state.value.panelOpen)
    }

    @Test fun retainedCatalogIsFilteredAgainstCurrentPositionOnScreenReturn() = runTest {
        val northDelta = 9_900.0 / 6_371_000.0 * 180.0 / Math.PI
        val southDelta = 500.0 / 6_371_000.0 * 180.0 / Math.PI
        val edge = GeoPoint(point.latitude + northDelta, point.longitude)
        val moved = GeoPoint(point.latitude - southDelta, point.longitude)
        var requests = 0
        val controller = CctvController(CctvSource { requests++; listOf(camera(at = edge)) }, backgroundScope)
        controller.setVisible(true); controller.updateLocation(point, true); runCurrent()
        controller.setVisible(false)
        controller.setVisible(true); controller.updateLocation(moved, true); runCurrent()
        assertEquals(1, requests)
        assertTrue(controller.state.value.cameras.isEmpty())
    }
}
