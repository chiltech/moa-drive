package com.moadrive.pleos.navigation

import org.junit.Assert.*
import org.junit.Test

class NavigationSafetyOptionsTest {
    private val fixed = "KNSafetyCode_SpeedViolationCamera"
    private val mobile = "KNSafetyCode_MovableSpeedViolationCamera"
    @Test fun distanceLimitsAreInclusiveAndInvalidPreferenceFallsBackToOneKilometer() {
        val short = NavigationSafetyOptions(warningDistanceMeters=500)
        assertNotNull(speedCameraWarning(fixed,60,500,false,options=short))
        assertNull(speedCameraWarning(fixed,60,501,false,options=short))
        assertNotNull(speedCameraWarning(fixed,60,1000,false,options=NavigationSafetyOptions(warningDistanceMeters=-1)))
        assertNull(speedCameraWarning(fixed,60,1001,false))
    }
    @Test fun mobilePreferenceDoesNotSuppressFixedCamera() {
        val options=NavigationSafetyOptions(includeMobileCameras=false)
        assertNull(speedCameraWarning(mobile,60,100,false,options=options))
        assertNotNull(speedCameraWarning(fixed,60,100,false,options=options))
        assertTrue(speedCameraWarning(mobile,60,100,false)!!.isMobile)
    }
    @Test fun activeSectionSurvivesDistanceFilterWithoutInventingItsEndDistance() {
        val warning=speedCameraWarning("KNSafetyCode_SpeedViolationSection",100,8000,false,82,
            NavigationSafetyOptions(warningDistanceMeters=500,includeMobileCameras=false))!!
        assertEquals(NavigationSectionPhase.ACTIVE,warning.sectionPhase)
        assertNull(warning.distanceMeters)
        assertEquals(82,warning.sectionAverageKmh)
        assertNull(speedCameraWarning("KNSafetyCode_SpeedViolationSection",100,null,true,82))
    }
    @Test fun sectionEndpointsAreApproachAndExitPointsNotCompletedClaims() {
        assertEquals(NavigationSectionPhase.APPROACH,speedCameraWarning("KNSafetyCode_SpeedViolationSectionInCamera",100,400,false)!!.sectionPhase)
        assertEquals(NavigationSectionPhase.EXIT,speedCameraWarning("KNSafetyCode_SpeedViolationSectionOutCamera",100,400,false)!!.sectionPhase)
        assertEquals("구간 단속 종료 지점",speedCameraWarning("KNSafetyCode_SpeedViolationSectionOutCamera",100,400,false)!!.label)
    }
    @Test fun overspeedRequiresValidCurrentSpeedOrActiveSectionAverage() {
        val fixed=NavigationSpeedCamera(60,100,"과속")
        assertFalse(isNavigationOverspeed(fixed,null));assertFalse(isNavigationOverspeed(fixed,-1))
        assertFalse(isNavigationOverspeed(fixed,400));assertFalse(isNavigationOverspeed(fixed,60))
        assertTrue(isNavigationOverspeed(fixed,61))
        val section=NavigationSpeedCamera(100,null,"구간",90,sectionPhase=NavigationSectionPhase.ACTIVE)
        assertFalse(isNavigationOverspeed(section,90))
        assertTrue(isNavigationOverspeed(section,120))
        assertTrue(isNavigationOverspeed(section.copy(sectionAverageKmh=101),20))
        assertTrue(isNavigationOverspeed(section.copy(sectionAverageKmh=null),120))
        assertFalse(isNavigationOverspeed(section.copy(sectionAverageKmh=null),null))
    }
    @Test fun activeSectionCannotBeDisplacedByNearerFixedCamera() {
        val ordinary=NavigationSpeedCamera(60,10,"과속")
        val section=NavigationSpeedCamera(100,null,"구간",82,sectionPhase=NavigationSectionPhase.ACTIVE)
        assertEquals(section,selectNavigationSpeedWarning(listOf(ordinary,section)))
        assertEquals(ordinary,selectNavigationSpeedWarning(listOf(ordinary.copy(distanceMeters=100),ordinary)))
    }
}
