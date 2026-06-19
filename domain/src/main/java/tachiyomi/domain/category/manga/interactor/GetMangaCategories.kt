package tachiyomi.domain.category.manga.interactor

import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.asCategoryRepositoryOps
import tachiyomi.domain.category.manga.repository.MangaCategoryRepository
import tachiyomi.domain.category.model.Category

class GetMangaCategories(
    private val categoryRepository: MangaCategoryRepository,
) {
    private val getCategories = GetCategories(categoryRepository.asCategoryRepositoryOps())

    fun subscribe() = getCategories.subscribe()

    fun subscribe(mangaId: Long) = getCategories.subscribe(mangaId)

    suspend fun await(): List<Category> = getCategories.await()

    suspend fun await(mangaId: Long): List<Category> = getCategories.await(mangaId)
}
