package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupExtensionRepos
import eu.kanade.tachiyomi.data.backup.models.backupExtensionReposMapper
import eu.kanade.tachiyomi.di.appGraph
import mihon.domain.extensionrepo.anime.interactor.GetAnimeExtensionRepo

class AnimeExtensionRepoBackupCreator(
    private val getAnimeExtensionRepos: GetAnimeExtensionRepo = appGraph.getAnimeExtensionRepo,
) {

    suspend operator fun invoke(): List<BackupExtensionRepos> {
        return getAnimeExtensionRepos.getAll()
            .map(backupExtensionReposMapper)
    }
}
