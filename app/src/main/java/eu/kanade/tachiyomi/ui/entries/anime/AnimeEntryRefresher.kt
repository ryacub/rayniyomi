package eu.kanade.tachiyomi.ui.entries.anime

import eu.kanade.domain.entries.anime.interactor.SetAnimeViewerFlags
import eu.kanade.domain.entries.anime.interactor.SyncSeasonsWithSource
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.anime.model.toSAnime
import eu.kanade.domain.items.episode.interactor.PopulateFillerMarks
import eu.kanade.domain.items.episode.interactor.SyncEpisodesWithSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.UnmeteredSource
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.ui.entries.anime.track.AnimeTrackItem
import eu.kanade.tachiyomi.ui.entries.common.InFlightRefreshes
import eu.kanade.tachiyomi.util.AniChartApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.domain.items.episode.interactor.FilterEpisodesForDownload
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.source.anime.AnimeSourceGateway
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.library.service.LibraryPreferences

data class AnimeRefreshResult(
    val detailsError: Throwable?,
    val itemsError: Throwable?,
    val nextAiringEpisode: Pair<Int, Long>,
)

class AnimeEntryRefresher(
    private val updateAnime: UpdateAnime,
    private val syncEpisodesWithSource: SyncEpisodesWithSource,
    private val syncSeasonsWithSource: SyncSeasonsWithSource,
    private val populateFillerMarks: PopulateFillerMarks,
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
    private val filterEpisodesForDownload: FilterEpisodesForDownload,
    private val downloadManager: AnimeDownloadManager,
    private val libraryPreferences: LibraryPreferences,
    private val animeRepository: AnimeRepository,
    private val setAnimeViewerFlags: SetAnimeViewerFlags,
    private val aniChartApi: AniChartApi = AniChartApi(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val refreshes = InFlightRefreshes<AnimeRefreshResult>(scope)

    fun refresh(
        source: AnimeSource,
        anime: Anime,
        trackItems: List<AnimeTrackItem>,
    ): Deferred<AnimeRefreshResult> = refreshes.join(anime.id) {
        val (detailsError, itemsError) = coroutineScope {
            val details = async { failureOf { fetchDetails(source, anime, manualFetch = true) } }
            val items = async { failureOf { fetchItems(source, anime, manualFetch = true) } }
            details.await() to items.await()
        }
        val refreshedAnime = animeRepository.getAnimeById(anime.id)
        AnimeRefreshResult(detailsError, itemsError, updateAiringTime(refreshedAnime, trackItems, manualFetch = true))
    }

    suspend fun fetchDetails(source: AnimeSource, anime: Anime, manualFetch: Boolean) {
        withContext(dispatcher) {
            val networkAnime = AnimeSourceGateway.details(source, anime.toSAnime())
            updateAnime.awaitUpdateFromSource(anime, networkAnime, manualFetch)
        }
    }

    suspend fun fetchItems(source: AnimeSource, anime: Anime, manualFetch: Boolean) = withContext(dispatcher) {
        val newEpisodes = when (anime.fetchType) {
            FetchType.Seasons -> fetchSeasons(source, anime, manualFetch)
            FetchType.Episodes -> fetchEpisodes(source, anime, manualFetch)
        }
        if (manualFetch) downloadNewEpisodes(anime, newEpisodes)
    }

    suspend fun updateAiringTime(
        anime: Anime,
        trackItems: List<AnimeTrackItem>,
        manualFetch: Boolean,
    ): Pair<Int, Long> {
        val airingEpisodeData = aniChartApi.loadAiringTime(anime, trackItems, manualFetch)
        setAnimeViewerFlags.awaitSetNextEpisodeAiring(anime.id, airingEpisodeData)
        return airingEpisodeData
    }

    private suspend fun fetchEpisodes(source: AnimeSource, anime: Anime, manualFetch: Boolean): List<Episode> {
        val episodes = AnimeSourceGateway.episodes(source, anime.toSAnime())
        val newEpisodes = syncEpisodesWithSource.await(episodes, anime, source, manualFetch)
        scope.launch { populateFillerMarks.await(anime, getEpisodesByAnimeId.await(anime.id)) }
        return newEpisodes
    }

    private suspend fun fetchSeasons(source: AnimeSource, anime: Anime, manualFetch: Boolean): List<Episode> {
        val seasons = AnimeSourceGateway.seasons(source, anime.toSAnime())
        val newSeasons = syncSeasonsWithSource.await(seasons, anime, source)
        if (!libraryPreferences.updateSeasonOnRefresh().get()) return emptyList()
        return fetchEpisodesOfSeasons(source, newSeasons, manualFetch)
    }

    private suspend fun fetchEpisodesOfSeasons(
        source: AnimeSource,
        seasons: List<Anime>,
        manualFetch: Boolean,
    ): List<Episode> = coroutineScope {
        val outdatedSeasons = seasons.filter {
            it.fetchType == FetchType.Episodes && (it.lastUpdate == 0L || it.status.toInt() != SAnime.COMPLETED)
        }
        val fetch: suspend (Anime) -> List<Episode> = { season ->
            try {
                fetchEpisodes(source, season, manualFetch)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                emptyList()
            }
        }
        if (source is UnmeteredSource) {
            outdatedSeasons.map { async { fetch(it) } }.awaitAll().flatten()
        } else {
            outdatedSeasons.flatMap {
                ensureActive()
                fetch(it)
            }
        }
    }

    private suspend fun downloadNewEpisodes(anime: Anime, newEpisodes: List<Episode>) {
        val episodes = filterEpisodesForDownload.await(anime, newEpisodes)
        if (episodes.isNotEmpty()) downloadManager.downloadEpisodes(anime, episodes)
    }

    private suspend fun failureOf(block: suspend () -> Unit): Throwable? =
        try {
            block()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            e
        }
}
