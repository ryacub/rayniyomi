package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.di.appGraph
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories

class AnimeCategoriesBackupCreator(
    private val getAnimeCategories: GetAnimeCategories = appGraph.getAnimeCategories,
) {

    private val creator = CategoriesBackupCreator { getAnimeCategories.await() }

    suspend operator fun invoke(): List<BackupCategory> {
        return creator()
    }
}
