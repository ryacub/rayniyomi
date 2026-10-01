package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupExtensionRepos
import eu.kanade.tachiyomi.data.backup.models.backupExtensionReposMapper
import eu.kanade.tachiyomi.di.appGraph
import mihon.domain.extensionrepo.manga.interactor.GetMangaExtensionRepo

class MangaExtensionRepoBackupCreator(
    private val getMangaExtensionRepos: GetMangaExtensionRepo = appGraph.getMangaExtensionRepo,
) {

    suspend operator fun invoke(): List<BackupExtensionRepos> {
        return getMangaExtensionRepos.getAll()
            .map(backupExtensionReposMapper)
    }
}
