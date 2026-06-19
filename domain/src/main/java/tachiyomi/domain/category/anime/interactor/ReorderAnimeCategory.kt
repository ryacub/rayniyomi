package tachiyomi.domain.category.anime.interactor

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository
import tachiyomi.domain.category.interactor.ReorderCategory
import tachiyomi.domain.category.interactor.asCategoryRepositoryOps
import tachiyomi.domain.category.model.Category

class ReorderAnimeCategory(
    private val categoryRepository: AnimeCategoryRepository,
) {

    private val mutex = Mutex()
    private val reorderCategory = ReorderCategory(categoryRepository.asCategoryRepositoryOps())

    suspend fun await(category: Category, newIndex: Int) = withNonCancellableContext {
        mutex.withLock {
            when (val result = reorderCategory.await(category, newIndex)) {
                ReorderCategory.Result.Success -> Result.Success
                ReorderCategory.Result.Unchanged -> Result.Unchanged
                is ReorderCategory.Result.InternalError -> Result.InternalError(result.error)
            }
        }
    }

    sealed interface Result {
        data object Success : Result
        data object Unchanged : Result
        data class InternalError(val error: Throwable) : Result
    }
}
