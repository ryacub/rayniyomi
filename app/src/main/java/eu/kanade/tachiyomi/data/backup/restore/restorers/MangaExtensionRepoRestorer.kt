package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.ExtensionRepoValidator
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionRepos
import eu.kanade.tachiyomi.di.appGraph
import mihon.domain.extensionrepo.manga.interactor.GetMangaExtensionRepo
import tachiyomi.data.handlers.manga.MangaDatabaseHandler

class MangaExtensionRepoRestorer(
    private val mangaHandler: MangaDatabaseHandler = appGraph.mangaDatabaseHandler,
    private val getExtensionRepos: GetMangaExtensionRepo = appGraph.getMangaExtensionRepo,
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

        mangaHandler.await {
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
