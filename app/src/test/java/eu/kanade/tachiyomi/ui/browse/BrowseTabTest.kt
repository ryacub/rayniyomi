package eu.kanade.tachiyomi.ui.browse

import eu.kanade.tachiyomi.feature.novel.IncompatibleReason
import eu.kanade.tachiyomi.feature.novel.LightNovelPluginUiState
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
}
