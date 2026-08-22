package eu.kanade.presentation.more.settings.widget

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.StateRestorationTester
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditTextPreferenceWidgetAndroidTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun secretValue_visibilityToggleShowsAndHidesEnteredText() = runComposeUiTest {
        setContent {
            MaterialTheme {
                EditTextPreferenceWidget(
                    title = "BYOK key",
                    subtitle = "No API key configured",
                    icon = null,
                    value = "",
                    isSecret = true,
                    onConfirm = { true },
                )
            }
        }

        onNodeWithText("BYOK key").performClick()
        onNode(hasSetTextAction()).performTextInput("replacement-secret")
        onNode(hasSetTextAction()).assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Password),
        )
        val maskedText = onNode(hasSetTextAction()).laidOutText()
        onNode(hasSetTextAction()).assertIsFocused()

        onNodeWithContentDescription("Show text").performClick()

        onNode(hasSetTextAction()).assertIsFocused()
        onNode(hasSetTextAction()).assertTextEquals("replacement-secret")
        assertNotEquals("replacement-secret", maskedText)
        assertEquals("replacement-secret", onNode(hasSetTextAction()).laidOutText())
        onNode(hasSetTextAction()).assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Password),
        )
        onNodeWithContentDescription("Hide text").performClick()

        onNode(hasSetTextAction()).assertIsFocused()
        assertEquals(maskedText, onNode(hasSetTextAction()).laidOutText())
        onNodeWithContentDescription("Show text").assertExists()
        onNode(hasSetTextAction()).assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Password),
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun nonSecretValue_keepsPrefillAndClearAction() = runComposeUiTest {
        setContent {
            MaterialTheme {
                EditTextPreferenceWidget(
                    title = "Model",
                    subtitle = null,
                    icon = null,
                    value = "plain-value",
                    onConfirm = { true },
                )
            }
        }

        onNodeWithText("Model").performClick()
        onNode(hasSetTextAction()).assertTextEquals("plain-value")

        onNodeWithContentDescription("Clear text").performClick()

        onNode(hasSetTextAction()).assertTextEquals("")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun secretValue_isNotPrefilledOrRestored() = runComposeUiTest {
        val restorationTester = StateRestorationTester(this)
        restorationTester.setContent {
            MaterialTheme {
                EditTextPreferenceWidget(
                    title = "BYOK key",
                    subtitle = "API key is set",
                    icon = null,
                    value = "stored-secret",
                    isSecret = true,
                    onConfirm = { true },
                )
            }
        }

        onNodeWithText("BYOK key").performClick()
        onNode(hasSetTextAction()).assertTextEquals("")
        onNode(hasSetTextAction()).performTextInput("replacement-secret")

        restorationTester.emulateSaveAndRestore()

        onNodeWithText("BYOK key").performClick()
        onNode(hasSetTextAction()).assertTextEquals("")
        onNodeWithText("stored-secret", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText("replacement-secret", useUnmergedTree = true).assertDoesNotExist()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.laidOutText(): String {
        val results = mutableListOf<TextLayoutResult>()
        val action = fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action
        check(requireNotNull(action)(results))
        return results.single().layoutInput.text.text
    }
}
