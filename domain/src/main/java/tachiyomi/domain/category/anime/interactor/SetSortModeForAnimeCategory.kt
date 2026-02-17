package tachiyomi.domain.category.anime.interactor

import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryUpdate
import tachiyomi.domain.library.anime.model.AnimeLibrarySort
import tachiyomi.domain.library.model.plus
import tachiyomi.domain.library.service.LibraryPreferences
import kotlin.random.Random

class SetSortModeForAnimeCategory(
    private val preferences: LibraryPreferences,
    private val categoryRepository: AnimeCategoryRepository,
) {

    private val sortMask: Long = AnimeLibrarySort.Type.Alphabetical.mask or AnimeLibrarySort.Direction.Ascending.mask

    suspend fun await(
        categoryId: Long?,
        type: AnimeLibrarySort.Type,
        direction: AnimeLibrarySort.Direction,
    ) {
        val category = categoryId?.let { categoryRepository.getAnimeCategory(it) }
        val flags = ((category?.flags ?: 0L) and sortMask.inv()) + type + direction
        if (type == AnimeLibrarySort.Type.Random) {
            preferences.randomAnimeSortSeed().set(Random.nextInt())
        }
        if (category != null && preferences.categorizedDisplaySettings().get()) {
            categoryRepository.updatePartialAnimeCategory(
                CategoryUpdate(
                    id = category.id,
                    flags = flags,
                ),
            )
        } else {
            preferences.animeSortingMode().set(AnimeLibrarySort(type, direction))
            val updates = categoryRepository.getAllAnimeCategories().map {
                CategoryUpdate(
                    id = it.id,
                    flags = (it.flags and sortMask.inv()) + type + direction,
                )
            }
            categoryRepository.updatePartialAnimeCategories(updates)
        }
    }

    suspend fun await(
        category: Category?,
        type: AnimeLibrarySort.Type,
        direction: AnimeLibrarySort.Direction,
    ) {
        await(category?.id, type, direction)
    }
}
