package com.moagauge.pleos.navigation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Uses platform GPS on AAOS; no Google Play Services dependency or invented origin. */
class NavigationLocation(context: Context) : LocationListener {
    private val context = context.applicationContext
    private val manager = context.getSystemService(LocationManager::class.java)
    private val mutableLocation = MutableStateFlow<Location?>(null)
    val location = mutableLocation.asStateFlow()
    private var listening = false

    fun start() {
        if (listening || !hasPrecisePermission()) return
        listening = true
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { provider ->
            try {
                if (manager.isProviderEnabled(provider)) {
                    manager.getLastKnownLocation(provider)?.let(::onLocationChanged)
                    manager.requestLocationUpdates(provider, 1_000L, 0f, this, Looper.getMainLooper())
                }
            } catch (_: SecurityException) { mutableLocation.value = null }
              catch (_: IllegalArgumentException) { /* This provider is not installed on the vehicle. */ }
        }
    }

    fun stop() { manager.removeUpdates(this); listening = false; mutableLocation.value = null }

    fun hasPrecisePermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun currentPoint(): GeoPoint? = mutableLocation.value?.takeIf(::isFresh)?.let { GeoPoint(it.latitude, it.longitude) }

    override fun onLocationChanged(location: Location) {
        if (!isFresh(location)) return
        val current = mutableLocation.value
        if (current == null || location.elapsedRealtimeNanos >= current.elapsedRealtimeNanos) mutableLocation.value = Location(location)
    }

    private fun isFresh(location: Location): Boolean {
        val age = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
        return age in 0..30_000_000_000L && location.hasAccuracy() && location.accuracy <= 100f &&
            location.latitude.isFinite() && location.longitude.isFinite() &&
            location.latitude in -90.0..90.0 && location.longitude in -180.0..180.0
    }

    override fun onProviderDisabled(provider: String) { if (mutableLocation.value?.provider == provider) mutableLocation.value = null }
    override fun onProviderEnabled(provider: String) = Unit
    @Deprecated("Legacy Android callback")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
}
