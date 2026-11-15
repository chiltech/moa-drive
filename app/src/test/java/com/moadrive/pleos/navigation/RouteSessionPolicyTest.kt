package com.moadrive.pleos.navigation

import org.junit.Assert.*
import org.junit.Test

class RouteSessionPolicyTest {
    @Test fun preferenceChangeRejectsOldRequestEvenAfterReturningToItsOriginalChoice() {
        val policy = RouteSessionPolicy()
        policy.setForeground(true)
        val oldRecommended = policy.token()
        policy.grantFocus(oldRecommended)
        policy.selectRoutePreference(NavigationRoutePreference.FREE_ROADS)
        val free = policy.token()
        policy.selectRoutePreference(NavigationRoutePreference.RECOMMENDED)
        assertFalse(policy.grantFocus(oldRecommended))
        assertFalse(policy.grantFocus(free))
        assertTrue(policy.grantFocus(policy.token()))
    }

    @Test fun repeatingPreferenceDoesNotRetireItsPreparedRoute() {
        val policy = RouteSessionPolicy()
        policy.setForeground(true)
        policy.selectRoutePreference(NavigationRoutePreference.HIGHWAY_FIRST)
        val highway = policy.token()
        policy.selectRoutePreference(NavigationRoutePreference.HIGHWAY_FIRST)
        assertEquals(NavigationRoutePreference.HIGHWAY_FIRST, policy.routePreference)
        assertTrue(policy.grantFocus(highway))
    }

    @Test fun leavingAndReturningKeepsPreferenceButCannotReviveOldRoute() {
        val policy = RouteSessionPolicy()
        policy.setForeground(true)
        policy.selectRoutePreference(NavigationRoutePreference.FREE_ROADS)
        val before = policy.token()
        policy.setForeground(false)
        policy.setForeground(true)
        assertEquals(NavigationRoutePreference.FREE_ROADS, policy.routePreference)
        assertFalse(policy.grantFocus(before))
    }

    @Test fun backgroundDuringPendingFocusRejectsLateGrant() {
        val policy = RouteSessionPolicy()
        policy.setForeground(true)
        val pending = policy.token()
        policy.setForeground(false)
        policy.setForeground(true)
        assertFalse(policy.grantFocus(pending))
        assertFalse(policy.mayGuide(pending))
    }

    @Test fun focusLossInvalidatesPreviouslyActiveRoute() {
        val policy = RouteSessionPolicy()
        policy.setForeground(true)
        val token = policy.token()
        assertTrue(policy.grantFocus(token))
        assertTrue(policy.mayGuide(token))
        policy.invalidate()
        assertFalse(policy.mayGuide(token))
        assertFalse(policy.grantFocus(token))
    }

    @Test fun unsupportedProviderCannotReplaceActiveKakaoRoute() {
        val policy = RouteSessionPolicy()
        policy.setForeground(true)
        val token = policy.token()
        policy.grantFocus(token)
        assertFalse(policy.select(NavigationProviderId.TMAP))
        assertFalse(policy.select(NavigationProviderId.NAVER))
        assertEquals(NavigationProviderId.KAKAO, policy.selectedProvider)
        assertTrue(policy.mayGuide(token))
    }

    @Test fun returningToForegroundDoesNotResumeOldGuidance() {
        val policy = RouteSessionPolicy()
        policy.setForeground(true)
        val old = policy.token()
        policy.grantFocus(old)
        policy.setForeground(false)
        policy.setForeground(true)
        assertFalse(policy.mayGuide(old))
        val fresh = policy.token()
        assertTrue(policy.grantFocus(fresh))
        assertTrue(policy.mayGuide(fresh))
    }
}
