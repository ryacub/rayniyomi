package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.service.LibraryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CategoriesRestorer(
    private val getCategories: suspend () -> List<Category>,
    private val insertCategory: suspend (name: String, order: Long, flags: Long, parentId: Long?) -> Long,
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
) {

    suspend operator fun invoke(backupCategories: List<BackupCategory>) {
        if (backupCategories.isNotEmpty()) {
            val dbCategories = getCategories()
            val dbCategoriesByName = dbCategories.associateBy { it.name }
            var nextOrder = dbCategories.maxOfOrNull { it.order }?.plus(1) ?: 0

            val insertedByOldId = mutableMapOf<Long, Category>()
            val categories = mutableListOf<Category>()
            val pendingChildren = mutableListOf<BackupCategory>()

            backupCategories
                .sortedBy { it.order }
                .forEach { backupCategory ->
                    val dbCategory = dbCategoriesByName[backupCategory.name]
                    if (dbCategory != null) {
                        categories += dbCategory
                        insertedByOldId[backupCategory.id] = dbCategory
                        return@forEach
                    }

                    if (backupCategory.parentId != null) {
                        pendingChildren += backupCategory
                        return@forEach
                    }

                    val order = nextOrder++
                    val created = insertCategory(backupCategory.name, order, backupCategory.flags, null)
                        .let { id -> backupCategory.toCategory(id).copy(order = order, parentId = null) }
                    categories += created
                    insertedByOldId[backupCategory.id] = created
                }

            pendingChildren.forEach { backupCategory ->
                val remappedParentId = backupCategory.parentId?.let { insertedByOldId[it]?.id }
                val order = nextOrder++
                val created = insertCategory(
                    backupCategory.name,
                    order,
                    backupCategory.flags,
                    remappedParentId,
                ).let { id -> backupCategory.toCategory(id).copy(order = order, parentId = remappedParentId) }
                categories += created
                insertedByOldId[backupCategory.id] = created
            }

            libraryPreferences.categorizedDisplaySettings().set(
                (dbCategories + categories)
                    .distinctBy { it.flags }
                    .size > 1,
            )
        }
    }
}
