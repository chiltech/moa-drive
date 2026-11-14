package com.moagauge.pleos.navigation

import org.junit.Assert.*
import org.junit.Test

class NavigationGuidanceTest {
    @Test fun unknownInstructionDoesNotPretendToBeStraight() {
        assertEquals(NavigationTurn.OTHER, maneuverPresentation("FutureSdkCode").first)
    }
    @Test fun roadBranchesRetainDirectionAndInfrastructure() {
        assertEquals(NavigationTurn.LEFT_FORK, maneuverPresentation("KNRGCode_LeftOutHighway").first)
        assertEquals("왼쪽 고속도로 출구", maneuverPresentation("KNRGCode_LeftOutHighway").second)
        assertEquals("우측 지하차도 옆길", maneuverPresentation("KNRGCode_RightUnderPathSide").second)
        assertEquals("회전교차로 9시 방향", maneuverPresentation("KNRGCode_RoundaboutDirection_9").second)
    }
    @Test fun warningRejectsPassedBehindAndNonSpeedCamera() {
        assertNull(speedCameraWarning("KNSafetyCode_SpeedViolationCamera",60,100,true))
        assertNull(speedCameraWarning("KNSafetyCode_SpeedViolationCamera",60,-1,false))
        assertNull(speedCameraWarning("KNSafetyCode_SignalViolationCamera",60,100,false))
        assertNull(speedCameraWarning("KNSafetyCode_TrafficCollectionCamera",60,100,false))
        assertNull(speedCameraWarning("KNSafetyCode_SpeedViolationCamera",0,100,false))
    }
    @Test fun sectionWithoutPublicRemainingDistanceRetainsLimitButDoesNotInventDistance() {
        val warning = speedCameraWarning("KNSafetyCode_SpeedViolationSection",100,null,false,sectionAverageKmh=82)
        assertNotNull(warning)
        assertNull(warning!!.distanceMeters)
        assertEquals(82, warning.sectionAverageKmh)
        assertEquals("구간 단속", warning.label)
    }
    @Test fun missingCameraDistanceIsNotRenderedAsZero() {
        assertNull(speedCameraWarning("KNSafetyCode_SpeedViolationCamera",80,null,false))
        assertEquals(0, speedCameraWarning("KNSafetyCode_SpeedViolationCamera",80,0,false)!!.distanceMeters)
    }
    @Test fun distanceKeepsUnknownSeparateFromReachedPoint() {
        assertEquals("—", navigationDistance(null))
        assertEquals("0 m", navigationDistance(0))
        assertEquals("1.2 km", navigationDistance(1234))
    }
}
