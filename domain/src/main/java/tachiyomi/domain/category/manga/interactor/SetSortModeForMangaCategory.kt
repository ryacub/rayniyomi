package tachiyomi.domain.category.manga.interactor

import tachiyomi.domain.category.manga.repository.MangaCategoryRepository
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryUpdate
import tachiyomi.domain.library.manga.model.MangaLibrarySort
import tachiyomi.domain.library.model.plus
import tachiyomi.domain.library.service.LibraryPreferences
import kotlin.random.Random

class SetSortModeForMangaCategory(
    private val preferences: LibraryPreferences,
    private val categoryRepository: MangaCategoryRepository,
) {

    private val sortMask: Long = MangaLibrarySort.Type.Alphabetical.mask or MangaLibrarySort.Direction.Ascending.mask

    suspend fun await(
        categoryId: Long?,
        type: MangaLibrarySort.Type,
        direction: MangaLibrarySort.Direction,
    ) {
        val category = categoryId?.let { categoryRepository.getMangaCategory(it) }
        val flags = ((category?.flags ?: 0L) and sortMask.inv()) + type + direction
        if (type == MangaLibrarySort.Type.Random) {
            preferences.randomMangaSortSeed().set(Random.nextInt())
        }
        if (category != null && preferences.categorizedDisplaySettings().get()) {
            categoryRepository.updatePartialMangaCategory(
                CategoryUpdate(
                    id = category.id,
                    flags = flags,
                ),
            )
        } else {
            preferences.mangaSortingMode().set(MangaLibrarySort(type, direction))
            val updates = categoryRepository.getAllMangaCategories().map {
                CategoryUpdate(
                    id = it.id,
                    flags = (it.flags and sortMask.inv()) + type + direction,
                )
            }
            categoryRepository.updatePartialMangaCategories(updates)
        }
    }

    suspend fun await(
        category: Category?,
        type: MangaLibrarySort.Type,
        direction: MangaLibrarySort.Direction,
    ) {
        await(category?.id, type, direction)
    }
}
