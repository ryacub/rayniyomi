package tachiyomi.domain.category.manga.interactor

import tachiyomi.domain.category.manga.repository.MangaCategoryRepository
import tachiyomi.domain.category.model.CategoryFlags
import tachiyomi.domain.category.model.CategoryUpdate

class SetMangaCategoryAlphabeticalSort(
    private val categoryRepository: MangaCategoryRepository,
) {

    suspend fun await(enabled: Boolean) {
        val updates = categoryRepository.getAllMangaCategories().map { category ->
            val updatedFlags = if (enabled) {
                category.flags or CategoryFlags.CATEGORY_FLAG_SORT_ALPHABETICAL
            } else {
                category.flags and CategoryFlags.CATEGORY_FLAG_SORT_ALPHABETICAL.inv()
            }
            CategoryUpdate(
                id = category.id,
                flags = updatedFlags,
            )
        }
        categoryRepository.updatePartialMangaCategories(updates)
    }
}
