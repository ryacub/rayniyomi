package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.di.appGraph
import tachiyomi.domain.category.manga.interactor.GetMangaCategories

class MangaCategoriesBackupCreator(
    private val getMangaCategories: GetMangaCategories = appGraph.getMangaCategories,
) {

    private val creator = CategoriesBackupCreator { getMangaCategories.await() }

    suspend operator fun invoke(): List<BackupCategory> {
        return creator()
    }
}
