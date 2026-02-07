package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.ExtensionRepoValidator
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionRepos
import mihon.domain.extensionrepo.anime.interactor.GetAnimeExtensionRepo
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeExtensionRepoRestorer(
    private val animeHandler: AnimeDatabaseHandler = Injekt.get(),
    private val getExtensionRepos: GetAnimeExtensionRepo = Injekt.get(),
) {

    suspend operator fun invoke(
        backupRepo: BackupExtensionRepos,
    ) {
        val dbRepos = getExtensionRepos.getAll()
        val validationResult = ExtensionRepoValidator.validateForRestore(backupRepo, dbRepos)
        validationResult.throwIfInvalid()

        animeHandler.await {
            extension_reposQueries.insert(
                backupRepo.baseUrl,
                backupRepo.name,
                backupRepo.shortName,
                backupRepo.website,
                backupRepo.signingKeyFingerprint,
            )
        }
    }
}
