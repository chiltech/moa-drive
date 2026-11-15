package com.moadrive.pleos.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.moadrive.pleos.R

/** Render only the car's viewport from our existing concept asset. UI text is never rasterized. */
@Composable
internal fun VehicleArtwork(modifier: Modifier = Modifier) {
    val source = ImageBitmap.imageResource(R.drawable.home_art_reference)
    Canvas(modifier.semantics { contentDescription = "실버 전기차 디자인 이미지" }) {
        drawImage(
            image = source,
            srcOffset = IntOffset(890, 640),
            srcSize = IntSize(1240, 550),
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        )
        // Blend the reference viewport into the white surface without editing its source file.
        drawRect(Brush.horizontalGradient(0f to Color.White, 0.035f to Color.Transparent,
            0.94f to Color.Transparent, 1f to Color.White))
        drawRect(Brush.verticalGradient(0f to Color.White, 0.1f to Color.Transparent,
            0.88f to Color.Transparent, 1f to Color.White))
    }
}
