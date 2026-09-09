package eu.kanade.tachiyomi.data.download.anime

import com.hippo.unifile.UniFile
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnimeDownloadCacheDirectoryScanTest {

    @Test
    fun `a directory whose name disappears mid-scan is skipped instead of crashing`() {
        val vanishing = mockk<UniFile>()
        every { vanishing.isDirectory } returns true
        every { vanishing.name } returnsMany listOf("Gone", null)

        val stable = mockk<UniFile>()
        every { stable.isDirectory } returns true
        every { stable.name } returns "Kept"

        val root = mockk<UniFile>()
        every { root.listFiles() } returns arrayOf(vanishing, stable)

        val result = namedChildDirectories(root)

        assertEquals(listOf("Gone", "Kept"), result.map { it.second })
    }

    @Test
    fun `a null parent has no child directories`() {
        val result = namedChildDirectories(null)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `a child directory with a blank name is dropped`() {
        val blank = mockk<UniFile>()
        every { blank.isDirectory } returns true
        every { blank.name } returns "   "

        val root = mockk<UniFile>()
        every { root.listFiles() } returns arrayOf(blank)

        val result = namedChildDirectories(root)

        assertTrue(result.isEmpty())
    }
}
