package com.moagauge.pleos.ui.passport

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.util.Log
import android.view.SurfaceHolder
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.kakaomobility.knsdk.KNSDK
import com.kakaomobility.knsdk.common.util.FloatPoint
import com.kakaomobility.knsdk.common.util.IntPoint
import com.kakaomobility.knsdk.map.knmaprenderer.objects.KNMapCameraUpdate
import com.kakaomobility.knsdk.map.knmaprenderer.objects.KNMapCoordinateRegion
import com.kakaomobility.knsdk.map.knmapview.KNMapView
import com.kakaomobility.knsdk.map.knmapview.constants.KNLineDashType
import com.kakaomobility.knsdk.map.uicustomsupport.renewal.KNMapMarker
import com.kakaomobility.knsdk.map.uicustomsupport.renewal.KNMapPolygon
import com.kakaomobility.knsdk.map.uicustomsupport.renewal.theme.base.KNMapTheme
import com.moagauge.pleos.passport.PassportCell
import com.moagauge.pleos.BuildConfig
import com.moagauge.pleos.ui.IsDarkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** SDK-authenticated map; every overlay is a persisted, GPS-confirmed cell. */
@OptIn(kotlinx.coroutines.ObsoleteCoroutinesApi::class)
@Composable
internal fun PassportMap(
    cells: List<PassportCell>,
    numbers: Map<String, Int>,
    selectedId: String?,
    territory: Boolean,
    interactive: Boolean,
    modifier: Modifier = Modifier,
    onError: (String) -> Unit,
    onReady: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val night = IsDarkTheme
    val latestNight by rememberUpdatedState(night)
    val map = remember(context) { KNMapView(context) }
    var ready by remember(map) { mutableStateOf(false) }
    var released by remember(map) { mutableStateOf(false) }
    var bindingRejected by remember(map) { mutableStateOf(false) }
    var firstFrameDrawn by remember(map) { mutableStateOf(false) }
    val latestCells by rememberUpdatedState(cells)
    val latestNumbers by rememberUpdatedState(numbers)
    val latestSelected by rememberUpdatedState(selectedId)
    val latestTerritory by rememberUpdatedState(territory)
    val latestError by rememberUpdatedState(onError)
    val latestReady by rememberUpdatedState(onReady)
    val generation = remember(map) { intArrayOf(0) }
    val bindingAttempts = remember(map) { intArrayOf(0) }
    val diagnosticCount = remember(map) { intArrayOf(0) }
    fun diagnose(event: String) {
        if (BuildConfig.DEBUG && diagnosticCount[0]++ < 100) Log.d("PleosPassportMap",
            "$event ready=$ready initialized=${map.isInitialize()} attached=${map.isAttachedToWindow} " +
                "surface=${map.holder.surface.isValid} size=${map.width}x${map.height} visible=${lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)}")
    }
    fun requestSurfaceLayout() {
        if (released || !ready || !map.isAttachedToWindow || map.holder.surface.isValid) return
        diagnose("request surface layout")
        // The renderer may bind after Compose measured its AndroidView holder. Invalidate
        // through the Compose host so SurfaceView receives a traversal without waiting
        // for an unrelated screen-state change. Never toggle visibility or recreate it.
        var ancestor: View? = map
        while (ancestor != null) {
            ancestor.requestLayout()
            ancestor.postInvalidateOnAnimation()
            ancestor = ancestor.parent as? View
        }
    }
    val draw = remember(map) {
        {
            val frame = ++generation[0]
            diagnose("draw frame=$frame")
            map.post {
                if (!released && ready && frame == generation[0] && map.isAttachedToWindow &&
                    map.holder.surface.isValid && map.width > 0 && map.height > 0) {
                    // KNMapView's projection is established by its first GL frame.
                    map.surfaceRedrawNeededAsync(map.holder) {
                        map.post {
                            if (!released && ready && frame == generation[0] && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                                if (!firstFrameDrawn) latestReady()
                                firstFrameDrawn = true
                                diagnose("GL frame=$frame")
                                try {
                                    map.removeMarkersAll()
                                    map.removePolygonsAll()
                                    latestCells.forEach { cell ->
                                        val selected = cell.id == latestSelected
                                        if (latestTerritory) {
                                            // NW → NE → SE → SW, clockwise in map coordinates.
                                            val corners = listOf(point(cell.north, cell.west), point(cell.north, cell.east),
                                                point(cell.south, cell.east), point(cell.south, cell.west))
                                            map.addPolygon(KNMapPolygon(corners,
                                                if (selected) 0x802864D7.toInt() else 0x402864D7,
                                                if (selected) 0xFF174CA9.toInt() else 0xFF2864D7.toInt(),
                                                if (selected) 4f else 2f, KNLineDashType.KNLineDashType_Solid, 0))
                                        } else {
                                            map.addMarker(KNMapMarker(point(cell.centerLatitude, cell.centerLongitude)).apply {
                                                icon = stampBitmap(latestNumbers[cell.id] ?: 0, selected, map.resources.displayMetrics.density)
                                                pixelOffset = IntPoint(0, 0)
                                                useSingleTapped = false
                                                useDoubleTapped = false
                                                useLongPressed = false
                                            })
                                        }
                                    }
                                    map.surfaceRedrawNeededAsync(map.holder) {
                                        map.post camera@{
                                            if (released || !ready || frame != generation[0] || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@camera
                                            val fitCells = latestCells.firstOrNull { it.id == latestSelected }?.let(::listOf) ?: latestCells
                                            val corners = if (fitCells.isEmpty()) {
                                                // A neutral country overview, never presented as a visited location.
                                                listOf(point(34.0, 125.0), point(38.7, 130.0))
                                            } else fitCells.flatMap { listOf(point(it.south, it.west), point(it.north, it.east)) }
                                            val minX = corners.minOf { it.x }; val maxX = corners.maxOf { it.x }
                                            val minY = corners.minOf { it.y }; val maxY = corners.maxOf { it.y }
                                            val margin = if (fitCells.isEmpty()) 0f else 400f
                                            val inset = 32f * map.resources.displayMetrics.density
                                            map.moveCamera(KNMapCameraUpdate().tiltTo(0f).bearingTo(0f).anchorTo(FloatPoint(.5f, .5f))
                                                .fitTo(KNMapCoordinateRegion().initWithMinMax(
                                                    FloatPoint(minX - margin, minY - margin), FloatPoint(maxX + margin, maxY + margin)),
                                                    RectF(inset, inset, (map.width - inset).coerceAtLeast(inset + 1f), (map.height - inset).coerceAtLeast(inset + 1f))), false, false)
                                        }
                                    }
                                } catch (_: Exception) { latestError("방문 지도를 표시하지 못했어요. 다시 연결해 주세요.") }
                            }
                        }
                    }
                }
            }
            Unit
        }
    }
    fun bind() {
        if (released || map.isInitialize() || bindingRejected) return
        diagnose("bind attempt=${++bindingAttempts[0]}")
        KNSDK.bindingMapView(map, if (latestNight) KNMapTheme.searchNight() else KNMapTheme.searchDay()) { error ->
            map.post {
                if (!released) {
                    ready = error == null
                    bindingRejected = error != null
                    diagnose("bind callback")
                    if (error != null) latestError("카카오 지도를 불러오지 못했어요. 다시 연결해 주세요.")
                    else {
                        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) map.onResume()
                        requestSurfaceLayout()
                        draw()
                    }
                }
            }
        }
        diagnose("bind returned")
    }
    LaunchedEffect(map, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val startedAt = SystemClock.elapsedRealtime()
            // A successful SDK initialization does not guarantee that a new map renderer
            // can bind immediately during a screen/style transition. Retry only when the
            // first factory binding returned without installing a renderer on this view.
            while (isActive && !released && !ready && !bindingRejected &&
                SystemClock.elapsedRealtime() - startedAt < 20_000) {
                if (map.isAttachedToWindow && map.width > 0 && map.height > 0 && !map.isInitialize()) bind()
                delay(500)
            }
            while (isActive && !released && ready && !firstFrameDrawn &&
                SystemClock.elapsedRealtime() - startedAt < 20_000) delay(250)
            if (!released && !bindingRejected && !firstFrameDrawn) {
                diagnose("first-frame timeout")
                latestError("방문 지도 준비가 지연되고 있어요. 지도를 다시 연결해 주세요.")
            }
        }
    }
    LaunchedEffect(map, ready, cells, numbers, selectedId, territory) { if (ready) draw() }
    // Theme the existing renderer after its first GL frame; retain the SurfaceView and
    // its layout wakeup/watchdog during theme changes, just as the Drive map does.
    LaunchedEffect(map, firstFrameDrawn, night) {
        if (firstFrameDrawn) map.post {
            if (!released && ready) {
                map.mapTheme = if (latestNight) KNMapTheme.searchNight() else KNMapTheme.searchDay()
            }
        }
    }
    DisposableEffect(map, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (ready) when (event) {
                Lifecycle.Event.ON_START -> { map.onResume(); requestSurfaceLayout(); draw() }
                Lifecycle.Event.ON_STOP -> map.onPause()
                else -> Unit
            }
        }
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                diagnose("attached")
                if (ready && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) { map.onResume(); requestSurfaceLayout(); draw() }
            }
            override fun onViewDetachedFromWindow(view: View) { diagnose("detached") }
        }
        val callback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) { diagnose("surface created"); if (ready) draw() }
            override fun surfaceDestroyed(holder: SurfaceHolder) { diagnose("surface destroyed") }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { diagnose("surface changed"); draw() }
        }
        lifecycle.addObserver(observer)
        map.addOnAttachStateChangeListener(attachListener)
        map.holder.addCallback(callback)
        onDispose {
            diagnose("dispose")
            released = true; generation[0]++
            lifecycle.removeObserver(observer); map.removeOnAttachStateChangeListener(attachListener); map.holder.removeCallback(callback)
            if (map.isInitialize()) map.onPause()
            map.onHostActivityDestroyed()
        }
    }
    AndroidView(factory = { bind(); map }, update = {
        it.usePanningGesture = interactive
        it.useZoomGesture = interactive
        it.useTiltGesture = false
        it.useBearingGesture = false
        it.usePanningGestureOnZoom = interactive
        if (ready) draw()
    }, modifier = modifier)
}

private fun point(latitude: Double, longitude: Double): FloatPoint =
    KNSDK.convertWGS84ToKATEC(longitude, latitude).let { FloatPoint(it.x.toFloat(), it.y.toFloat()) }

private fun stampBitmap(number: Int, selected: Boolean, density: Float): Bitmap {
    val size = (72 * density).toInt().coerceAtLeast(48)
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(center, center, size * .45f, paint)
        paint.color = if (selected) 0xFF174CA9.toInt() else 0xFF2864D7.toInt()
        canvas.drawCircle(center, center, size * .39f, paint)
        paint.color = android.graphics.Color.WHITE
        paint.style = Paint.Style.STROKE; paint.strokeWidth = size * .018f
        canvas.drawCircle(center, center, size * .32f, paint)
        paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.textSize = size * if (number < 100) .27f else .21f
        val baseline = center - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(number.toString().padStart(2, '0'), center, baseline, paint)
    }
}
