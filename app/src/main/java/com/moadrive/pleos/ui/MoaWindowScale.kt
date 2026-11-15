package com.moadrive.pleos.ui

/** Keep physical controls comfortable in a Pleos split task, not phone-sized.
 * Width and height are the host task's dp before applying our content density.
 * Preserve a 600dp working width and short-window scrolling instead of shrinking text.
 */
internal fun moaWindowScale(widthDp: Float, heightDp: Float): Float {
    val expandedScale = minOf(widthDp / 1100f, heightDp / 700f).coerceIn(1f, 1.6f)
    val comfortableScale = minOf(widthDp / 600f, heightDp / 650f).coerceIn(1f, 1.3f)
    return maxOf(expandedScale, comfortableScale)
}
