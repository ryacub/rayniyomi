package tachiyomi.domain.category.manga.interactor

import tachiyomi.domain.category.manga.repository.MangaCategoryRepository
import tachiyomi.domain.category.model.CategoryUpdate
import tachiyomi.domain.library.model.plus
import tachiyomi.domain.library.service.LibraryPreferences

class ResetMangaCategoryFlags(
    private val preferences: LibraryPreferences,
    private val categoryRepository: MangaCategoryRepository,
) {

    private val sortMask = 0b01111100L

    suspend fun await() {
        val sort = preferences.mangaSortingMode().get()
        val updates = categoryRepository.getAllMangaCategories().map {
            // Reset only sort mode bits; keep auxiliary flags such as alphabetical category sorting.
            CategoryUpdate(
                id = it.id,
                flags = (it.flags and sortMask.inv()) + sort.type + sort.direction,
            )
        }
        categoryRepository.updatePartialMangaCategories(updates)
    }
}
