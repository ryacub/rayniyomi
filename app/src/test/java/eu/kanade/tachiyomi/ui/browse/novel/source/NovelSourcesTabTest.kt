package eu.kanade.tachiyomi.ui.browse.novel.source

import eu.kanade.tachiyomi.feature.novel.LightNovelPluginLauncher
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NovelSourcesTabTest {

    @Test
    fun `launchLibrarySafely returns true when launcher succeeds`() {
        val launcher = mockk<LightNovelPluginLauncher>()
        every { launcher.launchLibrary() } returns true

        val result = launchLibrarySafely(launcher)

        assertTrue(result)
    }

    @Test
    fun `launchLibrarySafely returns false when launcher returns false`() {
        val launcher = mockk<LightNovelPluginLauncher>()
        every { launcher.launchLibrary() } returns false

        val result = launchLibrarySafely(launcher)

        assertFalse(result)
    }

    @Test
    fun `launchLibrarySafely returns false when launcher throws`() {
        val launcher = mockk<LightNovelPluginLauncher>()
        every { launcher.launchLibrary() } throws RuntimeException("boom")

        val result = launchLibrarySafely(launcher)

        assertFalse(result)
    }
}
