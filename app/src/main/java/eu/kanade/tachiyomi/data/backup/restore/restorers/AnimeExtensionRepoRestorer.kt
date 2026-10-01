package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.ExtensionRepoValidator
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionRepos
import eu.kanade.tachiyomi.di.appGraph
import mihon.domain.extensionrepo.anime.interactor.GetAnimeExtensionRepo
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler

class AnimeExtensionRepoRestorer(
    private val animeHandler: AnimeDatabaseHandler = appGraph.animeDatabaseHandler,
    private val getExtensionRepos: GetAnimeExtensionRepo = appGraph.getAnimeExtensionRepo,
) {

    suspend operator fun invoke(
        backupRepo: BackupExtensionRepos,
    ) {
        val dbRepos = getExtensionRepos.getAll()
        val validationResult = ExtensionRepoValidator.validateForRestore(backupRepo, dbRepos)
        if (validationResult is ExtensionRepoValidator.ValidationResult.AlreadyExists) {
            return
        }
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
