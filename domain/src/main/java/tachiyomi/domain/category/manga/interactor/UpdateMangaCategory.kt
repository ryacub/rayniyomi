package tachiyomi.domain.category.manga.interactor

import tachiyomi.domain.category.interactor.UpdateCategory
import tachiyomi.domain.category.interactor.asCategoryRepositoryOps
import tachiyomi.domain.category.manga.repository.MangaCategoryRepository
import tachiyomi.domain.category.model.CategoryUpdate

class UpdateMangaCategory(
    private val categoryRepository: MangaCategoryRepository,
) {

    private val updateCategory = UpdateCategory(categoryRepository.asCategoryRepositoryOps())

    suspend fun await(payload: CategoryUpdate): Result {
        return when (val result = updateCategory.await(payload)) {
            UpdateCategory.Result.Success -> Result.Success
            is UpdateCategory.Result.InternalError -> Result.Error(result.error)
        }
    }

    sealed interface Result {
        data object Success : Result
        data class Error(val error: Exception) : Result
    }
}
