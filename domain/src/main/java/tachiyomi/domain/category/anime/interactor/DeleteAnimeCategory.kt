package tachiyomi.domain.category.anime.interactor

import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository
import tachiyomi.domain.category.interactor.DeleteCategory
import tachiyomi.domain.category.interactor.asCategoryRepositoryOps
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences

class DeleteAnimeCategory(
    private val categoryRepository: AnimeCategoryRepository,
    private val libraryPreferences: LibraryPreferences,
    private val downloadPreferences: DownloadPreferences,
) {

    private val deleteCategory = DeleteCategory(
        repository = categoryRepository.asCategoryRepositoryOps(),
        defaultCategory = libraryPreferences.defaultAnimeCategory(),
        categoryPreferences = listOf(
            libraryPreferences.animeUpdateCategories(),
            libraryPreferences.animeUpdateCategoriesExclude(),
            downloadPreferences.removeExcludeAnimeCategories(),
            downloadPreferences.downloadNewEpisodeCategories(),
            downloadPreferences.downloadNewEpisodeCategoriesExclude(),
        ),
    )

    suspend fun await(categoryId: Long): Result {
        return when (val result = deleteCategory.await(categoryId)) {
            DeleteCategory.Result.Success -> Result.Success
            is DeleteCategory.Result.InternalError -> Result.InternalError(result.error)
        }
    }

    sealed interface Result {
        data object Success : Result
        data class InternalError(val error: Throwable) : Result
    }
}
