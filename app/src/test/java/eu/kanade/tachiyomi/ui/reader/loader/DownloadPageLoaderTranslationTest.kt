package eu.kanade.tachiyomi.ui.reader.loader

import android.app.Application
import android.net.Uri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.database.models.manga.ChapterImpl
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadProvider
import eu.kanade.tachiyomi.data.download.manga.model.DownloadedChapterPage
import eu.kanade.tachiyomi.data.translation.TranslationPreferences
import eu.kanade.tachiyomi.data.translation.TranslationStorageManager
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.entries.manga.model.Manga
import java.io.ByteArrayInputStream
import java.io.IOException

class DownloadPageLoaderTranslationTest {

    private val application = mockk<Application>()
    private val downloadManager = mockk<MangaDownloadManager>()
    private val downloadProvider = mockk<MangaDownloadProvider>()
    private val source = mockk<MangaSource>()
    private val readerPreferences = mockk<ReaderPreferences>()
    private val translationPreferences = mockk<TranslationPreferences>()
    private val translationStorageManager = mockk<TranslationStorageManager>()
    private val showTranslatedPages = mockk<Preference<Boolean>>()
    private val targetLanguage = mockk<Preference<String>>()
    private val manga = Manga.create().copy(id = 1L, source = 2L, title = "Test Manga")
    private val chapter = ReaderChapter(
        ChapterImpl(id = 3L).apply {
            manga_id = manga.id
            url = "chapter-1"
            name = "Chapter 1"
        },
    )

    @Test
    fun `translated page URI replaces the original URI when the preference is on`() = runTest {
        val originalBytes = byteArrayOf(1, 2, 3)
        val translatedBytes = byteArrayOf(9, 8, 7)
        val translatedUri = mockk<Uri>()
        val translatedFile = mockk<UniFile> {
            every { uri } returns translatedUri
        }
        stubLoader(showTranslated = true, originalBytes = originalBytes)
        every {
            translationStorageManager.getTranslatedPageFiles(
                chapter.chapter.name,
                chapter.chapter.scanlator,
                manga.title,
                source,
                "es",
            )
        } returns mapOf(0 to translatedFile)
        every { application.contentResolver.openInputStream(translatedUri) } returns
            ByteArrayInputStream(translatedBytes)

        val page = buildLoader().getPages().single()

        assertArrayEquals(translatedBytes, page.stream!!.invoke().readBytes())
    }

    @Test
    fun `original page URI remains when the preference is off`() = runTest {
        val originalBytes = byteArrayOf(1, 2, 3)
        stubLoader(showTranslated = false, originalBytes = originalBytes)

        val page = buildLoader().getPages().single()

        assertArrayEquals(originalBytes, page.stream!!.invoke().readBytes())
        verify(exactly = 0) {
            translationStorageManager.getTranslatedPageFiles(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `a translated page that fails to open falls back to the original page`() = runTest {
        val originalBytes = byteArrayOf(1, 2, 3)
        val translatedUri = mockk<Uri>()
        val translatedFile = mockk<UniFile> {
            every { uri } returns translatedUri
        }
        stubLoader(showTranslated = true, originalBytes = originalBytes)
        every {
            translationStorageManager.getTranslatedPageFiles(
                chapter.chapter.name,
                chapter.chapter.scanlator,
                manga.title,
                source,
                "es",
            )
        } returns mapOf(0 to translatedFile)
        every { application.contentResolver.openInputStream(translatedUri) } throws IOException("file vanished")

        val page = buildLoader().getPages().single()

        assertArrayEquals(originalBytes, page.stream!!.invoke().readBytes())
    }

    @Test
    fun `a chapter reads translation coverage and lists translated files once`() = runTest {
        val chapterDir = mockk<UniFile> { every { isFile } returns false }
        val root = mockk<UniFile>()
        val translatedDir = mockk<UniFile>()
        val coverageFile = mockk<UniFile>()
        val files = List(3) { index ->
            mockk<UniFile> {
                every { isFile } returns true
                every { name } returns "%03d.jpg".format(index + 1)
                every { uri } returns mockk()
            }
        }
        val coverage = """{"totalPages":3,"outcomes":{"0":"TRANSLATED","1":"TRANSLATED","2":"STORAGE_FAILURE"}}"""
        stubLoader(showTranslated = true, originalBytes = byteArrayOf(1))
        every { downloadProvider.findChapterDir(any(), any(), any(), any()) } returns chapterDir
        every { chapterDir.findFile("_translated") } returns root
        every { root.findFile("es") } returns translatedDir
        every { translatedDir.findFile(any<String>()) } returns null
        every { translatedDir.findFile(".translation_coverage") } returns coverageFile
        every { coverageFile.openInputStream() } answers { ByteArrayInputStream(coverage.toByteArray()) }
        every { translatedDir.listFiles() } returns files.toTypedArray()
        files.forEachIndexed { index, file ->
            every { application.contentResolver.openInputStream(file.uri) } answers {
                ByteArrayInputStream(byteArrayOf((index + 10).toByte()))
            }
        }
        coEvery {
            downloadManager.buildPageList<List<ReaderPage>>(source, manga, any(), any())
        } coAnswers {
            val consume = arg<suspend (List<DownloadedChapterPage>) -> List<ReaderPage>>(3)
            consume(List(3) { index -> DownloadedChapterPage(index) { ByteArrayInputStream(byteArrayOf(1)) } })
        }
        val loader = DownloadPageLoader(
            chapter, manga, source, downloadManager, downloadProvider, application,
            readerPreferences, translationPreferences, TranslationStorageManager(downloadProvider),
        )

        val pages = loader.getPages()

        pages.forEachIndexed { index, page ->
            val expected = if (index == 2) byteArrayOf(1) else byteArrayOf((index + 10).toByte())
            assertArrayEquals(expected, page.stream!!.invoke().readBytes())
        }
        verify(exactly = 1) { translatedDir.listFiles() }
        verify(exactly = 1) { coverageFile.openInputStream() }
    }

    private fun stubLoader(showTranslated: Boolean, originalBytes: ByteArray) {
        every { readerPreferences.showTranslatedPages() } returns showTranslatedPages
        every { showTranslatedPages.get() } returns showTranslated
        every { translationPreferences.targetLanguage() } returns targetLanguage
        every { targetLanguage.get() } returns "es"
        every { downloadProvider.findChapterDir(any(), any(), any(), any()) } returns null
        coEvery {
            downloadManager.buildPageList<List<ReaderPage>>(source, manga, any(), any())
        } coAnswers {
            val consume = arg<suspend (List<DownloadedChapterPage>) -> List<ReaderPage>>(3)
            consume(
                listOf(
                    DownloadedChapterPage(0) { ByteArrayInputStream(originalBytes) },
                ),
            )
        }
    }

    private fun buildLoader() = DownloadPageLoader(
        chapter = chapter,
        manga = manga,
        source = source,
        downloadManager = downloadManager,
        downloadProvider = downloadProvider,
        context = application,
        readerPreferences = readerPreferences,
        translationPreferences = translationPreferences,
        translationStorageManager = translationStorageManager,
    )
}
