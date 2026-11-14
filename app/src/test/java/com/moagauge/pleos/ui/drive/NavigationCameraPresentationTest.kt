package com.moagauge.pleos.ui.drive

import com.moagauge.pleos.navigation.NavigationSectionPhase
import com.moagauge.pleos.navigation.NavigationSpeedCamera
import org.junit.Assert.*
import org.junit.Test

class NavigationCameraPresentationTest {
    @Test fun approachingExitNeverClaimsTheSectionHasAlreadyEnded() {
        val camera = NavigationSpeedCamera(80, 400, "구간 단속", sectionPhase = NavigationSectionPhase.EXIT)
        assertEquals("구간 단속 종료 지점", cameraStatusTitle(camera))
    }

    @Test fun activeSectionKeepsAverageAndLimitTogetherEvenWhenDistanceIsPresent() {
        val camera = NavigationSpeedCamera(80, 400, "구간 단속", 88, sectionPhase = NavigationSectionPhase.ACTIVE)
        assertEquals("평균 88 km/h · 제한 80", cameraAverageLine(camera))
        assertEquals(cameraAverageLine(camera), cameraAverageLine(camera.copy(distanceMeters = null)))
    }

    @Test fun absentOrInvalidSectionAverageStaysUnknown() {
        listOf(null, 0, -1, 301).forEach { average ->
            val camera = NavigationSpeedCamera(80, null, "구간 단속", average, sectionPhase = NavigationSectionPhase.ACTIVE)
            assertEquals("평균 확인 중 · 제한 80 km/h", cameraAverageLine(camera))
        }
        assertNull(cameraAverageLine(NavigationSpeedCamera(60, 300, "과속 단속")))
    }
}
