package com.moagauge.pleos.navigation.kakao

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.moagauge.pleos.R
import com.moagauge.pleos.cctv.CctvCamera
import com.moagauge.pleos.navigation.GeoPoint
import com.kakaomobility.knsdk.KNSDK
import com.kakaomobility.knsdk.common.util.FloatPoint
import com.kakaomobility.knsdk.map.knmapview.KNMapView
import com.kakaomobility.knsdk.map.knmapview.idl.KNMarkerEventListener
import com.kakaomobility.knsdk.map.uicustomsupport.renewal.KNMapMarker
import com.kakaomobility.knsdk.ui.view.KNNaviView
import kotlin.math.min
import kotlin.math.roundToInt

/** Owns only this application's camera markers; native navigation markers/listeners stay intact. */
internal class KakaoMapDecorations(private val map: KNMapView) {
    private data class CameraInfo(val id: String)
    private val markers = linkedMapOf<String, KNMapMarker>()
    private val cameraIcon by lazy { cameraBitmap(map.resources.displayMetrics.density) }
    private val vehicleIcon by lazy {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(map.resources, R.drawable.ioniq5_navigation, options)
        options.inJustDecodeBounds = false
        options.inSampleSize = 1
        val targetDensity = map.resources.displayMetrics.densityDpi
        val densityRatio = if (options.inDensity > 0) targetDensity.toFloat() / options.inDensity else 1f
        while (maxOf(options.outWidth, options.outHeight) * densityRatio / options.inSampleSize > 512) options.inSampleSize *= 2
        val original = BitmapFactory.decodeResource(map.resources, R.drawable.ioniq5_navigation, options)
        val density = map.resources.displayMetrics.density
        val scale = min(80f * density / original.width, 120f * density / original.height)
        Bitmap.createScaledBitmap(original, (original.width * scale).roundToInt().coerceAtLeast(1),
            (original.height * scale).roundToInt().coerceAtLeast(1), true)
    }
    private var previousListener: KNMarkerEventListener? = null
    private var onCameraClick: (String) -> Unit = {}
    private var released = false
    private val listener = object : KNMarkerEventListener {
        override fun onSingleTapped(mapView: KNMapView?, marker: KNMapMarker) {
            val info = marker.info as? CameraInfo
            if (info == null) previousListener?.onSingleTapped(mapView, marker)
            else map.post { if (!released && markers[info.id] === marker) onCameraClick(info.id) }
        }
        override fun onCalloutBubbleSelected(mapView: KNMapView?, marker: KNMapMarker) {
            if (marker.info is CameraInfo) onSingleTapped(mapView, marker)
            else previousListener?.onCalloutBubbleSelected(mapView, marker)
        }
        override fun onDoubleTapped(mapView: KNMapView?, marker: KNMapMarker) {
            if (marker.info !is CameraInfo) previousListener?.onDoubleTapped(mapView, marker)
        }
        override fun onLongPressed(mapView: KNMapView?, marker: KNMapMarker) {
            if (marker.info !is CameraInfo) previousListener?.onLongPressed(mapView, marker)
        }
        override fun onMarkerAnimateEnded(mapView: KNMapView?, marker: KNMapMarker) {
            if (marker.info !is CameraInfo) previousListener?.onMarkerAnimateEnded(mapView, marker)
        }
    }

    /** Call on the main thread once the native map has drawn its first frame. */
    fun updateCameras(cameras: List<CctvCamera>, onClick: (String) -> Unit) {
        if (released) return
        onCameraClick = onClick
        // KNNaviView may reinstall its listener while initializing a new guidance session.
        if (map.knMarkerEventListener !== listener) {
            previousListener = map.knMarkerEventListener
            map.knMarkerEventListener = listener
        }
        val current = cameras.associateBy { it.id }
        markers.keys.filter { it !in current }.forEach { id -> markers.remove(id)?.let(map::removeMarker) }
        current.forEach { (id, camera) ->
            val coordinate = camera.point.toKatec()
            val existing = markers[id]
            if (existing != null) {
                if (existing.coordinate.x != coordinate.x || existing.coordinate.y != coordinate.y) existing.coordinate = coordinate
            } else {
                val marker = KNMapMarker(coordinate).apply {
                    icon = cameraIcon
                    info = CameraInfo(id)
                    priority = 500
                    useSingleTapped = true
                    useDoubleTapped = false
                    useLongPressed = false
                    isVisibleCalloutBubble = false
                }
                markers[id] = marker
                map.addMarker(marker)
            }
        }
    }

    /** Standalone map: show a fresh platform GPS fix without inventing a vehicle location. */
    fun updateVehicle(point: GeoPoint?, heading: Float?): Boolean {
        if (released) return false
        // The SDK installs this object while binding the map. The surface retries until
        // it is available; creating an unattached KNMapUserLocation would never render.
        val userLocation = map.userLocation ?: return false
        userLocation.isVisible = point != null
        userLocation.isVisibleGuideLine = false
        if (point == null) return true
        if (userLocation.icon !== vehicleIcon) userLocation.icon = vehicleIcon
        userLocation.coordinate = point.toKatec()
        userLocation.angle = heading?.takeIf { it.isFinite() }?.let { ((it % 360f) + 360f) % 360f } ?: 0f
        userLocation.isFlat = true
        userLocation.useSingleTapped = false
        userLocation.useDoubleTapped = false
        userLocation.useLongPressed = false
        return true
    }

    /** Guidance owns map-matched position/heading; only fresh-location visibility is ours. */
    fun setVehicleVisible(visible: Boolean): Boolean {
        if (released) return false
        val userLocation = map.userLocation ?: return false
        userLocation.isVisible = visible
        return true
    }

    fun release() {
        if (released) return
        released = true
        onCameraClick = {}
        markers.values.forEach(map::removeMarker)
        markers.clear()
        if (map.knMarkerEventListener === listener) map.knMarkerEventListener = previousListener
        previousListener = null
    }
}

/** Use before initWithGuidance so SDK state changes use this image instead of replacing it. */
internal fun installIoniqGuidanceImage(view: KNNaviView) {
    val resource = R.drawable.ioniq5_navigation
    view.mapComponent.setCustomCarImages(
        RectF(0f, 0f, 80f, 120f), FloatPoint(.5f, .5f),
        mutableListOf(resource, resource), mutableListOf(resource, resource),
    )
}

private fun GeoPoint.toKatec(): FloatPoint = KNSDK.convertWGS84ToKATEC(longitude, latitude).let {
    FloatPoint(it.x.toFloat(), it.y.toFloat())
}

/** Deterministic UI icon, rendered at native map density; no image service or external asset. */
private fun cameraBitmap(density: Float): Bitmap {
    val size = (46f * density).roundToInt().coerceAtLeast(46)
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        canvas.scale(size / 46f, size / 46f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        canvas.drawCircle(23f, 23f, 22f, paint)
        paint.color = Color.rgb(40, 100, 215)
        canvas.drawCircle(23f, 23f, 19.5f, paint)
        paint.color = Color.WHITE
        canvas.drawRoundRect(RectF(11f, 16f, 29f, 30f), 3f, 3f, paint)
        val lens = Path().apply { moveTo(30f, 20f); lineTo(36f, 16f); lineTo(36f, 30f); lineTo(30f, 26f); close() }
        canvas.drawPath(lens, paint)
    }
}
