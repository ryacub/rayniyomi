package tachiyomi.domain.category.manga.interactor

import tachiyomi.domain.category.interactor.ResetCategoryFlags
import tachiyomi.domain.category.interactor.asCategoryRepositoryOps
import tachiyomi.domain.category.manga.repository.MangaCategoryRepository
import tachiyomi.domain.library.model.plus
import tachiyomi.domain.library.service.LibraryPreferences

class ResetMangaCategoryFlags(
    private val preferences: LibraryPreferences,
    private val categoryRepository: MangaCategoryRepository,
) {

    private val resetCategoryFlags = ResetCategoryFlags(categoryRepository.asCategoryRepositoryOps())

    suspend fun await() {
        val sort = preferences.mangaSortingMode().get()
        resetCategoryFlags.await(sort.type + sort.direction)
    }
}
