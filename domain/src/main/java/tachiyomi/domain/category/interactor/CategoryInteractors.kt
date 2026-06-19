package tachiyomi.domain.category.interactor

import logcat.LogPriority
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryFlags
import tachiyomi.domain.category.model.CategoryUpdate
import tachiyomi.domain.library.model.Flag
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.plus
import tachiyomi.domain.library.service.LibraryPreferences

internal class GetCategories(
    private val repository: CategoryRepositoryOps,
) {
    fun subscribe() = repository.getAllCategoriesAsFlow()

    fun subscribe(entryId: Long) = repository.getCategoriesByEntryIdAsFlow(entryId)

    suspend fun await(): List<Category> = repository.getAllCategories()

    suspend fun await(entryId: Long): List<Category> = repository.getCategoriesByEntryId(entryId)
}

internal class GetVisibleCategories(
    private val repository: CategoryRepositoryOps,
) {
    fun subscribe() = repository.getAllVisibleCategoriesAsFlow()

    fun subscribe(entryId: Long) = repository.getVisibleCategoriesByEntryIdAsFlow(entryId)

    suspend fun await(): List<Category> = repository.getAllVisibleCategories()

    suspend fun await(entryId: Long): List<Category> = repository.getVisibleCategoriesByEntryId(entryId)
}

internal class CreateCategoryWithName(
    private val repository: CategoryRepositoryOps,
    private val initialFlags: () -> Long,
) {
    suspend fun await(name: String, parentId: Long? = null): Result = withNonCancellableContext {
        val categories = repository.getAllCategories()
        val validatedParentId = parentId?.let { id ->
            categories.find { it.id == id && it.parentId == null }?.id
                ?: return@withNonCancellableContext Result.InvalidParent
        }
        val nextOrder = categories.maxOfOrNull { it.order }?.plus(1) ?: 0
        val newCategory = Category(
            id = 0,
            name = name,
            order = nextOrder,
            flags = initialFlags(),
            hidden = false,
            parentId = validatedParentId,
        )

        try {
            repository.insertCategory(newCategory)
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    sealed interface Result {
        data object Success : Result
        data object InvalidParent : Result
        data class InternalError(val error: Exception) : Result
    }
}

internal class RenameCategory(
    private val repository: CategoryRepositoryOps,
) {
    suspend fun await(categoryId: Long, name: String): Result = withNonCancellableContext {
        try {
            repository.updatePartialCategory(CategoryUpdate(id = categoryId, name = name))
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    suspend fun await(category: Category, name: String): Result = await(category.id, name)

    sealed interface Result {
        data object Success : Result
        data class InternalError(val error: Exception) : Result
    }
}

internal class UpdateCategory(
    private val repository: CategoryRepositoryOps,
) {
    suspend fun await(payload: CategoryUpdate): Result = withNonCancellableContext {
        try {
            repository.updatePartialCategory(payload)
            Result.Success
        } catch (e: Exception) {
            Result.InternalError(e)
        }
    }

    sealed interface Result {
        data object Success : Result
        data class InternalError(val error: Exception) : Result
    }
}

internal class HideCategory(
    private val repository: CategoryRepositoryOps,
) {
    suspend fun await(category: Category): Result = withNonCancellableContext {
        val update = CategoryUpdate(
            id = category.id,
            hidden = !category.hidden,
        )

        try {
            repository.updatePartialCategory(update)
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    sealed class Result {
        data object Success : Result()
        data class InternalError(val error: Exception) : Result()
    }
}

internal class ReorderCategory(
    private val repository: CategoryRepositoryOps,
) {
    suspend fun await(category: Category, newIndex: Int): Result = withNonCancellableContext {
        val categories = repository.getAllCategories()
            .filterNot(Category::isSystemCategory)
            .toMutableList()

        val currentIndex = categories.indexOfFirst { it.id == category.id }
        if (currentIndex == -1) {
            return@withNonCancellableContext Result.Unchanged
        }

        try {
            categories.add(newIndex, categories.removeAt(currentIndex))

            val updates = categories.mapIndexed { index, movedCategory ->
                CategoryUpdate(
                    id = movedCategory.id,
                    order = index.toLong(),
                )
            }

            repository.updatePartialCategories(updates)
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    sealed interface Result {
        data object Success : Result
        data object Unchanged : Result
        data class InternalError(val error: Exception) : Result
    }
}

internal class SetCategoryAlphabeticalSort(
    private val repository: CategoryRepositoryOps,
) {
    suspend fun await(enabled: Boolean) {
        val updates = repository.getAllCategories().map { category ->
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
        repository.updatePartialCategories(updates)
    }
}

internal class ResetCategoryFlags(
    private val repository: CategoryRepositoryOps,
) {
    suspend fun await(sortFlags: Long) {
        val updates = repository.getAllCategories().map {
            CategoryUpdate(
                id = it.id,
                flags = (it.flags and SORT_MASK.inv()) + sortFlags,
            )
        }
        repository.updatePartialCategories(updates)
    }

    private companion object {
        const val SORT_MASK = 0b01111100L
    }
}

internal suspend fun setSortModeForCategory(
    repository: CategoryRepositoryOps,
    categoryId: Long?,
    type: Flag,
    direction: Flag,
    sortMask: Long,
    categorizedDisplaySettings: Boolean,
    isRandomSort: Boolean,
    onRandomSortSelected: () -> Unit,
    onGlobalSortSelected: () -> Unit,
) {
    val category = categoryId?.let { repository.getCategory(it) }
    val flags = ((category?.flags ?: 0L) and sortMask.inv()) + type + direction
    if (isRandomSort) {
        onRandomSortSelected()
    }
    if (category != null && categorizedDisplaySettings) {
        repository.updatePartialCategory(
            CategoryUpdate(
                id = category.id,
                flags = flags,
            ),
        )
    } else {
        onGlobalSortSelected()
        val updates = repository.getAllCategories().map {
            CategoryUpdate(
                id = it.id,
                flags = (it.flags and sortMask.inv()) + type + direction,
            )
        }
        repository.updatePartialCategories(updates)
    }
}

internal class DeleteCategory(
    private val repository: CategoryRepositoryOps,
    private val defaultCategory: Preference<Int>,
    private val categoryPreferences: List<Preference<Set<String>>>,
) {
    suspend fun await(categoryId: Long): Result = withNonCancellableContext {
        try {
            repository.deleteCategory(categoryId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return@withNonCancellableContext Result.InternalError(e)
        }

        val categories = repository.getAllCategories()
        val updates = categories.mapIndexed { index, category ->
            CategoryUpdate(
                id = category.id,
                order = index.toLong(),
                parentId = if (category.parentId == categoryId) null else category.parentId,
                updateParentId = category.parentId == categoryId,
            )
        }

        if (defaultCategory.get() == categoryId.toInt()) {
            defaultCategory.delete()
        }

        val categoryIdString = categoryId.toString()
        categoryPreferences.forEach { preference ->
            val ids = preference.get()
            if (categoryIdString !in ids) return@forEach
            preference.set(ids.minus(categoryIdString))
        }

        try {
            repository.updatePartialCategories(updates)
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    sealed interface Result {
        data object Success : Result
        data class InternalError(val error: Exception) : Result
    }
}

internal class SetEntryCategories(
    private val setCategories: suspend (entryId: Long, categoryIds: List<Long>) -> Unit,
) {
    suspend fun await(entryId: Long, categoryIds: List<Long>) {
        try {
            setCategories(entryId, categoryIds)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}

internal class SetCategoryDisplayMode(
    private val preferences: LibraryPreferences,
) {
    fun await(display: LibraryDisplayMode) {
        preferences.displayMode().set(display)
    }
}
