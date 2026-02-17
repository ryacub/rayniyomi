package tachiyomi.domain.category.anime.interactor

import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository
import tachiyomi.domain.category.model.CategoryFlags
import tachiyomi.domain.category.model.CategoryUpdate

class SetAnimeCategoryAlphabeticalSort(
    private val categoryRepository: AnimeCategoryRepository,
) {

    suspend fun await(enabled: Boolean) {
        val updates = categoryRepository.getAllAnimeCategories().map { category ->
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
        categoryRepository.updatePartialAnimeCategories(updates)
    }
}
