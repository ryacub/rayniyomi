package tachiyomi.domain.category.anime.interactor

import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository
import tachiyomi.domain.category.interactor.asCategoryRepositoryOps
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.category.interactor.CreateCategoryWithName as SharedCreateCategoryWithName

class CreateAnimeCategoryWithName(
    private val categoryRepository: AnimeCategoryRepository,
    private val preferences: LibraryPreferences,
) {

    private val createCategory = SharedCreateCategoryWithName(
        repository = categoryRepository.asCategoryRepositoryOps(),
        initialFlags = {
            val sort = preferences.animeSortingMode().get()
            sort.type.flag or sort.direction.flag
        },
    )

    suspend fun await(name: String, parentId: Long? = null): Result {
        return when (val result = createCategory.await(name, parentId)) {
            SharedCreateCategoryWithName.Result.Success -> Result.Success
            SharedCreateCategoryWithName.Result.InvalidParent -> Result.InvalidParent
            is SharedCreateCategoryWithName.Result.InternalError -> Result.InternalError(result.error)
        }
    }

    sealed interface Result {
        data object Success : Result
        data object InvalidParent : Result
        data class InternalError(val error: Throwable) : Result
    }
}
