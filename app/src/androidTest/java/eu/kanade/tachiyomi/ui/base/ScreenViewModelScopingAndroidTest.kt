package eu.kanade.tachiyomi.ui.base

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.presentation.util.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Characterization guard for R1077. A ViewModel built inside a Screen belongs to that Screen's
 * own store, and a pop clears only the popped Screen's store. It passes on the Voyager
 * ScreenModel base too, so it is not a failing-first test.
 */
@RunWith(AndroidJUnit4::class)
class ScreenViewModelScopingAndroidTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun resetProbes() {
        ProbeViewModel.constructed.clear()
        ProbeViewModel.cleared.clear()
    }

    @Test
    fun pushedScreensGetSeparateViewModels() {
        lateinit var navigator: Navigator
        composeRule.setContent {
            MaterialTheme {
                Navigator(ProbeScreen(1)) {
                    navigator = it
                    DefaultNavigatorScreenTransition(it)
                }
            }
        }
        composeRule.runOnIdle { navigator.push(ProbeScreen(2)) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("probe 2").assertExists()
        assertEquals(listOf(1, 2), ProbeViewModel.constructed.map { it.id })
        assertNotSame(ProbeViewModel.constructed[0], ProbeViewModel.constructed[1])
    }

    @Test
    fun popClearsOnlyThePoppedScreensViewModel() {
        lateinit var navigator: Navigator
        composeRule.setContent {
            MaterialTheme {
                Navigator(ProbeScreen(1)) {
                    navigator = it
                    DefaultNavigatorScreenTransition(it)
                }
            }
        }
        composeRule.runOnIdle { navigator.push(ProbeScreen(2)) }
        composeRule.waitForIdle()
        composeRule.runOnIdle { navigator.pop() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("probe 1").assertExists()
        assertEquals(listOf(2), ProbeViewModel.cleared)
    }

    @Test
    fun tabsKeepOneViewModelEachAcrossSwitches() {
        lateinit var tabNavigator: TabNavigator
        composeRule.setContent {
            MaterialTheme {
                TabNavigator(ProbeTab(1)) {
                    tabNavigator = LocalTabNavigator.current
                    CurrentTab()
                }
            }
        }
        composeRule.runOnIdle { tabNavigator.current = ProbeTab(2) }
        composeRule.waitForIdle()
        composeRule.runOnIdle { tabNavigator.current = ProbeTab(1) }
        composeRule.waitForIdle()

        assertEquals(1, ProbeViewModel.constructed.count { it.id == 1 })
        assertEquals(1, ProbeViewModel.constructed.count { it.id == 2 })
    }

    @Test
    fun exitingScreenBuildsNoSecondViewModelDuringPop() {
        lateinit var navigator: Navigator
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MaterialTheme {
                Navigator(ProbeScreen(1)) {
                    navigator = it
                    DefaultNavigatorScreenTransition(it)
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.runOnIdle { navigator.push(ProbeScreen(2)) }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.runOnIdle { navigator.pop() }
        composeRule.mainClock.advanceTimeByFrame()

        // R1077 plan audit: record the count. The base commit sets the expected value.
        val builtForSecond = ProbeViewModel.constructed.count { it.id == 2 }
        assertEquals(1, builtForSecond)
    }

    private class ProbeViewModel(val id: Int) : ViewModel() {
        init {
            constructed += this
        }

        override fun onCleared() {
            cleared += id
        }

        companion object {
            val constructed = mutableListOf<ProbeViewModel>()
            val cleared = mutableListOf<Int>()
        }
    }

    private class ProbeScreen(private val id: Int) : Screen() {
        @Composable
        override fun Content() {
            viewModel { ProbeViewModel(id) }
            Text(text = "probe $id")
        }
    }

    private class ProbeTab(private val id: Int) : Tab {
        override val key: String = "probe-tab-$id"

        override val options: TabOptions
            @Composable
            get() = TabOptions(index = id.toUShort(), title = "tab $id")

        @Composable
        override fun Content() {
            viewModel { ProbeViewModel(id) }
            Text(text = "tab $id")
        }
    }
}
