package eu.kanade.tachiyomi.ui.entries.anime

import eu.kanade.domain.entries.anime.interactor.SetAnimeViewerFlags
import eu.kanade.domain.entries.anime.interactor.SyncSeasonsWithSource
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.anime.model.toSAnime
import eu.kanade.domain.entries.anime.model.toSourceSeasons
import eu.kanade.domain.items.episode.interactor.PopulateFillerMarks
import eu.kanade.domain.items.episode.interactor.SyncEpisodesWithSource
import eu.kanade.domain.items.episode.model.toSourceEpisodes
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.UnmeteredSource
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
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
import tachiyomi.core.common.util.lang.SourceLinkageException
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.source.anime.AnimeSourceGateway
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeUpdate
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

    fun running(animeId: Long): Deferred<AnimeRefreshResult>? = refreshes.running(animeId)

    fun refresh(
        source: AnimeSource,
        anime: Anime,
        trackItems: List<AnimeTrackItem>,
    ): Deferred<AnimeRefreshResult> = refreshes.join(anime.id) {
        val (detailsError, itemsError) = fetchFromSource(
            source,
            anime,
            fetchDetails = true,
            fetchItems = true,
            manualFetch = true,
        )
        val refreshedAnime = animeRepository.getAnimeById(anime.id)
        AnimeRefreshResult(detailsError, itemsError, updateAiringTime(refreshedAnime, trackItems, manualFetch = true))
    }

    suspend fun fetchFromSource(
        source: AnimeSource,
        anime: Anime,
        fetchDetails: Boolean,
        fetchItems: Boolean,
        manualFetch: Boolean = false,
    ): Pair<Throwable?, Throwable?> = withContext(dispatcher) {
        if (!fetchDetails && !fetchItems) return@withContext null to null
        try {
            if (AnimeSourceGateway.usesCombinedUpdates(source, anime.fetchType)) {
                refreshCombined(source, anime, fetchDetails, fetchItems, manualFetch)
            } else {
                coroutineScope {
                    val details = async {
                        if (!fetchDetails) return@async null
                        failureOf {
                            val remote = AnimeSourceGateway.details(source, anime.toSAnime())
                            updateAnime.awaitUpdateFromSource(anime, remote, manualFetch)
                        }
                    }
                    val items = async {
                        if (!fetchItems) return@async null
                        failureOf {
                            val episodes = when (anime.fetchType) {
                                FetchType.Episodes -> fetchEpisodes(source, anime, manualFetch)
                                FetchType.Seasons -> {
                                    val seasons = AnimeSourceGateway.seasons(source, anime.toSAnime())
                                    saveSeasons(source, anime, seasons, manualFetch, refreshChildren = true)
                                }
                            }
                            if (manualFetch) downloadNewEpisodes(anime, episodes)
                        }
                    }
                    details.await() to items.await()
                }
            }
        } catch (e: SourceLinkageException) {
            if (fetchDetails) e to null else null to e
        }
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

    private suspend fun refreshCombined(
        source: AnimeSource,
        anime: Anime,
        fetchDetails: Boolean,
        fetchItems: Boolean,
        manualFetch: Boolean,
    ): Pair<Throwable?, Throwable?> {
        val (details, saveItems) = try {
            when (anime.fetchType) {
                FetchType.Episodes -> {
                    val existing = getEpisodesByAnimeId.await(anime.id)
                    val expectedEpisodes = if (fetchItems) null else existing.toSet()
                    val update = AnimeSourceGateway.updateEpisodes(
                        source,
                        anime.toSAnime(),
                        existing.toSourceEpisodes(),
                        fetchDetails,
                        fetchItems,
                    )
                    update.anime to suspend {
                        if (fetchItems || update.episodes.isNotEmpty()) {
                            saveEpisodes(source, anime, update.episodes, manualFetch, expectedEpisodes)
                        } else {
                            emptyList()
                        }
                    }
                }
                FetchType.Seasons -> {
                    val existing = animeRepository.getAnimeSeasonsById(anime.id).map { it.anime }
                    val expectedSeasons = if (fetchItems) null else existing.toSet()
                    val update = AnimeSourceGateway.updateSeasons(
                        source,
                        anime.toSAnime(),
                        existing.toSourceSeasons(),
                        fetchDetails,
                        fetchItems,
                    )
                    update.anime to suspend {
                        if (fetchItems || update.seasons.isNotEmpty()) {
                            saveSeasons(
                                source,
                                anime,
                                update.seasons,
                                manualFetch,
                                refreshChildren = fetchItems,
                                expectedSeasons = expectedSeasons,
                            )
                        } else {
                            emptyList()
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return if (fetchDetails) e to null else null to e
        }
        val detailsError = failureOf {
            if (fetchDetails) {
                updateAnime.awaitUpdateFromSource(anime, details, manualFetch)
            } else {
                saveSourceMemo(anime, details)
            }
        }
        val itemsError = failureOf {
            val episodes = saveItems()
            if (manualFetch) downloadNewEpisodes(anime, episodes)
        }
        return detailsError to itemsError
    }

    private suspend fun existingEpisodes(anime: Anime): List<SEpisode> =
        getEpisodesByAnimeId.await(anime.id).toSourceEpisodes()

    private suspend fun saveSourceMemo(anime: Anime, sourceAnime: SAnime) {
        if (sourceAnime.memo != anime.memo) {
            updateAnime.await(AnimeUpdate(id = anime.id, memo = sourceAnime.memo))
        }
    }

    private suspend fun fetchEpisodes(source: AnimeSource, anime: Anime, manualFetch: Boolean): List<Episode> {
        val update = AnimeSourceGateway.episodeSnapshot(source, anime.toSAnime()) { existingEpisodes(anime) }
        saveSourceMemo(anime, update.anime)
        return saveEpisodes(source, anime, update.episodes, manualFetch)
    }

    private suspend fun saveEpisodes(
        source: AnimeSource,
        anime: Anime,
        episodes: List<SEpisode>,
        manualFetch: Boolean,
        expectedEpisodes: Set<Episode>? = null,
    ): List<Episode> {
        val newEpisodes = syncEpisodesWithSource.await(
            episodes,
            anime,
            source,
            manualFetch,
            expectedEpisodes = expectedEpisodes,
        )
        scope.launch { populateFillerMarks.await(anime, getEpisodesByAnimeId.await(anime.id)) }
        return newEpisodes
    }

    private suspend fun saveSeasons(
        source: AnimeSource,
        anime: Anime,
        seasons: List<SAnime>,
        manualFetch: Boolean,
        refreshChildren: Boolean,
        expectedSeasons: Set<Anime>? = null,
    ): List<Episode> {
        val newSeasons = syncSeasonsWithSource.await(seasons, anime, source, expectedSeasons = expectedSeasons)
        if (!refreshChildren || !libraryPreferences.updateSeasonOnRefresh().get()) return emptyList()
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
