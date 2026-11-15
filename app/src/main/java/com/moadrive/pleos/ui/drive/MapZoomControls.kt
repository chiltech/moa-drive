package com.moadrive.pleos.ui.drive

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import com.moadrive.pleos.ui.common.SoftSurface as Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.moadrive.pleos.ui.Blue
import com.moadrive.pleos.ui.FloatingShadowElevation
import com.moadrive.pleos.ui.Line
import com.moadrive.pleos.ui.Secondary
import com.moadrive.pleos.ui.SurfaceColor

@Composable
internal fun MapZoomControls(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = SurfaceColor,
        border = BorderStroke(1.dp, Line),
        shadowElevation = if (enabled) FloatingShadowElevation else 0.dp,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MapZoomButton(zoomIn = true, enabled = enabled, onClick = onZoomIn)
            HorizontalDivider(Modifier.width(28.dp), thickness = 1.dp, color = Line)
            MapZoomButton(zoomIn = false, enabled = enabled, onClick = onZoomOut)
        }
    }
}

@Composable
private fun MapZoomButton(zoomIn: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val tint = if (enabled) Blue else Secondary
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(52.dp).semantics {
            contentDescription = if (zoomIn) "지도 확대" else "지도 축소"
        },
    ) {
        Canvas(Modifier.size(24.dp)) {
            val strokeWidth = 2.25.dp.toPx()
            drawLine(
                color = tint,
                start = Offset(size.width * 5f / 24f, size.height / 2f),
                end = Offset(size.width * 19f / 24f, size.height / 2f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            if (zoomIn) {
                drawLine(
                    color = tint,
                    start = Offset(size.width / 2f, size.height * 5f / 24f),
                    end = Offset(size.width / 2f, size.height * 19f / 24f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
