package tachiyomi.domain.items

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.entries.anime.interactor.GetAnimeFavorites
import tachiyomi.domain.entries.anime.interactor.SetAnimeEpisodeFlags
import tachiyomi.domain.entries.anime.interactor.SetAnimeSeasonFlags
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.manga.interactor.GetMangaFavorites
import tachiyomi.domain.entries.manga.interactor.SetMangaChapterFlags
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.items.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.items.episode.interactor.SetAnimeDefaultEpisodeFlags
import tachiyomi.domain.items.season.interactor.SetAnimeDefaultSeasonFlags
import tachiyomi.domain.library.service.LibraryPreferences

class SetDefaultItemFlagsTest {

    private val libraryPreferences = LibraryPreferences(InMemoryPreferenceStore())

    @Test
    fun `apply to all writes every favorite manga in one update`() = runTest {
        val repository = mockk<MangaRepository>(relaxed = true) {
            coEvery { getMangaFavorites() } returns listOf(1L, 2L, 3L).map { Manga.create().copy(id = it) }
        }
        val setDefaults = SetMangaDefaultChapterFlags(
            libraryPreferences,
            SetMangaChapterFlags(repository),
            GetMangaFavorites(repository),
        )

        setDefaults.awaitAll()

        coVerify(exactly = 1) {
            repository.updateAllManga(match { updates -> updates.map { it.id } == listOf(1L, 2L, 3L) })
        }
        coVerify(exactly = 0) { repository.updateManga(any()) }
    }

    @Test
    fun `apply to all writes every favorite anime episode flag in one update`() = runTest {
        val repository = favoriteAnimeRepository()
        val setDefaults = SetAnimeDefaultEpisodeFlags(
            libraryPreferences,
            SetAnimeEpisodeFlags(repository),
            GetAnimeFavorites(repository),
        )

        setDefaults.awaitAll()

        coVerify(exactly = 1) {
            repository.updateAllAnime(match { updates -> updates.map { it.id } == listOf(1L, 2L, 3L) })
        }
        coVerify(exactly = 0) { repository.updateAnime(any()) }
    }

    @Test
    fun `apply to all writes every favorite anime season flag in one update`() = runTest {
        val repository = favoriteAnimeRepository()
        val setDefaults = SetAnimeDefaultSeasonFlags(
            libraryPreferences,
            SetAnimeSeasonFlags(repository),
            GetAnimeFavorites(repository),
        )

        setDefaults.awaitAll()

        coVerify(exactly = 1) {
            repository.updateAllAnime(match { updates -> updates.map { it.id } == listOf(1L, 2L, 3L) })
        }
        coVerify(exactly = 0) { repository.updateAnime(any()) }
    }

    private fun favoriteAnimeRepository() = mockk<AnimeRepository>(relaxed = true) {
        coEvery { getAnimeFavorites() } returns listOf(1L, 2L, 3L).map { Anime.create().copy(id = it) }
    }
}
