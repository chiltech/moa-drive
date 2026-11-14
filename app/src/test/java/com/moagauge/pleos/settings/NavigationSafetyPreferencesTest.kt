package com.moagauge.pleos.settings

import org.junit.Assert.*
import org.junit.Test

class NavigationSafetyPreferencesTest {
    @Test fun oldInstallsReceiveDefaultCameraAndMapChoices() {
        val preferences = AppPreferences()
        assertTrue(preferences.speedWarningFlash)
        assertEquals(1000, preferences.speedWarningDistanceMeters)
        assertTrue(preferences.includeMobileCameras)
        assertFalse(preferences.map3dEnabled)
    }

    @Test fun warningDistanceAcceptsOnlyTheTwoOfferedChoices() {
        for (distance in listOf(500, 1000)) {
            assertEquals(distance, AppPreferences(speedWarningDistanceMeters = distance).normalized().speedWarningDistanceMeters)
        }
        for (distance in listOf(Int.MIN_VALUE, -1, 0, 499, 501, 999, 1001, Int.MAX_VALUE)) {
            assertEquals(1000, AppPreferences(speedWarningDistanceMeters = distance).normalized().speedWarningDistanceMeters)
        }
    }

    @Test fun normalizationPreservesExistingSelectionsAndIndependentNewOptions() {
        val chosen = AppPreferences(appearance = AppearanceMode.DARK, mapAppearance = MapAppearance.NIGHT,
            speedWarnings = false, voiceGuidance = false, routineSuggestions = false,
            showIntro = false, autoLaunchOnBoarding = true,
            navigationLayers = NavigationLayerPreferences(cardScale = NavigationCardScale.LARGE,
                background = NavigationCardBackground.DARK, accent = NavigationCardAccent.ORANGE, opacity = .8f),
            speedWarningFlash = false, speedWarningDistanceMeters = 500, includeMobileCameras = false, map3dEnabled = true)
        assertEquals(chosen, chosen.normalized())
        assertEquals(chosen, chosen.copy(speedWarningDistanceMeters = -1).normalized().copy(speedWarningDistanceMeters = 500))
        assertEquals(.7f, chosen.copy(navigationLayers = chosen.navigationLayers.copy(opacity = .1f)).normalized().navigationLayers.opacity, .001f)
    }
}
