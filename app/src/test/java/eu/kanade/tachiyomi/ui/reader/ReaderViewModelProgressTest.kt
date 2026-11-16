package eu.kanade.tachiyomi.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.source.manga.interactor.GetMangaIncognitoState
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.translation.TranslationManager
import eu.kanade.tachiyomi.data.translation.TranslationPreferences
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.test.VirtualTime
import eu.kanade.tachiyomi.ui.reader.loader.ChapterLoader
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.repository.MangaSourceLinkRepository
import tachiyomi.domain.items.chapter.interactor.UpdateChapter
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.chapter.model.ChapterUpdate
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.manga.service.MangaSourceManager

class ReaderViewModelProgressTest {

    private val vt = VirtualTime()

    @BeforeEach
    fun setUp() = vt.setUpMain()

    @AfterEach
    fun tearDown() = vt.tearDownMain()

    private class Fixture(
        removeAfterReadSlots: Int,
        savedStateValues: Map<String, Any?> = mapOf("page_index" to 0),
    ) {
        val manga = Manga.create().copy(id = 1L, source = 2L)
        val chapter = ReaderChapter(Chapter.create().copy(id = 3L, mangaId = 1L, chapterNumber = 1.0))
        val pages = List(10) { ReaderPage(it).apply { this.chapter = this@Fixture.chapter } }
        val savedState = SavedStateHandle(savedStateValues)
        val updateChapter = mockk<UpdateChapter>(relaxed = true)
        val downloadManager = mockk<MangaDownloadManager>(relaxed = true)
        val model: ReaderViewModel

        init {
            chapter.state = ReaderChapter.State.Loaded(pages)
            val getManga = mockk<GetManga> { coEvery { await(manga.id) } returns manga }
            val readerPreferences = mockk<ReaderPreferences>(relaxed = true) {
                every { defaultOrientationType().get() } returns ReaderOrientation.FREE.flagValue
                every { showTranslatedPages().get() } returns false
            }
            val downloadPreferences = mockk<DownloadPreferences> {
                every { autoDownloadWhileReading().get() } returns 0
                every { removeAfterReadSlots().get() } returns removeAfterReadSlots
            }
            val trackPreferences = mockk<TrackPreferences>(relaxed = true) {
                every { autoUpdateTrack().get() } returns false
            }
            val libraryPreferences = mockk<LibraryPreferences>(relaxed = true) {
                every { markDuplicateReadChapterAsRead().get() } returns emptySet()
            }
            val incognito = mockk<GetMangaIncognitoState> { every { await(any()) } returns false }
            val translationManager = mockk<TranslationManager> {
                every { languageGeneration } returns MutableStateFlow(0)
                every { translationStates } returns MutableStateFlow(emptyMap())
            }
            model = ReaderViewModel(
                savedState = savedState,
                sourceManager = mockk<MangaSourceManager>(relaxed = true),
                downloadManager = downloadManager,
                downloadProvider = mockk(relaxed = true),
                imageSaver = mockk(relaxed = true),
                readerPreferences = readerPreferences,
                basePreferences = mockk(relaxed = true),
                downloadPreferences = downloadPreferences,
                trackPreferences = trackPreferences,
                trackChapter = mockk(relaxed = true),
                getManga = getManga,
                mangaSourceLinkRepository = mockk<MangaSourceLinkRepository>(relaxed = true) {
                    coEvery { getForManga(any()) } returns null
                },
                getChaptersByMangaId = mockk(relaxed = true),
                getNextChapters = mockk(relaxed = true),
                upsertHistory = mockk(relaxed = true),
                updateChapter = updateChapter,
                setMangaViewerFlags = mockk(relaxed = true),
                getIncognitoState = incognito,
                sourcePreferences = mockk<SourcePreferences>(relaxed = true),
                libraryPreferences = libraryPreferences,
                translationStorageManager = mockk(relaxed = true),
                translationPreferences = mockk<TranslationPreferences>(relaxed = true) {
                    every { targetLanguage().get() } returns "en"
                },
                translationManager = translationManager,
                application = mockk(relaxed = true),
            )
            mutableState().value = ReaderViewModel.State(
                manga = manga,
                viewerChapters = ViewerChapters(chapter, null, null),
            )
        }

        @Suppress("UNCHECKED_CAST")
        private fun mutableState(): MutableStateFlow<ReaderViewModel.State> {
            return ReaderViewModel::class.java.getDeclaredField("mutableState").apply {
                isAccessible = true
            }.get(model) as MutableStateFlow<ReaderViewModel.State>
        }

        fun putChapterInChapterList() {
            putChaptersInChapterList(listOf(chapter))
        }

        fun putChaptersInChapterList(chapters: List<ReaderChapter>) {
            val manager = ReaderViewModel::class.java.getDeclaredField("chapterListManager").apply {
                isAccessible = true
            }.get(model)
            manager.javaClass.getDeclaredField("_chapterList").apply { isAccessible = true }
                .set(manager, chapters)
        }

        fun setChapterLoader(loader: ChapterLoader) {
            @Suppress("UNCHECKED_CAST")
            val loaders = ReaderViewModel::class.java.getDeclaredField("loaders").apply { isAccessible = true }
                .get(model) as MutableMap<Long, ChapterLoader>
            loaders[manga.id] = loader
        }

        fun setNextChapter(nextChapter: ReaderChapter) {
            val state = mutableState()
            state.value = state.value.copy(
                viewerChapters = ViewerChapters(chapter, null, nextChapter),
            )
        }

        fun verifyUpdate(update: ChapterUpdate) {
            coVerify(timeout = WAIT_MS) { updateChapter.await(update) }
        }

        fun close() = model.viewModelScope.cancel()
    }

    @Test
    fun `restoring a saved checkpoint applies its page to the matching chapter`() = runTest(vt.scheduler) {
        val fixture = Fixture(
            removeAfterReadSlots = -1,
            savedStateValues = mapOf("reader_checkpoint" to longArrayOf(3L, 6L)),
        )
        try {
            advanceUntilIdle()

            assertEquals(6, fixture.chapter.requestedPage)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `selecting a middle page records the page and keeps the chapter unread`() = runTest(vt.scheduler) {
        val fixture = Fixture(removeAfterReadSlots = -1)
        try {
            fixture.model.onPageSelected(fixture.pages[5])

            fixture.verifyUpdate(ChapterUpdate(id = 3L, read = false, lastPageRead = 5L))
            assertEquals(false, fixture.chapter.chapter.read)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `moving to another chapter restores that chapter's saved page`() = runTest(vt.scheduler) {
        val fixture = Fixture(removeAfterReadSlots = -1)
        val nextChapter = ReaderChapter(
            Chapter.create().copy(id = 4L, mangaId = fixture.manga.id, lastPageRead = 2L),
        )
        val nextPages = List(10) { ReaderPage(it).apply { chapter = nextChapter } }
        nextChapter.state = ReaderChapter.State.Loaded(nextPages)
        val loader = mockk<ChapterLoader> {
            coEvery { loadChapter(nextChapter) } coAnswers {
                nextChapter.requestedPage = 8
            }
        }
        fixture.setChapterLoader(loader)
        fixture.putChaptersInChapterList(listOf(fixture.chapter, nextChapter))
        fixture.setNextChapter(nextChapter)

        try {
            fixture.model.onPageSelected(fixture.pages[8])
            fixture.verifyUpdate(ChapterUpdate(id = 3L, read = false, lastPageRead = 8L))
            fixture.model.loadNextChapter()
            coVerify(timeout = WAIT_MS) { loader.loadChapter(nextChapter) }

            assertEquals(nextChapter, fixture.model.state.value.currentChapter)
            assertEquals(2, nextChapter.requestedPage)
            assertEquals(listOf(4L, 2L), fixture.savedState.get<LongArray>("reader_checkpoint")?.toList())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `swiping into another chapter checkpoints its visible page`() = runTest(vt.scheduler) {
        val fixture = Fixture(removeAfterReadSlots = -1)
        val nextChapter = ReaderChapter(
            Chapter.create().copy(id = 4L, mangaId = fixture.manga.id, lastPageRead = 2L),
        )
        val nextPages = List(10) { ReaderPage(it).apply { chapter = nextChapter } }
        nextChapter.state = ReaderChapter.State.Loaded(nextPages)
        val loader = mockk<ChapterLoader> {
            coEvery { loadChapter(nextChapter) } coAnswers {
                nextChapter.requestedPage = 8
            }
        }
        fixture.setChapterLoader(loader)
        fixture.putChaptersInChapterList(listOf(fixture.chapter, nextChapter))
        fixture.setNextChapter(nextChapter)

        try {
            fixture.model.onPageSelected(nextPages[5])
            withContext(Dispatchers.Default) {
                withTimeout(WAIT_MS) {
                    fixture.model.state.first { it.currentChapter?.chapter?.id == nextChapter.chapter.id }
                }
            }

            assertEquals(5, nextChapter.requestedPage)
            assertEquals(listOf(4L, 5L), fixture.savedState.get<LongArray>("reader_checkpoint")?.toList())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `selecting the last page marks the chapter read`() = runTest(vt.scheduler) {
        val fixture = Fixture(removeAfterReadSlots = -1)
        try {
            fixture.model.onPageSelected(fixture.pages.last())

            fixture.verifyUpdate(ChapterUpdate(id = 3L, read = true, lastPageRead = 9L))
            assertTrue(fixture.chapter.chapter.read)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `selecting an earlier page after completion keeps the chapter read`() = runTest(vt.scheduler) {
        val fixture = Fixture(removeAfterReadSlots = -1)
        try {
            fixture.model.onPageSelected(fixture.pages.last())
            fixture.verifyUpdate(ChapterUpdate(id = 3L, read = true, lastPageRead = 9L))

            fixture.model.onPageSelected(fixture.pages[2])

            fixture.verifyUpdate(ChapterUpdate(id = 3L, read = true, lastPageRead = 2L))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `completion with removeAfterReadSlots 0 enqueues the current chapter for deletion`() = runTest(vt.scheduler) {
        val fixture = Fixture(removeAfterReadSlots = 0)
        try {
            fixture.putChapterInChapterList()

            fixture.model.onPageSelected(fixture.pages.last())

            coVerify(timeout = WAIT_MS) {
                fixture.downloadManager.enqueueChaptersToDelete(match { it.single().id == 3L }, fixture.manga)
            }
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `toggling the bookmark twice writes true then false`() = runTest(vt.scheduler) {
        val fixture = Fixture(removeAfterReadSlots = -1)
        try {
            fixture.model.toggleChapterBookmark()
            fixture.verifyUpdate(ChapterUpdate(id = 3L, bookmark = true))
            assertTrue(fixture.model.state.value.bookmarked)

            fixture.model.toggleChapterBookmark()
            fixture.verifyUpdate(ChapterUpdate(id = 3L, bookmark = false))
            assertEquals(false, fixture.model.state.value.bookmarked)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `an error page records nothing`() = runTest(vt.scheduler) {
        val fixture = Fixture(removeAfterReadSlots = -1)
        try {
            fixture.pages[4].status = Page.State.ERROR
            fixture.model.onPageSelected(fixture.pages[4])
            fixture.model.onPageSelected(fixture.pages[1])

            fixture.verifyUpdate(ChapterUpdate(id = 3L, read = false, lastPageRead = 1L))
            coVerify(exactly = 1) { fixture.updateChapter.await(any()) }
        } finally {
            fixture.close()
        }
    }

    private companion object {
        const val WAIT_MS = 5_000L
    }
}
