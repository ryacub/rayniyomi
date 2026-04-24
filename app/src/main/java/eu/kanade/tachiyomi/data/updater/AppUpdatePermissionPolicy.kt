package eu.kanade.tachiyomi.data.updater

internal object AppUpdatePermissionPolicy {

    enum class StartMode {
        START_WITH_NOTIFICATIONS,
        REQUEST_NOTIFICATIONS_PERMISSION,
        START_WITH_IN_APP_FALLBACK,
    }

    fun initialStartMode(
        sdkInt: Int,
        notificationPermissionGranted: Boolean,
    ): StartMode {
        return if (sdkInt < 33 || notificationPermissionGranted) {
            StartMode.START_WITH_NOTIFICATIONS
        } else {
            StartMode.REQUEST_NOTIFICATIONS_PERMISSION
        }
    }

    fun startModeAfterPermissionResult(granted: Boolean): StartMode {
        return if (granted) {
            StartMode.START_WITH_NOTIFICATIONS
        } else {
            StartMode.START_WITH_IN_APP_FALLBACK
        }
    }
}
