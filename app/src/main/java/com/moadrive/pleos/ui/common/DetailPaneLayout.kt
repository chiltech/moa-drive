package com.moadrive.pleos.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One window rule and the same pane proportions for every service detail. */
object DetailPaneLayout {
    const val LeftWeight = .38f
    const val RightWeight = .62f
    val Gap = 28.dp

    // Evaluate before removing page padding or the service rail. A Pleos one-area
    // window stacks its sections; two-area windows keep the tablet composition.
    fun isExpanded(windowWidth: Dp, fontScale: Float): Boolean =
        windowWidth >= 1000.dp && fontScale <= 1.5f

    fun pagePadding(expanded: Boolean): Dp = if (expanded) 32.dp else 16.dp
}

/** Supplied by DetailPage so artwork follows the same outer-window decision. */
val LocalDetailExpanded = staticCompositionLocalOf { true }
