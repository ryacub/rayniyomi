package tachiyomi.domain.category.anime.interactor

import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository
import tachiyomi.domain.category.interactor.asCategoryRepositoryOps
import tachiyomi.domain.category.interactor.setSortModeForCategory
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.anime.model.AnimeLibrarySort
import tachiyomi.domain.library.service.LibraryPreferences
import kotlin.random.Random

class SetSortModeForAnimeCategory(
    private val preferences: LibraryPreferences,
    private val categoryRepository: AnimeCategoryRepository,
) {

    private val categoryOps = categoryRepository.asCategoryRepositoryOps()
    private val sortMask: Long = AnimeLibrarySort.Type.Alphabetical.mask or AnimeLibrarySort.Direction.Ascending.mask

    suspend fun await(
        categoryId: Long?,
        type: AnimeLibrarySort.Type,
        direction: AnimeLibrarySort.Direction,
    ) {
        setSortModeForCategory(
            repository = categoryOps,
            categoryId = categoryId,
            type = type,
            direction = direction,
            sortMask = sortMask,
            categorizedDisplaySettings = preferences.categorizedDisplaySettings().get(),
            isRandomSort = type == AnimeLibrarySort.Type.Random,
            onRandomSortSelected = { preferences.randomAnimeSortSeed().set(Random.nextInt()) },
            onGlobalSortSelected = { preferences.animeSortingMode().set(AnimeLibrarySort(type, direction)) },
        )
    }

    suspend fun await(
        category: Category?,
        type: AnimeLibrarySort.Type,
        direction: AnimeLibrarySort.Direction,
    ) {
        await(category?.id, type, direction)
    }
}
