package tachiyomi.domain.category.manga.interactor

import tachiyomi.domain.category.interactor.SetEntryCategories
import tachiyomi.domain.entries.manga.repository.MangaRepository

class SetMangaCategories(
    private val mangaRepository: MangaRepository,
) {

    private val setEntryCategories = SetEntryCategories(mangaRepository::setMangaCategories)

    suspend fun await(mangaId: Long, categoryIds: List<Long>) {
        setEntryCategories.await(mangaId, categoryIds)
    }
}
