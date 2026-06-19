package tachiyomi.domain.category.manga.interactor

import tachiyomi.domain.category.interactor.CreateCategoryWithName
import tachiyomi.domain.category.interactor.asCategoryRepositoryOps
import tachiyomi.domain.category.manga.repository.MangaCategoryRepository
import tachiyomi.domain.library.service.LibraryPreferences

class CreateMangaCategoryWithName(
    private val categoryRepository: MangaCategoryRepository,
    private val preferences: LibraryPreferences,
) {

    private val createCategory = CreateCategoryWithName(
        repository = categoryRepository.asCategoryRepositoryOps(),
        initialFlags = {
            val sort = preferences.mangaSortingMode().get()
            sort.type.flag or sort.direction.flag
        },
    )

    suspend fun await(name: String, parentId: Long? = null): Result {
        return when (val result = createCategory.await(name, parentId)) {
            CreateCategoryWithName.Result.Success -> Result.Success
            CreateCategoryWithName.Result.InvalidParent -> Result.InvalidParent
            is CreateCategoryWithName.Result.InternalError -> Result.InternalError(result.error)
        }
    }

    sealed interface Result {
        data object Success : Result
        data object InvalidParent : Result
        data class InternalError(val error: Throwable) : Result
    }
}
