package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.backupCategoryMapper
import tachiyomi.domain.category.model.Category

class CategoriesBackupCreator(
    private val getCategories: suspend () -> List<Category>,
) {

    suspend operator fun invoke(): List<BackupCategory> {
        return getCategories()
            .filterNot(Category::isSystemCategory)
            .map(backupCategoryMapper)
    }
}
