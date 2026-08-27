package eu.kanade.tachiyomi.data.translation

import eu.kanade.tachiyomi.data.download.manga.model.DownloadedChapterPage
import eu.kanade.tachiyomi.data.translation.TranslationEngine
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.MangaSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import java.io.ByteArrayInputStream

@OptIn(ExperimentalCoroutinesApi::class)
class TranslationChapterRunnerTest {

    private val translationStorageManager = mockk<TranslationStorageManager>(relaxed = true)
    private val engine = mockk<TranslationEngine>()
    private val source = mockk<MangaSource>()

    private val manga = Manga.create().copy(id = 1L, title = "Test Manga")
    private val chapter = Chapter(
        id = 100L,
        mangaId = 1L,
        read = false,
        bookmark = false,
        lastPageRead = 0L,
        dateFetch = 0L,
        sourceOrder = 0L,
        url = "",
        name = "Chapter 1",
        dateUpload = 0L,
        chapterNumber = 1.0,
        scanlator = null,
        lastModifiedAt = 0L,
        version = 0L,
    )

    /** The real renderer decodes bitmaps and cannot run on the JVM. */
    private var renderCalls = 0

    private fun createRunner(): TranslationChapterRunner =
        TranslationChapterRunner(
            translationStorageManager = translationStorageManager,
            render = { bytes, _ ->
                renderCalls++
                bytes
            },
        )

    @BeforeEach
    fun setUp() {
        // A relaxed mock returns a non-null file, which would skip every page.
        every {
            translationStorageManager.getTranslatedPageFile(any(), any(), any(), any(), any(), any())
        } returns null
        every {
            translationStorageManager.getTranslationCoverage(any(), any(), any(), any(), any())
        } returns null
        every {
            translationStorageManager.initializeTranslationCoverage(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } returns true
        every {
            translationStorageManager.writePageOutcome(any(), any(), any(), any(), any(), any(), any())
        } returns true
        renderCalls = 0
    }

    private fun pagesOf(count: Int, imageBytes: ByteArray): List<DownloadedChapterPage> =
        (0 until count).map { i -> DownloadedChapterPage(i) { ByteArrayInputStream(imageBytes) } }

    @Test
    fun `emits zero progress before the first page`() = runTest {
        coEvery { engine.detectAndTranslate(any(), "en") } returns TranslationResult(emptyList())
        every { translationStorageManager.writeTranslatedPage(any(), any(), any(), any(), any(), any(), any()) } returns
            mockk()
        val emissions = mutableListOf<TranslationState>()
        val runner = createRunner()

        runner.run(manga, chapter, source, pagesOf(1, byteArrayOf(1)), engine, "en", "CLAUDE") {
            emissions += it
        }
        advanceUntilIdle()

        assertEquals(TranslationState.Translating(0, 1), emissions.first())
    }

    @Test
    fun `counts finished pages, not attempted pages`() = runTest {
        coEvery { engine.detectAndTranslate(any(), "en") } returns TranslationResult(emptyList())
        every { translationStorageManager.writeTranslatedPage(any(), any(), any(), any(), any(), any(), any()) } returns
            mockk()
        val emissions = mutableListOf<TranslationState>()
        val runner = createRunner()

        runner.run(manga, chapter, source, pagesOf(3, byteArrayOf(1)), engine, "en", "CLAUDE") {
            emissions += it
        }
        advanceUntilIdle()

        assertEquals(
            listOf(0, 1, 2, 3),
            emissions.filterIsInstance<TranslationState.Translating>().map { it.currentPage },
        )
        assertEquals(TranslationState.Translated, emissions.last())
    }

    @Test
    fun `an already written page still advances the count`() = runTest {
        coEvery { engine.detectAndTranslate(any(), "en") } returns TranslationResult(emptyList())
        every {
            translationStorageManager.getTranslatedPageFile(any(), any(), any(), any(), any(), any())
        } answers {
            if (arg<Int>(5) == 0) mockk() else null
        }
        val emissions = mutableListOf<TranslationState>()
        val runner = createRunner()

        runner.run(manga, chapter, source, pagesOf(2, byteArrayOf(1)), engine, "en", "CLAUDE") {
            emissions += it
        }
        advanceUntilIdle()

        assertEquals(
            listOf(1, 1, 2),
            emissions.filterIsInstance<TranslationState.Translating>().map { it.currentPage },
        )
        coVerify(exactly = 1) { engine.detectAndTranslate(any(), "en") }
    }

    @Test
    fun `resume uses stored outcomes and retries only unresolved pages`() = runTest {
        every {
            translationStorageManager.getTranslationCoverage(any(), any(), any(), any(), any())
        } returns TranslationCoverage(
            totalPages = 2,
            outcomes = mapOf(
                0 to TranslationPageOutcome.TRANSLATED,
                1 to TranslationPageOutcome.NOT_ATTEMPTED,
            ),
        )
        every {
            translationStorageManager.getTranslatedPageFile(any(), any(), any(), any(), any(), 0)
        } returns mockk()
        coEvery { engine.detectAndTranslate(any(), "en") } returns TranslationResult(emptyList())
        every {
            translationStorageManager.writeTranslatedPage(any(), any(), any(), any(), any(), any(), any())
        } returns mockk()

        createRunner().run(manga, chapter, source, pagesOf(2, byteArrayOf(1)), engine, "en", "CLAUDE") {}

        coVerify(exactly = 1) { engine.detectAndTranslate(any(), "en") }
        verify(exactly = 0) {
            translationStorageManager.initializeTranslationCoverage(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        }
    }

    @Test
    fun `a retried page reports the one-based page in the phase`() = runTest {
        var attempts = 0
        coEvery { engine.detectAndTranslate(any(), "en") } answers {
            // Page 1 succeeds; page 2 fails once transiently, then succeeds.
            if (attempts++ == 1) throw HttpException(503)
            TranslationResult(emptyList())
        }
        every { translationStorageManager.writeTranslatedPage(any(), any(), any(), any(), any(), any(), any()) } returns
            mockk()
        val emissions = mutableListOf<TranslationState>()
        val runner = createRunner()

        runner.run(manga, chapter, source, pagesOf(2, byteArrayOf(1)), engine, "en", "CLAUDE") {
            emissions += it
        }
        advanceUntilIdle()

        assertTrue(
            emissions.any { it == TranslationState.Translating(1, 2, TranslationPhase.Retrying(2)) },
            "Expected a retrying emission for page 2 but got $emissions",
        )
    }

    @Test
    fun `a retried page does not lower the finished count`() = runTest {
        var attempts = 0
        coEvery { engine.detectAndTranslate(any(), "en") } answers {
            if (attempts++ == 1) throw HttpException(503)
            TranslationResult(emptyList())
        }
        every { translationStorageManager.writeTranslatedPage(any(), any(), any(), any(), any(), any(), any()) } returns
            mockk()
        val emissions = mutableListOf<TranslationState>()
        val runner = createRunner()

        runner.run(manga, chapter, source, pagesOf(2, byteArrayOf(1)), engine, "en", "CLAUDE") {
            emissions += it
        }
        advanceUntilIdle()

        val counts = emissions.filterIsInstance<TranslationState.Translating>().map { it.currentPage }
        assertEquals(counts.sorted(), counts, "Progress went backwards: $emissions")
    }

    @Test
    fun `writes metadata once and ends in Translated`() = runTest {
        coEvery { engine.detectAndTranslate(any(), "en") } returns TranslationResult(emptyList())
        every { translationStorageManager.writeTranslatedPage(any(), any(), any(), any(), any(), any(), any()) } returns
            mockk()
        val emissions = mutableListOf<TranslationState>()
        val runner = createRunner()

        runner.run(manga, chapter, source, pagesOf(2, byteArrayOf(1)), engine, "en", "CLAUDE") {
            emissions += it
        }
        advanceUntilIdle()

        verify(exactly = 1) {
            translationStorageManager.writeMetadata(
                chapterName = chapter.name,
                chapterScanlator = chapter.scanlator,
                mangaTitle = manga.title,
                source = source,
                targetLang = "en",
                provider = "CLAUDE",
            )
        }
        assertEquals(TranslationState.Translated, emissions.last())
    }

    @Test
    fun `reports a permanent failure without claiming completion`() {
        coEvery { engine.detectAndTranslate(any(), "en") } throws HttpException(401)
        val emissions = mutableListOf<TranslationState>()
        val runner = createRunner()

        runTest {
            runner.run(manga, chapter, source, pagesOf(1, byteArrayOf(1)), engine, "en", "CLAUDE") {
                emissions += it
            }
        }

        assertTrue(emissions.none { it == TranslationState.Translated })
        assertTrue(emissions.last() is TranslationState.Incomplete)
    }

    @Test
    fun `renders the overlay only when the result has regions`() = runTest {
        val noRegions = TranslationResult(emptyList())
        val withRegions = TranslationResult(
            listOf(TextRegion(NormalizedRect(0.1f, 0.1f, 0.9f, 0.2f), "hello", "world")),
        )
        coEvery { engine.detectAndTranslate(any(), "en") } returnsMany listOf(withRegions, noRegions)
        every { translationStorageManager.writeTranslatedPage(any(), any(), any(), any(), any(), any(), any()) } returns
            mockk()
        val runner = createRunner()

        runner.run(manga, chapter, source, pagesOf(2, byteArrayOf(1)), engine, "en", "CLAUDE") {}
        advanceUntilIdle()

        assertEquals(1, renderCalls)
    }

    @Test
    fun `skips a page whose stream cannot be opened`() = runTest {
        coEvery { engine.detectAndTranslate(any(), "en") } returns TranslationResult(emptyList())
        val closedPage = DownloadedChapterPage(0) { null }
        val goodPage = DownloadedChapterPage(1) { ByteArrayInputStream(byteArrayOf(1)) }
        val emissions = mutableListOf<TranslationState>()
        val runner = createRunner()

        runner.run(manga, chapter, source, listOf(closedPage, goodPage), engine, "en", "CLAUDE") {
            emissions += it
        }
        advanceUntilIdle()

        coVerify(exactly = 1) { engine.detectAndTranslate(any(), "en") }
        assertEquals(
            TranslationState.Incomplete(
                resolvedPages = 1,
                totalPages = 2,
                unresolvedPages = listOf(1),
                reason = "Page 1 could not be read",
            ),
            emissions.last(),
        )
    }

    @Test
    fun `an empty source stream is not a resolved no-text page`() = runTest {
        coEvery { engine.detectAndTranslate(any(), "en") } returns TranslationResult(emptyList())
        val emissions = mutableListOf<TranslationState>()

        createRunner().run(
            manga,
            chapter,
            source,
            listOf(DownloadedChapterPage(0) { ByteArrayInputStream(byteArrayOf()) }),
            engine,
            "en",
            "CLAUDE",
        ) { emissions += it }
        advanceUntilIdle()

        coVerify(exactly = 0) { engine.detectAndTranslate(any(), "en") }
        assertEquals(
            TranslationState.Incomplete(
                resolvedPages = 0,
                totalPages = 1,
                unresolvedPages = listOf(1),
                reason = "Page 1 could not be read",
            ),
            emissions.last(),
        )
    }

    @Test
    fun `does not complete when translated page storage fails`() = runTest {
        coEvery { engine.detectAndTranslate(any(), "en") } returns TranslationResult(emptyList())
        every {
            translationStorageManager.writeTranslatedPage(any(), any(), any(), any(), any(), any(), any())
        } returns null
        val emissions = mutableListOf<TranslationState>()

        createRunner().run(manga, chapter, source, pagesOf(1, byteArrayOf(1)), engine, "en", "CLAUDE") {
            emissions += it
        }

        assertEquals(
            TranslationState.Incomplete(
                resolvedPages = 0,
                totalPages = 1,
                unresolvedPages = listOf(1),
                reason = "Page 1 could not be stored",
            ),
            emissions.last(),
        )
    }
}
