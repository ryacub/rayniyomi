package eu.kanade.tachiyomi.data.translation

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.download.manga.model.DownloadedChapterPage
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.MangaSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import java.io.ByteArrayInputStream

@OptIn(ExperimentalCoroutinesApi::class)
class TranslationManagerTest {

    private val context = mockk<Context>(relaxed = true)
    private val translationEngineFactory = mockk<TranslationEngineFactory>()
    private val translationPreferences = mockk<TranslationPreferences>()
    private val translationStorageManager = mockk<TranslationStorageManager>(relaxed = true)
    private val downloadManager = mockk<MangaDownloadManager>()

    private val source = mockk<MangaSource>()
    private val engine = mockk<TranslationEngine>()

    private val targetLanguageFlow = MutableStateFlow(TargetLanguages.DEFAULT)
    private val targetLanguagePref = mockk<Preference<String>> {
        every { get() } answers { targetLanguageFlow.value }
        every { changes() } returns targetLanguageFlow
    }

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

    private lateinit var manager: TranslationManager

    @BeforeEach
    fun setUp() {
        every { translationPreferences.targetLanguage() } returns targetLanguagePref
        mockTargetLanguage("en")
        mockTranslationProvider(TranslationProvider.CLAUDE)
        every { translationPreferences.translationModel(any()) } returns mockk {
            every { get() } returns "claude-3"
        }
        // Relaxed mocks return a non-null file, which would skip every page.
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
    }

    private fun createManager(scope: CoroutineScope? = null): TranslationManager {
        return TranslationManager(
            context = context,
            translationEngineFactory = translationEngineFactory,
            translationPreferences = translationPreferences,
            translationStorageManager = translationStorageManager,
            downloadManager = downloadManager,
            scope = scope,
        ).also { manager = it }
    }

    /** Not a child of [TestScope], so the endless language observer cannot block runTest. */
    private fun TestScope.createEagerManager(): TranslationManager =
        createManager(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

    /** The chapter's state from the public flow; an absent chapter is Idle, as production sees it. */
    private fun stateOf(chapterId: Long): TranslationState =
        manager.translationStates.value[chapterId] ?: TranslationState.Idle

    // -----------------------------------------------------------------------
    // State transition: IDLE -> TRANSLATING -> TRANSLATED (success path)
    // -----------------------------------------------------------------------

    @Test
    fun `translateChapter transitions from IDLE to TRANSLATING to TRANSLATED on success`() = runTest {
        createEagerManager()
        val imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x00) // JPEG header
        every { translationEngineFactory.create() } returns engine
        mockBuildPageList(listOf(DownloadedChapterPage(0) { ByteArrayInputStream(imageBytes) }))
        coEvery { engine.detectAndTranslate(imageBytes, "en") } returns TranslationResult(emptyList())
        every { translationStorageManager.writeTranslatedPage(any(), any(), any(), any(), any(), any(), any()) } returns
            mockk()

        // Initial state should be IDLE
        assertEquals(TranslationState.Idle, stateOf(chapter.id))

        manager.translateChapter(manga, chapter, source)

        // Allow the coroutine to complete
        advanceUntilIdle()

        assertEquals(TranslationState.Translated, stateOf(chapter.id))
    }

    // -----------------------------------------------------------------------
    // State transition: IDLE -> TRANSLATING -> INCOMPLETE (failure path)
    // -----------------------------------------------------------------------

    @Test
    fun `translateChapter transitions to INCOMPLETE when engine throws exception`() = runTest {
        createEagerManager()
        val imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x00)

        every { translationEngineFactory.create() } returns engine
        mockBuildPageList(listOf(DownloadedChapterPage(0) { ByteArrayInputStream(imageBytes) }))
        coEvery { engine.detectAndTranslate(imageBytes, "en") } throws RuntimeException("API rate limit exceeded")

        manager.translateChapter(manga, chapter, source)
        advanceUntilIdle()

        val state = stateOf(chapter.id)
        assertTrue(state is TranslationState.Incomplete, "Expected Incomplete state but got $state")
        assertEquals(listOf(1), (state as TranslationState.Incomplete).unresolvedPages)
    }

    @Test
    fun `translateChapter transitions to ERROR when no pages found`() = runTest {
        createEagerManager()
        every { translationEngineFactory.create() } returns engine
        mockBuildPageList(emptyList())

        manager.translateChapter(manga, chapter, source)
        advanceUntilIdle()

        val state = stateOf(chapter.id)
        assertTrue(state is TranslationState.Error, "Expected Error state but got $state")
        assertEquals("No pages found", (state as TranslationState.Error).message)
    }

    @Test
    fun `translateChapter sets ERROR when no translation provider configured`() {
        createManager()
        every { translationEngineFactory.create() } returns null

        manager.translateChapter(manga, chapter, source)

        val state = stateOf(chapter.id)
        assertTrue(state is TranslationState.Error, "Expected Error state but got $state")
        assertEquals(
            "No translation model is selected. Choose a model in Settings > Translation.",
            (state as TranslationState.Error).message,
        )
    }

    // -----------------------------------------------------------------------
    // Transient failure retry
    // -----------------------------------------------------------------------

    @Test
    fun `translateChapter retries a transient failure and completes`() = runTest {
        createEagerManager()
        val imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x00)
        every { translationEngineFactory.create() } returns engine
        mockBuildPageList(listOf(DownloadedChapterPage(0) { ByteArrayInputStream(imageBytes) }))
        var attempts = 0
        coEvery { engine.detectAndTranslate(imageBytes, "en") } answers {
            if (attempts++ == 0) throw HttpException(503)
            TranslationResult(emptyList())
        }
        every { translationStorageManager.writeTranslatedPage(any(), any(), any(), any(), any(), any(), any()) } returns
            mockk()

        manager.translateChapter(manga, chapter, source)
        advanceUntilIdle()

        assertEquals(TranslationState.Translated, stateOf(chapter.id))
        coVerify(exactly = 2) { engine.detectAndTranslate(imageBytes, "en") }
    }

    @Test
    fun `translateChapter skips pages whose output already exists on disk`() = runTest {
        createEagerManager()
        val imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x00)
        every { translationEngineFactory.create() } returns engine
        mockBuildPageList(
            listOf(
                DownloadedChapterPage(0) { ByteArrayInputStream(imageBytes) },
                DownloadedChapterPage(1) { ByteArrayInputStream(imageBytes) },
            ),
        )
        coEvery { engine.detectAndTranslate(imageBytes, "en") } returns TranslationResult(emptyList())
        every { translationStorageManager.writeTranslatedPage(any(), any(), any(), any(), any(), any(), any()) } returns
            mockk()
        every {
            translationStorageManager.getTranslatedPageFile(any(), any(), any(), any(), any(), any())
        } answers {
            if (arg<Int>(5) == 0) mockk<UniFile>() else null
        }

        manager.translateChapter(manga, chapter, source)
        advanceUntilIdle()

        assertEquals(TranslationState.Translated, stateOf(chapter.id))
        coVerify(exactly = 1) { engine.detectAndTranslate(imageBytes, "en") }
        verify(exactly = 1) {
            translationStorageManager.getTranslatedPageFile(
                chapterName = chapter.name,
                chapterScanlator = chapter.scanlator,
                mangaTitle = manga.title,
                source = source,
                targetLang = "en",
                pageIndex = 1,
            )
        }
    }

    @Test
    fun `translateChapter fails immediately on non-transient error`() = runTest {
        createEagerManager()
        val imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x00)
        every { translationEngineFactory.create() } returns engine
        mockBuildPageList(listOf(DownloadedChapterPage(0) { ByteArrayInputStream(imageBytes) }))
        coEvery { engine.detectAndTranslate(imageBytes, "en") } throws HttpException(401)

        manager.translateChapter(manga, chapter, source)
        advanceUntilIdle()

        val state = stateOf(chapter.id)
        assertTrue(state is TranslationState.Incomplete, "Expected Incomplete state but got $state")
        assertEquals(listOf(1), (state as TranslationState.Incomplete).unresolvedPages)
        coVerify(exactly = 1) { engine.detectAndTranslate(imageBytes, "en") }
    }

    @Test
    fun `translateChapter exhausts retries on persistent transient failures`() = runTest {
        createEagerManager()
        val imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x00)
        every { translationEngineFactory.create() } returns engine
        mockBuildPageList(listOf(DownloadedChapterPage(0) { ByteArrayInputStream(imageBytes) }))
        coEvery { engine.detectAndTranslate(imageBytes, "en") } throws HttpException(503)

        manager.translateChapter(manga, chapter, source)
        advanceUntilIdle()

        val state = stateOf(chapter.id)
        assertTrue(state is TranslationState.Incomplete, "Expected Incomplete state but got $state")
        assertEquals(listOf(1), (state as TranslationState.Incomplete).unresolvedPages)
        coVerify(exactly = 4) { engine.detectAndTranslate(imageBytes, "en") }
    }

    // -----------------------------------------------------------------------
    // Retry feedback state
    // -----------------------------------------------------------------------

    /** Collects every state for a single-chapter fixture. `values.firstOrNull()` needs exactly one chapter. */
    private fun TestScope.collectChapterStates(): Pair<MutableList<TranslationState>, Job> {
        val emissions = mutableListOf<TranslationState>()
        val observer = launch(UnconfinedTestDispatcher(testScheduler)) {
            manager.translationStates.collect {
                emissions += it.values.firstOrNull() ?: TranslationState.Idle
            }
        }
        return emissions to observer
    }

    @Test
    fun `translateChapter emits a retrying state while a transient failure retries`() = runTest {
        createEagerManager()
        val imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x00)
        every { translationEngineFactory.create() } returns engine
        mockBuildPageList(listOf(DownloadedChapterPage(0) { ByteArrayInputStream(imageBytes) }))
        var attempts = 0
        coEvery { engine.detectAndTranslate(imageBytes, "en") } answers {
            if (attempts++ == 0) throw HttpException(503)
            TranslationResult(emptyList())
        }
        every { translationStorageManager.writeTranslatedPage(any(), any(), any(), any(), any(), any(), any()) } returns
            mockk()

        val (emissions, observer) = collectChapterStates()
        manager.translateChapter(manga, chapter, source)
        advanceUntilIdle()
        observer.cancel()

        // Exact equality is deliberate: the phase must name the retried page, 1-based.
        assertTrue(
            emissions.any { it == TranslationState.Translating(0, 1, TranslationPhase.Retrying(1)) },
            "Expected a retrying emission but got $emissions",
        )
        assertEquals(TranslationState.Translated, stateOf(chapter.id))
    }

    @Test
    fun `translateChapter clears the retrying state when the retried page succeeds`() = runTest {
        createEagerManager()
        val imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x00)
        every { translationEngineFactory.create() } returns engine
        mockBuildPageList(listOf(DownloadedChapterPage(0) { ByteArrayInputStream(imageBytes) }))
        var attempts = 0
        coEvery { engine.detectAndTranslate(imageBytes, "en") } answers {
            if (attempts++ == 0) throw HttpException(503)
            TranslationResult(emptyList())
        }
        every { translationStorageManager.writeTranslatedPage(any(), any(), any(), any(), any(), any(), any()) } returns
            mockk()

        val (emissions, observer) = collectChapterStates()
        manager.translateChapter(manga, chapter, source)
        advanceUntilIdle()
        observer.cancel()

        val lastRetryingIndex = emissions.indexOfLast {
            it is TranslationState.Translating && it.phase is TranslationPhase.Retrying
        }
        assertTrue(lastRetryingIndex >= 0, "Expected a retrying emission but got $emissions")
        val clearedAfterRetry = emissions.drop(lastRetryingIndex + 1).none {
            it is TranslationState.Translating && it.phase is TranslationPhase.Retrying
        }
        assertTrue(clearedAfterRetry, "Retrying flag survived page success: $emissions")
        assertEquals(TranslationState.Translated, stateOf(chapter.id))
    }

    @Test
    fun `translateChapter ends INCOMPLETE after showing retrying when retries are exhausted`() = runTest {
        createEagerManager()
        val imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x00)
        every { translationEngineFactory.create() } returns engine
        mockBuildPageList(listOf(DownloadedChapterPage(0) { ByteArrayInputStream(imageBytes) }))
        coEvery { engine.detectAndTranslate(imageBytes, "en") } throws HttpException(503)

        val (emissions, observer) = collectChapterStates()
        manager.translateChapter(manga, chapter, source)
        advanceUntilIdle()
        observer.cancel()

        assertTrue(
            emissions.any { it is TranslationState.Translating && it.phase is TranslationPhase.Retrying },
            "Expected a retrying emission but got $emissions",
        )
        assertEquals(
            TranslationState.Incomplete(
                resolvedPages = 0,
                totalPages = 1,
                unresolvedPages = listOf(1),
                reason = "Page 1 could not be translated",
            ),
            stateOf(chapter.id),
        )
        coVerify(exactly = 4) { engine.detectAndTranslate(imageBytes, "en") }
    }

    @Test
    fun `translateChapter shows no retrying state when the first attempt succeeds`() = runTest {
        createEagerManager()
        val imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x00)
        every { translationEngineFactory.create() } returns engine
        mockBuildPageList(listOf(DownloadedChapterPage(0) { ByteArrayInputStream(imageBytes) }))
        coEvery { engine.detectAndTranslate(imageBytes, "en") } returns TranslationResult(emptyList())
        every { translationStorageManager.writeTranslatedPage(any(), any(), any(), any(), any(), any(), any()) } returns
            mockk()

        val (emissions, observer) = collectChapterStates()
        manager.translateChapter(manga, chapter, source)
        advanceUntilIdle()
        observer.cancel()
        assertEquals(
            emptyList<TranslationState>(),
            emissions.filter { it is TranslationState.Translating && it.phase is TranslationPhase.Retrying },
        )
        assertEquals(TranslationState.Translated, stateOf(chapter.id))
    }

    @Test
    fun `repeated identical retries emit the retrying state only once`() = runTest {
        createEagerManager()
        val imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x00)
        every { translationEngineFactory.create() } returns engine
        mockBuildPageList(listOf(DownloadedChapterPage(0) { ByteArrayInputStream(imageBytes) }))
        var attempts = 0
        coEvery { engine.detectAndTranslate(imageBytes, "en") } answers {
            if (attempts++ < 2) throw HttpException(503)
            TranslationResult(emptyList())
        }
        every { translationStorageManager.writeTranslatedPage(any(), any(), any(), any(), any(), any(), any()) } returns
            mockk()

        val (emissions, observer) = collectChapterStates()
        manager.translateChapter(manga, chapter, source)
        advanceUntilIdle()
        observer.cancel()

        // MutableStateFlow dedupes equal values, so identical retries must not re-emit.
        assertEquals(1, emissions.count { it == TranslationState.Translating(0, 1, TranslationPhase.Retrying(1)) })
        assertEquals(TranslationState.Translated, stateOf(chapter.id))
    }

    @Test
    fun `cancelling during a retry backoff resets the state to Idle`() = runTest {
        createEagerManager()
        val imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x00)
        every { translationEngineFactory.create() } returns engine
        mockBuildPageList(listOf(DownloadedChapterPage(0) { ByteArrayInputStream(imageBytes) }))
        coEvery { engine.detectAndTranslate(imageBytes, "en") } throws HttpException(503)

        manager.translateChapter(manga, chapter, source)

        // The eager dispatcher ran the first attempt and its onRetry before suspending in the backoff.
        val stateBeforeCancel = stateOf(chapter.id)
        assertTrue(
            stateBeforeCancel == TranslationState.Translating(0, 1, TranslationPhase.Retrying(1)),
            "Expected the retrying state before cancel but got $stateBeforeCancel",
        )

        manager.cancelTranslation(chapter.id)
        advanceUntilIdle()

        assertEquals(TranslationState.Idle, stateOf(chapter.id))
    }

    @Test
    fun `a retry on a later page never lowers the progress count`() = runTest {
        createEagerManager()
        val imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x00)
        every { translationEngineFactory.create() } returns engine
        mockBuildPageList(
            listOf(
                DownloadedChapterPage(0) { ByteArrayInputStream(imageBytes) },
                DownloadedChapterPage(1) { ByteArrayInputStream(imageBytes) },
            ),
        )
        var attempts = 0
        coEvery { engine.detectAndTranslate(any(), "en") } answers {
            // Page 1 succeeds; page 2 fails once transiently, then succeeds.
            if (attempts++ == 1) throw HttpException(503)
            TranslationResult(emptyList())
        }
        every { translationStorageManager.writeTranslatedPage(any(), any(), any(), any(), any(), any(), any()) } returns
            mockk()

        val (emissions, observer) = collectChapterStates()
        manager.translateChapter(manga, chapter, source)
        advanceUntilIdle()
        observer.cancel()

        val counts = emissions.filterIsInstance<TranslationState.Translating>().map { it.currentPage }
        assertEquals(counts.sorted(), counts, "Progress went backwards: $emissions")
        // The retry reports the page it is retrying without touching the finished count.
        assertTrue(
            emissions.any { it == TranslationState.Translating(1, 2, TranslationPhase.Retrying(2)) },
            "Expected a retrying emission for page 2 but got $emissions",
        )
        assertEquals(TranslationState.Translated, stateOf(chapter.id))
    }
    // -----------------------------------------------------------------------
    // Cancellation
    // -----------------------------------------------------------------------

    @Test
    fun `cancelTranslation sets state back to IDLE`() = runTest {
        createEagerManager()
        val imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x00)

        every { translationEngineFactory.create() } returns engine
        mockBuildPageList(listOf(DownloadedChapterPage(0) { ByteArrayInputStream(imageBytes) }))
        // Simulate a slow engine call so we can cancel mid-flight
        coEvery { engine.detectAndTranslate(imageBytes, "en") } coAnswers {
            kotlinx.coroutines.delay(10_000)
            TranslationResult(emptyList())
        }

        manager.translateChapter(manga, chapter, source)

        // Cancel before the engine finishes
        manager.cancelTranslation(chapter.id)

        assertEquals(TranslationState.Idle, stateOf(chapter.id))
    }

    @Test
    fun `cancelTranslation on non-existent chapter does not throw`() {
        createManager()
        // Should be a no-op
        manager.cancelTranslation(999L)
        assertEquals(TranslationState.Idle, stateOf(999L))
    }

    // -----------------------------------------------------------------------
    // translationStates lookup
    // -----------------------------------------------------------------------

    @Test
    fun `the states lookup returns Idle for an unknown chapter`() {
        createManager()
        assertEquals(TranslationState.Idle, stateOf(999L))
    }

    @Test
    fun `the states lookup returns the right state for each chapter independently`() = runTest {
        createEagerManager()
        val chapter2 = chapter.copy(id = 200L, name = "Chapter 2")

        // Set up chapter 1 to fail
        every { translationEngineFactory.create() } returns null
        manager.translateChapter(manga, chapter, source)

        // chapter 1 should be in Error, chapter 2 should be Idle
        assertTrue(stateOf(chapter.id) is TranslationState.Error)
        assertEquals(TranslationState.Idle, stateOf(chapter2.id))
    }

    // -----------------------------------------------------------------------
    // isChapterTranslated delegates to storage manager
    // -----------------------------------------------------------------------

    @Test
    fun `isChapterTranslated delegates to storage manager with correct parameters`() {
        createManager()
        every {
            translationStorageManager.isChapterTranslated(
                chapterName = chapter.name,
                chapterScanlator = chapter.scanlator,
                mangaTitle = manga.title,
                source = source,
                targetLang = "en",
            )
        } returns true

        val result = manager.isChapterTranslated(chapter, manga.title, source)

        assertTrue(result)
        verify {
            translationStorageManager.isChapterTranslated(
                chapterName = "Chapter 1",
                chapterScanlator = null,
                mangaTitle = "Test Manga",
                source = source,
                targetLang = "en",
            )
        }
    }

    @Test
    fun `isChapterTranslated returns false when storage manager says no`() {
        createManager()
        every {
            translationStorageManager.isChapterTranslated(
                chapterName = any(),
                chapterScanlator = any(),
                mangaTitle = any(),
                source = any(),
                targetLang = any(),
            )
        } returns false

        val result = manager.isChapterTranslated(chapter, manga.title, source)

        assertFalse(result)
    }

    @Test
    fun `isChapterTranslated uses target language from preferences`() {
        mockTargetLanguage("ja")
        createManager()

        every {
            translationStorageManager.isChapterTranslated(
                chapterName = any(),
                chapterScanlator = any(),
                mangaTitle = any(),
                source = any(),
                targetLang = "ja",
            )
        } returns true

        val result = manager.isChapterTranslated(chapter, manga.title, source)

        assertTrue(result)
        verify {
            translationStorageManager.isChapterTranslated(
                chapterName = any(),
                chapterScanlator = any(),
                mangaTitle = any(),
                source = any(),
                targetLang = "ja",
            )
        }
    }

    // -----------------------------------------------------------------------
    // deleteTranslation
    // -----------------------------------------------------------------------

    @Test
    fun `deleteTranslation resets state to IDLE`() = runTest {
        createEagerManager()
        // First put the chapter into an error state
        every { translationEngineFactory.create() } returns null
        manager.translateChapter(manga, chapter, source)
        assertTrue(stateOf(chapter.id) is TranslationState.Error)

        // Now delete -- should reset to IDLE
        every {
            translationStorageManager.deleteTranslation(
                chapterName = any(),
                chapterScanlator = any(),
                mangaTitle = any(),
                source = any(),
                targetLang = any(),
            )
        } returns true

        manager.deleteTranslation(chapter, manga.title, source)

        assertEquals(TranslationState.Idle, stateOf(chapter.id))
    }

    // -----------------------------------------------------------------------
    // Duplicate translation guard
    // -----------------------------------------------------------------------

    @Test
    fun `translateChapter does not start if already translating`() = runTest {
        // Scheduler-owned eager scope: the first translation starts immediately and
        // stays suspended inside detectAndTranslate.
        createEagerManager()
        val imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x00)
        val translationStarted = CompletableDeferred<Unit>()

        every { translationEngineFactory.create() } returns engine
        coEvery {
            downloadManager.buildPageList<Unit>(source, manga, chapter, any())
        } coAnswers {
            translationStarted.complete(Unit)
            arg<suspend (List<DownloadedChapterPage>) -> Unit>(3).invoke(
                listOf(DownloadedChapterPage(0) { ByteArrayInputStream(imageBytes) }),
            )
        }
        coEvery { engine.detectAndTranslate(any(), any()) } coAnswers {
            delay(60_000) // Virtual time; holds the first job open.
            TranslationResult(emptyList())
        }

        // Start first translation
        manager.translateChapter(manga, chapter, source)

        // Explicit signal replaces the sleep: the first job reached buildPageList.
        translationStarted.await()

        // Try to start again -- should be a no-op (job is already active)
        manager.translateChapter(manga, chapter, source)

        // buildPageList is called inside the coroutine, so with the second call being a no-op,
        // it should only have been called once
        coVerify(exactly = 1) { downloadManager.buildPageList<Any>(source, manga, chapter, any()) }

        // Clean up
        manager.cancelTranslation(chapter.id)
    }

    // -----------------------------------------------------------------------
    // translationStates flow
    // -----------------------------------------------------------------------

    @Test
    fun `translationStates flow emits state updates`() = runTest {
        createEagerManager()
        // Initial state should be empty map
        assertTrue(manager.translationStates.value.isEmpty())

        // Trigger an error state
        every { translationEngineFactory.create() } returns null
        manager.translateChapter(manga, chapter, source)

        // Now the states map should contain the chapter
        val states = manager.translationStates.value
        assertTrue(states.containsKey(chapter.id))
        assertTrue(states[chapter.id] is TranslationState.Error)
    }

    @Test
    fun `translationStates removes entry when state returns to IDLE`() = runTest {
        createEagerManager()
        // Trigger an error state first
        every { translationEngineFactory.create() } returns null
        manager.translateChapter(manga, chapter, source)
        assertTrue(manager.translationStates.value.containsKey(chapter.id))

        // Cancel to reset to IDLE
        manager.cancelTranslation(chapter.id)

        // Entry should be removed from the map (IDLE means absent)
        assertFalse(manager.translationStates.value.containsKey(chapter.id))
    }

    // -----------------------------------------------------------------------
    // Storage manager integration on success
    // -----------------------------------------------------------------------

    @Test
    fun `translateChapter writes metadata on successful translation`() = runTest {
        createEagerManager()
        val imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x00)

        every { translationEngineFactory.create() } returns engine
        mockBuildPageList(listOf(DownloadedChapterPage(0) { ByteArrayInputStream(imageBytes) }))
        coEvery { engine.detectAndTranslate(imageBytes, "en") } returns TranslationResult(emptyList())
        every { translationStorageManager.writeTranslatedPage(any(), any(), any(), any(), any(), any(), any()) } returns
            mockk()

        manager.translateChapter(manga, chapter, source)
        advanceUntilIdle()

        verify {
            translationStorageManager.writeMetadata(
                chapterName = "Chapter 1",
                chapterScanlator = null,
                mangaTitle = "Test Manga",
                source = source,
                targetLang = "en",
                provider = "CLAUDE",
            )
        }
    }

    // -----------------------------------------------------------------------
    // Target language change handling
    // -----------------------------------------------------------------------

    @Test
    fun `changing target language resets in-memory chapter states`() = runTest {
        createEagerManager()
        advanceUntilIdle() // subscribe the observer before the value changes
        every { translationEngineFactory.create() } returns null
        manager.translateChapter(manga, chapter, source)
        assertTrue(manager.translationStates.value.containsKey(chapter.id))

        targetLanguageFlow.value = "it"
        advanceUntilIdle()

        assertFalse(manager.translationStates.value.containsKey(chapter.id))
        assertEquals(TranslationState.Idle, stateOf(chapter.id))
    }

    @Test
    fun `changing target language cancels an in-flight translation`() = runTest {
        createEagerManager()
        advanceUntilIdle() // subscribe the observer before the value changes
        val imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x00)

        every { translationEngineFactory.create() } returns engine
        mockBuildPageList(listOf(DownloadedChapterPage(0) { ByteArrayInputStream(imageBytes) }))
        coEvery { engine.detectAndTranslate(imageBytes, "en") } coAnswers {
            kotlinx.coroutines.delay(10_000)
            TranslationResult(emptyList())
        }

        manager.translateChapter(manga, chapter, source)

        targetLanguageFlow.value = "it"
        advanceUntilIdle()

        assertEquals(TranslationState.Idle, stateOf(chapter.id))
        coVerify(exactly = 1) { engine.detectAndTranslate(imageBytes, "en") }
        coVerify(exactly = 0) { engine.detectAndTranslate(any(), "it") }
    }

    @Test
    fun `chapter translation uses one target language even if preference changes mid-run`() = runTest {
        createEagerManager()
        advanceUntilIdle() // subscribe the observer before the value changes
        val imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x00)
        val usedLanguages = mutableListOf<String>()

        every { translationEngineFactory.create() } returns engine
        mockBuildPageList(listOf(DownloadedChapterPage(0) { ByteArrayInputStream(imageBytes) }))
        coEvery { engine.detectAndTranslate(any(), any()) } coAnswers {
            usedLanguages += secondArg<String>()
            targetLanguageFlow.value = "it"
            TranslationResult(emptyList())
        }
        every { translationStorageManager.writeTranslatedPage(any(), any(), any(), any(), any(), any(), any()) } returns
            mockk()

        manager.translateChapter(manga, chapter, source)
        advanceUntilIdle()

        assertTrue(usedLanguages.isNotEmpty())
        assertTrue(usedLanguages.all { it == "en" })
    }

    @Test
    fun `initial preference emission does not reset chapter states`() = runTest {
        createEagerManager()
        advanceUntilIdle() // subscribe the observer before the value changes
        every { translationEngineFactory.create() } returns null
        manager.translateChapter(manga, chapter, source)
        assertTrue(manager.translationStates.value.containsKey(chapter.id))

        advanceUntilIdle()

        assertTrue(manager.translationStates.value.containsKey(chapter.id))
    }

    @Test
    fun `language change does not leave stale state when a cancelled job finishes late`() = runTest {
        val releaseHeldEngine = CompletableDeferred<Unit>()
        createEagerManager()
        advanceUntilIdle() // Subscribe the language observer.
        val imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x00)

        every { translationEngineFactory.create() } returns engine
        mockBuildPageList(listOf(DownloadedChapterPage(0) { ByteArrayInputStream(imageBytes) }))
        coEvery { engine.detectAndTranslate(any(), any()) } coAnswers {
            // Hold the last resumption past cancellation so the tail runs late, after
            // the observer cancelled this job and parked in join().
            withContext(NonCancellable) {
                releaseHeldEngine.await()
            }
            TranslationResult(emptyList())
        }
        every { translationStorageManager.writeTranslatedPage(any(), any(), any(), any(), any(), any(), any()) } returns
            mockk()

        manager.translateChapter(manga, chapter, source)

        // The job sits suspended in the NonCancellable block. Change the language: the
        // observer cancels the job, blocks in join() on the held resumption, and clears
        // the state map only after the late tail finishes.
        targetLanguageFlow.value = "it"
        advanceUntilIdle()

        releaseHeldEngine.complete(Unit)
        advanceUntilIdle()

        assertFalse(manager.translationStates.value.containsKey(chapter.id))
        assertEquals(TranslationState.Idle, stateOf(chapter.id))
    }

    // -----------------------------------------------------------------------
    // chapterTitles
    // -----------------------------------------------------------------------

    @Test
    fun `translateChapter records the chapter title`() {
        createManager()
        every { translationEngineFactory.create() } returns null

        manager.translateChapter(manga, chapter, source)

        assertEquals("Test Manga - Chapter 1", manager.chapterTitles.value[chapter.id])
    }

    @Test
    fun `cancelTranslation removes the chapter title`() {
        createManager()
        every { translationEngineFactory.create() } returns null
        manager.translateChapter(manga, chapter, source)
        assertTrue(manager.chapterTitles.value.containsKey(chapter.id))

        manager.cancelTranslation(chapter.id)

        assertFalse(manager.chapterTitles.value.containsKey(chapter.id))
    }

    @Test
    fun `a target language change clears every chapter title`() = runTest {
        createEagerManager()
        advanceUntilIdle() // subscribe the observer before the value changes
        every { translationEngineFactory.create() } returns null
        manager.translateChapter(manga, chapter, source)
        assertTrue(manager.chapterTitles.value.isNotEmpty())

        targetLanguageFlow.value = "it"
        advanceUntilIdle()

        assertTrue(manager.chapterTitles.value.isEmpty())
    }

    @Test
    fun `the chapter title survives after the state reaches Translated`() = runTest {
        createEagerManager()
        val imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x00)
        every { translationEngineFactory.create() } returns engine
        mockBuildPageList(listOf(DownloadedChapterPage(0) { ByteArrayInputStream(imageBytes) }))
        coEvery { engine.detectAndTranslate(imageBytes, "en") } returns TranslationResult(emptyList())
        every { translationStorageManager.writeTranslatedPage(any(), any(), any(), any(), any(), any(), any()) } returns
            mockk()

        manager.translateChapter(manga, chapter, source)
        advanceUntilIdle()

        assertEquals(TranslationState.Translated, stateOf(chapter.id))
        assertEquals("Test Manga - Chapter 1", manager.chapterTitles.value[chapter.id])
    }
    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun mockTargetLanguage(lang: String) {
        targetLanguageFlow.value = lang
    }

    // -----------------------------------------------------------------------
    // languageGeneration
    // -----------------------------------------------------------------------

    @Test
    fun `languageGeneration does not advance on construction`() = runTest {
        createEagerManager()
        advanceUntilIdle()

        assertEquals(0, manager.languageGeneration.value)
    }

    @Test
    fun `languageGeneration advances once per target language change`() = runTest {
        createEagerManager()
        advanceUntilIdle() // subscribe the observer before the value changes

        targetLanguageFlow.value = "it"
        advanceUntilIdle()
        assertEquals(1, manager.languageGeneration.value)

        targetLanguageFlow.value = "fr"
        advanceUntilIdle()
        assertEquals(2, manager.languageGeneration.value)
    }

    @Test
    fun `languageGeneration does not advance when the language is set to its current value`() = runTest {
        createEagerManager()
        advanceUntilIdle()

        targetLanguageFlow.value = "en"
        advanceUntilIdle()

        assertEquals(0, manager.languageGeneration.value)
    }

    /**
     * Collectors of [TranslationManager.languageGeneration] resume only after
     * onTargetLanguageChanged returns, so they never observe a half-applied change.
     */
    @Test
    fun `a languageGeneration observer never sees leftover chapter states`() = runTest {
        createEagerManager()
        advanceUntilIdle() // subscribe the observer before the value changes
        every { translationEngineFactory.create() } returns null
        manager.translateChapter(manga, chapter, source)
        assertTrue(manager.translationStates.value.isNotEmpty())

        val statesAtBump = mutableListOf<Map<Long, TranslationState>>()
        // Unconfined so the observer subscribes now; a lazy one would see the post-bump
        // value as its initial emission and drop it.
        val observer = launch(UnconfinedTestDispatcher(testScheduler)) {
            manager.languageGeneration.drop(1).collect {
                statesAtBump += manager.translationStates.value
            }
        }

        targetLanguageFlow.value = "it"
        advanceUntilIdle()
        observer.cancel()

        assertEquals(listOf(emptyMap<Long, TranslationState>()), statesAtBump)
    }

    private fun mockTranslationProvider(provider: TranslationProvider) {
        val pref = mockk<Preference<TranslationProvider>>()
        every { pref.get() } returns provider
        every { translationPreferences.translationProvider() } returns pref
    }

    private fun mockBuildPageList(pages: List<DownloadedChapterPage>) {
        coEvery {
            downloadManager.buildPageList<Unit>(source, manga, chapter, any())
        } coAnswers {
            arg<suspend (List<DownloadedChapterPage>) -> Unit>(3).invoke(pages)
        }
    }
}
