package com.moagauge.pleos.navigation

data class NavigationSafetyOptions(
    val flashOnOverspeed: Boolean = true,
    val warningDistanceMeters: Int = 1_000,
    val includeMobileCameras: Boolean = true,
) {
    val effectiveWarningDistanceMeters: Int get() = if (warningDistanceMeters == 500) 500 else 1_000
}

/** Instantaneous overspeed still matters inside a section; its average can independently exceed the limit. */
fun isNavigationOverspeed(camera: NavigationSpeedCamera, currentSpeedKmh: Int?): Boolean {
    if (camera.limitKmh !in 1..200) return false
    val currentOver = currentSpeedKmh?.takeIf { it in 0..300 }?.let { it > camera.limitKmh } == true
    val averageOver = camera.sectionPhase == NavigationSectionPhase.ACTIVE &&
        camera.sectionAverageKmh?.takeIf { it in 1..300 }?.let { it > camera.limitKmh } == true
    return currentOver || averageOver
}

fun selectNavigationSpeedWarning(warnings: List<NavigationSpeedCamera>): NavigationSpeedCamera? =
    warnings.firstOrNull { it.sectionPhase == NavigationSectionPhase.ACTIVE }
        ?: warnings.minByOrNull { it.distanceMeters ?: Int.MAX_VALUE }

fun navigationSectionPhase(code: String): NavigationSectionPhase? = when {
    code.endsWith("SpeedViolationSection") || code.endsWith("SpeedViolationSectionHalf") -> NavigationSectionPhase.ACTIVE
    code.contains("SpeedViolationSectionIn") -> NavigationSectionPhase.APPROACH
    code.contains("SpeedViolationSectionOut") -> NavigationSectionPhase.EXIT
    else -> null
}

fun isSpeedEnforcementCode(code: String): Boolean = code.contains("SpeedViolation")

/** Shared range/mobile gate for visual information and the SDK's generated safety voice/alert requests. */
fun NavigationSafetyOptions.allowsSpeedWarning(code: String, distanceMeters: Int?): Boolean {
    if (!isSpeedEnforcementCode(code)) return true
    if (navigationSectionPhase(code) == NavigationSectionPhase.ACTIVE) return true
    if (!includeMobileCameras && code.contains("Movable")) return false
    return distanceMeters != null && distanceMeters in 0..effectiveWarningDistanceMeters
}
