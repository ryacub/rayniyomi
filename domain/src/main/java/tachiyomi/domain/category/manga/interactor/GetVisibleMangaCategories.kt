package tachiyomi.domain.category.manga.interactor

import tachiyomi.domain.category.interactor.GetVisibleCategories
import tachiyomi.domain.category.interactor.asCategoryRepositoryOps
import tachiyomi.domain.category.manga.repository.MangaCategoryRepository
import tachiyomi.domain.category.model.Category

class GetVisibleMangaCategories(
    private val categoryRepository: MangaCategoryRepository,
) {
    private val getVisibleCategories = GetVisibleCategories(categoryRepository.asCategoryRepositoryOps())

    fun subscribe() = getVisibleCategories.subscribe()

    fun subscribe(mangaId: Long) = getVisibleCategories.subscribe(mangaId)

    suspend fun await(): List<Category> = getVisibleCategories.await()

    suspend fun await(mangaId: Long): List<Category> = getVisibleCategories.await(mangaId)
}
