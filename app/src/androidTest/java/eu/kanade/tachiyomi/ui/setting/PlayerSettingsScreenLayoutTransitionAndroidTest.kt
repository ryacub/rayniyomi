package eu.kanade.tachiyomi.ui.setting

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.util.Screen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerSettingsScreenLayoutTransitionAndroidTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun layoutTransition_providesBackPressToMainScreenDuringExit() {
        val singlePaneConfiguration = Configuration().apply {
            orientation = Configuration.ORIENTATION_PORTRAIT
            screenWidthDp = 411
            screenHeightDp = 891
        }
        val twoPaneConfiguration = Configuration(singlePaneConfiguration).apply {
            orientation = Configuration.ORIENTATION_LANDSCAPE
            screenWidthDp = 840
            screenHeightDp = 411
        }
        var configuration by mutableStateOf(singlePaneConfiguration)

        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalConfiguration provides configuration,
                ) {
                    Navigator(BackPressScreen) {
                        PlayerSettingsScreen(mainSettings = false).Content()
                    }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { configuration = twoPaneConfiguration }
        composeRule.mainClock.advanceTimeBy(1)
        composeRule.onAllNodesWithText("Gestures").assertCountEquals(2)
        composeRule.onAllNodesWithText("Gestures").onFirst().performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Enable Volume and Brightness Gestures").assertExists()
    }

    private object BackPressScreen : Screen() {
        @Composable
        override fun Content() {
            Text(text = "Parent")
        }
    }
}
