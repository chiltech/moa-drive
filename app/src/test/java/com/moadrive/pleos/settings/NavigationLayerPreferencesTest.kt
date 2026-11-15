package com.moadrive.pleos.settings

import org.junit.Assert.*
import org.junit.Test

class NavigationLayerPreferencesTest {
    @Test fun opacityIsBoundedAndNonFiniteValuesFallBackToReadableDefault() {
        assertEquals(.7f, NavigationLayerPreferences(opacity = .1f).normalized().opacity, .001f)
        assertEquals(1f, NavigationLayerPreferences(opacity = 1.5f).normalized().opacity, .001f)
        assertEquals(.94f, NavigationLayerPreferences(opacity = Float.NaN).normalized().opacity, .001f)
        assertEquals(.94f, NavigationLayerPreferences(opacity = Float.POSITIVE_INFINITY).normalized().opacity, .001f)
    }

    @Test fun normalizingOpacityPreservesVisibilityAndStyleChoices() {
        val chosen = NavigationLayerPreferences(NavigationCardScale.LARGE, NavigationCardBackground.DARK,
            .8f, showTraffic = false, showSpeed = true, showRestArea = false, accent = NavigationCardAccent.ORANGE)
        assertEquals(chosen, chosen.normalized())
    }

    @Test fun resettingOnlyLayersPreservesOtherPreferences() {
        val before = AppPreferences(appearance = AppearanceMode.DARK, speedWarnings = false, voiceGuidance = false,
            navigationLayers = NavigationLayerPreferences(cardScale = NavigationCardScale.SMALL, showRestArea = false))
        val after = before.copy(navigationLayers = NavigationLayerPreferences())
        assertEquals(AppearanceMode.DARK, after.appearance)
        assertFalse(after.speedWarnings)
        assertFalse(after.voiceGuidance)
        assertEquals(NavigationLayerPreferences(), after.navigationLayers)
    }
}
