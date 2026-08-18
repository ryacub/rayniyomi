package eu.kanade.tachiyomi.ui.setting

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.screen.player.PlayerSettingsMainScreen
import eu.kanade.presentation.more.settings.screen.player.PlayerSettingsPlayerScreen
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.SettingsNavigationLayout
import eu.kanade.presentation.util.currentWindowWidthClass
import eu.kanade.presentation.util.settingsNavigationLayoutFor
import eu.kanade.presentation.util.settingsStackForSinglePane
import eu.kanade.presentation.util.settingsStackForTwoPane
import tachiyomi.presentation.core.components.TwoPanelBox

class PlayerSettingsScreen(private val mainSettings: Boolean) : Screen() {
    @Composable
    override fun Content() {
        val parentNavigator = LocalNavigator.currentOrThrow
        val navigationLayout = settingsNavigationLayoutFor(currentWindowWidthClass())
        val twoPane = navigationLayout == SettingsNavigationLayout.TwoPane
        val mainScreen = remember(mainSettings) { PlayerSettingsMainScreen(mainSettings) }
        var previousNavigationLayout by remember { mutableStateOf<SettingsNavigationLayout?>(null) }
        Navigator(
            screen = if (twoPane) PlayerSettingsPlayerScreen else mainScreen,
        ) { navigator ->
            LaunchedEffect(navigationLayout) {
                val previousLayout = previousNavigationLayout
                if (twoPane && previousLayout != SettingsNavigationLayout.TwoPane) {
                    val screens = settingsStackForTwoPane(
                        screens = navigator.items,
                        isMainScreen = { it::class == mainScreen::class },
                        defaultScreen = PlayerSettingsPlayerScreen,
                    )
                    if (screens != navigator.items) {
                        navigator.replaceAll(screens)
                    }
                } else if (!twoPane &&
                    (previousLayout == null || previousLayout == SettingsNavigationLayout.TwoPane)
                ) {
                    val screens = settingsStackForSinglePane(
                        screens = navigator.items,
                        isMainScreen = { it::class == mainScreen::class },
                        mainScreen = mainScreen,
                    )
                    if (screens != navigator.items) {
                        navigator.replaceAll(screens)
                    }
                }
                previousNavigationLayout = navigationLayout
            }
            if (twoPane) {
                val insets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
                TwoPanelBox(
                    modifier = Modifier
                        .windowInsetsPadding(insets)
                        .consumeWindowInsets(insets),
                    startContent = {
                        CompositionLocalProvider(LocalBackPress provides parentNavigator::pop) {
                            mainScreen.Content(twoPane = true)
                        }
                    },
                    endContent = { DefaultNavigatorScreenTransition(navigator = navigator) },
                )
            } else {
                val pop: () -> Unit = {
                    if (navigator.canPop) {
                        navigator.pop()
                    } else {
                        parentNavigator.pop()
                    }
                }
                CompositionLocalProvider(LocalBackPress provides pop) {
                    DefaultNavigatorScreenTransition(navigator = navigator)
                }
            }
        }
    }
}
