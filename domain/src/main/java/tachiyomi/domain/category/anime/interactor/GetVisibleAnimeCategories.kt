package tachiyomi.domain.category.anime.interactor

import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository
import tachiyomi.domain.category.interactor.GetVisibleCategories
import tachiyomi.domain.category.interactor.asCategoryRepositoryOps
import tachiyomi.domain.category.model.Category

class GetVisibleAnimeCategories(
    private val categoryRepository: AnimeCategoryRepository,
) {
    private val getVisibleCategories = GetVisibleCategories(categoryRepository.asCategoryRepositoryOps())

    fun subscribe() = getVisibleCategories.subscribe()

    fun subscribe(animeId: Long) = getVisibleCategories.subscribe(animeId)

    suspend fun await(): List<Category> = getVisibleCategories.await()

    suspend fun await(animeId: Long): List<Category> = getVisibleCategories.await(animeId)
}
