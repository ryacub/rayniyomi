package tachiyomi.presentation.core.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EmptyScreenFaceTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun facesVaryOutsidePreview() {
        collectFaces(inspection = false).size shouldBeGreaterThanOrEqualTo 2
    }

    @Test
    fun faceIsFixedInPreview() {
        collectFaces(inspection = true) shouldHaveSize 1
    }

    private fun collectFaces(inspection: Boolean): Set<String> {
        var round by mutableIntStateOf(0)
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides inspection) {
                MaterialTheme {
                    key(round) { EmptyScreen(message = "m") }
                }
            }
        }
        return (1..ROUNDS).mapTo(mutableSetOf()) {
            round = it
            composeRule.waitForIdle()
            composeRule
                .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))[0]
                .fetchSemanticsNode()
                .config[SemanticsProperties.Text]
                .first()
                .text
        }
    }

    private companion object {
        const val ROUNDS = 40
    }
}
