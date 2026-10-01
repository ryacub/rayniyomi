package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupAnimeSource
import eu.kanade.tachiyomi.di.appGraph
import tachiyomi.domain.source.anime.service.AnimeSourceManager

class AnimeSourcesBackupCreator(
    private val animeSourceManager: AnimeSourceManager = appGraph.animeSourceManager,
) {

    operator fun invoke(animes: List<BackupAnime>): List<BackupAnimeSource> {
        return animes
            .asSequence()
            .map(BackupAnime::source)
            .distinct()
            .map(animeSourceManager::getOrStub)
            .map { it.toBackupSource() }
            .toList()
    }
}

private fun AnimeSource.toBackupSource() =
    BackupAnimeSource(
        name = this.name,
        sourceId = this.id,
    )
