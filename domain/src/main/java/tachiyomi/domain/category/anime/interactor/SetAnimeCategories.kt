package tachiyomi.domain.category.anime.interactor

import tachiyomi.domain.category.interactor.SetEntryCategories
import tachiyomi.domain.entries.anime.repository.AnimeRepository

class SetAnimeCategories(
    private val animeRepository: AnimeRepository,
) {

    private val setEntryCategories = SetEntryCategories(animeRepository::setAnimeCategories)

    suspend fun await(animeId: Long, categoryIds: List<Long>) {
        setEntryCategories.await(animeId, categoryIds)
    }
}
