package eu.kanade.tachiyomi.ui.browse.manga.migration.search

import eu.kanade.domain.entries.manga.interactor.UpdateManga
import eu.kanade.domain.source.manga.interactor.UpdateMangaFromRemote
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.MangaSource
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import java.io.IOException

class MigrateMangaDialogScreenModelTest {

    private val oldManga = Manga.create().copy(id = 1L, source = 10L, title = "Old")
    private val newManga = Manga.create().copy(id = 2L, source = 20L, title = "New")
    private val allFlags = Int.MAX_VALUE

    private val oldSource = mockk<MangaSource>(relaxed = true)
    private val newSource = mockk<MangaSource>(relaxed = true)
    private val sourceManager = mockk<MangaSourceManager> {
        every { get(10L) } returns oldSource
        every { get(20L) } returns newSource
    }
    private val downloadManager = mockk<MangaDownloadManager>(relaxed = true)
    private val updateManga = mockk<UpdateManga>(relaxed = true)
    private val updateMangaFromRemote = mockk<UpdateMangaFromRemote>()
    private val getTracks = mockk<GetMangaTracks> {
        coEvery { await(any()) } returns emptyList()
    }
    private val trackerManager = mockk<TrackerManager> {
        every { trackers } returns emptyList()
    }

    private val model = MigrateMangaDialogScreenModel(
        sourceManager = sourceManager,
        downloadManager = downloadManager,
        updateManga = updateManga,
        getChaptersByMangaId = mockk(relaxed = true),
        updateMangaFromRemote = updateMangaFromRemote,
        updateChapter = mockk(relaxed = true),
        getCategories = mockk(relaxed = true),
        setMangaCategories = mockk(relaxed = true),
        getTracks = getTracks,
        insertTrack = mockk(relaxed = true),
        coverCache = mockk<MangaCoverCache>(relaxed = true),
        preferenceStore = mockk<PreferenceStore>(relaxed = true),
        trackerManager = trackerManager,
    )

    private fun remoteFetchSucceeds() {
        coEvery { updateMangaFromRemote(any<Manga>(), any(), any(), any(), any()) } returns Result.success(mockk())
    }

    @Test
    fun `failed remote fetch reports failure and leaves the new entry out of the library`() = runTest {
        coEvery {
            updateMangaFromRemote(any<Manga>(), any(), any(), any(), any())
        } returns Result.failure(IOException("offline"))

        val result: Any? = model.migrateManga(oldManga, newManga, replace = true, flags = allFlags)

        result shouldBe false
        model.state.value.isMigrating shouldBe false
        coVerify(exactly = 0) { updateManga.await(any()) }
        coVerify(exactly = 0) { updateManga.awaitUpdateFavorite(oldManga.id, false) }
    }

    @Test
    fun `rejected library write keeps the old entry and its downloads`() = runTest {
        remoteFetchSucceeds()
        coEvery { updateManga.await(any()) } returns false

        val result: Any? = model.migrateManga(oldManga, newManga, replace = true, flags = allFlags)

        result shouldBe false
        verify(exactly = 0) { downloadManager.deleteManga(any(), any(), any()) }
        coVerify(exactly = 0) { updateManga.awaitUpdateFavorite(oldManga.id, false) }
    }

    @Test
    fun `rejected unfavorite of the old entry keeps its downloads`() = runTest {
        remoteFetchSucceeds()
        coEvery { updateManga.await(any()) } returns true
        coEvery { updateManga.awaitUpdateFavorite(any(), any()) } returns false

        val result: Any? = model.migrateManga(oldManga, newManga, replace = true, flags = allFlags)

        result shouldBe false
        verify(exactly = 0) { downloadManager.deleteManga(any(), any(), any()) }
    }

    @Test
    fun `successful migration reports success`() = runTest {
        remoteFetchSucceeds()
        coEvery { updateManga.await(any()) } returns true
        coEvery { updateManga.awaitUpdateFavorite(any(), any()) } returns true

        val result: Any? = model.migrateManga(oldManga, newManga, replace = true, flags = allFlags)

        result shouldBe true
        verify(exactly = 1) { downloadManager.deleteManga(oldManga, oldSource, any()) }
        coVerify(exactly = 1) { updateManga.awaitUpdateFavorite(oldManga.id, false) }
    }

    @Test
    fun `cancellation propagates to the caller`() = runTest {
        coEvery {
            updateMangaFromRemote(any<Manga>(), any(), any(), any(), any())
        } throws CancellationException("left screen")

        val error = runCatching {
            model.migrateManga(oldManga, newManga, replace = true, flags = allFlags)
        }.exceptionOrNull()

        error.shouldBeInstanceOf<CancellationException>()
    }
}
