package eu.kanade.tachiyomi.ui.browse.anime.migration.search

import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.items.episode.interactor.SyncEpisodesWithSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
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
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import java.io.IOException

class MigrateAnimeDialogScreenModelTest {

    private val oldAnime = Anime.create().copy(id = 1L, source = 10L, title = "Old")
    private val newAnime = Anime.create().copy(id = 2L, source = 20L, title = "New")
    private val allFlags = Int.MAX_VALUE

    private val oldSource = mockk<AnimeSource>(relaxed = true)
    private val newSource = mockk<AnimeSource>(relaxed = true)
    private val sourceManager = mockk<AnimeSourceManager> {
        every { get(10L) } returns oldSource
        every { get(20L) } returns newSource
    }
    private val downloadManager = mockk<AnimeDownloadManager>(relaxed = true)
    private val updateAnime = mockk<UpdateAnime>(relaxed = true)
    private val getTracks = mockk<GetAnimeTracks> {
        coEvery { await(any()) } returns emptyList()
    }
    private val syncEpisodesWithSource = mockk<SyncEpisodesWithSource>(relaxed = true)
    private val trackerManager = mockk<TrackerManager> {
        every { trackers } returns emptyList()
    }

    private val model = MigrateAnimeDialogScreenModel(
        sourceManager = sourceManager,
        downloadManager = downloadManager,
        updateAnime = updateAnime,
        getEpisodesByAnimeId = mockk(relaxed = true),
        syncEpisodesWithSource = syncEpisodesWithSource,
        updateEpisode = mockk(relaxed = true),
        getCategories = mockk(relaxed = true),
        setAnimeCategories = mockk(relaxed = true),
        getTracks = getTracks,
        insertTrack = mockk(relaxed = true),
        coverCache = mockk(relaxed = true),
        backgroundCache = mockk(relaxed = true),
        preferenceStore = mockk<PreferenceStore>(relaxed = true),
        trackerManager = trackerManager,
    )

    @Test
    fun `failed episode fetch reports failure and leaves the new entry out of the library`() = runTest {
        coEvery { newSource.getEpisodeList(any()) } throws IOException("offline")

        val result: Any? = model.migrateAnime(oldAnime, newAnime, replace = true, flags = allFlags)

        result shouldBe false
        model.state.value.isMigrating shouldBe false
        coVerify(exactly = 0) { updateAnime.await(any()) }
        coVerify(exactly = 0) { updateAnime.awaitUpdateFavorite(oldAnime.id, false) }
    }

    @Test
    fun `failed episode sync reports failure and keeps the old entry and its downloads`() = runTest {
        coEvery { newSource.getEpisodeList(any()) } returns emptyList()
        coEvery { syncEpisodesWithSource.await(any(), any(), any(), any(), any()) } throws IllegalStateException("db")
        coEvery { updateAnime.await(any()) } returns true
        coEvery { updateAnime.awaitUpdateFavorite(any(), any()) } returns true

        val result = model.migrateAnime(oldAnime, newAnime, replace = true, flags = allFlags)

        result shouldBe false
        verify(exactly = 0) { downloadManager.deleteAnime(any(), any(), any()) }
        coVerify(exactly = 0) { updateAnime.awaitUpdateFavorite(oldAnime.id, false) }
    }

    @Test
    fun `rejected library write keeps the old entry and its downloads`() = runTest {
        coEvery { newSource.getEpisodeList(any()) } returns emptyList()
        coEvery { updateAnime.await(any()) } returns false

        val result: Any? = model.migrateAnime(oldAnime, newAnime, replace = true, flags = allFlags)

        result shouldBe false
        verify(exactly = 0) { downloadManager.deleteAnime(any(), any(), any()) }
        coVerify(exactly = 0) { updateAnime.awaitUpdateFavorite(oldAnime.id, false) }
    }

    @Test
    fun `rejected unfavorite of the old entry keeps its downloads`() = runTest {
        coEvery { newSource.getEpisodeList(any()) } returns emptyList()
        coEvery { updateAnime.await(any()) } returns true
        coEvery { updateAnime.awaitUpdateFavorite(any(), any()) } returns false

        val result: Any? = model.migrateAnime(oldAnime, newAnime, replace = true, flags = allFlags)

        result shouldBe false
        verify(exactly = 0) { downloadManager.deleteAnime(any(), any(), any()) }
    }

    @Test
    fun `successful migration reports success`() = runTest {
        coEvery { newSource.getEpisodeList(any()) } returns emptyList()
        coEvery { updateAnime.await(any()) } returns true
        coEvery { updateAnime.awaitUpdateFavorite(any(), any()) } returns true

        val result: Any? = model.migrateAnime(oldAnime, newAnime, replace = true, flags = allFlags)

        result shouldBe true
        verify(exactly = 1) { downloadManager.deleteAnime(oldAnime, oldSource, any()) }
        coVerify(exactly = 1) { updateAnime.awaitUpdateFavorite(oldAnime.id, false) }
    }

    @Test
    fun `cancellation propagates to the caller`() = runTest {
        coEvery { newSource.getEpisodeList(any()) } throws CancellationException("left screen")

        val error = runCatching {
            model.migrateAnime(oldAnime, newAnime, replace = true, flags = allFlags)
        }.exceptionOrNull()

        error.shouldBeInstanceOf<CancellationException>()
    }
}
