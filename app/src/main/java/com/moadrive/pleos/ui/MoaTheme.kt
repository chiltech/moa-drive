package com.moadrive.pleos.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moadrive.pleos.settings.AppSettingsViewModel
import com.moadrive.pleos.settings.AppearanceMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moadrive.pleos.R

private data class MoaColors(val ink: Color, val blue: Color, val secondary: Color, val line: Color,
    val paleBlue: Color, val background: Color, val surface: Color, val card: Color, val dark: Boolean)
private val LightColors = MoaColors(Color(0xFF192130), Color(0xFF2762E8), Color(0xFF69707D),
    Color(0xFFE5E8EE), Color(0xFFEEF2FF), Color(0xFFFAFBFD), Color.White, Color.White, false)
private val DarkColors = MoaColors(Color(0xFFF0F4FA), Color(0xFF91B1FF), Color(0xFFA9B6C8),
    Color(0xFF293341), Color(0xFF203557), Color(0xFF0D131D), Color(0xFF111822), Color(0xFF141C27), true)
private val LocalMoaColors = staticCompositionLocalOf { LightColors }
internal val Ink: Color @Composable @ReadOnlyComposable get() = LocalMoaColors.current.ink
internal val Blue: Color @Composable @ReadOnlyComposable get() = LocalMoaColors.current.blue
internal val Secondary: Color @Composable @ReadOnlyComposable get() = LocalMoaColors.current.secondary
internal val Line: Color @Composable @ReadOnlyComposable get() = LocalMoaColors.current.line
internal val PaleBlue: Color @Composable @ReadOnlyComposable get() = LocalMoaColors.current.paleBlue
internal val BackgroundColor: Color @Composable @ReadOnlyComposable get() = LocalMoaColors.current.background
internal val SurfaceColor: Color @Composable @ReadOnlyComposable get() = LocalMoaColors.current.surface
internal val OnBlue: Color @Composable @ReadOnlyComposable get() = if (IsDarkTheme) Color(0xFF10203B) else Color.White
internal val CardColor: Color @Composable @ReadOnlyComposable get() = LocalMoaColors.current.card
internal val AccentOrange: Color @Composable @ReadOnlyComposable get() = if (IsDarkTheme) Color(0xFFFFAC66) else Color(0xFFFF842D)
internal val OrangeInk: Color @Composable @ReadOnlyComposable get() = if (IsDarkTheme) Color(0xFFFFAC66) else Color(0xFFD34D00)
internal val OrangeSurface: Color @Composable @ReadOnlyComposable get() = if (IsDarkTheme) CardColor else Color(0xFFFFF1E7)
internal val AccentYellow: Color @Composable @ReadOnlyComposable get() = if (IsDarkTheme) Color(0xFFFFD475) else Color(0xFFF2BE4E)
internal val AccentGreen: Color @Composable @ReadOnlyComposable get() = if (IsDarkTheme) Color(0xFF79D6B0) else Color(0xFF258A58)
internal val IsDarkTheme: Boolean @Composable @ReadOnlyComposable get() = LocalMoaColors.current.dark

// White cards stay close to the canvas; dark glass cards need a deeper diffuse shadow.
internal val CardShadowElevation: Dp @Composable @ReadOnlyComposable
    get() = if (IsDarkTheme) 3.5.dp else 2.dp
internal val FloatingShadowElevation: Dp @Composable @ReadOnlyComposable
    get() = if (IsDarkTheme) 0.dp else .9.dp

// Inter supplies crisp Latin and numeric glyphs; Android's Korean sans fallback
// keeps the Korean copy consistent with the approved design.
internal val MoaTypeface = FontFamily(
    Font(R.font.inter_variable, FontWeight.Normal),
    Font(R.font.inter_variable, FontWeight.Medium),
    Font(R.font.inter_variable, FontWeight.SemiBold),
    Font(R.font.inter_variable, FontWeight.Bold),
)

@Composable
fun MoaTheme(darkOverride: Boolean? = null, content: @Composable () -> Unit) {
    val settings: AppSettingsViewModel = viewModel()
    val preferences by settings.state.collectAsState()
    val dark = darkOverride ?: when (preferences.appearance) {
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
        AppearanceMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colors = if (dark) DarkColors else LightColors
    val scheme = if (dark) darkColorScheme() else lightColorScheme()
    CompositionLocalProvider(LocalMoaColors provides colors) {
    MaterialTheme(
        colorScheme = scheme.copy(
            primary = Blue, onPrimary = if (dark) Color(0xFF10203B) else Color.White,
            secondary = Blue, onSecondary = if (dark) Color(0xFF10203B) else Color.White,
            secondaryContainer = PaleBlue, onSecondaryContainer = Ink,
            background = BackgroundColor, onBackground = Ink,
            surface = SurfaceColor, onSurface = Ink,
            surfaceVariant = PaleBlue, onSurfaceVariant = Secondary,
            outline = Line,
        ),
        typography = Typography(
            displayLarge = TextStyle(fontFamily = MoaTypeface, fontSize = 48.sp, lineHeight = 56.sp, fontWeight = FontWeight.Medium),
            headlineLarge = TextStyle(fontFamily = MoaTypeface, fontSize = 32.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold),
            headlineMedium = TextStyle(fontFamily = MoaTypeface, fontSize = 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.Medium),
            titleLarge = TextStyle(fontFamily = MoaTypeface, fontSize = 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.Medium),
            titleMedium = TextStyle(fontFamily = MoaTypeface, fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
            titleSmall = TextStyle(fontFamily = MoaTypeface, fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium),
            bodyLarge = TextStyle(fontFamily = MoaTypeface, fontSize = 18.sp),
            bodyMedium = TextStyle(fontFamily = MoaTypeface, fontSize = 16.sp),
            bodySmall = TextStyle(fontFamily = MoaTypeface, fontSize = 13.sp),
            labelLarge = TextStyle(fontFamily = MoaTypeface, fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium),
            labelMedium = TextStyle(fontFamily = MoaTypeface, fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium),
            labelSmall = TextStyle(fontFamily = MoaTypeface, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
        ),
        content = content,
    )
    }
}
