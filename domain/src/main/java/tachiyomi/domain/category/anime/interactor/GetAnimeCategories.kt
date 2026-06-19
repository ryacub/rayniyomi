package tachiyomi.domain.category.anime.interactor

import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.asCategoryRepositoryOps
import tachiyomi.domain.category.model.Category

class GetAnimeCategories(
    private val categoryRepository: AnimeCategoryRepository,
) {

    private val getCategories = GetCategories(categoryRepository.asCategoryRepositoryOps())

    fun subscribe() = getCategories.subscribe()

    fun subscribe(animeId: Long) = getCategories.subscribe(animeId)

    suspend fun await(): List<Category> = getCategories.await()

    suspend fun await(animeId: Long): List<Category> = getCategories.await(animeId)
}
