package eu.kanade.presentation.util

import androidx.window.core.layout.WindowSizeClass
import eu.kanade.domain.ui.model.TabletUiMode

/**
 * Structural width buckets. The bounds come from androidx.window WindowSizeClass,
 * so they track the platform definition.
 *
 * This is the single source of truth for width-based layout decisions. The
 * declaration order defines the natural ordering that the [TabletUiMode]
 * mapping relies on to raise a class without ever lowering it.
 *
 * Migration contract: [eu.kanade.tachiyomi.util.system.isTabletUi] stays as a
 * compatibility adapter for call sites that still expect one binary tablet
 * decision. New code reads a [WindowWidthClass] instead. The adapter is removed
 * only once every consumer is migrated.
 */
enum class WindowWidthClass {
    Compact,
    Medium,
    Expanded,
}

/** The navigation chrome that Home uses for a [WindowWidthClass]. */
enum class HomeNavigationLayout {
    BottomBar,
    Rail,
    Drawer,
}

fun homeNavigationLayoutFor(windowWidthClass: WindowWidthClass): HomeNavigationLayout = when (windowWidthClass) {
    WindowWidthClass.Compact -> HomeNavigationLayout.BottomBar
    WindowWidthClass.Medium -> HomeNavigationLayout.Rail
    WindowWidthClass.Expanded -> HomeNavigationLayout.Drawer
}

/** The navigation layout that Settings uses for a [WindowWidthClass]. */
enum class SettingsNavigationLayout {
    SinglePane,
    TwoPane,
}

fun settingsNavigationLayoutFor(
    windowWidthClass: WindowWidthClass,
): SettingsNavigationLayout = when (windowWidthClass) {
    WindowWidthClass.Compact,
    WindowWidthClass.Medium,
    -> SettingsNavigationLayout.SinglePane
    WindowWidthClass.Expanded -> SettingsNavigationLayout.TwoPane
}

/** Lower bound of [WindowWidthClass.Medium], in dp. */
const val MEDIUM_WIDTH_BREAKPOINT_DP = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND

/** Lower bound of [WindowWidthClass.Expanded], in dp. */
const val EXPANDED_WIDTH_BREAKPOINT_DP = WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND

/** Maps a measured window width to its class, ignoring any user preference. */
fun windowWidthClassFor(widthDp: Int): WindowWidthClass = when {
    widthDp < MEDIUM_WIDTH_BREAKPOINT_DP -> WindowWidthClass.Compact
    widthDp < EXPANDED_WIDTH_BREAKPOINT_DP -> WindowWidthClass.Medium
    else -> WindowWidthClass.Expanded
}

/**
 * Maps a measured window width to its class, applying [tabletUiMode].
 *
 * A mode that forces a wider layout raises the measured class but never lowers
 * it, so an expanded window keeps its extra space.
 */
fun windowWidthClassFor(
    widthDp: Int,
    isLandscape: Boolean,
    tabletUiMode: TabletUiMode,
): WindowWidthClass {
    val measured = windowWidthClassFor(widthDp)
    return when (tabletUiMode) {
        TabletUiMode.AUTOMATIC -> measured
        TabletUiMode.ALWAYS -> maxOf(measured, WindowWidthClass.Medium)
        TabletUiMode.LANDSCAPE -> if (isLandscape) {
            maxOf(measured, WindowWidthClass.Medium)
        } else {
            WindowWidthClass.Compact
        }
        TabletUiMode.NEVER -> WindowWidthClass.Compact
    }
}
