package eu.kanade.tachiyomi.ui.player.controls.components.sheets

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScreenshotSheetTest {

    @Test
    fun `capture failure reports error without invoking downstream action`() = runTest {
        var successCalls = 0
        var failureCalls = 0

        captureScreenshotOrNotify(
            takeScreenshot = { null },
            onSuccess = { successCalls++ },
            onFailure = { failureCalls++ },
        )

        assertEquals(0, successCalls)
        assertEquals(1, failureCalls)
    }
}
