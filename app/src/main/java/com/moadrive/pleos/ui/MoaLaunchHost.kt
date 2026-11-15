package com.moadrive.pleos.ui

import android.animation.ValueAnimator
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.Window
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.moadrive.pleos.R
import com.moadrive.pleos.BuildConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

private const val SplashEntranceDurationMillis = 1_600

/**
 * One launch per fresh task. Activity restoration always goes straight to its
 * saved screen, even if the previous activity was saved during the launch art.
 */
@Composable
fun MoaLaunchHost(showSplash: Boolean, hostWindow: Window) {
    var completed by rememberSaveable { mutableStateOf(!showSplash) }
    val startupReady = !showSplash || completed
    var contentPlaced by remember { mutableStateOf(false) }
    var contentFirstFrame by remember { mutableStateOf(false) }
    val entranceTimeline = remember { Animatable(if (ValueAnimator.areAnimatorsEnabled()) 0f else 1f) }
    val splashAlpha = remember { Animatable(1f) }
    val background = BackgroundColor.toArgb()
    val dark = IsDarkTheme

    SideEffect {
        // Local window styling follows live app preferences and system changes;
        // it does not override the vehicle's configuration or global night mode.
        hostWindow.setBackgroundDrawable(ColorDrawable(background))
        WindowCompat.getInsetsController(hostWindow, hostWindow.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    LaunchedEffect(contentPlaced) {
        if (contentPlaced) {
            // Layout has completed; let the app's first draw reach the window.
            // Native maps may continue loading normally, without a network wait.
            withFrameNanos { }
            withFrameNanos { }
            contentFirstFrame = true
            traceSplash("content first frame ready")
        }
    }
    LaunchedEffect(showSplash) {
        if (showSplash && !completed) {
            withFrameNanos { }
            traceSplash("same-window motion splash started")
            if (ValueAnimator.areAnimatorsEnabled()) {
                // Brand appears at the center, lifts at 600–1250 ms, then the
                // vehicle enters at 900–1600 ms before a brief hold and fade.
                entranceTimeline.animateTo(1f, tween(SplashEntranceDurationMillis, easing = LinearEasing))
                delay(400)
            } else {
                entranceTimeline.snapTo(1f)
                delay(900)
            }
            snapshotFlow { contentFirstFrame }.first { it }
            traceSplash("fade started")
            if (ValueAnimator.areAnimatorsEnabled()) {
                splashAlpha.animateTo(0f, tween(450, easing = FastOutSlowInEasing))
            } else {
                splashAlpha.snapTo(0f)
            }
            completed = true
            traceSplash("launch completed")
        }
    }

    Box(Modifier.fillMaxSize().background(BackgroundColor)) {
        Box(Modifier.fillMaxSize().onGloballyPositioned {
            if (it.size.width > 0 && it.size.height > 0) contentPlaced = true
        }) {
            // Compose prepares the destination beneath the artwork. DriveScreen
            // delays its native SurfaceView until startupReady becomes true.
            MoaDriveApp(startupReady = startupReady)
        }
        if (!startupReady) {
            // Keep both layers in one Activity window. Pleos can temporarily
            // reorder a separate Dialog while resizing its application area.
            MoaSplashArtwork(entranceProgress = { entranceTimeline.value }, modifier = Modifier.fillMaxSize()
                .graphicsLayer { alpha = splashAlpha.value }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                })
        }
    }
    BackHandler(enabled = !startupReady) { }
}

private fun traceSplash(message: String) {
    if (BuildConfig.DEBUG) Log.d("MoaSplash", message)
}

@Composable
private fun MoaSplashArtwork(entranceProgress: () -> Float, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.background(BackgroundColor), contentAlignment = Alignment.Center) {
        // Independent native elements preserve their proportions in both Pleos
        // full and split windows; typography stays separate from the vehicle artwork.
        val contentWidth = (maxWidth - 48.dp).coerceAtLeast(1.dp).coerceAtMost(680.dp)
        val markWidth = minOf(contentWidth * .25f, maxHeight * .18f).coerceIn(64.dp, 160.dp)
        val cityWidth = minOf(contentWidth * .90f, maxHeight * .68f, 560.dp)
        val cityHeight = minOf(cityWidth * .60f, maxHeight * .28f)
        val taglineSize = (maxWidth.value * .032f).coerceIn(22f, 30f).sp
        val markSpacing = (maxHeight * .035f).coerceIn(18.dp, 40.dp)
        val citySpacing = (maxHeight * .025f).coerceIn(16.dp, 30.dp)
        Column(Modifier.width(contentWidth), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.fillMaxWidth().graphicsLayer {
                val progress = entranceProgress()
                alpha = splashPhase(progress, 0, 180)
                // The hidden illustration still reserves its final space. This
                // offset centers the brand alone, then moves both elements as one.
                translationY = ((citySpacing + cityHeight) / 2).toPx() *
                    (1f - splashPhase(progress, 600, 1_250))
            }, horizontalAlignment = Alignment.CenterHorizontally) {
                MoaLaunchMark(entranceProgress, Modifier.size(markWidth, markWidth * .70f))
                Spacer(Modifier.height(markSpacing))
                Text("일상을 케어하는 모아드라이브", color = Secondary,
                    fontFamily = MoaTypeface, fontWeight = FontWeight.Normal,
                    fontSize = taglineSize, lineHeight = taglineSize * 1.45f,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(citySpacing))
            Image(
                painter = painterResource(if (IsDarkTheme) R.drawable.moa_splash_vehicle_dark_20261115 else R.drawable.moa_splash_vehicle_light_20261115),
                contentDescription = null,
                modifier = Modifier.size(cityWidth, cityHeight).graphicsLayer {
                    val fraction = splashPhase(entranceProgress(), 900, SplashEntranceDurationMillis)
                    alpha = fraction
                    translationY = (1f - fraction) * 24.dp.toPx()
                    scaleX = .97f + .03f * fraction
                    scaleY = scaleX
                },
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun MoaLaunchMark(entranceProgress: () -> Float, modifier: Modifier = Modifier) {
    val color = Blue
    val path = remember {
        // Rounded shoulders and a soft central valley match the approved M.
        Path().apply {
            moveTo(20f, 122f)
            lineTo(20f, 36f)
            cubicTo(20f, 10f, 45f, 5f, 57f, 27f)
            lineTo(96f, 95f)
            cubicTo(99f, 101f, 103f, 101f, 107f, 95f)
            lineTo(145f, 27f)
            cubicTo(157f, 5f, 182f, 10f, 182f, 36f)
            lineTo(182f, 122f)
        }
    }
    val measure = remember(path) { PathMeasure().apply { setPath(path, false) } }
    val segment = remember { Path() }
    Canvas(modifier.semantics { contentDescription = "모아드라이브 로고" }) {
        val fraction = splashPhase(entranceProgress(), 0, 300)
        if (fraction > 0f) {
            segment.reset()
            measure.getSegment(0f, measure.length * fraction, segment, startWithMoveTo = true)
            val unit = minOf(size.width / 200f, size.height / 140f)
            withTransform({
                translate((size.width - 200f * unit) / 2f, (size.height - 140f * unit) / 2f)
                scale(unit, unit, pivot = Offset.Zero)
            }) {
                drawPath(segment, color, style = Stroke(18f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

private fun splashPhase(value: Float, startMillis: Int, endMillis: Int): Float =
    FastOutSlowInEasing.transform(
        ((value * SplashEntranceDurationMillis - startMillis) / (endMillis - startMillis)).coerceIn(0f, 1f))
