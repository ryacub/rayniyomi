package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.di.appGraph
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.category.manga.interactor.GetMangaCategories

class MangaCategoriesRestorer(
    private val mangaHandler: MangaDatabaseHandler = appGraph.mangaDatabaseHandler,
    private val getMangaCategories: GetMangaCategories = appGraph.getMangaCategories,
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
