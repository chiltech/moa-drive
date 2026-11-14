package com.moagauge.pleos.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.moagauge.pleos.ui.Blue
import com.moagauge.pleos.ui.Line

/** A continuous capsule track and fill, without the indicator's trailing stop marker. */
@Composable
internal fun RoundedMetricBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = Blue,
    trackColor: Color = Line,
) {
    val fraction = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    Canvas(modifier.fillMaxWidth().height(8.dp).semantics {
        progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
    }) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(trackColor, cornerRadius = radius)
        if (fraction > 0f) {
            drawRoundRect(color, size = Size(size.width * fraction, size.height), cornerRadius = radius)
        }
    }
}
