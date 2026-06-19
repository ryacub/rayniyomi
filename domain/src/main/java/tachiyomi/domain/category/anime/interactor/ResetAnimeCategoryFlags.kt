package tachiyomi.domain.category.anime.interactor

import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository
import tachiyomi.domain.category.interactor.ResetCategoryFlags
import tachiyomi.domain.category.interactor.asCategoryRepositoryOps
import tachiyomi.domain.library.model.plus
import tachiyomi.domain.library.service.LibraryPreferences

class ResetAnimeCategoryFlags(
    private val preferences: LibraryPreferences,
    private val categoryRepository: AnimeCategoryRepository,
) {

    private val resetCategoryFlags = ResetCategoryFlags(categoryRepository.asCategoryRepositoryOps())

    suspend fun await() {
        val sort = preferences.animeSortingMode().get()
        resetCategoryFlags.await(sort.type + sort.direction)
    }
}
