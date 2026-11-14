package com.moagauge.pleos.navigation.kakao

import android.graphics.RectF
import android.util.Log
import com.moagauge.pleos.BuildConfig
import android.view.SurfaceHolder
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.moagauge.pleos.navigation.*
import com.moagauge.pleos.cctv.CctvCamera
import com.kakaomobility.knsdk.KNSDK
import com.kakaomobility.knsdk.common.util.FloatPoint
import com.kakaomobility.knsdk.map.knmaprenderer.objects.KNMapCameraUpdate
import com.kakaomobility.knsdk.map.knmaprenderer.objects.KNMapCoordinateRegion
import com.kakaomobility.knsdk.map.knmapview.KNMapView
import com.kakaomobility.knsdk.map.knmapview.idl.KNMapRouteProperties
import com.kakaomobility.knsdk.map.uicustomsupport.renewal.theme.base.KNMapRouteTheme
import com.kakaomobility.knsdk.map.uicustomsupport.renewal.theme.base.KNMapTheme
import com.kakaomobility.knsdk.ui.view.KNNaviView
import com.kakaomobility.knsdk.ui.component.MapViewCameraMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val IDLE_MAP_HALF_SPAN_METERS = 400f

/** Caller creates this only after precise location permission and SDK authentication succeed. */
@OptIn(kotlinx.coroutines.ObsoleteCoroutinesApi::class)
@Composable
fun KakaoNavigationSurface(
    provider: KakaoNavigationProvider,
    route: NavigationRoute?,
    guiding: Boolean,
    origin: GeoPoint?,
    modifier: Modifier = Modifier,
    onError: (String) -> Unit = {},
    cameras: List<CctvCamera> = emptyList(),
    onCameraClick: (String) -> Unit = {},
    heading: Float? = null,
    night: Boolean = false,
    showVehicle: Boolean = true,
    voiceGuidance: Boolean = true,
    speedWarnings: Boolean = true,
    showSpeedLayer: Boolean = true,
    safetyOptions: NavigationSafetyOptions = NavigationSafetyOptions(),
    map3dEnabled: Boolean = false,
    previewBottomInsetDp: Float = 0f,
    zoomController: NavigationMapZoomController? = null,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestError by rememberUpdatedState(onError)
    val latestCameras by rememberUpdatedState(cameras)
    val latestCameraClick by rememberUpdatedState(onCameraClick)
    val latestOrigin by rememberUpdatedState(origin)
    val latestHeading by rememberUpdatedState(heading)
    val latestNight by rememberUpdatedState(night)
    val latestShowVehicle by rememberUpdatedState(showVehicle)
    val latestShowSpeedLayer by rememberUpdatedState(showSpeedLayer)
    val latestMap3d by rememberUpdatedState(map3dEnabled)
    val previewBottomInsetPx = with(LocalDensity.current) { previewBottomInsetDp.coerceAtLeast(0f).dp.toPx() }
    val latestBottomInsetPx by rememberUpdatedState(previewBottomInsetPx)
    var manualIdleScale by rememberSaveable { mutableStateOf<Float?>(null) }
    SideEffect {
        provider.updateDisplayOptions(KakaoDisplayOptions(night, showVehicle, voiceGuidance, speedWarnings, safetyOptions, map3dEnabled), origin != null)
    }
    if (guiding) {
        val view = remember(context, provider) { KNNaviView(context) }
        val map = view.mapComponent.mapView
        val decorations = remember(map) { KakaoMapDecorations(map) }
        var geometryReady by remember(map) { mutableStateOf(false) }
        var released by remember(map) { mutableStateOf(false) }
        // KNNaviView binds its own map asynchronously. Wait for its first real camera frame.
        LaunchedEffect(map) {
            while (isActive && !released && !geometryReady) {
                if (map.isAttachedToWindow && map.width > 0 && map.holder.surface.isValid) {
                    val center = map.getMapToCenter()
                    if (center.x != 0f || center.y != 0f) {
                        map.surfaceRedrawNeededAsync(map.holder) { map.post { if (!released) geometryReady = true } }
                    }
                }
                delay(250)
            }
        }
        LaunchedEffect(view, geometryReady) {
            if (geometryReady && !released) {
                val mode = if (latestMap3d) MapViewCameraMode.Bird else MapViewCameraMode.Top
                // A new KNNaviView caches Bird while its map inherits the SDK's
                // previous shared mode. Publish once after binding even when the
                // cached property already matches; subsequent SDK adaptive tilt stays intact.
                if (view.mapViewMode == mode) {
                    view.mapViewMode = if (mode == MapViewCameraMode.Bird) MapViewCameraMode.Top else MapViewCameraMode.Bird
                }
                view.mapViewMode = mode
            }
        }
        DisposableEffect(view, geometryReady, zoomController) {
            val unbind = if (geometryReady) zoomController?.bind { zoomIn ->
                if (!released && map.isAttachedToWindow && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    // Keep the navigation component's own camera scale in sync with its follow mode.
                    view.mapComponent.delegate?.mapViewWillMoveToMap()
                    if (zoomIn) view.mapComponent.zoomIn() else view.mapComponent.zoomOut()
                }
            } else null
            onDispose { unbind?.invoke() }
        }
        LaunchedEffect(map, geometryReady, cameras, origin, route, showVehicle, night) {
            if (geometryReady && !released) {
                decorations.updateCameras(latestCameras) { latestCameraClick(it) }
                // This effect runs on the UI thread and is cancelled when the surface or
                // settings change. Do not lose a visibility update during SDK binding.
                while (isActive && !released) {
                    if (decorations.setVehicleVisible(latestShowVehicle && latestOrigin != null)) break
                    delay(100)
                }
            }
        }
        DisposableEffect(view, provider) {
            var attachment: GuidanceAttachment? = null
            val controls = NativeControlsPolicy(view) {
                latestShowSpeedLayer &&
                    provider.progress.value.speedCamera?.sectionPhase == NavigationSectionPhase.ACTIVE
            }.also { it.attach() }
            val listener = object : android.view.View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: android.view.View) {
                    controls.attach()
                    try { if (attachment == null) attachment = provider.attach(view) }
                    catch (error: Exception) { latestError("안내 화면을 연결하지 못했습니다.") }
                }
                override fun onViewDetachedFromWindow(v: android.view.View) {
                    attachment?.let(provider::detach); attachment = null
                }
            }
            view.addOnAttachStateChangeListener(listener)
            if (view.isAttachedToWindow) listener.onViewAttachedToWindow(view)
            onDispose {
                released = true
                decorations.release()
                controls.release()
                view.removeOnAttachStateChangeListener(listener)
                attachment?.let(provider::detach)
                view.mapComponent.mapView.onPause()
                view.mapComponent.mapView.onHostActivityDestroyed()
            }
        }
        DisposableEffect(view, lifecycle) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> view.mapComponent.mapView.onResume()
                    Lifecycle.Event.ON_STOP -> view.mapComponent.mapView.onPause()
                    else -> Unit
                }
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
        AndroidView(factory = { view }, modifier = modifier)
    } else {
        val map = remember(context, provider) { KNMapView(context) }
        val decorations = remember(map) { KakaoMapDecorations(map) }
        var ready by remember(map) { mutableStateOf(false) }
        var geometryReady by remember(map) { mutableStateOf(false) }
        var released by remember(map) { mutableStateOf(false) }
        val nativeRoute = provider.prepared(route)?.mapRoute
        val latestRoute by rememberUpdatedState(nativeRoute)
        val drawn = remember(map) { arrayOfNulls<com.kakaomobility.knsdk.trip.kntrip.knroute.KNRoute>(1) }
        val frameGeneration = remember(map) { intArrayOf(0) }
        val zoomGeneration = remember(map) { intArrayOf(0) }
        val pendingZoomScale = remember(map) { arrayOfNulls<Float>(1) }
        val diagnosticCount = remember(map) { intArrayOf(0) }
        fun diagnose(message: String) {
            if (BuildConfig.DEBUG && diagnosticCount[0]++ < 16) Log.d("PleosKakaoMap", message)
        }
        val draw = remember(map) {
            {
                val frame = ++frameGeneration[0]
                diagnose("draw request=$frame ready=$ready attached=${map.isAttachedToWindow} surface=${map.holder.surface.isValid} size=${map.width}x${map.height} origin=${latestOrigin != null}")
                map.post {
                    if (frame == frameGeneration[0] && !released && ready && map.isAttachedToWindow && map.width > 0 && map.height > 0 && map.holder.surface.isValid) {
                        map.surfaceRedrawNeededAsync(map.holder) {
                            map.post {
                                diagnose("GL frame=$frame current=${frameGeneration[0]} visible=${lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)}")
                                if (frame == frameGeneration[0] && !released && ready && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                                    geometryReady = true
                                    // The first GL frame establishes native route/projection state.
                                    // Install geometry only now, then fit after the frame that draws it.
                                    if (drawn[0] !== latestRoute) {
                                        if (latestRoute == null) map.removeRoutesAll() else latestRoute?.let(map::setRoute)
                                        drawn[0] = latestRoute
                                        diagnose("route installed=${latestRoute != null}")
                                    }
                                    map.surfaceRedrawNeededAsync(map.holder) {
                                        map.post camera@{
                                            if (frame != frameGeneration[0] || released || !ready || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@camera
                                            if (latestRoute == null) latestOrigin?.let { point ->
                                                val position = KNSDK.convertWGS84ToKATEC(point.longitude, point.latitude)
                                                val x = position.x.toFloat()
                                                val y = position.y.toFloat()
                                                val inset = 24f * map.resources.displayMetrics.density
                                                val camera = KNMapCameraUpdate().tiltTo(if (latestMap3d) 45f else 0f)
                                                    .bearingTo(0f).anchorTo(FloatPoint(.5f, .5f))
                                                val chosenScale = manualIdleScale
                                                if (chosenScale != null) {
                                                    camera.targetTo(FloatPoint(x, y)).zoomTo(chosenScale)
                                                } else {
                                                    camera.fitTo(KNMapCoordinateRegion().initWithMinMax(
                                                            FloatPoint(x - IDLE_MAP_HALF_SPAN_METERS, y - IDLE_MAP_HALF_SPAN_METERS),
                                                            FloatPoint(x + IDLE_MAP_HALF_SPAN_METERS, y + IDLE_MAP_HALF_SPAN_METERS)),
                                                            RectF(inset, inset, (map.width - inset).coerceAtLeast(inset + 1),
                                                                (map.height - inset).coerceAtLeast(inset + 1)))
                                                }
                                                map.moveCamera(camera, false, false)
                                                diagnose("idle camera applied manualScale=$chosenScale")
                                            }
                                            latestRoute?.let { route ->
                                                val inset = 24f * map.resources.displayMetrics.density
                                                val left = if (latestBottomInsetPx > 0f) inset else map.width * .36f
                                                val bottom = (map.height - inset - latestBottomInsetPx).coerceAtLeast(inset + 1f)
                                                val anchorY = if (latestBottomInsetPx > 0f) (inset + bottom) / (2f * map.height) else .5f
                                                val target = RectF(left, inset, (map.width - inset).coerceAtLeast(left + 1f), bottom)
                                                val region = KNMapCoordinateRegion().initWithRoute(listOf(route))
                                                fun currentFrame() = frame == frameGeneration[0] && !released && ready &&
                                                    map.isAttachedToWindow && map.holder.surface.isValid && map.width > 0 && map.height > 0 &&
                                                    latestRoute === route && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                                                fun validBounds(bounds: RectF) = !bounds.isEmpty &&
                                                    listOf(bounds.left, bounds.top, bounds.right, bounds.bottom).all { it.isFinite() }
                                                fun fitRoute(attempt: Int) {
                                                    if (!currentFrame()) return
                                                    val before = map.getScreenRect(region)
                                                    if (validBounds(before)) map.moveCamera(KNMapCameraUpdate().fitTo(region, target), false, false)
                                                    map.surfaceRedrawNeededAsync(map.holder) {
                                                        map.post {
                                                            if (currentFrame()) {
                                                                val bounds = map.getScreenRect(region)
                                                                val tolerance = 4f * map.resources.displayMetrics.density
                                                                val fitted = validBounds(bounds) && RectF(target).apply { inset(-tolerance, -tolerance) }.contains(bounds)
                                                                if (BuildConfig.DEBUG) Log.d("PleosKakaoMap", "preview fit attempt=$attempt boundsReady=${validBounds(before)} fitted=$fitted")
                                                                // The SDK can expose its preceding projection during the first
                                                                // route frame. Retry only this selection, then leave pan/zoom alone.
                                                                if (!fitted && attempt < 3) map.postDelayed({ fitRoute(attempt + 1) }, 32L)
                                                            }
                                                        }
                                                    }
                                                }
                                                // Fit against the projection produced by the new anchor, not the
                                                // preceding idle camera. surfaceRedraw is the native GL boundary.
                                                map.moveCamera(KNMapCameraUpdate().tiltTo(if (latestMap3d) 45f else 0f).bearingTo(0f)
                                                    .anchorTo(FloatPoint(.5f, anchorY)), false, false)
                                                map.surfaceRedrawNeededAsync(map.holder) { map.post { fitRoute(0) } }
                                            }
                                            if (latestRoute == null && latestOrigin == null) {
                                                map.moveCamera(KNMapCameraUpdate().tiltTo(if (latestMap3d) 45f else 0f), false, false)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Unit
            }
        }
        // AndroidView's stable update lambda does not observe values read later in a posted
        // GL callback. Explicit keys ensure a newly prepared route is actually rendered.
        // GPS/marker refreshes must not reset a user's pan or zoom while comparing a route.
        LaunchedEffect(map, ready, nativeRoute, if (nativeRoute == null) origin else null,
            map3dEnabled, previewBottomInsetPx) { if (ready) draw() }
        DisposableEffect(map, geometryReady, zoomController) {
            val unbind = if (geometryReady) zoomController?.bind { zoomIn ->
                if (!released && ready && map.isAttachedToWindow && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    // The SDK scale is metres per screen unit: a smaller value zooms in.
                    // Read the rendered camera rather than the delayed public zoom cache.
                    val currentScale = pendingZoomScale[0] ?: map.getMapCameraScale()
                    if (currentScale.isFinite() && currentScale > 0f) {
                        val targetScale = (currentScale * if (zoomIn) 1f / 1.5f else 1.5f).coerceIn(.5f, 2000f)
                        val request = ++zoomGeneration[0]
                        pendingZoomScale[0] = targetScale
                        if (latestRoute == null) manualIdleScale = targetScale
                        map.moveCamera(KNMapCameraUpdate().zoomTo(targetScale), false, false)
                        // Accumulate quick taps until their camera frame has reached the renderer.
                        map.surfaceRedrawNeededAsync(map.holder) {
                            map.post { if (!released && request == zoomGeneration[0]) pendingZoomScale[0] = null }
                        }
                        if (BuildConfig.DEBUG) Log.d("PleosKakaoMap", "manual zoom in=$zoomIn scale=$currentScale target=$targetScale")
                    }
                }
            } else null
            onDispose { unbind?.invoke() }
        }
        // Marker changes do not refit/recenter the camera or disturb a route preview.
        LaunchedEffect(map, geometryReady, cameras, origin, heading, showVehicle) {
            if (geometryReady && !released && ready) {
                decorations.updateCameras(latestCameras) { latestCameraClick(it) }
                // userLocation is nullable until the SDK's map control manager is bound.
                // Retry the latest GPS/settings without moving the camera again.
                while (isActive && !released && ready) {
                    if (decorations.updateVehicle(if (latestShowVehicle) latestOrigin else null, latestHeading)) break
                    delay(100)
                }
            }
        }
        LaunchedEffect(map, geometryReady, night) {
            if (geometryReady) map.post {
                if (!released && ready) {
                    map.mapTheme = if (latestNight) KNMapTheme.searchNight() else KNMapTheme.searchDay()
                    map.routeProperties?.theme = if (latestNight) KNMapRouteTheme.driveNight() else KNMapRouteTheme.driveDay()
                }
            }
        }
        DisposableEffect(map, lifecycle) {
            val observer = LifecycleEventObserver { _, event ->
                if (ready) when (event) {
                    Lifecycle.Event.ON_START -> { map.onResume(); draw() }
                    Lifecycle.Event.ON_STOP -> map.onPause()
                    else -> Unit
                }
            }
            val callback = object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) = Unit
                override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { draw() }
            }
            lifecycle.addObserver(observer)
            map.holder.addCallback(callback)
            onDispose { released = true; frameGeneration[0]++; decorations.release(); lifecycle.removeObserver(observer); map.holder.removeCallback(callback); map.onPause(); map.onHostActivityDestroyed() }
        }
        AndroidView(factory = {
            KNSDK.bindingMapView(map, if (latestNight) KNMapTheme.searchNight() else KNMapTheme.searchDay()) { error ->
                map.post {
                    if (!released) {
                        ready = error == null
                        diagnose("binding completed ready=$ready")
                        if (error != null) latestError("지도를 불러오지 못했습니다.")
                        else {
                            // Standalone KNMapView does not create routeProperties itself.
                            map.routeProperties = KNMapRouteProperties().apply {
                                isVisible = true
                                isVisibleFullRoute = true
                                useSingleTapped = false
                                useDoubleTapped = false
                                useLongPressed = false
                                theme = if (latestNight) KNMapRouteTheme.driveNight() else KNMapRouteTheme.driveDay()
                            }
                            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) map.onResume()
                            draw()
                        }
                    }
                }
            }
            map
        }, modifier = modifier)
    }
}
