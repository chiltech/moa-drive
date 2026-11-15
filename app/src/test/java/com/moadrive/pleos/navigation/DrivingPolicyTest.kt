package com.moadrive.pleos.navigation

import android.car.drivingstate.CarUxRestrictions
import org.junit.Assert.*
import org.junit.Test

class DrivingPolicyTest {
    @Test fun missingRestrictionSnapshotDisablesEveryInteraction() {
        val policy = DrivingPolicy.fromRestrictions(null)
        assertFalse(policy.connected)
        assertFalse(policy.settingsAllowed)
        assertFalse(policy.setupAllowed)
        assertFalse(policy.videoAllowed)
        assertFalse(policy.textInputAllowed)
        assertFalse(policy.searchAllowed)
        assertEquals(0, policy.maxItems)
    }

    @Test fun unrestrictedConnectedVehicleAllowsEachFeature() {
        val policy = DrivingPolicy.fromRestrictions(0)
        assertTrue(policy.connected)
        assertTrue(policy.settingsAllowed)
        assertTrue(policy.setupAllowed)
        assertTrue(policy.videoAllowed)
        assertTrue(policy.textInputAllowed)
        assertTrue(policy.searchAllowed)
        assertEquals(6, policy.maxItems)
    }

    @Test fun noSetupBlocksFormsButKeepsMenusMediaAndSearchAvailable() {
        val policy = DrivingPolicy.fromRestrictions(CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP)
        assertTrue(policy.settingsAllowed)
        assertFalse(policy.setupAllowed)
        assertTrue(policy.searchAllowed)
        assertTrue(policy.textInputAllowed)
        assertTrue(policy.videoAllowed)
    }

    @Test fun keyboardAndFilteringRestrictionsIndependentlyBlockSearch() {
        for (flag in listOf(CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD, CarUxRestrictions.UX_RESTRICTIONS_NO_FILTERING)) {
            val policy = DrivingPolicy.fromRestrictions(flag)
            assertFalse(policy.searchAllowed)
            assertTrue(policy.settingsAllowed)
            assertTrue(policy.setupAllowed)
            assertTrue(policy.videoAllowed)
            assertEquals(flag != CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD, policy.textInputAllowed)
        }
    }

    @Test fun noVideoStopsVideoWithoutBlockingBasicMenuOrMediaControls() {
        val policy = DrivingPolicy.fromRestrictions(CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO)
        assertFalse(policy.videoAllowed)
        assertTrue(policy.settingsAllowed)
        assertTrue(policy.setupAllowed)
        assertTrue(policy.textInputAllowed)
        assertTrue(policy.searchAllowed)
    }

    @Test fun simultaneousDrivingRestrictionsRemainFeatureSpecific() {
        val flags = CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP or CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO or
            CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD or CarUxRestrictions.UX_RESTRICTIONS_LIMIT_CONTENT
        val policy = DrivingPolicy.fromRestrictions(flags, 2)
        assertTrue(policy.connected)
        assertTrue(policy.settingsAllowed)
        assertFalse(policy.setupAllowed)
        assertFalse(policy.videoAllowed)
        assertFalse(policy.textInputAllowed)
        assertFalse(policy.searchAllowed)
        assertEquals(2, policy.maxItems)
    }

    @Test fun contentLimitsStillBoundSearchResultsAndZeroResultsDenySearch() {
        for ((requested, expected) in listOf(-1 to 0, 0 to 0, 3 to 3, 50 to 6)) {
            val policy = DrivingPolicy.fromRestrictions(CarUxRestrictions.UX_RESTRICTIONS_LIMIT_CONTENT, requested)
            assertEquals(expected, policy.maxItems)
            assertEquals(expected > 0, policy.searchAllowed)
            assertTrue(policy.settingsAllowed)
        }
        assertEquals(6, DrivingPolicy.fromRestrictions(0, 0).maxItems)
    }
}
