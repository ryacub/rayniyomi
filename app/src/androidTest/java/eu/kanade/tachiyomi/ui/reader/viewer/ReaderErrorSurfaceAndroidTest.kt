package eu.kanade.tachiyomi.ui.reader.viewer

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

@RunWith(AndroidJUnit4::class)
class ReaderErrorSurfaceAndroidTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun heading_and_retry_are_always_rendered() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            MaterialTheme {
                ReaderErrorSurface(
                    state = ReaderErrorUiState(showOpenInWebView = false),
                    actions = ReaderErrorUiActions(
                        onRetry = {},
                        onOpenInWebView = {},
                    ),
                )
            }
        }

        composeRule.onNodeWithText(context.stringResource(MR.strings.decode_image_error))
            .assertExists()
            .assert(
                SemanticsMatcher("is heading") { node ->
                    node.config.getOrNull(SemanticsProperties.Heading) != null
                },
            )
        composeRule.onNodeWithText(context.stringResource(MR.strings.action_retry)).assertExists()
        composeRule.onNodeWithText(context.stringResource(MR.strings.action_open_in_web_view)).assertDoesNotExist()
    }

    @Test
    fun open_in_web_action_visibility_and_callbacks_follow_state() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var retried = false
        var opened = false
        composeRule.setContent {
            MaterialTheme {
                ReaderErrorSurface(
                    state = ReaderErrorUiState(showOpenInWebView = true),
                    actions = ReaderErrorUiActions(
                        onRetry = { retried = true },
                        onOpenInWebView = { opened = true },
                    ),
                )
            }
        }

        composeRule.onNodeWithText(context.stringResource(MR.strings.action_retry)).performClick()
        composeRule.onNodeWithText(context.stringResource(MR.strings.action_open_in_web_view)).performClick()

        assertTrue(retried)
        assertTrue(opened)
    }

    @Test
    fun semantics_contract_exposes_heading_and_discoverable_actions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            MaterialTheme {
                ReaderErrorSurface(
                    state = ReaderErrorUiState(showOpenInWebView = true),
                    actions = ReaderErrorUiActions(
                        onRetry = {},
                        onOpenInWebView = {},
                    ),
                )
            }
        }

        composeRule.onNodeWithText(context.stringResource(MR.strings.decode_image_error))
            .assert(
                SemanticsMatcher("is heading") { node ->
                    node.config.getOrNull(SemanticsProperties.Heading) != null
                },
            )

        composeRule.onNodeWithText(context.stringResource(MR.strings.action_retry))
            .assert(hasClickAction())

        composeRule.onNodeWithText(context.stringResource(MR.strings.action_open_in_web_view))
            .assert(hasClickAction())

        composeRule.onAllNodesWithText(context.stringResource(MR.strings.decode_image_error))
            .assertCountEquals(1)
    }
}
