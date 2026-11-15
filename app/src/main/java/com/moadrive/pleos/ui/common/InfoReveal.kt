package com.moadrive.pleos.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private const val RevealDurationMillis = 420
private const val RevealStaggerMillis = 70
private const val LastStaggerIndex = 4
private const val RevealTimelineMillis = RevealDurationMillis + RevealStaggerMillis * LastStaggerIndex

/** The launch cover prepares the app first, then lets its information cards appear. */
val LocalInfoRevealActive = staticCompositionLocalOf { true }

/** One clock per screen, independent of card data, theme and responsive layout branches. */
@Stable
class InfoRevealState internal constructor() {
    internal val elapsed = Animatable(0f)
}

@Composable
fun rememberInfoRevealState(): InfoRevealState {
    val state = remember { InfoRevealState() }
    val active = LocalInfoRevealActive.current
    LaunchedEffect(state, active) {
        // Compose applies the system animator duration scale, including animations disabled.
        if (active) state.elapsed.animateTo(RevealTimelineMillis.toFloat(), tween(RevealTimelineMillis, easing = LinearEasing))
    }
    return state
}

val LocalInfoRevealState = staticCompositionLocalOf<InfoRevealState?> { null }

/** Keeps final layout and scroll geometry in place; only opacity and drawing position change. */
@Composable
fun Modifier.infoReveal(state: InfoRevealState, index: Int): Modifier {
    val distance = with(LocalDensity.current) { 16.dp.toPx() }
    val delay = index.coerceIn(0, LastStaggerIndex) * RevealStaggerMillis
    return graphicsLayer {
        // Avoid an offscreen buffer clipping the cards' soft shadows during the fade.
        compositingStrategy = CompositingStrategy.ModulateAlpha
        // Read animation state in the layer phase; telemetry updates never restart the clock.
        val fraction = ((state.elapsed.value - delay) / RevealDurationMillis).coerceIn(0f, 1f)
        val eased = LinearOutSlowInEasing.transform(fraction)
        alpha = eased
        translationY = distance * (1f - eased)
    }
}

/** A right-pane information group. Native maps and urgent alerts stay outside these groups. */
@Composable
fun InfoReveal(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = LocalInfoRevealState.current
    Box(if (state == null) modifier else modifier.infoReveal(state, index)) { content() }
}
