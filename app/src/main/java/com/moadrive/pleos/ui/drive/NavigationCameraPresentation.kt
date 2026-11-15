package com.moadrive.pleos.ui.drive

import com.moadrive.pleos.navigation.NavigationSectionPhase
import com.moadrive.pleos.navigation.NavigationSpeedCamera

/** EXIT is an upcoming endpoint, never proof that the driver has already completed the section. */
internal fun cameraStatusTitle(camera: NavigationSpeedCamera): String = when (camera.sectionPhase) {
    NavigationSectionPhase.APPROACH -> "구간 단속 접근"
    NavigationSectionPhase.ACTIVE -> "구간 단속 중"
    NavigationSectionPhase.EXIT -> "구간 단속 종료 지점"
    null -> camera.label
}

/** Both regular and compact cards reserve this line so distance never replaces the section average. */
internal fun cameraAverageLine(camera: NavigationSpeedCamera): String? {
    if (camera.sectionPhase == null) return null
    val average = camera.sectionAverageKmh?.takeIf { it in 1..300 }
    return if (average != null) "평균 $average km/h · 제한 ${camera.limitKmh}"
    else if (camera.sectionPhase == NavigationSectionPhase.ACTIVE) "평균 확인 중 · 제한 ${camera.limitKmh} km/h"
    else null
}
