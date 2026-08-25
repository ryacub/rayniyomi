package eu.kanade.tachiyomi.data.download.manga

import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.translation.TranslationStorageLayout
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MangaDownloadCacheChapterDirTest {

    private fun fileNode(name: String, isDirectory: Boolean, isFile: Boolean = !isDirectory): UniFile =
        mockk {
            every { this@mockk.name } returns name
            every { this@mockk.isDirectory } returns isDirectory
            every { this@mockk.isFile } returns isFile
        }

    @Test
    fun `sidecar folder is not a chapter`() {
        val sidecar = fileNode(TranslationStorageLayout.sidecarDirName("Chapter 1.cbz"), isDirectory = true)
        assertNull(chapterDirNameOrNull(sidecar))
    }

    @Test
    fun `folder of images is a chapter`() {
        val dir = fileNode("Chapter 1", isDirectory = true)
        assertEquals("Chapter 1", chapterDirNameOrNull(dir))
    }

    @Test
    fun `cbz file is a chapter without its extension`() {
        val cbz = fileNode("Chapter 1.cbz", isDirectory = false, isFile = true)
        assertEquals("Chapter 1", chapterDirNameOrNull(cbz))
    }

    @Test
    fun `loose chapter directory whose name ends in translated is still a chapter`() {
        val loose = fileNode("Chapter 1_translated", isDirectory = true)
        assertEquals("Chapter 1_translated", chapterDirNameOrNull(loose))
    }

    @Test
    fun `temp download folder is ignored`() {
        val tmp = fileNode("Chapter 1" + MangaDownloader.TMP_DIR_SUFFIX, isDirectory = true)
        assertNull(chapterDirNameOrNull(tmp))
    }

    @Test
    fun `other files are ignored`() {
        val other = fileNode("cover.jpg", isDirectory = false, isFile = true)
        assertNull(chapterDirNameOrNull(other))
    }
}
