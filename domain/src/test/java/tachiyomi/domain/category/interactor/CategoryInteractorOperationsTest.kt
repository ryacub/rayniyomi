package tachiyomi.domain.category.interactor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.category.anime.interactor.HideAnimeCategory
import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository
import tachiyomi.domain.category.manga.interactor.DeleteMangaCategory
import tachiyomi.domain.category.manga.repository.MangaCategoryRepository
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryFlags
import tachiyomi.domain.category.model.CategoryUpdate
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.model.FlagWithMask
import tachiyomi.domain.library.model.plus
import tachiyomi.domain.library.service.LibraryPreferences

class CategoryInteractorOperationsTest {

    @Test
    fun `create validates parent and assigns next order with initial flags`() = runTest {
        val parent = category(id = 1, name = "Parent", order = 0)
        val child = category(id = 2, name = "Child", order = 1, parentId = 1)
        val repository = FakeCategoryRepositoryOps(listOf(parent, child))
        val createCategory = CreateCategoryWithName(repository) { INITIAL_FLAGS }

        val result = createCategory.await(name = "New", parentId = parent.id)

        assertEquals(CreateCategoryWithName.Result.Success, result)
        assertEquals(
            category(
                id = 0,
                name = "New",
                order = 2,
                flags = INITIAL_FLAGS,
                parentId = parent.id,
            ),
            repository.insertedCategory,
        )
    }

    @Test
    fun `create rejects child category as parent`() = runTest {
        val child = category(id = 2, name = "Child", order = 1, parentId = 1)
        val repository = FakeCategoryRepositoryOps(listOf(child))
        val createCategory = CreateCategoryWithName(repository) { INITIAL_FLAGS }

        val result = createCategory.await(name = "New", parentId = child.id)

        assertEquals(CreateCategoryWithName.Result.InvalidParent, result)
        assertEquals(null, repository.insertedCategory)
    }

    @Test
    fun `rename update and hide produce expected category updates`() = runTest {
        val category = category(id = 1, name = "Old", order = 0, hidden = false)
        val repository = FakeCategoryRepositoryOps(listOf(category))

        assertEquals(RenameCategory.Result.Success, RenameCategory(repository).await(category, "New"))
        assertEquals(CategoryUpdate(id = 1, name = "New"), repository.singleUpdate())

        repository.clearUpdates()
        assertEquals(
            UpdateCategory.Result.Success,
            UpdateCategory(repository).await(CategoryUpdate(id = 1, flags = 99L)),
        )
        assertEquals(CategoryUpdate(id = 1, flags = 99L), repository.singleUpdate())

        repository.clearUpdates()
        assertEquals(HideCategory.Result.Success, HideCategory(repository).await(category))
        assertEquals(CategoryUpdate(id = 1, hidden = true), repository.singleUpdate())
    }

    @Test
    fun `anime hide facade returns anime success result`() = runTest {
        val category = category(id = 1, name = "Anime", order = 0, hidden = false)
        val repository = FakeAnimeCategoryRepository(listOf(category))

        val result = HideAnimeCategory(repository).await(category)

        assertEquals(HideAnimeCategory.Result.Success, result)
        assertEquals(CategoryUpdate(id = 1, hidden = true), repository.singleUpdate())
    }

    @Test
    fun `reorder ignores system category and rewrites user category order`() = runTest {
        val first = category(id = 1, name = "First", order = 0)
        val second = category(id = 2, name = "Second", order = 1)
        val repository = FakeCategoryRepositoryOps(
            listOf(
                category(id = Category.UNCATEGORIZED_ID, name = "System", order = -1),
                first,
                second,
            ),
        )

        val result = ReorderCategory(repository).await(second, newIndex = 0)

        assertEquals(ReorderCategory.Result.Success, result)
        assertEquals(
            listOf(
                CategoryUpdate(id = 2, order = 0),
                CategoryUpdate(id = 1, order = 1),
            ),
            repository.updates,
        )
    }

    @Test
    fun `reorder returns unchanged for missing category`() = runTest {
        val repository = FakeCategoryRepositoryOps(listOf(category(id = 1, name = "Only", order = 0)))

        val result = ReorderCategory(repository).await(category(id = 99, name = "Missing", order = 99), newIndex = 0)

        assertEquals(ReorderCategory.Result.Unchanged, result)
        assertTrue(repository.updates.isEmpty())
    }

    @Test
    fun `alphabetical sort sets and clears only alphabetical sort flag`() = runTest {
        val readingListFlag = CategoryFlags.CATEGORY_FLAG_IS_READING_LIST
        val repository = FakeCategoryRepositoryOps(
            listOf(
                category(id = 1, name = "One", order = 0, flags = readingListFlag),
                category(
                    id = 2,
                    name = "Two",
                    order = 1,
                    flags =
                    readingListFlag or CategoryFlags.CATEGORY_FLAG_SORT_ALPHABETICAL,
                ),
            ),
        )

        SetCategoryAlphabeticalSort(repository).await(enabled = true)

        assertEquals(
            listOf(
                CategoryUpdate(id = 1, flags = readingListFlag or CategoryFlags.CATEGORY_FLAG_SORT_ALPHABETICAL),
                CategoryUpdate(id = 2, flags = readingListFlag or CategoryFlags.CATEGORY_FLAG_SORT_ALPHABETICAL),
            ),
            repository.updates,
        )

        repository.clearUpdates()
        SetCategoryAlphabeticalSort(repository).await(enabled = false)

        assertEquals(
            listOf(
                CategoryUpdate(id = 1, flags = readingListFlag),
                CategoryUpdate(id = 2, flags = readingListFlag),
            ),
            repository.updates,
        )
    }

    @Test
    fun `reset flags preserves auxiliary flags while replacing sort bits`() = runTest {
        val auxiliaryFlags =
            CategoryFlags.CATEGORY_FLAG_IS_READING_LIST or CategoryFlags.CATEGORY_FLAG_SORT_ALPHABETICAL
        val repository = FakeCategoryRepositoryOps(
            listOf(
                category(id = 1, name = "One", order = 0, flags = auxiliaryFlags or OLD_SORT_FLAG),
            ),
        )

        ResetCategoryFlags(repository).await(sortFlags = NEW_SORT_FLAG)

        assertEquals(
            listOf(
                CategoryUpdate(
                    id = 1,
                    flags = auxiliaryFlags or NEW_SORT_FLAG,
                ),
            ),
            repository.updates,
        )
    }

    @Test
    fun `sort mode updates single category when categorized settings are enabled`() = runTest {
        val category = category(id = 1, name = "One", order = 0, flags = CategoryFlags.CATEGORY_FLAG_IS_READING_LIST)
        val repository = FakeCategoryRepositoryOps(listOf(category))
        var globalSortUpdated = false
        var randomSeedUpdated = false

        setSortModeForCategory(
            repository = repository,
            categoryId = category.id,
            type = SortTypeRandom,
            direction = SortDirectionAscending,
            sortMask = SORT_MASK,
            categorizedDisplaySettings = true,
            isRandomSort = true,
            onRandomSortSelected = { randomSeedUpdated = true },
            onGlobalSortSelected = { globalSortUpdated = true },
        )

        assertTrue(randomSeedUpdated)
        assertFalse(globalSortUpdated)
        assertEquals(
            CategoryUpdate(
                id = category.id,
                flags = CategoryFlags.CATEGORY_FLAG_IS_READING_LIST + SortTypeRandom + SortDirectionAscending,
            ),
            repository.singleUpdate(),
        )
    }

    @Test
    fun `sort mode updates global sort and all categories when category settings are disabled`() = runTest {
        val repository = FakeCategoryRepositoryOps(
            listOf(
                category(id = 1, name = "One", order = 0, flags = CategoryFlags.CATEGORY_FLAG_IS_READING_LIST),
                category(id = 2, name = "Two", order = 1, flags = OLD_SORT_FLAG),
            ),
        )
        var globalSortUpdated = false

        setSortModeForCategory(
            repository = repository,
            categoryId = 1L,
            type = SortTypeAlphabetical,
            direction = SortDirectionAscending,
            sortMask = SORT_MASK,
            categorizedDisplaySettings = false,
            isRandomSort = false,
            onRandomSortSelected = {},
            onGlobalSortSelected = { globalSortUpdated = true },
        )

        assertTrue(globalSortUpdated)
        assertEquals(
            listOf(
                CategoryUpdate(
                    id = 1,
                    flags =
                    CategoryFlags.CATEGORY_FLAG_IS_READING_LIST + SortTypeAlphabetical + SortDirectionAscending,
                ),
                CategoryUpdate(id = 2, flags = SortTypeAlphabetical + SortDirectionAscending),
            ),
            repository.updates,
        )
    }

    @Test
    fun `delete reparents children compacts order clears default and removes category preferences`() = runTest {
        val categoryId = 2L
        val defaultCategory = MutablePreference(defaultValue = -1, initialValue = categoryId.toInt())
        val includePreference = MutablePreference(defaultValue = emptySet<String>(), initialValue = setOf("1", "2"))
        val excludePreference = MutablePreference(defaultValue = emptySet<String>(), initialValue = setOf("2", "3"))
        val untouchedPreference = MutablePreference(defaultValue = emptySet<String>(), initialValue = setOf("9"))
        val repository = FakeCategoryRepositoryOps(
            listOf(
                category(id = 1, name = "Parent", order = 0),
                category(id = categoryId, name = "Deleted", order = 1),
                category(id = 3, name = "Child", order = 2, parentId = categoryId),
                category(id = 4, name = "Later", order = 3),
            ),
        )
        val deleteCategory = DeleteCategory(
            repository = repository,
            defaultCategory = defaultCategory,
            categoryPreferences = listOf(includePreference, excludePreference, untouchedPreference),
        )

        val result = deleteCategory.await(categoryId)

        assertEquals(DeleteCategory.Result.Success, result)
        assertEquals(-1, defaultCategory.get())
        assertEquals(setOf("1"), includePreference.get())
        assertEquals(setOf("3"), excludePreference.get())
        assertEquals(setOf("9"), untouchedPreference.get())
        assertEquals(
            listOf(
                CategoryUpdate(id = 1, order = 0, parentId = null, updateParentId = false),
                CategoryUpdate(id = 3, order = 1, parentId = null, updateParentId = true),
                CategoryUpdate(id = 4, order = 2, parentId = null, updateParentId = false),
            ),
            repository.updates,
        )
    }

    @Test
    fun `delete reports internal error when repository delete fails`() = runTest {
        val repository = FakeCategoryRepositoryOps(emptyList())
        repository.deleteError = IllegalStateException("boom")
        val deleteCategory = DeleteCategory(
            repository = repository,
            defaultCategory = MutablePreference(defaultValue = -1, initialValue = -1),
            categoryPreferences = emptyList(),
        )

        val result = deleteCategory.await(1L)

        assertInstanceOf(DeleteCategory.Result.InternalError::class.java, result)
    }

    @Test
    fun `manga delete facade removes deleted category from manga preferences`() = runTest {
        val categoryId = 2L
        val preferences = KeyedMutablePreferenceStore()
        val libraryPreferences = LibraryPreferences(preferences)
        val downloadPreferences = DownloadPreferences(preferences)
        libraryPreferences.defaultMangaCategory().set(categoryId.toInt())
        libraryPreferences.mangaUpdateCategories().set(setOf("1", "2"))
        libraryPreferences.mangaUpdateCategoriesExclude().set(setOf("2", "3"))
        downloadPreferences.removeExcludeCategories().set(setOf("2", "4"))
        downloadPreferences.downloadNewChapterCategories().set(setOf("2", "5"))
        downloadPreferences.downloadNewChapterCategoriesExclude().set(setOf("2", "6"))
        libraryPreferences.animeUpdateCategoriesExclude().set(setOf("2", "7"))
        val repository = FakeMangaCategoryRepository(
            listOf(
                category(id = 1, name = "Parent", order = 0),
                category(id = categoryId, name = "Deleted", order = 1),
                category(id = 3, name = "Child", order = 2, parentId = categoryId),
                category(id = 4, name = "Later", order = 3),
            ),
        )
        val deleteMangaCategory = DeleteMangaCategory(
            categoryRepository = repository,
            libraryPreferences = libraryPreferences,
            downloadPreferences = downloadPreferences,
        )

        val result = deleteMangaCategory.await(categoryId)

        assertEquals(DeleteMangaCategory.Result.Success, result)
        assertEquals(-1, libraryPreferences.defaultMangaCategory().get())
        assertEquals(setOf("1"), libraryPreferences.mangaUpdateCategories().get())
        assertEquals(setOf("3"), libraryPreferences.mangaUpdateCategoriesExclude().get())
        assertEquals(setOf("4"), downloadPreferences.removeExcludeCategories().get())
        assertEquals(setOf("5"), downloadPreferences.downloadNewChapterCategories().get())
        assertEquals(setOf("6"), downloadPreferences.downloadNewChapterCategoriesExclude().get())
        assertEquals(setOf("2", "7"), libraryPreferences.animeUpdateCategoriesExclude().get())
        assertEquals(
            listOf(
                CategoryUpdate(id = 1, order = 0, parentId = null, updateParentId = false),
                CategoryUpdate(id = 3, order = 1, parentId = null, updateParentId = true),
                CategoryUpdate(id = 4, order = 2, parentId = null, updateParentId = false),
            ),
            repository.updates,
        )
    }

    private class FakeCategoryRepositoryOps(
        initialCategories: List<Category>,
    ) : CategoryRepositoryOps {

        private val categories = initialCategories.toMutableList()
        val updates = mutableListOf<CategoryUpdate>()
        var insertedCategory: Category? = null
            private set
        var deleteError: Exception? = null

        override suspend fun getCategory(id: Long): Category? = categories.find { it.id == id }

        override suspend fun getAllCategories(): List<Category> = categories.toList()

        override suspend fun getAllVisibleCategories(): List<Category> = categories.filterNot { it.hidden }

        override fun getAllCategoriesAsFlow(): Flow<List<Category>> = MutableStateFlow(categories.toList())

        override fun getAllVisibleCategoriesAsFlow(): Flow<List<Category>> =
            MutableStateFlow(categories.filterNot { it.hidden })

        override suspend fun getCategoriesByEntryId(entryId: Long): List<Category> = categories.toList()

        override suspend fun getVisibleCategoriesByEntryId(entryId: Long): List<Category> =
            categories.filterNot { it.hidden }

        override fun getCategoriesByEntryIdAsFlow(entryId: Long): Flow<List<Category>> =
            MutableStateFlow(categories.toList())

        override fun getVisibleCategoriesByEntryIdAsFlow(entryId: Long): Flow<List<Category>> =
            MutableStateFlow(categories.filterNot { it.hidden })

        override suspend fun insertCategory(category: Category) {
            insertedCategory = category
        }

        override suspend fun updatePartialCategory(update: CategoryUpdate) {
            updates += update
        }

        override suspend fun updatePartialCategories(updates: List<CategoryUpdate>) {
            this.updates += updates
        }

        override suspend fun updateAllCategoryFlags(flags: Long?) = Unit

        override suspend fun deleteCategory(categoryId: Long) {
            deleteError?.let { throw it }
            categories.removeAll { it.id == categoryId }
        }

        fun singleUpdate(): CategoryUpdate = updates.single()

        fun clearUpdates() = updates.clear()
    }

    private class FakeAnimeCategoryRepository(
        initialCategories: List<Category>,
    ) : AnimeCategoryRepository {

        private val categories = initialCategories.toMutableList()
        private val updates = mutableListOf<CategoryUpdate>()

        override suspend fun getAnimeCategory(id: Long): Category? = categories.find { it.id == id }

        override suspend fun getAllAnimeCategories(): List<Category> = categories.toList()

        override suspend fun getAllVisibleAnimeCategories(): List<Category> =
            categories.filterNot { it.hidden }

        override fun getAllAnimeCategoriesAsFlow(): Flow<List<Category>> = MutableStateFlow(categories.toList())

        override fun getAllVisibleAnimeCategoriesAsFlow(): Flow<List<Category>> =
            MutableStateFlow(categories.filterNot { it.hidden })

        override suspend fun getCategoriesByAnimeId(animeId: Long): List<Category> = categories.toList()

        override suspend fun getVisibleCategoriesByAnimeId(animeId: Long): List<Category> =
            categories.filterNot { it.hidden }

        override fun getCategoriesByAnimeIdAsFlow(animeId: Long): Flow<List<Category>> =
            MutableStateFlow(categories.toList())

        override fun getVisibleCategoriesByAnimeIdAsFlow(animeId: Long): Flow<List<Category>> =
            MutableStateFlow(categories.filterNot { it.hidden })

        override suspend fun insertAnimeCategory(category: Category) {
            throw UnsupportedOperationException("Not needed by this test")
        }

        override suspend fun updatePartialAnimeCategory(update: CategoryUpdate) {
            updates += update
        }

        override suspend fun updatePartialAnimeCategories(updates: List<CategoryUpdate>) {
            throw UnsupportedOperationException("Not needed by this test")
        }

        override suspend fun updateAllAnimeCategoryFlags(flags: Long?) {
            throw UnsupportedOperationException("Not needed by this test")
        }

        override suspend fun deleteAnimeCategory(categoryId: Long) {
            throw UnsupportedOperationException("Not needed by this test")
        }

        fun singleUpdate(): CategoryUpdate = updates.single()
    }

    private class FakeMangaCategoryRepository(
        initialCategories: List<Category>,
    ) : MangaCategoryRepository {

        private val categories = initialCategories.toMutableList()
        val updates = mutableListOf<CategoryUpdate>()

        override suspend fun getMangaCategory(id: Long): Category? = categories.find { it.id == id }

        override suspend fun getAllMangaCategories(): List<Category> = categories.toList()

        override suspend fun getAllVisibleMangaCategories(): List<Category> =
            categories.filterNot { it.hidden }

        override fun getAllMangaCategoriesAsFlow(): Flow<List<Category>> = MutableStateFlow(categories.toList())

        override fun getAllVisibleMangaCategoriesAsFlow(): Flow<List<Category>> =
            MutableStateFlow(categories.filterNot { it.hidden })

        override suspend fun getCategoriesByMangaId(mangaId: Long): List<Category> = categories.toList()

        override suspend fun getVisibleCategoriesByMangaId(mangaId: Long): List<Category> =
            categories.filterNot { it.hidden }

        override fun getCategoriesByMangaIdAsFlow(mangaId: Long): Flow<List<Category>> =
            MutableStateFlow(categories.toList())

        override fun getVisibleCategoriesByMangaIdAsFlow(mangaId: Long): Flow<List<Category>> =
            MutableStateFlow(categories.filterNot { it.hidden })

        override suspend fun insertMangaCategory(category: Category) {
            throw UnsupportedOperationException("Not needed by this test")
        }

        override suspend fun updatePartialMangaCategory(update: CategoryUpdate) {
            throw UnsupportedOperationException("Not needed by this test")
        }

        override suspend fun updatePartialMangaCategories(updates: List<CategoryUpdate>) {
            this.updates += updates
        }

        override suspend fun updateAllMangaCategoryFlags(flags: Long?) {
            throw UnsupportedOperationException("Not needed by this test")
        }

        override suspend fun deleteMangaCategory(categoryId: Long) {
            categories.removeAll { it.id == categoryId }
        }
    }

    private class KeyedMutablePreferenceStore(
        initialValues: Map<String, Any?> = emptyMap(),
    ) : PreferenceStore {

        private val values = initialValues.toMutableMap()

        override fun getString(key: String, defaultValue: String): Preference<String> =
            getPreference(key, defaultValue)

        override fun getLong(key: String, defaultValue: Long): Preference<Long> =
            getPreference(key, defaultValue)

        override fun getInt(key: String, defaultValue: Int): Preference<Int> =
            getPreference(key, defaultValue)

        override fun getFloat(key: String, defaultValue: Float): Preference<Float> =
            getPreference(key, defaultValue)

        override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
            getPreference(key, defaultValue)

        override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
            getPreference(key, defaultValue)

        override fun <T> getObject(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ): Preference<T> = getPreference(key, defaultValue)

        override fun getAll(): Map<String, *> = values.toMap()

        private fun <T> getPreference(key: String, defaultValue: T): Preference<T> {
            return KeyedMutablePreference(key, defaultValue, values)
        }
    }

    private class KeyedMutablePreference<T>(
        private val key: String,
        private val defaultValue: T,
        private val values: MutableMap<String, Any?>,
    ) : Preference<T> {

        private val flow = MutableStateFlow(currentValue())

        override fun key(): String = key

        override fun get(): T = currentValue()

        override fun set(value: T) {
            values[key] = value
            flow.value = value
        }

        override fun isSet(): Boolean = values.containsKey(key)

        override fun delete() {
            values.remove(key)
            flow.value = defaultValue
        }

        override fun defaultValue(): T = defaultValue

        override fun changes(): Flow<T> = flow

        override fun stateIn(scope: CoroutineScope): StateFlow<T> = flow.asStateFlow()

        @Suppress("UNCHECKED_CAST")
        private fun currentValue(): T = values.getOrDefault(key, defaultValue) as T
    }

    private class MutablePreference<T>(
        private val defaultValue: T,
        initialValue: T,
    ) : Preference<T> {

        private val flow = MutableStateFlow(initialValue)

        override fun key(): String = "test"

        override fun get(): T = flow.value

        override fun set(value: T) {
            flow.value = value
        }

        override fun isSet(): Boolean = flow.value != defaultValue

        override fun delete() {
            flow.value = defaultValue
        }

        override fun defaultValue(): T = defaultValue

        override fun changes(): Flow<T> = flow

        override fun stateIn(scope: CoroutineScope): StateFlow<T> = flow
    }

    private fun category(
        id: Long,
        name: String,
        order: Long,
        flags: Long = 0,
        hidden: Boolean = false,
        parentId: Long? = null,
    ) = Category(
        id = id,
        name = name,
        order = order,
        flags = flags,
        hidden = hidden,
        parentId = parentId,
    )

    private data object SortTypeAlphabetical : FlagWithMask {
        override val flag: Long = 0b00000000
        override val mask: Long = 0b00111100
    }

    private data object SortTypeRandom : FlagWithMask {
        override val flag: Long = 0b00111100
        override val mask: Long = 0b00111100
    }

    private data object SortDirectionAscending : FlagWithMask {
        override val flag: Long = 0b01000000
        override val mask: Long = 0b01000000
    }

    private companion object {
        const val INITIAL_FLAGS = 0b01000100L
        const val OLD_SORT_FLAG = 0b00001000L
        const val NEW_SORT_FLAG = 0b01000100L
        const val SORT_MASK = 0b01111100L
    }
}
