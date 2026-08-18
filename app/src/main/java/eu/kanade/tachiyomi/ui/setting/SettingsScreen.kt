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
import eu.kanade.presentation.more.settings.screen.SettingsAppearanceScreen
import eu.kanade.presentation.more.settings.screen.SettingsDataScreen
import eu.kanade.presentation.more.settings.screen.SettingsMainScreen
import eu.kanade.presentation.more.settings.screen.SettingsTrackingScreen
import eu.kanade.presentation.more.settings.screen.about.AboutScreen
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.SettingsNavigationLayout
import eu.kanade.presentation.util.currentWindowWidthClass
import eu.kanade.presentation.util.settingsNavigationLayoutFor
import eu.kanade.presentation.util.settingsStackForSinglePane
import eu.kanade.presentation.util.settingsStackForTwoPane
import tachiyomi.presentation.core.components.TwoPanelBox

class SettingsScreen(
    private val destination: Int? = null,
) : Screen() {

    constructor(destination: Destination) : this(destination.id)

    @Composable
    override fun Content() {
        val parentNavigator = LocalNavigator.currentOrThrow
        val navigationLayout = settingsNavigationLayoutFor(currentWindowWidthClass())
        val twoPane = navigationLayout == SettingsNavigationLayout.TwoPane
        var previousNavigationLayout by remember { mutableStateOf<SettingsNavigationLayout?>(null) }
        Navigator(
            screen = when (destination) {
                Destination.About.id -> AboutScreen
                Destination.DataAndStorage.id -> SettingsDataScreen
                Destination.Tracking.id -> SettingsTrackingScreen
                else -> if (twoPane) SettingsAppearanceScreen else SettingsMainScreen
            },
        ) { navigator ->
            LaunchedEffect(navigationLayout) {
                val previousLayout = previousNavigationLayout
                if (twoPane && previousLayout != SettingsNavigationLayout.TwoPane) {
                    val screens = settingsStackForTwoPane(
                        screens = navigator.items,
                        isMainScreen = { it::class == SettingsMainScreen::class },
                        defaultScreen = SettingsAppearanceScreen,
                    )
                    if (screens != navigator.items) {
                        navigator.replaceAll(screens)
                    }
                } else if (!twoPane && destination == null &&
                    (previousLayout == null || previousLayout == SettingsNavigationLayout.TwoPane)
                ) {
                    val screens = settingsStackForSinglePane(
                        screens = navigator.items,
                        isMainScreen = { it::class == SettingsMainScreen::class },
                        mainScreen = SettingsMainScreen,
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
                            SettingsMainScreen.Content(twoPane = true)
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

    sealed class Destination(val id: Int) {
        data object About : Destination(0)
        data object DataAndStorage : Destination(1)
        data object Tracking : Destination(2)
    }
}
