package tachiyomi.domain.category.anime.interactor

import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository
import tachiyomi.domain.category.interactor.RenameCategory
import tachiyomi.domain.category.interactor.asCategoryRepositoryOps
import tachiyomi.domain.category.model.Category

class RenameAnimeCategory(
    private val categoryRepository: AnimeCategoryRepository,
) {

    private val renameCategory = RenameCategory(categoryRepository.asCategoryRepositoryOps())

    suspend fun await(categoryId: Long, name: String): Result {
        return when (val result = renameCategory.await(categoryId, name)) {
            RenameCategory.Result.Success -> Result.Success
            is RenameCategory.Result.InternalError -> Result.InternalError(result.error)
        }
    }

    suspend fun await(category: Category, name: String) = await(category.id, name)

    sealed interface Result {
        data object Success : Result
        data class InternalError(val error: Throwable) : Result
    }
}
