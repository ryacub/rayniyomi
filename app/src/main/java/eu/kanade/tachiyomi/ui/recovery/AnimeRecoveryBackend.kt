package eu.kanade.tachiyomi.ui.recovery

import eu.kanade.domain.entries.anime.model.hasCustomCover
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.track.EnhancedAnimeTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.flow.first
import tachiyomi.data.source.anime.AnimeSourceGateway
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.GetAnimeFavorites
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.recovery.RecoveryItemMatcher
import tachiyomi.domain.recovery.RecoveryPreparer
import tachiyomi.domain.recovery.RecoverySourceSearch
import tachiyomi.domain.recovery.model.RecoveryApplyRequest
import tachiyomi.domain.recovery.model.RecoveryCandidate
import tachiyomi.domain.recovery.model.RecoveryDestination
import tachiyomi.domain.recovery.model.RecoveryEntry
import tachiyomi.domain.recovery.model.RecoveryIdentity
import tachiyomi.domain.recovery.model.RecoveryItem
import tachiyomi.domain.recovery.model.RecoverySession
import tachiyomi.domain.recovery.model.RecoveryTrack
import tachiyomi.domain.recovery.repository.RecoveryApplier
import tachiyomi.domain.recovery.repository.RecoveryRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.model.AnimeTrack

class AnimeRecoveryBackend(
    override val repository: RecoveryRepository,
    override val applier: RecoveryApplier,
    private val sourceManager: AnimeSourceManager,
    private val getAnime: GetAnime,
    private val getFavorites: GetAnimeFavorites,
    private val getEpisodes: GetEpisodesByAnimeId,
    private val getTracks: GetAnimeTracks,
    private val trackerManager: TrackerManager,
    private val coverCache: AnimeCoverCache,
    private val sourcePreferences: SourcePreferences,
    private val now: () -> Long = System::currentTimeMillis,
) : RecoveryBackend {

    override val search = RecoverySourceSearch { sourceId, query ->
        val source = requireSource(sourceId)
        AnimeSourceGateway.search(source, 1, query, AnimeSourceGateway.filters(source)).animes
            .map { RecoveryCandidate(sourceId = sourceId, url = it.url, title = it.title) }
    }

    override val preparer = object : RecoveryPreparer {
        override suspend fun identities(originalIds: List<Long>): Map<Long, RecoveryIdentity> =
            originalIds.mapNotNull { id -> getAnime.await(id)?.let { id to RecoveryIdentity(it.source, it.url) } }
                .toMap()

        override suspend fun prepare(entry: RecoveryEntry, session: RecoverySession): RecoveryApplyRequest {
            val choice = checkNotNull(entry.choice) { "Entry ${entry.id} has no approved choice" }
            val original = checkNotNull(getAnime.await(entry.originalId)) { "Original ${entry.originalId} is gone" }
            val destinationSource = requireSource(choice.sourceId)
            return RecoveryApplyRequest(
                entryId = entry.id,
                runToken = checkNotNull(entry.runToken),
                originalId = entry.originalId,
                destination = fetchDestination(destinationSource, choice),
                pairs = entry.pairs,
                tracks = migrateTracks(original, choice, destinationSource),
                keepOriginal = session.keepOriginal,
                now = now(),
            )
        }
    }

    override suspend fun originals(sourceId: Long?, originalIds: List<Long>): List<RecoveryOriginal> {
        val animes = if (originalIds.isNotEmpty()) {
            originalIds.mapNotNull { getAnime.await(it) }
        } else {
            sourceId?.let { getFavorites.subscribe(it).first() }.orEmpty()
        }
        return animes.sortedBy { it.title.lowercase() }.map {
            RecoveryOriginal(
                id = it.id,
                title = it.title,
                thumbnailUrl = it.thumbnailUrl,
                sourceId = it.source,
                seasons =
                it.fetchType == FetchType.Seasons,
            )
        }
    }

    override suspend fun destinationSources(excludeSourceIds: Set<Long>): List<RecoverySourceOption> =
        sourceManager.getCatalogueSources()
            .filter { it.id !in excludeSourceIds && it.isEnabled() }
            .sortedWith(compareBy({ it.lang }, { it.name.lowercase() }))
            .map { RecoverySourceOption(id = it.id, name = it.name, lang = it.lang) }

    override suspend fun preview(originalId: Long, candidate: RecoveryCandidate): RecoveryPreview {
        val destination = fetchDestination(requireSource(candidate.sourceId), candidate)
        val originalItems = getEpisodes.await(originalId).map {
            RecoveryItem(url = it.url, name = it.name, number = it.episodeNumber)
        }
        return RecoveryPreview(
            destination = destination,
            pairs = RecoveryItemMatcher.suggest(originalItems, destination.items),
            originalItemCount = originalItems.size,
        )
    }

    override suspend fun copyCover(originalId: Long, destinationId: Long) {
        val original = getAnime.await(originalId) ?: return
        if (!original.hasCustomCover(coverCache)) return
        val destination = getAnime.await(destinationId) ?: return
        coverCache.getCustomCoverFile(original.id).inputStream().use {
            coverCache.setCustomCoverToCache(destination, it)
        }
    }

    private suspend fun fetchDestination(source: AnimeSource, candidate: RecoveryCandidate): RecoveryDestination {
        val request = SAnime.create().apply {
            url = candidate.url
            title = candidate.title
        }
        val details = AnimeSourceGateway.details(source, request)
        val title = details.title.ifBlank { candidate.title }
        val seasons = details.fetch_type == FetchType.Seasons
        val episodes = if (seasons) emptyList() else AnimeSourceGateway.episodes(source, request)
        return RecoveryDestination(
            sourceId = source.id,
            url = candidate.url,
            title = title,
            items = episodes.toRecoveryItems(title),
            seasons = seasons,
            artist = details.artist,
            author = details.author,
            description = details.description,
            genre = details.getGenres(),
            status = details.status.toLong(),
            thumbnailUrl = details.thumbnail_url,
        )
    }

    private suspend fun migrateTracks(
        original: Anime,
        choice: RecoveryCandidate,
        destinationSource: AnimeSource,
    ): List<RecoveryTrack> {
        val enhanced = trackerManager.trackers.filterIsInstance<EnhancedAnimeTracker>()
        val target = original.copy(source = choice.sourceId, url = choice.url)
        val originalSource = sourceManager.get(original.source)
        return getTracks.await(original.id).map { track ->
            val service = enhanced.firstOrNull { it.isTrackFrom(track, original, originalSource) }
            val moved = if (service == null) {
                track
            } else {
                checkNotNull(service.migrateTrack(track, target, destinationSource)) {
                    "A tracker cannot move to ${destinationSource.name}"
                }
            }
            moved.toRecoveryTrack()
        }
    }

    private fun AnimeSource.isEnabled(): Boolean =
        lang in sourcePreferences.enabledLanguages().get() &&
            id.toString() !in sourcePreferences.disabledAnimeSources().get()

    private fun requireSource(sourceId: Long): AnimeSource =
        checkNotNull(sourceManager.get(sourceId)) { "Source $sourceId is not installed" }
}

internal fun AnimeTrack.toRecoveryTrack() = RecoveryTrack(
    syncId = trackerId,
    remoteId = remoteId,
    libraryId = libraryId,
    title = title,
    lastProgress = lastEpisodeSeen,
    totalItems = totalEpisodes,
    status = status,
    score = score,
    remoteUrl = remoteUrl,
    startDate = startDate,
    finishDate = finishDate,
    private = private,
)
