package tachiyomi.domain.category.anime.interactor

import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository
import tachiyomi.domain.category.interactor.HideCategory
import tachiyomi.domain.category.interactor.asCategoryRepositoryOps
import tachiyomi.domain.category.model.Category

class HideAnimeCategory(
    private val categoryRepository: AnimeCategoryRepository,
) {

    private val hideCategory = HideCategory(categoryRepository.asCategoryRepositoryOps())

    suspend fun await(category: Category): Result {
        return when (val result = hideCategory.await(category)) {
            HideCategory.Result.Success -> Result.Success
            is HideCategory.Result.InternalError -> Result.InternalError(result.error)
        }
    }

    sealed class Result {
        data object Success : Result()
        data class InternalError(val error: Throwable) : Result()
    }
}
