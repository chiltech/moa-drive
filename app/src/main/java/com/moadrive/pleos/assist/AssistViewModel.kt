package com.moadrive.pleos.assist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moadrive.pleos.navigation.NavigationLocation
import com.moadrive.pleos.vehicle.VehicleTelemetry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AssistViewModel(application: Application) : AndroidViewModel(application) {
    private val location = NavigationLocation(application)
    private val session = AssistSession(viewModelScope)
    val state = session.state
    private var vehicle = VehicleTelemetry()
    private var allowed = false
    private var ticker: Job? = null

    fun setVisible(active: Boolean) {
        session.setVisible(active)
        if (active) {
            location.start()
            if (ticker?.isActive != true) ticker = viewModelScope.launch {
                while (isActive) {
                    location.start()
                    session.updateContext(vehicle, location.currentPoint(), allowed)
                    delay(1_000)
                }
            }
        } else {
            ticker?.cancel(); ticker = null
            location.stop()
        }
    }

    fun updateVehicle(telemetry: VehicleTelemetry, browsingAllowed: Boolean) {
        vehicle = telemetry; allowed = browsingAllowed
        session.updateContext(vehicle, location.currentPoint(), allowed)
    }

    fun simulateRoutine(scenario: RoutineScenario) = session.simulateRoutine(scenario)
    fun dismissRoutine() = session.dismissRoutine()
    fun dismissIncident() = session.dismissIncident()
    fun playIncidentDemo() = session.playIncidentDemo()
    fun armIncident() {
        location.start()
        session.updateContext(vehicle, location.currentPoint(), allowed)
        session.armIncident()
    }
    fun resetDemo() = session.resetDemo()
    fun setOptions(routines: Boolean, incidents: Boolean) = session.setOptions(routines, incidents)
    override fun onCleared() { setVisible(false) }
}
