package com.moadrive.pleos.ui.drive

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.moadrive.pleos.settings.NavigationCardAccent
import com.moadrive.pleos.settings.NavigationCardBackground
import com.moadrive.pleos.settings.NavigationLayerPreferences
import com.moadrive.pleos.ui.IsDarkTheme

internal data class NavigationLayerStyle(val card: Color, val ink: Color, val secondary: Color, val accent: Color)

@Composable
internal fun navigationLayerStyle(preferences: NavigationLayerPreferences): NavigationLayerStyle {
    val dark = when (preferences.background) {
        NavigationCardBackground.FOLLOW_THEME -> IsDarkTheme
        NavigationCardBackground.WHITE -> false
        NavigationCardBackground.DARK -> true
    }
    return NavigationLayerStyle(
        card = (if (dark) Color(0xFF151D29) else Color.White).copy(alpha = preferences.opacity.coerceIn(.7f, 1f)),
        ink = if (dark) Color(0xFFF0F4FA) else Color(0xFF1D2634),
        secondary = if (dark) Color(0xFFB3C0D0) else Color(0xFF526174),
        accent = when (preferences.accent) {
            NavigationCardAccent.BLUE -> if (dark) Color(0xFF91B1FF) else Color(0xFF2762E8)
            NavigationCardAccent.GREEN -> if (dark) Color(0xFF79D6B0) else Color(0xFF197A58)
            NavigationCardAccent.ORANGE -> if (dark) Color(0xFFFFAC66) else Color(0xFFD34D00)
        },
    )
}
