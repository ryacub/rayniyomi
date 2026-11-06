package eu.kanade.tachiyomi.ui.more

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.NavigatorDisposeBehavior
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.di.appGraph
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoreTabSettingsEntryAndroidTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fastDoubleTapOnSettings_opensSettingsOnce() {
        lateinit var rootNavigator: Navigator
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalMetroViewModelFactory provides appGraph.metroViewModelFactory) {
                    Navigator(
                        screen = HomeHostScreen,
                        disposeBehavior = NavigatorDisposeBehavior(
                            disposeNestedNavigators = false,
                            disposeSteps = true,
                        ),
                    ) { navigator ->
                        rootNavigator = navigator
                        DefaultNavigatorScreenTransition(navigator = navigator)
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithText(SETTINGS).performClick()
        composeRule.onNodeWithText(SETTINGS).performClick()
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertEquals(2, rootNavigator.size)

        composeRule.onNodeWithContentDescription(NAVIGATE_UP).performClick()
        composeRule.waitForIdle()

        assertEquals(1, rootNavigator.size)
    }

    private object HomeHostScreen : Screen() {
        @Composable
        override fun Content() {
            val navigator = LocalNavigator.currentOrThrow
            TabNavigator(MoreTab) {
                CompositionLocalProvider(LocalNavigator provides navigator) {
                    CurrentTab()
                }
            }
        }
    }

    private companion object {
        const val SETTINGS = "Settings"
        const val NAVIGATE_UP = "Navigate up"
    }
}
