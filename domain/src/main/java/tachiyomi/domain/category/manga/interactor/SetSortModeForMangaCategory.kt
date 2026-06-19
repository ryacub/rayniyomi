package tachiyomi.domain.category.manga.interactor

import tachiyomi.domain.category.interactor.asCategoryRepositoryOps
import tachiyomi.domain.category.interactor.setSortModeForCategory
import tachiyomi.domain.category.manga.repository.MangaCategoryRepository
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.manga.model.MangaLibrarySort
import tachiyomi.domain.library.service.LibraryPreferences
import kotlin.random.Random

class SetSortModeForMangaCategory(
    private val preferences: LibraryPreferences,
    private val categoryRepository: MangaCategoryRepository,
) {

    private val categoryOps = categoryRepository.asCategoryRepositoryOps()
    private val sortMask: Long = MangaLibrarySort.Type.Alphabetical.mask or MangaLibrarySort.Direction.Ascending.mask

    suspend fun await(
        categoryId: Long?,
        type: MangaLibrarySort.Type,
        direction: MangaLibrarySort.Direction,
    ) {
        setSortModeForCategory(
            repository = categoryOps,
            categoryId = categoryId,
            type = type,
            direction = direction,
            sortMask = sortMask,
            categorizedDisplaySettings = preferences.categorizedDisplaySettings().get(),
            isRandomSort = type == MangaLibrarySort.Type.Random,
            onRandomSortSelected = { preferences.randomMangaSortSeed().set(Random.nextInt()) },
            onGlobalSortSelected = { preferences.mangaSortingMode().set(MangaLibrarySort(type, direction)) },
        )
    }

    suspend fun await(
        category: Category?,
        type: MangaLibrarySort.Type,
        direction: MangaLibrarySort.Direction,
    ) {
        await(category?.id, type, direction)
    }
}
