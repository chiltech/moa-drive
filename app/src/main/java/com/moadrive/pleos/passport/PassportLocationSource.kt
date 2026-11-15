package com.moadrive.pleos.passport

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper

/** Only started after the recording service has acquired the location foreground type. */
class PassportLocationSource(
    context: Context,
    private val onFix: (PassportFix) -> Unit,
    private val onAvailability: (String?) -> Unit,
) : LocationListener {
    private val manager = context.getSystemService(LocationManager::class.java)
    var listening = false
        private set

    @SuppressLint("MissingPermission") // Service checks fine permission and foreground eligibility first.
    fun start() {
        if (listening) return
        listening = true
        var registered = false
        try {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { provider ->
                if (provider in manager.allProviders) {
                    manager.requestLocationUpdates(provider, 1_000L, 0f, this, Looper.getMainLooper())
                    registered = true
                }
            }
            onAvailability(if (!registered || !manager.isLocationEnabled) "차량의 위치 서비스를 켜 주세요" else null)
        } catch (_: SecurityException) {
            stop()
            onAvailability("위치 권한을 확인한 뒤 앱을 다시 열어 주세요")
        } catch (_: IllegalArgumentException) {
            stop()
            onAvailability("차량의 위치 신호를 사용할 수 없어요")
        }
    }

    fun stop() {
        listening = false
        manager.removeUpdates(this)
    }

    override fun onLocationChanged(location: Location) {
        if (!listening || !location.hasAccuracy()) return
        onFix(PassportFix(location.latitude, location.longitude, location.accuracy.toDouble(),
            location.time, location.elapsedRealtimeNanos / 1_000_000))
    }

    override fun onProviderEnabled(provider: String) { if (listening) onAvailability(null) }
    override fun onProviderDisabled(provider: String) {
        if (listening && !manager.isLocationEnabled) onAvailability("차량의 위치 서비스를 켜 주세요")
    }
    @Deprecated("Legacy Android callback")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
}
