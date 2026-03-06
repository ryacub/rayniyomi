package eu.kanade.domain.track.enrichment

import eu.kanade.domain.track.enrichment.interactor.RefreshAnimeEnrichment
import eu.kanade.domain.track.enrichment.interactor.RefreshMangaEnrichment
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga

class BulkEnrichmentCoordinator(
    private val getLibraryManga: GetLibraryManga,
    private val getLibraryAnime: GetLibraryAnime,
    private val refreshMangaEnrichment: RefreshMangaEnrichment,
    private val refreshAnimeEnrichment: RefreshAnimeEnrichment,
) {

    suspend fun refreshAll(force: Boolean) = supervisorScope {
        val mangas = getLibraryManga.await()
        val animes = getLibraryAnime.await()

        val mangaJobs = mangas.map { manga ->
            async { refreshMangaEnrichment.await(manga.manga.id, manga.manga.title, force) }
        }
        val animeJobs = animes.map { anime ->
            async { refreshAnimeEnrichment.await(anime.anime.id, anime.anime.title, force) }
        }

        (mangaJobs + animeJobs).awaitAll()
    }
}
