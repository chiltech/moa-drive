package com.moagauge.pleos.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moagauge.pleos.ui.BackgroundColor
import com.moagauge.pleos.ui.CardColor
import com.moagauge.pleos.ui.IsDarkTheme
import com.moagauge.pleos.ui.SurfaceColor

/** A wider dark shadow separates translucent cards without adding a bright border. */
@Composable
internal fun Modifier.softSurfaceShadow(elevation: Dp, shape: Shape): Modifier {
    if (elevation <= 0.dp) return this
    val dark = IsDarkTheme
    return shadow(
        elevation = if (dark) (elevation * 4f).coerceAtLeast(12.dp)
            else (elevation * 3.5f).coerceAtLeast(6.dp),
        shape = shape,
        clip = false,
        ambientColor = Color.Black.copy(alpha = if (dark) .76f else .28f),
        spotColor = Color.Black.copy(alpha = if (dark) .72f else .20f),
    )
}

// The fill is genuinely translucent. Its quiet highlight sits behind content,
// so numbers, text and icons keep their original contrast and sharpness.
internal fun Modifier.darkGlassBackground(shape: Shape): Modifier = background(
    brush = Brush.linearGradient(
        0f to Color(0xFF344457).copy(alpha = .52f),
        .20f to Color(0xFF223044).copy(alpha = .50f),
        .62f to Color(0xFF182332).copy(alpha = .46f),
        1f to Color(0xFF26364B).copy(alpha = .52f),
    ),
    shape = shape,
)

@Composable
private fun isGlassCard(color: Color, elevation: Dp): Boolean =
    IsDarkTheme && elevation > 0.dp &&
        (color == CardColor || color == SurfaceColor || color == BackgroundColor)

@Composable
internal fun SoftSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(color),
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    border: BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    val glass = isGlassCard(color, shadowElevation)
    val surfaceModifier = modifier.softSurfaceShadow(shadowElevation, shape)
    androidx.compose.material3.Surface(
        modifier = if (glass) surfaceModifier.darkGlassBackground(shape) else surfaceModifier,
        shape = shape, color = if (glass) Color.Transparent else color,
        contentColor = contentColor, tonalElevation = if (glass) 0.dp else tonalElevation,
        border = border, content = content,
    )
}

@Composable
internal fun SoftSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RectangleShape,
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(color),
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    border: BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    val glass = isGlassCard(color, shadowElevation)
    val surfaceModifier = modifier.softSurfaceShadow(shadowElevation, shape)
    androidx.compose.material3.Surface(
        onClick = onClick,
        modifier = if (glass) surfaceModifier.darkGlassBackground(shape) else surfaceModifier,
        enabled = enabled,
        shape = shape, color = if (glass) Color.Transparent else color, contentColor = contentColor,
        tonalElevation = if (glass) 0.dp else tonalElevation, border = border, content = content,
    )
}
