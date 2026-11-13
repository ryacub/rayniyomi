package eu.kanade.tachiyomi.ui.player

import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.model.Episode

class PlayerEpisodeListManagerTest {

    private lateinit var manager: PlayerEpisodeListManager
    private lateinit var mockGetEpisodes: GetEpisodesByAnimeId
    private lateinit var mockDownloadManager: AnimeDownloadManager
    private lateinit var mockBasePreferences: BasePreferences

    @BeforeEach
    fun setup() {
        mockGetEpisodes = mockk()
        mockDownloadManager = mockk()
        mockBasePreferences = mockk()

        manager = PlayerEpisodeListManager(
            getEpisodesByAnimeId = mockGetEpisodes,
            downloadManager = mockDownloadManager,
            basePreferences = mockBasePreferences,
        )
    }

    private fun createEpisode(
        id: Long,
        seen: Boolean = false,
        bookmark: Boolean = false,
        fillermark: Boolean = false,
        name: String = "Episode $id",
    ): Episode {
        return Episode.create().copy(
            id = id,
            seen = seen,
            bookmark = bookmark,
            fillermark = fillermark,
            name = name,
            scanlator = "",
        )
    }

    private fun createMockAnime(
        unseenFilter: Long = 0L,
        downloadedFilter: Long = 0L,
        bookmarkedFilter: Long = 0L,
        fillermarkedFilter: Long = 0L,
    ): Anime {
        return mockk<Anime>(relaxed = true).also {
            every { it.unseenFilterRaw } returns unseenFilter
            every { it.downloadedFilterRaw } returns downloadedFilter
            every { it.bookmarkedFilterRaw } returns bookmarkedFilter
            every { it.fillermarkedFilterRaw } returns fillermarkedFilter
            every { it.title } returns "Test Anime"
            every { it.source } returns 1L
        }
    }

    @Test
    fun `initEpisodeList keeps the episode memo from the database`() = runTest {
        val memo = buildJsonObject { put("token", "abc") }
        val anime = createMockAnime()
        every { anime.id } returns 1L
        coEvery { mockGetEpisodes.await(1L) } returns listOf(
            Episode.create().copy(
                id = 1L,
                animeId = 1L,
                url = "e1",
                name = "Episode 1",
                memo = memo,
            ),
        )
        every { mockBasePreferences.downloadedOnly() } returns mockk { every { get() } returns false }

        val episodes = manager.initEpisodeList(anime)

        assertEquals(memo, episodes.single().memo)
    }

    @Test
    fun `filterEpisodeList filters seen episodes when EPISODE_SHOW_UNSEEN`() = runTest {
        val episodes = listOf(
            createEpisode(1, seen = false),
            createEpisode(2, seen = true),
            createEpisode(3, seen = false),
        )
        val anime = createMockAnime(unseenFilter = Anime.EPISODE_SHOW_UNSEEN)
        manager.setEpisodeId(1L)

        val filtered = manager.filterEpisodeList(episodes, anime)

        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.id == 1L })
        assertTrue(filtered.any { it.id == 3L })
        assertFalse(filtered.any { it.id == 2L })
    }

    @Test
    fun `filterEpisodeList filters unseen episodes when EPISODE_SHOW_SEEN`() = runTest {
        val episodes = listOf(
            createEpisode(1, seen = false),
            createEpisode(2, seen = true),
            createEpisode(3, seen = false),
        )
        val anime = createMockAnime(unseenFilter = Anime.EPISODE_SHOW_SEEN)
        manager.setEpisodeId(2L)

        val filtered = manager.filterEpisodeList(episodes, anime)

        assertEquals(1, filtered.size)
        assertTrue(filtered.any { it.id == 2L })
    }

    @Test
    fun `filterEpisodeList filters bookmarked episodes when EPISODE_SHOW_NOT_BOOKMARKED`() = runTest {
        val episodes = listOf(
            createEpisode(1, bookmark = false),
            createEpisode(2, bookmark = true),
            createEpisode(3, bookmark = false),
        )
        val anime = createMockAnime(bookmarkedFilter = Anime.EPISODE_SHOW_NOT_BOOKMARKED)
        manager.setEpisodeId(1L)

        val filtered = manager.filterEpisodeList(episodes, anime)

        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.id == 1L })
        assertTrue(filtered.any { it.id == 3L })
    }

    @Test
    fun `filterEpisodeList filters fillermarked episodes when EPISODE_SHOW_NOT_FILLERMARKED`() = runTest {
        val episodes = listOf(
            createEpisode(1, fillermark = false),
            createEpisode(2, fillermark = true),
            createEpisode(3, fillermark = false),
        )
        val anime = createMockAnime(fillermarkedFilter = Anime.EPISODE_SHOW_NOT_FILLERMARKED)
        manager.setEpisodeId(1L)

        val filtered = manager.filterEpisodeList(episodes, anime)

        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.id == 1L })
        assertTrue(filtered.any { it.id == 3L })
    }

    @Test
    fun `filterEpisodeList always includes selected episode even if filtered`() = runTest {
        val episodes = listOf(
            createEpisode(1, seen = false),
            createEpisode(2, seen = true),
            createEpisode(3, seen = false),
        )
        val anime = createMockAnime(unseenFilter = Anime.EPISODE_SHOW_SEEN)
        manager.setEpisodeId(1L) // Selected episode is unseen

        val filtered = manager.filterEpisodeList(episodes, anime)

        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.id == 1L }) // Selected episode included
        assertTrue(filtered.any { it.id == 2L })
    }

    @Test
    fun `filterEpisodeList skips download checks when no filters are active`() = runTest {
        val episodes = listOf(
            createEpisode(1, seen = false),
            createEpisode(2, seen = true),
        )
        val anime = createMockAnime()
        manager.setEpisodeId(1L)

        manager.filterEpisodeList(episodes, anime)

        verify(exactly = 0) {
            mockDownloadManager.isEpisodeDownloaded(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `filterEpisodeList checks downloads when downloaded filter is active`() = runTest {
        val episodes = listOf(
            createEpisode(1, seen = false),
            createEpisode(2, seen = true),
        )
        val anime = createMockAnime(downloadedFilter = Anime.EPISODE_SHOW_DOWNLOADED)
        manager.setEpisodeId(1L)
        every { mockDownloadManager.isEpisodeDownloaded(any(), any(), any(), any(), any()) } returns true

        manager.filterEpisodeList(episodes, anime)

        verify(exactly = episodes.size) {
            mockDownloadManager.isEpisodeDownloaded(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `getCurrentEpisodeIndex returns correct index`() = runTest {
        val episodes = listOf(
            createEpisode(1),
            createEpisode(2),
            createEpisode(3),
        )
        val anime = createMockAnime()
        manager.setEpisodeId(2L)
        manager.updateEpisodeList(episodes, anime)
        manager.setCurrentEpisode(episodes[1])

        val index = manager.getCurrentEpisodeIndex()

        assertEquals(1, index)
    }

    @Test
    fun `getCurrentEpisodeIndex returns -1 when episode not found`() = runTest {
        val episodes = listOf(
            createEpisode(1),
            createEpisode(2),
        )
        val anime = createMockAnime()
        manager.setEpisodeId(1L)
        manager.updateEpisodeList(episodes, anime)
        manager.setCurrentEpisode(createEpisode(99)) // Not in list

        val index = manager.getCurrentEpisodeIndex()

        assertEquals(-1, index)
    }

    @Test
    fun `getAdjacentEpisodeId returns -1 for previous at first episode`() = runTest {
        val episodes = listOf(
            createEpisode(1),
            createEpisode(2),
            createEpisode(3),
        )
        val anime = createMockAnime()
        manager.setEpisodeId(1L)
        manager.updateEpisodeList(episodes, anime)
        manager.setCurrentEpisode(episodes[0])

        val adjacentId = manager.getAdjacentEpisodeId(previous = true)

        assertEquals(-1L, adjacentId)
    }

    @Test
    fun `getAdjacentEpisodeId returns -1 for next at last episode`() = runTest {
        val episodes = listOf(
            createEpisode(1),
            createEpisode(2),
            createEpisode(3),
        )
        val anime = createMockAnime()
        manager.setEpisodeId(3L)
        manager.updateEpisodeList(episodes, anime)
        manager.setCurrentEpisode(episodes[2])

        val adjacentId = manager.getAdjacentEpisodeId(previous = false)

        assertEquals(-1L, adjacentId)
    }

    @Test
    fun `getAdjacentEpisodeId returns correct previous episode`() = runTest {
        val episodes = listOf(
            createEpisode(1),
            createEpisode(2),
            createEpisode(3),
        )
        val anime = createMockAnime()
        manager.setEpisodeId(2L)
        manager.updateEpisodeList(episodes, anime)
        manager.setCurrentEpisode(episodes[1])

        val adjacentId = manager.getAdjacentEpisodeId(previous = true)

        assertEquals(1L, adjacentId)
    }

    @Test
    fun `getAdjacentEpisodeId returns correct next episode`() = runTest {
        val episodes = listOf(
            createEpisode(1),
            createEpisode(2),
            createEpisode(3),
        )
        val anime = createMockAnime()
        manager.setEpisodeId(2L)
        manager.updateEpisodeList(episodes, anime)
        manager.setCurrentEpisode(episodes[1])

        val adjacentId = manager.getAdjacentEpisodeId(previous = false)

        assertEquals(3L, adjacentId)
    }

    @Test
    fun `updateNavigationState sets hasPreviousEpisode false at first episode`() = runTest {
        val episodes = listOf(
            createEpisode(1),
            createEpisode(2),
            createEpisode(3),
        )
        val anime = createMockAnime()
        manager.setEpisodeId(1L)
        manager.updateEpisodeList(episodes, anime)
        manager.setCurrentEpisode(episodes[0])

        manager.updateNavigationState()

        assertFalse(manager.hasPreviousEpisode.first())
        assertTrue(manager.hasNextEpisode.first())
    }

    @Test
    fun `updateNavigationState sets hasNextEpisode false at last episode`() = runTest {
        val episodes = listOf(
            createEpisode(1),
            createEpisode(2),
            createEpisode(3),
        )
        val anime = createMockAnime()
        manager.setEpisodeId(3L)
        manager.updateEpisodeList(episodes, anime)
        manager.setCurrentEpisode(episodes[2])

        manager.updateNavigationState()

        assertTrue(manager.hasPreviousEpisode.first())
        assertFalse(manager.hasNextEpisode.first())
    }

    @Test
    fun `updateNavigationState sets both true at middle episode`() = runTest {
        val episodes = listOf(
            createEpisode(1),
            createEpisode(2),
            createEpisode(3),
        )
        val anime = createMockAnime()
        manager.setEpisodeId(2L)
        manager.updateEpisodeList(episodes, anime)
        manager.setCurrentEpisode(episodes[1])

        manager.updateNavigationState()

        assertTrue(manager.hasPreviousEpisode.first())
        assertTrue(manager.hasNextEpisode.first())
    }

    @Test
    fun `updateEpisodeList updates currentPlaylist state flow`() = runTest {
        val episodes = listOf(
            createEpisode(1),
            createEpisode(2),
        )
        val anime = createMockAnime()
        manager.setEpisodeId(1L)

        manager.updateEpisodeList(episodes, anime)

        val playlist = manager.currentPlaylist.first()
        assertEquals(2, playlist.size)
    }

    @Test
    fun `patchEpisode replaces the episode in the playlist and the current episode`() = runTest {
        val episodes = listOf(createEpisode(1), createEpisode(2), createEpisode(3))
        manager.setEpisodeId(2L)
        manager.updateEpisodeList(episodes, null)
        manager.setCurrentEpisode(episodes[1])

        val stored = manager.patchEpisode(2L) { it.copy(lastSecondSeen = 5_000L, bookmark = true) }

        assertEquals(5_000L, stored?.lastSecondSeen)
        assertEquals(stored, manager.currentEpisode.value)
        assertEquals(stored, manager.currentPlaylist.value.single { it.id == 2L })
        assertEquals(0L, manager.currentPlaylist.value.single { it.id == 1L }.lastSecondSeen)
    }

    @Test
    fun `patchEpisode does not re-filter the playlist`() = runTest {
        val episodes = listOf(createEpisode(1), createEpisode(2), createEpisode(3))
        val anime = createMockAnime(unseenFilter = Anime.EPISODE_SHOW_UNSEEN)
        manager.setEpisodeId(2L)
        manager.updateEpisodeList(episodes, anime)
        manager.setCurrentEpisode(episodes[1])

        manager.patchEpisode(2L) { it.copy(seen = true) }
        manager.patchEpisode(3L) { it.copy(seen = true) }

        assertEquals(listOf(1L, 2L, 3L), manager.currentPlaylist.value.map { it.id })
    }

    @Test
    fun `patchEpisode returns null for an episode that is not in the playlist`() = runTest {
        val episodes = listOf(createEpisode(1))
        manager.setEpisodeId(1L)
        manager.updateEpisodeList(episodes, null)

        assertEquals(null, manager.patchEpisode(99L) { it.copy(seen = true) })
        assertEquals(episodes, manager.currentPlaylist.value)
    }
}
