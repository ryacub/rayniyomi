package tachiyomi.domain.category.manga.interactor

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.domain.category.interactor.ReorderCategory
import tachiyomi.domain.category.interactor.asCategoryRepositoryOps
import tachiyomi.domain.category.manga.repository.MangaCategoryRepository
import tachiyomi.domain.category.model.Category

class ReorderMangaCategory(
    private val categoryRepository: MangaCategoryRepository,
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
