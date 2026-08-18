package eu.kanade.presentation.util

import eu.kanade.domain.ui.model.TabletUiMode
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class WindowWidthClassTest {

    @ParameterizedTest
    @CsvSource(
        "0, Compact",
        "599, Compact",
        "600, Medium",
        "839, Medium",
        "840, Expanded",
        "1280, Expanded",
    )
    fun `maps a measured width to its window width class`(
        widthDp: Int,
        expected: WindowWidthClass,
    ) {
        windowWidthClassFor(widthDp) shouldBe expected
    }

    @ParameterizedTest
    @CsvSource(
        // AUTOMATIC always returns the measured class.
        "400, true, AUTOMATIC, Compact",
        "400, false, AUTOMATIC, Compact",
        "700, true, AUTOMATIC, Medium",
        "700, false, AUTOMATIC, Medium",
        "1000, true, AUTOMATIC, Expanded",
        "1000, false, AUTOMATIC, Expanded",
        // ALWAYS raises Compact to Medium but never lowers Expanded.
        "400, true, ALWAYS, Medium",
        "400, false, ALWAYS, Medium",
        "700, true, ALWAYS, Medium",
        "700, false, ALWAYS, Medium",
        "1000, true, ALWAYS, Expanded",
        "1000, false, ALWAYS, Expanded",
        // LANDSCAPE raises only when the device is in landscape.
        "400, true, LANDSCAPE, Medium",
        "700, true, LANDSCAPE, Medium",
        "1000, true, LANDSCAPE, Expanded",
        "400, false, LANDSCAPE, Compact",
        "700, false, LANDSCAPE, Compact",
        "1000, false, LANDSCAPE, Compact",
        // NEVER forces Compact in both orientations.
        "400, true, NEVER, Compact",
        "400, false, NEVER, Compact",
        "700, true, NEVER, Compact",
        "700, false, NEVER, Compact",
        "1000, true, NEVER, Compact",
        "1000, false, NEVER, Compact",
    )
    fun `applies the tablet UI mode to the measured width class`(
        widthDp: Int,
        isLandscape: Boolean,
        tabletUiMode: TabletUiMode,
        expected: WindowWidthClass,
    ) {
        windowWidthClassFor(widthDp, isLandscape, tabletUiMode) shouldBe expected
    }

    @ParameterizedTest
    @CsvSource(
        "400, false, AUTOMATIC, BottomBar",
        "400, false, ALWAYS, Rail",
        "400, true, LANDSCAPE, Rail",
        "1000, false, AUTOMATIC, Drawer",
        "1000, true, NEVER, BottomBar",
    )
    fun `maps the window width class to Home navigation layout`(
        widthDp: Int,
        isLandscape: Boolean,
        tabletUiMode: TabletUiMode,
        expected: HomeNavigationLayout,
    ) {
        homeNavigationLayoutFor(
            windowWidthClassFor(widthDp, isLandscape, tabletUiMode),
        ) shouldBe expected
    }

    @ParameterizedTest
    @CsvSource(
        "400, false, AUTOMATIC, SinglePane",
        "700, false, AUTOMATIC, SinglePane",
        "1000, false, AUTOMATIC, TwoPane",
        "400, false, ALWAYS, SinglePane",
        "400, true, LANDSCAPE, SinglePane",
        "1000, true, LANDSCAPE, TwoPane",
        "1000, false, NEVER, SinglePane",
    )
    fun `maps the window width class to Settings navigation layout`(
        widthDp: Int,
        isLandscape: Boolean,
        tabletUiMode: TabletUiMode,
        expected: SettingsNavigationLayout,
    ) {
        settingsNavigationLayoutFor(
            windowWidthClassFor(widthDp, isLandscape, tabletUiMode),
        ) shouldBe expected
    }
}
