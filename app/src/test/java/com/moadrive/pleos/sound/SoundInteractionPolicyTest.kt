package com.moadrive.pleos.sound

import com.moadrive.pleos.vehicle.VehicleTelemetry
import org.junit.Assert.*
import org.junit.Test

class SoundInteractionPolicyTest {
    private val moving = VehicleTelemetry(active = true, speedKmh = 201, gear = "D", ignitionOn = true)

    @Test fun validDrivingTelemetryAllowsMediaControlAndDoesNotStopPreview() {
        for (vehicle in listOf(moving, moving.copy(gear = "R", speedKmh = 5), moving.copy(speedKmh = 0))) {
            assertTrue(vehicle.canStartSound())
            assertTrue(vehicle.canPreviewSound())
            assertFalse(vehicle.shouldStopSoundPreview())
        }
    }

    @Test fun invalidActiveVehicleSignalsDenyPlaybackAndStopPreview() {
        for (vehicle in listOf(moving.copy(speedKmh = null), moving.copy(speedKmh = -1),
            moving.copy(speedKmh = 361), moving.copy(gear = null), moving.copy(gear = "?"),
            moving.copy(ignitionOn = false), moving.copy(speedKmh = 0, gear = "P", ignitionOn = false))) {
            assertFalse("$vehicle may not start", vehicle.canStartSound())
            assertFalse("$vehicle may not preview", vehicle.canPreviewSound())
            assertTrue("$vehicle should stop a preview", vehicle.shouldStopSoundPreview())
        }
    }

    @Test fun disconnectedTelemetryCannotStartLiveAudioButKeepsStandalonePreview() {
        val offline = VehicleTelemetry()
        assertFalse(offline.canStartSound())
        assertTrue(offline.canPreviewSound())
        assertFalse(offline.shouldStopSoundPreview())
    }

    @Test fun parkedPlaybackBehaviorRemainsAvailable() {
        val parked = moving.copy(gear = "P", speedKmh = 0)
        assertTrue(parked.canStartSound())
        assertTrue(parked.canPreviewSound())
        assertFalse(parked.shouldStopSoundPreview())
    }
}
