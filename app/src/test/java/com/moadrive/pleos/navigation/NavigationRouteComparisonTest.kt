package com.moadrive.pleos.navigation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationRouteComparisonTest {
    private val origin = GeoPoint(37.5665, 126.9780)
    private val destination = NavigationDestination("station", "서울역", "서울 중구", GeoPoint(37.5547, 126.9706))

    @Test fun initialOptionsCannotBeSelectedBeforeProviderResultsArrive() {
        val options = pendingRouteAlternatives()

        assertEquals(
            listOf(NavigationRoutePreference.RECOMMENDED, NavigationRoutePreference.FREE_ROADS, NavigationRoutePreference.HIGHWAY_FIRST),
            options.map { it.preference },
        )
        assertTrue(options.all { it.loading && it.route == null && it.message == null })
    }

    @Test fun preferredOptionIsRequestedFirstAndEachOptionRetainsItsOwnProviderMetrics() = runTest {
        val provider = FakeProvider { preference -> route(preference) }

        val results = loadRouteAlternatives(provider, origin, destination, NavigationRoutePreference.HIGHWAY_FIRST).toList()

        assertEquals(
            listOf(NavigationRoutePreference.HIGHWAY_FIRST, NavigationRoutePreference.RECOMMENDED, NavigationRoutePreference.FREE_ROADS),
            provider.requests.map { it.preference },
        )
        assertTrue(provider.requests.all { it.origin == origin && it.destination == destination })
        assertEquals(3, results.size)
        assertEquals(listOf("highway", "recommended", "free"), results.map { it.route?.id })
        assertEquals(listOf(18_100, 15_200, 14_100), results.map { it.route?.distanceMeters })
        assertEquals(listOf(1_180, 1_230, 1_650), results.map { it.route?.durationSeconds })
        assertEquals(listOf(1_500, null, 0), results.map { it.route?.tollWon })
        assertTrue(results.all { !it.loading && it.message == null })
    }

    @Test fun failedOptionsDoNotHideLaterSuccessOrInventRoutes() = runTest {
        val provider = FakeProvider { preference ->
            when (preference) {
                NavigationRoutePreference.RECOMMENDED -> throw NavigationFailure("추천 경로를 찾을 수 없어요.")
                NavigationRoutePreference.FREE_ROADS -> throw IllegalStateException("provider unavailable")
                NavigationRoutePreference.HIGHWAY_FIRST -> route(preference)
            }
        }

        val results = loadRouteAlternatives(provider, origin, destination, NavigationRoutePreference.RECOMMENDED).toList()

        assertEquals(3, results.size)
        assertEquals("추천 경로를 찾을 수 없어요.", results[0].message)
        assertNull(results[0].route)
        assertNull(results[1].route)
        assertFalse(results[1].message.isNullOrBlank())
        assertEquals("highway", results[2].route?.id)
        assertTrue(results.none { it.loading })
    }

    @Test fun completedOptionIsEmittedBeforeNextProviderRequestFinishes() = runTest {
        val secondStarted = CompletableDeferred<Unit>()
        val releaseSecond = CompletableDeferred<Unit>()
        val provider = FakeProvider { preference ->
            if (preference == NavigationRoutePreference.FREE_ROADS) {
                secondStarted.complete(Unit)
                releaseSecond.await()
            }
            route(preference)
        }
        val results = mutableListOf<NavigationRouteAlternative>()
        val collection = launch {
            loadRouteAlternatives(provider, origin, destination, NavigationRoutePreference.RECOMMENDED).toList(results)
        }
        try {
            runCurrent()
            assertTrue(secondStarted.isCompleted)
            assertEquals(listOf("recommended"), results.map { it.route?.id })
            assertEquals(2, provider.requests.size)
        } finally {
            releaseSecond.complete(Unit)
            collection.join()
        }
        assertEquals(3, results.size)
    }

    @Test fun providerCancellationIsRethrownWithoutFailureOrRemainingRequests() = runTest {
        val cancellation = CancellationException("comparison superseded")
        val provider = FakeProvider { preference ->
            if (preference == NavigationRoutePreference.FREE_ROADS) throw cancellation
            route(preference)
        }
        val results = mutableListOf<NavigationRouteAlternative>()
        var thrown: CancellationException? = null

        try {
            loadRouteAlternatives(provider, origin, destination, NavigationRoutePreference.RECOMMENDED).toList(results)
        } catch (error: CancellationException) {
            thrown = error
        }

        assertSame(cancellation, thrown)
        assertEquals(listOf(NavigationRoutePreference.RECOMMENDED, NavigationRoutePreference.FREE_ROADS), provider.requests.map { it.preference })
        assertEquals(listOf("recommended"), results.map { it.route?.id })
    }

    @Test fun cancellingCollectionWhileProviderWaitsPreventsAnyResultAndRemainingCalls() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val provider = FakeProvider {
            requestStarted.complete(Unit)
            awaitCancellation()
        }
        val results = mutableListOf<NavigationRouteAlternative>()
        val collection = launch {
            loadRouteAlternatives(provider, origin, destination, NavigationRoutePreference.FREE_ROADS).toList(results)
        }
        runCurrent()
        assertTrue(requestStarted.isCompleted)
        collection.cancelAndJoin()

        assertEquals(listOf(NavigationRoutePreference.FREE_ROADS), provider.requests.map { it.preference })
        assertTrue(results.isEmpty())
    }

    @Test fun routeForAnotherDestinationCannotBeSelected() = runTest {
        assertMismatchedRouteRejected { it.copy(destination = destination.copy(id = "other", point = GeoPoint(37.5, 127.0))) }
    }

    @Test fun routeForAnotherPreferenceCannotBeSelected() = runTest {
        assertMismatchedRouteRejected { it.copy(preference = NavigationRoutePreference.FREE_ROADS) }
    }

    @Test fun routeFromAnotherProviderCannotBeSelected() = runTest {
        assertMismatchedRouteRejected { it.copy(provider = NavigationProviderId.TMAP) }
    }

    private suspend fun assertMismatchedRouteRejected(change: (NavigationRoute) -> NavigationRoute) {
        val provider = FakeProvider { preference ->
            route(preference).let { if (preference == NavigationRoutePreference.RECOMMENDED) change(it) else it }
        }

        val results = loadRouteAlternatives(provider, origin, destination, NavigationRoutePreference.RECOMMENDED).toList()

        assertEquals(3, results.size)
        assertNull(results[0].route)
        assertFalse(results[0].loading)
        assertFalse(results[0].message.isNullOrBlank())
        assertEquals(listOf("free", "highway"), results.drop(1).map { it.route?.id })
    }

    private fun route(preference: NavigationRoutePreference): NavigationRoute = when (preference) {
        NavigationRoutePreference.RECOMMENDED -> NavigationRoute("recommended", NavigationProviderId.KAKAO, destination, 15_200, 1_230, null, preference)
        NavigationRoutePreference.FREE_ROADS -> NavigationRoute("free", NavigationProviderId.KAKAO, destination, 14_100, 1_650, 0, preference)
        NavigationRoutePreference.HIGHWAY_FIRST -> NavigationRoute("highway", NavigationProviderId.KAKAO, destination, 18_100, 1_180, 1_500, preference)
    }

    private data class Request(val origin: GeoPoint, val destination: NavigationDestination, val preference: NavigationRoutePreference)

    private class FakeProvider(private val prepare: suspend (NavigationRoutePreference) -> NavigationRoute) : NavigationProvider {
        override val id = NavigationProviderId.KAKAO
        override val progress = MutableStateFlow(NavigationProgress())
        val requests = mutableListOf<Request>()
        override suspend fun initialize() = Unit
        override suspend fun search(query: String, origin: GeoPoint?) = emptyList<NavigationDestination>()
        override suspend fun prepareRoute(origin: GeoPoint, destination: NavigationDestination, preference: NavigationRoutePreference): NavigationRoute {
            requests += Request(origin, destination, preference)
            return prepare(preference)
        }
        override suspend fun start(route: NavigationRoute, onEnded: () -> Unit) = Unit
        override fun clearPreparedRoutes() = Unit
        override fun stop() = Unit
        override fun foreground(active: Boolean) = Unit
    }
}
