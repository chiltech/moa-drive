package com.moagauge.pleos.navigation

import org.junit.Assert.*
import org.junit.Test

class SuggestedDestinationRequestTest {
    @Test fun replacementAndCancellationRejectLateResponses() {
        val requests = SuggestedDestinationRequests()
        val first = requests.begin("회사", 100)
        val second = requests.begin("학교", 110)
        assertFalse(requests.accepts(first, 120))
        assertTrue(requests.accepts(second, 120))
        requests.cancel()
        assertFalse(requests.accepts(second, 120))
    }

    @Test fun deadlineIncludesReadinessAndNetworkTimeAndNeverAcceptsExpiredResponses() {
        val requests = SuggestedDestinationRequests(timeoutMs = 30_000)
        val request = requests.begin("공덕역", 1_000)
        assertTrue(requests.accepts(request, 30_999))
        assertFalse(requests.accepts(request, 31_000))
        assertFalse(requests.accepts(request, 999))
    }

    @Test fun finishingAnOldRequestCannotCancelTheReplacement() {
        val requests = SuggestedDestinationRequests()
        val first = requests.begin("회사", 100)
        val second = requests.begin("학교", 110)
        requests.finish(first)
        assertTrue(requests.accepts(second, 120))
        requests.finish(second)
        assertFalse(requests.accepts(second, 120))
    }

    @Test fun nativeSearchResultWithMatchingNameIsPreferredAndRetained() {
        val nearby = NavigationDestination("native-nearby", "서울특별시청 주차장", "서울", GeoPoint(37.5, 127.0))
        val exact = NavigationDestination("native-exact", "서울특별시청", "서울", GeoPoint(37.6, 127.0))
        assertSame(exact, chooseSuggestedDestination(" 서울특별시청 ", listOf(nearby, exact)))
        assertSame(nearby, chooseSuggestedDestination("예시 장소", listOf(nearby, exact)))
        assertNull(chooseSuggestedDestination("회사", emptyList()))
    }
}
