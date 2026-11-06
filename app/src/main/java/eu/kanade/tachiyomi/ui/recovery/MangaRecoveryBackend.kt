package eu.kanade.tachiyomi.ui.recovery

import eu.kanade.domain.entries.manga.model.hasCustomCover
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.data.track.EnhancedMangaTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.flow.first
import tachiyomi.data.source.manga.MangaSourceGateway
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.interactor.GetMangaFavorites
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
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
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.domain.track.manga.model.MangaTrack

class MangaRecoveryBackend(
    override val repository: RecoveryRepository,
    override val applier: RecoveryApplier,
    private val sourceManager: MangaSourceManager,
    private val getManga: GetManga,
    private val getFavorites: GetMangaFavorites,
    private val getChapters: GetChaptersByMangaId,
    private val getTracks: GetMangaTracks,
    private val trackerManager: TrackerManager,
    private val coverCache: MangaCoverCache,
    private val now: () -> Long = System::currentTimeMillis,
) : RecoveryBackend {

    override val search = RecoverySourceSearch { sourceId, query ->
        val source = requireSource(sourceId)
        MangaSourceGateway.search(source, 1, query, MangaSourceGateway.filters(source)).mangas
            .map { RecoveryCandidate(sourceId = sourceId, url = it.url, title = it.title) }
    }

    override val preparer = object : RecoveryPreparer {
        override suspend fun identities(originalIds: List<Long>): Map<Long, RecoveryIdentity> =
            originalIds.mapNotNull { id -> getManga.await(id)?.let { id to RecoveryIdentity(it.source, it.url) } }
                .toMap()

        override suspend fun prepare(entry: RecoveryEntry, session: RecoverySession): RecoveryApplyRequest {
            val choice = checkNotNull(entry.choice) { "Entry ${entry.id} has no approved choice" }
            val original = checkNotNull(getManga.await(entry.originalId)) { "Original ${entry.originalId} is gone" }
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
        val mangas = if (originalIds.isNotEmpty()) {
            originalIds.mapNotNull { getManga.await(it) }
        } else {
            sourceId?.let { getFavorites.subscribe(it).first() }.orEmpty()
        }
        return mangas.sortedBy { it.title.lowercase() }.map {
            RecoveryOriginal(
                id = it.id,
                title = it.title,
                thumbnailUrl = it.thumbnailUrl,
                sourceId = it.source,
                seasons = false,
            )
        }
    }

    override suspend fun destinationSources(excludeSourceId: Long?): List<RecoverySourceOption> =
        sourceManager.getOnlineSources()
            .filter { it.id != excludeSourceId }
            .sortedWith(compareBy({ it.lang }, { it.name.lowercase() }))
            .map { RecoverySourceOption(id = it.id, name = it.name, lang = it.lang) }

    override suspend fun preview(originalId: Long, candidate: RecoveryCandidate): RecoveryPreview {
        val destination = fetchDestination(requireSource(candidate.sourceId), candidate)
        val originalItems = getChapters.await(originalId).map {
            RecoveryItem(url = it.url, name = it.name, number = it.chapterNumber)
        }
        return RecoveryPreview(
            destination = destination,
            pairs = RecoveryItemMatcher.suggest(originalItems, destination.items),
            originalItemCount = originalItems.size,
        )
    }

    override suspend fun copyCover(originalId: Long, destinationId: Long) {
        val original = getManga.await(originalId) ?: return
        if (!original.hasCustomCover(coverCache)) return
        val destination = getManga.await(destinationId) ?: return
        coverCache.getCustomCoverFile(original.id).inputStream().use {
            coverCache.setCustomCoverToCache(destination, it)
        }
    }

    private suspend fun fetchDestination(source: MangaSource, candidate: RecoveryCandidate): RecoveryDestination {
        val update = MangaSourceGateway.update(
            source,
            SManga.create().apply {
                url = candidate.url
                title = candidate.title
            },
        )
        val details = update.manga
        val title = details.title.ifBlank { candidate.title }
        return RecoveryDestination(
            sourceId = source.id,
            url = candidate.url,
            title = title,
            items = update.chapters.toRecoveryItems(title),
            artist = details.artist,
            author = details.author,
            description = details.description,
            genre = details.getGenres(),
            status = details.status.toLong(),
            thumbnailUrl = details.thumbnail_url,
        )
    }

    private suspend fun migrateTracks(
        original: Manga,
        choice: RecoveryCandidate,
        destinationSource: MangaSource,
    ): List<RecoveryTrack> {
        val enhanced = trackerManager.trackers.filterIsInstance<EnhancedMangaTracker>()
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

    private fun requireSource(sourceId: Long): MangaSource =
        checkNotNull(sourceManager.get(sourceId)) { "Source $sourceId is not installed" }
}

internal fun MangaTrack.toRecoveryTrack() = RecoveryTrack(
    syncId = trackerId,
    remoteId = remoteId,
    libraryId = libraryId,
    title = title,
    lastProgress = lastChapterRead,
    totalItems = totalChapters,
    status = status,
    score = score,
    remoteUrl = remoteUrl,
    startDate = startDate,
    finishDate = finishDate,
    private = private,
)
