package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.ExtensionRepoValidator
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionRepos
import mihon.domain.extensionrepo.manga.interactor.GetMangaExtensionRepo
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaExtensionRepoRestorer(
    private val mangaHandler: MangaDatabaseHandler = Injekt.get(),
    private val getExtensionRepos: GetMangaExtensionRepo = Injekt.get(),
) {

    suspend operator fun invoke(
        backupRepo: BackupExtensionRepos,
    ) {
        val dbRepos = getExtensionRepos.getAll()
        val validationResult = ExtensionRepoValidator.validateForRestore(backupRepo, dbRepos)
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
