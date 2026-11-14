package com.moagauge.pleos.ui

import org.junit.Assert.*
import org.junit.Test

class MoaWindowScaleTest {
    @Test fun pleosSplitKeepsTouchTargetsLargeAndAUsableColumn() {
        for (width in listOf(813f, 828f, 840f)) {
            val scale = moaWindowScale(width, 1210f)
            assertTrue("48dp touch target should render at least 60 host dp", 48f * scale >= 60f)
            assertTrue("Leave a full-width service column", width / scale >= 600f)
            assertTrue("Shared detail layout stays stacked", width / scale < 1000f)
        }
    }
    @Test fun expandedPleosPreservesTabletLayout() {
        val scale = moaWindowScale(1682f, 1210f)
        assertEquals(1682f / 1100f, scale, .001f)
        assertTrue(1682f / scale >= 1000f)
    }
    @Test fun narrowAndShortWindowsDoNotGetForcedToTabletDensity() {
        assertEquals(1f, moaWindowScale(480f, 800f), .001f)
        assertEquals(1f, moaWindowScale(828f, 480f), .001f)
    }
}
