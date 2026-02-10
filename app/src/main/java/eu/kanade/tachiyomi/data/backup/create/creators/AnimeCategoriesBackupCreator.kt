package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeCategoriesBackupCreator(
    private val getAnimeCategories: GetAnimeCategories = Injekt.get(),
) {

    private val creator = CategoriesBackupCreator { getAnimeCategories.await() }

    suspend operator fun invoke(): List<BackupCategory> {
        return creator()
    }
}
