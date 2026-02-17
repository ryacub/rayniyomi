package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaCategoriesRestorer(
    private val mangaHandler: MangaDatabaseHandler = Injekt.get(),
    private val getMangaCategories: GetMangaCategories = Injekt.get(),
) {

    private val restorer = CategoriesRestorer(
        getCategories = { getMangaCategories.await() },
        insertCategory = { name, order, flags, parentId ->
            mangaHandler.awaitOneExecutable {
                categoriesQueries.insert(name, order, flags, parentId)
                categoriesQueries.selectLastInsertedRowId()
            }
        },
        updateCategoryParent = { categoryId, parentId ->
            mangaHandler.await {
                categoriesQueries.update(
                    name = null,
                    order = null,
                    flags = null,
                    hidden = null,
                    parentId = parentId,
                    updateParentId = true,
                    categoryId = categoryId,
                )
            }
        },
    )

    suspend operator fun invoke(backupCategories: List<BackupCategory>) {
        restorer(backupCategories)
    }
}
