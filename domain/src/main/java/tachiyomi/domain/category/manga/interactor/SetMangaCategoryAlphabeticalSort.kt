package tachiyomi.domain.category.manga.interactor

import tachiyomi.domain.category.interactor.SetCategoryAlphabeticalSort
import tachiyomi.domain.category.interactor.asCategoryRepositoryOps
import tachiyomi.domain.category.manga.repository.MangaCategoryRepository

class SetMangaCategoryAlphabeticalSort(
    private val categoryRepository: MangaCategoryRepository,
) {

    private val setCategoryAlphabeticalSort =
        SetCategoryAlphabeticalSort(categoryRepository.asCategoryRepositoryOps())

    suspend fun await(enabled: Boolean) {
        setCategoryAlphabeticalSort.await(enabled)
    }
}
