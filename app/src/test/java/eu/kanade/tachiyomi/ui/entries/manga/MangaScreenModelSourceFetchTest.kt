package eu.kanade.tachiyomi.ui.entries.manga

import androidx.lifecycle.Lifecycle
import eu.kanade.domain.source.manga.interactor.UpdateMangaFromRemote
import eu.kanade.domain.source.manga.model.RemoteMangaUpdate
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.translation.TranslationManager
import eu.kanade.tachiyomi.di.testAppGraph
import eu.kanade.tachiyomi.test.VirtualTime
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.entries.manga.interactor.GetMangaWithChapters
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.track.manga.interactor.GetMangaTracks

@OptIn(ExperimentalCoroutinesApi::class)
class MangaScreenModelSourceFetchTest {

    private val vt = VirtualTime()
    private val updateMangaFromRemote = mockk<UpdateMangaFromRemote>()

    @BeforeEach
    fun setUp() {
        vt.setUpMain()
        every { testAppGraph.mangaSourceManager } returns mockk<MangaSourceManager>(relaxed = true)
        coEvery { updateMangaFromRemote(any(), any(), any(), any(), any(), any()) } answers {
            Result.success(RemoteMangaUpdate(manga = secondArg(), newChapters = emptyList()))
        }
    }

    @AfterEach
    fun tearDown() {
        vt.tearDownMain()
    }

    @Test
    fun `opening an initialized manga with chapters does not call the source`() = runTest(vt.scheduler) {
        createModel(manga = manga(initialized = true), chapters = listOf(chapter()))
        advanceUntilIdle()

        coVerify(exactly = 0) { updateMangaFromRemote(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `opening a manga that is not initialized fetches details only`() = runTest(vt.scheduler) {
        createModel(manga = manga(initialized = false), chapters = listOf(chapter()))
        advanceUntilIdle()

        coVerify(exactly = 1) { updateMangaFromRemote(any(), any(), any(), any(), any(), any()) }
        coVerify { updateMangaFromRemote(any(), any(), fetchDetails = true, fetchChapters = false, any(), any()) }
    }

    @Test
    fun `opening a manga with no chapters fetches chapters only`() = runTest(vt.scheduler) {
        createModel(manga = manga(initialized = true), chapters = emptyList())
        advanceUntilIdle()

        coVerify(exactly = 1) { updateMangaFromRemote(any(), any(), any(), any(), any(), any()) }
        coVerify { updateMangaFromRemote(any(), any(), fetchDetails = false, fetchChapters = true, any(), any()) }
    }

    @Test
    fun `a manual refresh fetches details and chapters`() = runTest(vt.scheduler) {
        val model = createModel(manga = manga(initialized = true), chapters = listOf(chapter()))
        advanceUntilIdle()

        model.fetchAllFromSource()
        advanceUntilIdle()

        coVerify(exactly = 1) { updateMangaFromRemote(any(), any(), any(), any(), any(), any()) }
        coVerify { updateMangaFromRemote(any(), any(), fetchDetails = true, fetchChapters = true, any(), any()) }
    }

    private fun createModel(manga: Manga, chapters: List<Chapter>): MangaScreenModel {
        val getMangaAndChapters = mockk<GetMangaWithChapters> {
            coEvery { subscribe(any(), any()) } returns emptyFlow()
            coEvery { awaitManga(manga.id) } returns manga
            coEvery { awaitChapters(manga.id, any()) } returns chapters
        }
        return MangaScreenModel(
            context = mockk(relaxed = true),
            lifecycle = mockk<Lifecycle>(relaxed = true),
            mangaId = manga.id,
            isFromSource = false,
            libraryPreferences = LibraryPreferences(InMemoryPreferenceStore()),
            trackPreferences = TrackPreferences(InMemoryPreferenceStore()),
            readerPreferences = ReaderPreferences(InMemoryPreferenceStore()),
            trackerManager = mockk<TrackerManager> { every { loggedInTrackersFlow() } returns emptyFlow() },
            downloadManager = mockk<MangaDownloadManager>(relaxed = true) {
                every { queueState } returns MutableStateFlow(emptyList())
                every { statusFlow() } returns emptyFlow()
                every { progressFlow() } returns emptyFlow()
            },
            getMangaAndChapters = getMangaAndChapters,
            getTracks = mockk<GetMangaTracks> { every { subscribe(any<Long>()) } returns emptyFlow() },
            updateMangaFromRemote = updateMangaFromRemote,
            translationManager = mockk<TranslationManager> {
                every { translationStates } returns MutableStateFlow(emptyMap())
            },
            ioDispatcher = vt.io,
        )
    }

    private fun manga(initialized: Boolean) = Manga.create().copy(id = 1L, source = 1L, initialized = initialized)

    private fun chapter() = Chapter.create().copy(id = 10L, mangaId = 1L)
}
