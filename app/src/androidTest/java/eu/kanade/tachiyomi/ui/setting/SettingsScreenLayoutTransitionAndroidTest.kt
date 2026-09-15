package eu.kanade.tachiyomi.ui.setting

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.screen.SettingsMainScreen
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenLayoutTransitionAndroidTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun twoPaneContent_providesBackPressToMainScreenDuringExit() {
        composeRule.setContent {
            MaterialTheme {
                Navigator(BackPressScreen) { parentNavigator ->
                    Navigator(SettingsMainScreen) { navigator ->
                        LaunchedEffect(Unit) {
                            navigator.push(DetailScreen)
                        }
                        SettingsTwoPaneContent(
                            parentNavigator = parentNavigator,
                            navigator = navigator,
                            startContent = { backPressContent() },
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Back press unavailable").assertExists()
    }

    private object BackPressScreen : Screen() {
        @Composable
        override fun Content() {
            Text(text = "Parent")
        }
    }

    private object DetailScreen : Screen() {
        @Composable
        override fun Content() {
            Text(
                text = if (LocalBackPress.current == null) {
                    "Back press unavailable"
                } else {
                    "Back press available"
                },
            )
        }
    }
}

@Composable
private fun backPressContent() {
    LocalBackPress.currentOrThrow
    Text(text = "Back press available")
}
