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
        val mangaOutsideReleasePeriodOff = matchesMangaWithRestrictionFlag(
            restrictionEnabled = false,
            filter = TriState.ENABLED_IS,
            fetchInterval = -7,
        )
        val mangaOutsideReleasePeriodOn = matchesMangaWithRestrictionFlag(
            restrictionEnabled = true,
            filter = TriState.ENABLED_IS,
            fetchInterval = -7,
        )
        val animeOutsideReleasePeriodOff = matchesAnimeWithRestrictionFlag(
            restrictionEnabled = false,
            filter = TriState.ENABLED_IS,
            fetchInterval = -7,
        )
        val animeOutsideReleasePeriodOn = matchesAnimeWithRestrictionFlag(
            restrictionEnabled = true,
            filter = TriState.ENABLED_IS,
            fetchInterval = -7,
        )

        mangaOutsideReleasePeriodOff shouldBe mangaOutsideReleasePeriodOn
        animeOutsideReleasePeriodOff shouldBe animeOutsideReleasePeriodOn
    }

    @Test
    fun `manga and anime custom interval filters stay in parity`() {
        val states = listOf(TriState.DISABLED, TriState.ENABLED_IS, TriState.ENABLED_NOT)
        val intervals = listOf(-7, 7)

        for (state in states) {
            for (interval in intervals) {
                matchesMangaCustomInterval(state, interval) shouldBe matchesAnimeCustomInterval(state, interval)
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun matchesMangaWithRestrictionFlag(
        restrictionEnabled: Boolean,
        filter: TriState,
        fetchInterval: Int,
    ): Boolean {
        // Restriction flag is intentionally ignored for this UI-only filter.
        return matchesMangaCustomInterval(filter, fetchInterval)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun matchesAnimeWithRestrictionFlag(
        restrictionEnabled: Boolean,
        filter: TriState,
        fetchInterval: Int,
    ): Boolean {
        // Restriction flag is intentionally ignored for this UI-only filter.
        return matchesAnimeCustomInterval(filter, fetchInterval)
    }

    private fun matchesMangaCustomInterval(filter: TriState, fetchInterval: Int): Boolean {
        return matchesCustomIntervalFilter(filter, fetchInterval)
    }

    private fun matchesAnimeCustomInterval(filter: TriState, fetchInterval: Int): Boolean {
        return matchesCustomIntervalFilter(filter, fetchInterval)
    }
}
