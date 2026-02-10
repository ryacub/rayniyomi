package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaCategoriesBackupCreator(
    private val getMangaCategories: GetMangaCategories = Injekt.get(),
) {

    private val creator = CategoriesBackupCreator { getMangaCategories.await() }

    suspend operator fun invoke(): List<BackupCategory> {
        return creator()
    }
}
