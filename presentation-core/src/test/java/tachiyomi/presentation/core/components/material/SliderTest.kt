package tachiyomi.presentation.core.components.material

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val TAG = "slider"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SliderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun valueChangeFromOutsideMovesTheSlider() {
        var value by mutableIntStateOf(2)
        composeRule.setContent {
            Slider(
                value = value,
                onValueChange = {},
                modifier = Modifier.testTag(TAG),
                valueRange = 0..10,
            )
        }

        value = 7
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(7f, 0f..10f, 9),
            ),
        )
    }

    @Test
    fun setProgressReportsARoundedInt() {
        val received = mutableListOf<Int>()
        composeRule.setContent {
            Slider(
                value = 2,
                onValueChange = { received += it },
                modifier = Modifier.testTag(TAG),
                valueRange = 0..10,
            )
        }

        composeRule.onNodeWithTag(TAG).performSemanticsAction(SemanticsActions.SetProgress) { it(4.4f) }

        assertEquals(listOf(4), received)
    }
}
