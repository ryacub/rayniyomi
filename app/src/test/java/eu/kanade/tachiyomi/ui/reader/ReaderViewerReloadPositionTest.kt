package eu.kanade.tachiyomi.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.entries.manga.interactor.SetMangaViewerFlags
import eu.kanade.domain.source.manga.interactor.GetMangaIncognitoState
import eu.kanade.tachiyomi.data.database.models.manga.ChapterImpl
import eu.kanade.tachiyomi.data.translation.TranslationManager
import eu.kanade.tachiyomi.test.VirtualTime
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.interactor.UpdateChapter
import tachiyomi.domain.source.manga.service.MangaSourceManager

class ReaderViewerReloadPositionTest {

    private val vt = VirtualTime()

    @BeforeEach
    fun setUp() = vt.setUpMain()

    @AfterEach
    fun tearDown() = vt.tearDownMain()

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `viewer changes retain the selected page in incognito`(changeOrientation: Boolean) = runTest(vt.scheduler) {
        val manga = Manga.create().copy(id = 1L, source = 2L)
        val chapter = ReaderChapter(ChapterImpl(id = 3L).apply { last_page_read = 3 })
        val pages = List(50) { ReaderPage(it).apply { this.chapter = chapter } }
        chapter.state = ReaderChapter.State.Loaded(pages)
        val updateChapter = mockk<UpdateChapter>(relaxed = true)
        val viewerFlags = mockk<SetMangaViewerFlags>(relaxed = true)
        val getManga = mockk<GetManga> { coEvery { await(manga.id) } returns manga }
        val readerPreferences = mockk<ReaderPreferences>(relaxed = true) {
            every { defaultOrientationType().get() } returns ReaderOrientation.FREE.flagValue
        }
        val downloadPreferences = mockk<DownloadPreferences> {
            every { autoDownloadWhileReading().get() } returns 0
        }
        val incognito = mockk<GetMangaIncognitoState> { every { await(any()) } returns true }
        val sourceManager = mockk<MangaSourceManager>(relaxed = true)
        val translationManager = mockk<TranslationManager> {
            every { languageGeneration } returns MutableStateFlow(0)
            every { translationStates } returns MutableStateFlow(emptyMap())
        }
        val model = ReaderViewModel(
            savedState = SavedStateHandle(mapOf("page_index" to 3)),
            sourceManager = sourceManager,
            downloadManager = mockk(relaxed = true),
            downloadProvider = mockk(relaxed = true),
            imageSaver = mockk(relaxed = true),
            readerPreferences = readerPreferences,
            basePreferences = mockk(relaxed = true),
            downloadPreferences = downloadPreferences,
            trackPreferences = mockk(relaxed = true),
            trackChapter = mockk(relaxed = true),
            getManga = getManga,
            getChaptersByMangaId = mockk(relaxed = true),
            getNextChapters = mockk(relaxed = true),
            upsertHistory = mockk(relaxed = true),
            updateChapter = updateChapter,
            setMangaViewerFlags = viewerFlags,
            getIncognitoState = incognito,
            libraryPreferences = mockk(relaxed = true),
            translationStorageManager = mockk(relaxed = true),
            translationPreferences = mockk(relaxed = true),
            translationManager = translationManager,
            application = mockk(relaxed = true),
        )
        try {
            @Suppress("UNCHECKED_CAST")
            val state = ReaderViewModel::class.java.getDeclaredField("mutableState").apply {
                isAccessible = true
            }.get(model) as MutableStateFlow<ReaderViewModel.State>
            state.value = ReaderViewModel.State(manga = manga, viewerChapters = ViewerChapters(chapter, null, null))
            model.onPageSelected(pages[39])

            withContext(Dispatchers.Default) {
                withTimeout(5_000) {
                    model.state.first { it.currentPage == 40 }
                    if (changeOrientation) {
                        model.setMangaOrientationType(ReaderOrientation.LANDSCAPE)
                    } else {
                        model.setMangaReadingMode(ReadingMode.WEBTOON)
                    }
                    model.eventFlow.first { it == ReaderEvent.ReloadViewerChapters }
                }
            }

            assertEquals(39, chapter.requestedPage)
            assertEquals(3, chapter.chapter.last_page_read)
            coVerify(exactly = 0) { updateChapter.await(any()) }
        } finally {
            model.viewModelScope.cancel()
        }
    }
}
