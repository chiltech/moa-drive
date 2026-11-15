package com.moadrive.pleos.navigation

import android.car.drivingstate.CarUxRestrictions

/** Feature-specific UX restrictions; basic menu/media controls are separate from setup forms. */
data class DrivingPolicy(
    val connected: Boolean = false,
    val searchAllowed: Boolean = false,
    val settingsAllowed: Boolean = false,
    val maxItems: Int = 6,
    val setupAllowed: Boolean = false,
    val videoAllowed: Boolean = false,
    val textInputAllowed: Boolean = false,
) {
    companion object {
        /** A null flag snapshot means there is no authoritative connection, so fail closed. */
        fun fromRestrictions(flags: Int?, maxContentItems: Int = 6): DrivingPolicy {
            if (flags == null) return DrivingPolicy(maxItems = 0)
            val searchBlocked = CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD or
                CarUxRestrictions.UX_RESTRICTIONS_NO_FILTERING
            val limited = flags and CarUxRestrictions.UX_RESTRICTIONS_LIMIT_CONTENT != 0
            val items = if (limited) maxContentItems.coerceIn(0, 6) else 6
            return DrivingPolicy(
                connected = true,
                searchAllowed = flags and searchBlocked == 0 && items > 0,
                settingsAllowed = true,
                maxItems = items,
                setupAllowed = flags and CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP == 0,
                videoAllowed = flags and CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO == 0,
                textInputAllowed = flags and CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD == 0,
            )
        }
    }
}
