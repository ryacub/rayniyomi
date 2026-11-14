package eu.kanade.tachiyomi.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import eu.kanade.tachiyomi.data.translation.TranslationManager
import eu.kanade.tachiyomi.test.VirtualTime
import eu.kanade.tachiyomi.ui.reader.loader.ChapterLoader
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.items.chapter.model.Chapter

class ReaderViewModelPreloadTest {

    private val vt = VirtualTime()

    @BeforeEach
    fun setUp() = vt.setUpMain()

    @AfterEach
    fun tearDown() = vt.tearDownMain()

    private fun readerViewModel(): ReaderViewModel {
        val readerPreferences = mockk<ReaderPreferences>(relaxed = true) {
            every { defaultOrientationType().get() } returns ReaderOrientation.FREE.flagValue
        }
        val translationManager = mockk<TranslationManager> {
            every { languageGeneration } returns MutableStateFlow(0)
            every { translationStates } returns MutableStateFlow(emptyMap())
        }
        val downloadPreferences = mockk<DownloadPreferences> {
            every { autoDownloadWhileReading().get() } returns 0
        }
        return ReaderViewModel(
            savedState = SavedStateHandle(),
            sourceManager = mockk(relaxed = true),
            downloadManager = mockk(relaxed = true),
            downloadProvider = mockk(relaxed = true),
            imageSaver = mockk(relaxed = true),
            readerPreferences = readerPreferences,
            basePreferences = mockk(relaxed = true),
            downloadPreferences = downloadPreferences,
            trackPreferences = mockk(relaxed = true),
            trackChapter = mockk(relaxed = true),
            getManga = mockk(relaxed = true),
            getChaptersByMangaId = mockk(relaxed = true),
            getNextChapters = mockk(relaxed = true),
            upsertHistory = mockk(relaxed = true),
            updateChapter = mockk(relaxed = true),
            setMangaViewerFlags = mockk(relaxed = true),
            getIncognitoState = mockk(relaxed = true),
            libraryPreferences = mockk(relaxed = true),
            translationStorageManager = mockk(relaxed = true),
            translationPreferences = mockk(relaxed = true),
            translationManager = translationManager,
            application = mockk(relaxed = true),
        )
    }

    private fun ReaderViewModel.useChapterLoader(chapterLoader: ChapterLoader) {
        ReaderViewModel::class.java.getDeclaredField("loader").apply { isAccessible = true }
            .set(this, chapterLoader)
    }

    @Test
    fun `a repeated preload of a loading chapter keeps the load running`() = runTest(vt.scheduler) {
        val chapter = ReaderChapter(Chapter.create().copy(id = 3L, mangaId = 1L))
        val loadStarted = CompletableDeferred<Unit>()
        val loadCancelled = CompletableDeferred<Unit>()
        val chapterLoader = mockk<ChapterLoader> {
            coEvery { loadChapter(chapter) } coAnswers {
                chapter.state = ReaderChapter.State.Loading
                loadStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    loadCancelled.complete(Unit)
                }
            }
        }
        val model = readerViewModel().apply { useChapterLoader(chapterLoader) }
        try {
            model.preload(chapter)
            withContext(Dispatchers.Default) { withTimeout(5_000) { loadStarted.await() } }

            model.preload(chapter)

            val cancelled = withContext(Dispatchers.Default) { withTimeoutOrNull(500) { loadCancelled.await() } }
            withClue("the second preload cancelled the first load") { cancelled shouldBe null }
        } finally {
            model.viewModelScope.cancel()
        }
    }

    @Test
    fun `a preload of the other adjacent chapter starts while a load runs`() = runTest(vt.scheduler) {
        val loadingChapter = ReaderChapter(Chapter.create().copy(id = 3L, mangaId = 1L))
        val otherChapter = ReaderChapter(Chapter.create().copy(id = 4L, mangaId = 1L))
        val firstLoadStarted = CompletableDeferred<Unit>()
        val otherLoadStarted = CompletableDeferred<Unit>()
        val chapterLoader = mockk<ChapterLoader> {
            coEvery { loadChapter(loadingChapter) } coAnswers {
                loadingChapter.state = ReaderChapter.State.Loading
                firstLoadStarted.complete(Unit)
                awaitCancellation()
            }
            coEvery { loadChapter(otherChapter) } coAnswers { otherLoadStarted.complete(Unit) }
        }
        val model = readerViewModel().apply { useChapterLoader(chapterLoader) }
        try {
            model.preload(loadingChapter)
            withContext(Dispatchers.Default) { withTimeout(5_000) { firstLoadStarted.await() } }

            model.preload(otherChapter)

            withContext(Dispatchers.Default) { withTimeout(5_000) { otherLoadStarted.await() } }
        } finally {
            model.viewModelScope.cancel()
        }
    }
}
