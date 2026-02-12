package eu.kanade.tachiyomi.data.translation

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.model.Page
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
        mockTargetLanguage("en")
        mockTranslationProvider(TranslationProvider.CLAUDE)
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

    // -----------------------------------------------------------------------
    // State transition: IDLE -> TRANSLATING -> TRANSLATED (success path)
    // -----------------------------------------------------------------------

    @Test
    fun `translateChapter transitions from IDLE to TRANSLATING to TRANSLATED on success`() = runTest {
        createManager(this)
        val imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x00) // JPEG header
        val uri = mockk<Uri>()
        val page = Page(0, uri = uri).apply { status = Page.State.READY }

        every { translationEngineFactory.create() } returns engine
        every { downloadManager.buildPageList(source, manga, chapter) } returns listOf(page)
        mockContentResolver(uri, imageBytes)
        coEvery { engine.detectAndTranslate(imageBytes, "en") } returns TranslationResult(emptyList())
        every { translationStorageManager.writeTranslatedPage(any(), any(), any(), any(), any(), any(), any()) } returns
            mockk()

        // Initial state should be IDLE
        assertEquals(TranslationState.Idle, manager.getState(chapter.id))

        manager.translateChapter(manga, chapter, source)

        // Allow the coroutine to complete
        advanceUntilIdle()

        assertEquals(TranslationState.Translated, manager.getState(chapter.id))
    }

    // -----------------------------------------------------------------------
    // State transition: IDLE -> TRANSLATING -> ERROR (failure path)
    // -----------------------------------------------------------------------

    @Test
    fun `translateChapter transitions to ERROR when engine throws exception`() = runTest {
        createManager(this)
        val imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x00)
        val uri = mockk<Uri>()
        val page = Page(0, uri = uri).apply { status = Page.State.READY }

        every { translationEngineFactory.create() } returns engine
        every { downloadManager.buildPageList(source, manga, chapter) } returns listOf(page)
        mockContentResolver(uri, imageBytes)
        coEvery { engine.detectAndTranslate(imageBytes, "en") } throws RuntimeException("API rate limit exceeded")

        manager.translateChapter(manga, chapter, source)
        advanceUntilIdle()

        val state = manager.getState(chapter.id)
        assertTrue(state is TranslationState.Error, "Expected Error state but got $state")
        assertEquals("API rate limit exceeded", (state as TranslationState.Error).message)
    }

    @Test
    fun `translateChapter transitions to ERROR when no pages found`() = runTest {
        createManager(this)
        every { translationEngineFactory.create() } returns engine
        every { downloadManager.buildPageList(source, manga, chapter) } returns emptyList()

        manager.translateChapter(manga, chapter, source)
        advanceUntilIdle()

        val state = manager.getState(chapter.id)
        assertTrue(state is TranslationState.Error, "Expected Error state but got $state")
        assertEquals("No pages found", (state as TranslationState.Error).message)
    }

    @Test
    fun `translateChapter sets ERROR when no translation provider configured`() {
        createManager()
        every { translationEngineFactory.create() } returns null

        manager.translateChapter(manga, chapter, source)

        val state = manager.getState(chapter.id)
        assertTrue(state is TranslationState.Error, "Expected Error state but got $state")
        assertEquals("No translation provider configured", (state as TranslationState.Error).message)
    }

    // -----------------------------------------------------------------------
    // Cancellation
    // -----------------------------------------------------------------------

    @Test
    fun `cancelTranslation sets state back to IDLE`() = runTest {
        createManager(this)
        val imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x00)
        val uri = mockk<Uri>()
        val page = Page(0, uri = uri).apply { status = Page.State.READY }

        every { translationEngineFactory.create() } returns engine
        every { downloadManager.buildPageList(source, manga, chapter) } returns listOf(page)
        mockContentResolver(uri, imageBytes)
        // Simulate a slow engine call so we can cancel mid-flight
        coEvery { engine.detectAndTranslate(imageBytes, "en") } coAnswers {
            kotlinx.coroutines.delay(10_000)
            TranslationResult(emptyList())
        }

        manager.translateChapter(manga, chapter, source)

        // Cancel before the engine finishes
        manager.cancelTranslation(chapter.id)

        assertEquals(TranslationState.Idle, manager.getState(chapter.id))
    }

    @Test
    fun `cancelTranslation on non-existent chapter does not throw`() {
        createManager()
        // Should be a no-op
        manager.cancelTranslation(999L)
        assertEquals(TranslationState.Idle, manager.getState(999L))
    }

    // -----------------------------------------------------------------------
    // getState
    // -----------------------------------------------------------------------

    @Test
    fun `getState returns IDLE for unknown chapter`() {
        createManager()
        assertEquals(TranslationState.Idle, manager.getState(999L))
    }

    @Test
    fun `getState returns correct state for each chapter independently`() = runTest {
        createManager(this)
        val chapter2 = chapter.copy(id = 200L, name = "Chapter 2")

        // Set up chapter 1 to fail
        every { translationEngineFactory.create() } returns null
        manager.translateChapter(manga, chapter, source)

        // chapter 1 should be in Error, chapter 2 should be Idle
        assertTrue(manager.getState(chapter.id) is TranslationState.Error)
        assertEquals(TranslationState.Idle, manager.getState(chapter2.id))
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
        createManager(this)
        // First put the chapter into an error state
        every { translationEngineFactory.create() } returns null
        manager.translateChapter(manga, chapter, source)
        assertTrue(manager.getState(chapter.id) is TranslationState.Error)

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

        assertEquals(TranslationState.Idle, manager.getState(chapter.id))
    }

    // -----------------------------------------------------------------------
    // Duplicate translation guard
    // -----------------------------------------------------------------------

    @Test
    fun `translateChapter does not start if already translating`() {
        // Use a real IO scope so the first translation stays active during the second call
        createManager()
        val imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x00)
        val uri = mockk<Uri>()
        val page = Page(0, uri = uri).apply { status = Page.State.READY }

        every { translationEngineFactory.create() } returns engine
        every { downloadManager.buildPageList(source, manga, chapter) } returns listOf(page)
        mockContentResolver(uri, imageBytes)
        coEvery { engine.detectAndTranslate(any(), any()) } coAnswers {
            kotlinx.coroutines.delay(60_000)
            TranslationResult(emptyList())
        }

        // Start first translation (launches on IO, stays suspended)
        manager.translateChapter(manga, chapter, source)

        // Give the coroutine a moment to start
        Thread.sleep(100)

        // Try to start again -- should be a no-op (job is already active)
        manager.translateChapter(manga, chapter, source)

        // buildPageList is called inside the coroutine, so with the second call being a no-op,
        // it should only have been called once
        verify(exactly = 1) { downloadManager.buildPageList(source, manga, chapter) }

        // Clean up
        manager.cancelTranslation(chapter.id)
    }

    // -----------------------------------------------------------------------
    // translationStates flow
    // -----------------------------------------------------------------------

    @Test
    fun `translationStates flow emits state updates`() = runTest {
        createManager(this)
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
        createManager(this)
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
        createManager(this)
        val imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x00)
        val uri = mockk<Uri>()
        val page = Page(0, uri = uri).apply { status = Page.State.READY }

        every { translationEngineFactory.create() } returns engine
        every { downloadManager.buildPageList(source, manga, chapter) } returns listOf(page)
        mockContentResolver(uri, imageBytes)
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
    // Helpers
    // -----------------------------------------------------------------------

    private fun mockTargetLanguage(lang: String) {
        val pref = mockk<Preference<String>>()
        every { pref.get() } returns lang
        every { translationPreferences.targetLanguage() } returns pref
    }

    private fun mockTranslationProvider(provider: TranslationProvider) {
        val pref = mockk<Preference<TranslationProvider>>()
        every { pref.get() } returns provider
        every { translationPreferences.translationProvider() } returns pref
    }

    private fun mockContentResolver(uri: Uri, imageBytes: ByteArray) {
        val contentResolver = mockk<ContentResolver>()
        every { context.contentResolver } returns contentResolver
        every { contentResolver.openInputStream(uri) } returns ByteArrayInputStream(imageBytes)
    }
}
