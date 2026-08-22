package eu.kanade.presentation.more.settings.widget

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.StateRestorationTester
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditTextPreferenceWidgetAndroidTest {

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
}
