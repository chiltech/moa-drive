package com.moadrive.pleos.startup

import org.junit.Assert.*
import org.junit.Test

class StartupPolicyTest {
    @Test fun introControlsOnlyTheInitialDestination() {
        assertEquals(StartupDestination.HOME, StartupPolicy.initialDestination(showIntro = true))
        assertEquals(StartupDestination.NAVIGATION, StartupPolicy.initialDestination(showIntro = false))
    }

    @Test fun disabledStartupNeverPostsAnOffer() {
        assertEquals(StartupBootAction.NONE,
            StartupPolicy.bootAction("android.intent.action.BOOT_COMPLETED", false, true, 5, 4))
    }

    @Test fun bootOfferNeedsNotificationPermission() {
        assertEquals(StartupBootAction.NONE,
            StartupPolicy.bootAction("android.intent.action.BOOT_COMPLETED", true, false, 5, 4))
    }

    @Test fun optedInNewBootOffersUserInitiatedOpening() {
        assertEquals(StartupBootAction.SHOW_NOTIFICATION,
            StartupPolicy.bootAction("android.intent.action.BOOT_COMPLETED", true, true, 5, 4))
    }

    @Test fun sameBootDoesNotRepostAfterOpeningOrDismissing() {
        assertEquals(StartupBootAction.NONE,
            StartupPolicy.bootAction("android.intent.action.BOOT_COMPLETED", true, true, 5, 5))
    }

    @Test fun packageUpdatesAndWakeBroadcastsDoNotPretendToBeBoarding() {
        for (action in listOf(null, "android.intent.action.MY_PACKAGE_REPLACED", "android.intent.action.USER_PRESENT", "android.intent.action.SCREEN_ON")) {
            assertEquals(StartupBootAction.NONE, StartupPolicy.bootAction(action, true, true, 5, 4))
        }
    }

    @Test fun unavailableBootCounterDoesNotPreventAValidSystemBootOffer() {
        assertEquals(StartupBootAction.SHOW_NOTIFICATION,
            StartupPolicy.bootAction("android.intent.action.BOOT_COMPLETED", true, true, null, null))
    }
}
