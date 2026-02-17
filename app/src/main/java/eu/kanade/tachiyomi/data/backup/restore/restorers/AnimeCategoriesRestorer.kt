package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeCategoriesRestorer(
    private val animeHandler: AnimeDatabaseHandler = Injekt.get(),
    private val getAnimeCategories: GetAnimeCategories = Injekt.get(),
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
