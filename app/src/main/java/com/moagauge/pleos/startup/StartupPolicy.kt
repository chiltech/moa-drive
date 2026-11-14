package com.moagauge.pleos.startup

enum class StartupDestination { HOME, NAVIGATION }
enum class StartupBootAction { NONE, SHOW_NOTIFICATION }

/** These decisions never treat a boot broadcast as an ignition/boarding signal. */
object StartupPolicy {
    /** Use only in the UI's saved-state initializer; an existing/restored page takes precedence. */
    fun initialDestination(showIntro: Boolean) = if (showIntro) StartupDestination.HOME else StartupDestination.NAVIGATION

    fun bootAction(action: String?, enabled: Boolean, notificationsAllowed: Boolean,
        currentBoot: Int?, handledBoot: Int?): StartupBootAction {
        if (action != "android.intent.action.BOOT_COMPLETED" || !enabled || !notificationsAllowed) return StartupBootAction.NONE
        if (currentBoot != null && currentBoot == handledBoot) return StartupBootAction.NONE
        return StartupBootAction.SHOW_NOTIFICATION
    }
}
