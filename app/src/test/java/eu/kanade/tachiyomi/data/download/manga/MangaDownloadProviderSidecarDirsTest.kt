package eu.kanade.tachiyomi.data.download.manga

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.translation.TranslationStorageLayout
import eu.kanade.tachiyomi.source.MangaSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.storage.service.StorageManager

class MangaDownloadProviderSidecarDirsTest {

    private val source = mockk<MangaSource>()
    private val manga = Manga.create().copy(id = 1L, title = "Test Manga")

    private lateinit var provider: MangaDownloadProvider

    @BeforeEach
    fun setUp() {
        // A real provider keeps the real chapter-dir-name composition; only the
        // filesystem lookups are stubbed.
        provider = spyk(MangaDownloadProvider(mockk(relaxed = true), mockk<StorageManager>(relaxed = true)))
    }

    @Test
    fun `sidecar name is composed from scanlator and chapter name`() {
        val mangaDir = mockk<UniFile>("mangaDir") {
            every { findFile(any<String>()) } returns null
        }
        val sidecar = mockk<UniFile>("sidecar")
        val chapter = Chapter.create().copy(name = "Chapter 1", scanlator = "Scan")
        every { provider.findMangaDir(manga.title, source) } returns mangaDir
        every { mangaDir.findFile(TranslationStorageLayout.sidecarDirName("Scan_Chapter 1.cbz")) } returns sidecar

        val result = provider.findTranslationSidecarDirs(listOf(chapter), manga, source)

        assertEquals(listOf(sidecar), result)
    }

    @Test
    fun `missing manga dir returns an empty list`() {
        val chapter = Chapter.create().copy(name = "Chapter 1", scanlator = null)
        every { provider.findMangaDir(manga.title, source) } returns null

        val result = provider.findTranslationSidecarDirs(listOf(chapter), manga, source)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `absent sidecar is filtered out`() {
        val mangaDir = mockk<UniFile>("mangaDir") {
            every { findFile(any<String>()) } returns null
        }
        val chapter = Chapter.create().copy(name = "Chapter 1", scanlator = null)
        every { provider.findMangaDir(manga.title, source) } returns mangaDir
        every {
            mangaDir.findFile(TranslationStorageLayout.sidecarDirName("Chapter 1.cbz"))
        } returns null

        val result = provider.findTranslationSidecarDirs(listOf(chapter), manga, source)

        assertTrue(result.isEmpty())
    }
}
