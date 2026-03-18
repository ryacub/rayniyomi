package eu.kanade.tachiyomi.ui.browse

import eu.kanade.tachiyomi.feature.novel.IncompatibleReason
import eu.kanade.tachiyomi.feature.novel.LightNovelPluginUiState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BrowseTabTest {

    @Test
    fun `should show novel tab when plugin state is ready`() {
        assertTrue(shouldShowNovelSourcesTab(LightNovelPluginUiState.Ready))
    }

    @Test
    fun `should hide novel tab when plugin state is disabled`() {
        assertFalse(shouldShowNovelSourcesTab(LightNovelPluginUiState.Disabled))
    }

    @Test
    fun `should hide novel tab when plugin state is missing`() {
        assertFalse(shouldShowNovelSourcesTab(LightNovelPluginUiState.Missing))
    }

    @Test
    fun `should hide novel tab when plugin state is incompatible`() {
        assertFalse(
            shouldShowNovelSourcesTab(
                LightNovelPluginUiState.Incompatible(IncompatibleReason.API_MISMATCH),
            ),
        )
    }

    @Test
    fun `anime pages map to anime target`() {
        assertEquals(BrowseSearchTarget.ANIME, browseSearchTargetForPage(0))
        assertEquals(BrowseSearchTarget.ANIME, browseSearchTargetForPage(2))
        assertEquals(BrowseSearchTarget.ANIME, browseSearchTargetForPage(4))
    }

    @Test
    fun `manga pages map to manga target`() {
        assertEquals(BrowseSearchTarget.MANGA, browseSearchTargetForPage(1))
        assertEquals(BrowseSearchTarget.MANGA, browseSearchTargetForPage(3))
        assertEquals(BrowseSearchTarget.MANGA, browseSearchTargetForPage(5))
    }

    @Test
    fun `unknown pages keep target unchanged for novel and absent states`() {
        assertEquals(
            BrowseSearchTarget.MANGA,
            updateBrowseSearchTarget(BrowseSearchTarget.MANGA, 6),
        )
        assertEquals(
            BrowseSearchTarget.ANIME,
            updateBrowseSearchTarget(BrowseSearchTarget.ANIME, 6),
        )
        assertEquals(
            BrowseSearchTarget.ANIME,
            updateBrowseSearchTarget(BrowseSearchTarget.ANIME, 999),
        )
    }

    @Test
    fun `unknown reselect target defaults to anime`() {
        assertEquals(BrowseSearchTarget.ANIME, resolveBrowseReselectTarget(BrowseSearchTarget.UNKNOWN))
    }

    @Test
    fun `resolver defaults to anime when unknown`() {
        val resolver = BrowseReselectTargetResolver()

        assertEquals(BrowseSearchTarget.ANIME, resolver.resolvedTarget())
    }

    @Test
    fun `resolver preserves last non novel target`() {
        val resolver = BrowseReselectTargetResolver()

        resolver.updateForPage(1)
        resolver.updateForPage(6)

        assertEquals(BrowseSearchTarget.MANGA, resolver.resolvedTarget())
    }
}
