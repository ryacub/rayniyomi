package eu.kanade.presentation.util

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.util.system.isTabletUi
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Compatibility adapter for the single binary tablet decision.
 *
 * New code reads [currentWindowWidthClass] instead, which distinguishes compact,
 * medium, and expanded widths.
 */
@Composable
@ReadOnlyComposable
fun isTabletUi(): Boolean {
    return LocalConfiguration.current.isTabletUi()
}

/**
 * The current [WindowWidthClass], measured from the window and adjusted by the
 * user's tablet UI preference.
 *
 * This reads the composition-local configuration, so it needs no Activity and
 * recomposes on resize, fold, and rotation.
 */
@Composable
fun currentWindowWidthClass(): WindowWidthClass {
    val configuration = LocalConfiguration.current
    val tabletUiMode by remember { Injekt.get<UiPreferences>().tabletUiMode() }.collectAsState()
    return windowWidthClassFor(
        widthDp = configuration.screenWidthDp,
        isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
        tabletUiMode = tabletUiMode,
    )
}
