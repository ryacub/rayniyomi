package eu.kanade.tachiyomi.data.updater

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppUpdatePermissionPolicyTest {

    @Test
    fun `initialStartMode returns normal path below Android 13`() {
        val mode = AppUpdatePermissionPolicy.initialStartMode(
            sdkInt = 32,
            notificationPermissionGranted = false,
        )

        assertEquals(AppUpdatePermissionPolicy.StartMode.START_WITH_NOTIFICATIONS, mode)
    }

    @Test
    fun `initialStartMode requests permission on Android 13+ when denied`() {
        val mode = AppUpdatePermissionPolicy.initialStartMode(
            sdkInt = 33,
            notificationPermissionGranted = false,
        )

        assertEquals(AppUpdatePermissionPolicy.StartMode.REQUEST_NOTIFICATIONS_PERMISSION, mode)
    }

    @Test
    fun `startModeAfterPermissionResult returns fallback when denied`() {
        val mode = AppUpdatePermissionPolicy.startModeAfterPermissionResult(granted = false)

        assertEquals(AppUpdatePermissionPolicy.StartMode.START_WITH_IN_APP_FALLBACK, mode)
    }
}
