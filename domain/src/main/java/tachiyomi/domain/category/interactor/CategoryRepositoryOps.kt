package tachiyomi.domain.category.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository
import tachiyomi.domain.category.manga.repository.MangaCategoryRepository
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryUpdate

internal interface CategoryRepositoryOps {
    suspend fun getCategory(id: Long): Category?

    suspend fun getAllCategories(): List<Category>

    suspend fun getAllVisibleCategories(): List<Category>

    fun getAllCategoriesAsFlow(): Flow<List<Category>>

    fun getAllVisibleCategoriesAsFlow(): Flow<List<Category>>

    suspend fun getCategoriesByEntryId(entryId: Long): List<Category>

    suspend fun getVisibleCategoriesByEntryId(entryId: Long): List<Category>

    fun getCategoriesByEntryIdAsFlow(entryId: Long): Flow<List<Category>>

    fun getVisibleCategoriesByEntryIdAsFlow(entryId: Long): Flow<List<Category>>

    suspend fun insertCategory(category: Category)

    suspend fun updatePartialCategory(update: CategoryUpdate)

    suspend fun updatePartialCategories(updates: List<CategoryUpdate>)

    suspend fun updateAllCategoryFlags(flags: Long?)

    suspend fun deleteCategory(categoryId: Long)
}

internal fun AnimeCategoryRepository.asCategoryRepositoryOps(): CategoryRepositoryOps =
    object : CategoryRepositoryOps {
        override suspend fun getCategory(id: Long): Category? = getAnimeCategory(id)

        override suspend fun getAllCategories(): List<Category> = getAllAnimeCategories()

        override suspend fun getAllVisibleCategories(): List<Category> = getAllVisibleAnimeCategories()

        override fun getAllCategoriesAsFlow(): Flow<List<Category>> = getAllAnimeCategoriesAsFlow()

        override fun getAllVisibleCategoriesAsFlow(): Flow<List<Category>> = getAllVisibleAnimeCategoriesAsFlow()

        override suspend fun getCategoriesByEntryId(entryId: Long): List<Category> =
            getCategoriesByAnimeId(entryId)

        override suspend fun getVisibleCategoriesByEntryId(entryId: Long): List<Category> =
            getVisibleCategoriesByAnimeId(entryId)

        override fun getCategoriesByEntryIdAsFlow(entryId: Long): Flow<List<Category>> =
            getCategoriesByAnimeIdAsFlow(entryId)

        override fun getVisibleCategoriesByEntryIdAsFlow(entryId: Long): Flow<List<Category>> =
            getVisibleCategoriesByAnimeIdAsFlow(entryId)

        override suspend fun insertCategory(category: Category) = insertAnimeCategory(category)

        override suspend fun updatePartialCategory(update: CategoryUpdate) = updatePartialAnimeCategory(update)

        override suspend fun updatePartialCategories(updates: List<CategoryUpdate>) =
            updatePartialAnimeCategories(updates)

        override suspend fun updateAllCategoryFlags(flags: Long?) = updateAllAnimeCategoryFlags(flags)

        override suspend fun deleteCategory(categoryId: Long) = deleteAnimeCategory(categoryId)
    }

internal fun MangaCategoryRepository.asCategoryRepositoryOps(): CategoryRepositoryOps =
    object : CategoryRepositoryOps {
        override suspend fun getCategory(id: Long): Category? = getMangaCategory(id)

        override suspend fun getAllCategories(): List<Category> = getAllMangaCategories()

        override suspend fun getAllVisibleCategories(): List<Category> = getAllVisibleMangaCategories()

        override fun getAllCategoriesAsFlow(): Flow<List<Category>> = getAllMangaCategoriesAsFlow()

        override fun getAllVisibleCategoriesAsFlow(): Flow<List<Category>> = getAllVisibleMangaCategoriesAsFlow()

        override suspend fun getCategoriesByEntryId(entryId: Long): List<Category> =
            getCategoriesByMangaId(entryId)

        override suspend fun getVisibleCategoriesByEntryId(entryId: Long): List<Category> =
            getVisibleCategoriesByMangaId(entryId)

        override fun getCategoriesByEntryIdAsFlow(entryId: Long): Flow<List<Category>> =
            getCategoriesByMangaIdAsFlow(entryId)

        override fun getVisibleCategoriesByEntryIdAsFlow(entryId: Long): Flow<List<Category>> =
            getVisibleCategoriesByMangaIdAsFlow(entryId)

        override suspend fun insertCategory(category: Category) = insertMangaCategory(category)

        override suspend fun updatePartialCategory(update: CategoryUpdate) = updatePartialMangaCategory(update)

        override suspend fun updatePartialCategories(updates: List<CategoryUpdate>) =
            updatePartialMangaCategories(updates)

        override suspend fun updateAllCategoryFlags(flags: Long?) = updateAllMangaCategoryFlags(flags)

        override suspend fun deleteCategory(categoryId: Long) = deleteMangaCategory(categoryId)
    }
