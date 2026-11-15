package com.moadrive.pleos.vehicle

import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.moadrive.pleos.trips.TripRuntime

/** UI attaches to the service-owned stream; leaving this Activity does not end a trip. */
class VehicleViewModel(application: Application) : AndroidViewModel(application) {
    val trips = TripRuntime.get(application)
    val state = trips.telemetry
    private var visible = false
    private var granted = emptySet<String>()
    private var pendingPermissionRefresh = false

    fun missingPermissions(): Array<String> = READ_PERMISSIONS.filter {
        ContextCompat.checkSelfPermission(getApplication(), it) != PackageManager.PERMISSION_GRANTED
    }.toTypedArray()

    fun setVisible(active: Boolean) {
        val currentGrants = READ_PERMISSIONS.toSet() - missingPermissions().toSet()
        val permissionsChanged = granted != currentGrants
        pendingPermissionRefresh = pendingPermissionRefresh || permissionsChanged
        if (visible == active && !permissionsChanged && (!active || !pendingPermissionRefresh)) return
        visible = active
        granted = currentGrants
        // Permission results may arrive at STARTED, before the Activity is RESUMED.
        trips.setUiVisible(active, active && pendingPermissionRefresh)
        if (active) pendingPermissionRefresh = false
    }

    override fun onCleared() { trips.setUiVisible(false) }

    private companion object {
        val READ_PERMISSIONS = arrayOf("android.car.permission.CAR_SPEED", "android.car.permission.CAR_ENERGY")
    }
}
