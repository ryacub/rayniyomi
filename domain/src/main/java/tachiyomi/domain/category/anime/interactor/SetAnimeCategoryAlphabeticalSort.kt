package tachiyomi.domain.category.anime.interactor

import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository
import tachiyomi.domain.category.interactor.SetCategoryAlphabeticalSort
import tachiyomi.domain.category.interactor.asCategoryRepositoryOps

class SetAnimeCategoryAlphabeticalSort(
    private val categoryRepository: AnimeCategoryRepository,
) {

    private val setCategoryAlphabeticalSort =
        SetCategoryAlphabeticalSort(categoryRepository.asCategoryRepositoryOps())

    suspend fun await(enabled: Boolean) {
        setCategoryAlphabeticalSort.await(enabled)
    }
}
