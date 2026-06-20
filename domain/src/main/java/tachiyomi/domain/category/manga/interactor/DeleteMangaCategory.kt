package tachiyomi.domain.category.manga.interactor

import tachiyomi.domain.category.interactor.DeleteCategory
import tachiyomi.domain.category.interactor.asCategoryRepositoryOps
import tachiyomi.domain.category.manga.repository.MangaCategoryRepository
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences

class DeleteMangaCategory(
    private val categoryRepository: MangaCategoryRepository,
    private val libraryPreferences: LibraryPreferences,
    private val downloadPreferences: DownloadPreferences,
) {

    private val deleteCategory = DeleteCategory(
        repository = categoryRepository.asCategoryRepositoryOps(),
        defaultCategory = libraryPreferences.defaultMangaCategory(),
        categoryPreferences = listOf(
            libraryPreferences.mangaUpdateCategories(),
            libraryPreferences.mangaUpdateCategoriesExclude(),
            downloadPreferences.removeExcludeCategories(),
            downloadPreferences.downloadNewChapterCategories(),
            downloadPreferences.downloadNewChapterCategoriesExclude(),
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
