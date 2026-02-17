package tachiyomi.domain.category.anime.interactor

import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository
import tachiyomi.domain.category.model.CategoryUpdate
import tachiyomi.domain.library.model.plus
import tachiyomi.domain.library.service.LibraryPreferences

class ResetAnimeCategoryFlags(
    private val preferences: LibraryPreferences,
    private val categoryRepository: AnimeCategoryRepository,
) {

    private val sortMask = 0b01111100L

    suspend fun await() {
        val sort = preferences.animeSortingMode().get()
        val updates = categoryRepository.getAllAnimeCategories().map {
            CategoryUpdate(
                id = it.id,
                flags = (it.flags and sortMask.inv()) + sort.type + sort.direction,
            )
        }
        categoryRepository.updatePartialAnimeCategories(updates)
    }
}
