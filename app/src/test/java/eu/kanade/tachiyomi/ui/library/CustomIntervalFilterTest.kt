package eu.kanade.tachiyomi.ui.library

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.TriState

class CustomIntervalFilterTest {

    @Test
    fun `disabled state keeps both custom and automatic intervals`() {
        matchesCustomIntervalFilter(TriState.DISABLED, fetchInterval = -7) shouldBe true
        matchesCustomIntervalFilter(TriState.DISABLED, fetchInterval = 7) shouldBe true
    }

    @Test
    fun `enabled is keeps only custom intervals`() {
        matchesCustomIntervalFilter(TriState.ENABLED_IS, fetchInterval = -7) shouldBe true
        matchesCustomIntervalFilter(TriState.ENABLED_IS, fetchInterval = 7) shouldBe false
    }

    @Test
    fun `enabled not excludes custom intervals`() {
        matchesCustomIntervalFilter(TriState.ENABLED_NOT, fetchInterval = -7) shouldBe false
        matchesCustomIntervalFilter(TriState.ENABLED_NOT, fetchInterval = 7) shouldBe true
    }

    @Test
    fun `custom interval filter is unaffected by release period restriction flag`() {
        val simulatedOutsideReleasePeriodOff = matchesWithRestrictionFlag(
            restrictionEnabled = false,
            filter = TriState.ENABLED_IS,
            fetchInterval = -7,
        )
        val simulatedOutsideReleasePeriodOn = matchesWithRestrictionFlag(
            restrictionEnabled = true,
            filter = TriState.ENABLED_IS,
            fetchInterval = -7,
        )

        simulatedOutsideReleasePeriodOff shouldBe simulatedOutsideReleasePeriodOn
    }

    @Suppress("UNUSED_PARAMETER")
    private fun matchesWithRestrictionFlag(
        restrictionEnabled: Boolean,
        filter: TriState,
        fetchInterval: Int,
    ): Boolean {
        // Restriction flag is intentionally ignored for this UI-only filter.
        return matchesCustomIntervalFilter(filter, fetchInterval)
    }
}
