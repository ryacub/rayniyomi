package eu.kanade.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tachiyomi.presentation.core.components.AdaptiveSheet as AdaptiveSheetContent

@RunWith(AndroidJUnit4::class)
class AdaptiveSheetAndroidTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tabletScrimWaitsForFadeAndDismissesOnce() {
        var dismissCount = 0
        composeRule.setContent {
            MaterialTheme {
                AdaptiveSheetContent(
                    isTabletUi = true,
                    enableSwipeDismiss = true,
                    onDismissRequest = { dismissCount++ },
                ) {
                    Text("Sheet content")
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false

        composeRule.onRoot().performTouchInput { click(Offset(8f, centerY)) }
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.runOnIdle { assertEquals("Dismiss callback before fade ends", 0, dismissCount) }

        composeRule.onRoot().performTouchInput { click(Offset(8f, centerY)) }
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.runOnIdle { assertEquals("Dismiss callback after fade ends", 1, dismissCount) }
    }
}
