package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.di.appGraph
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories

class AnimeCategoriesRestorer(
    private val animeHandler: AnimeDatabaseHandler = appGraph.animeDatabaseHandler,
    private val getAnimeCategories: GetAnimeCategories = appGraph.getAnimeCategories,
) {

    private val restorer = CategoriesRestorer(
        getCategories = { getAnimeCategories.await() },
        insertCategory = { name, order, flags, parentId ->
            animeHandler.awaitOneExecutable {
                categoriesQueries.insert(name, order, flags, parentId)
                categoriesQueries.selectLastInsertedRowId()
            }
        },
        updateCategoryParent = { categoryId, parentId ->
            animeHandler.await {
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
