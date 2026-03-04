package tachiyomi.data.entries.manga

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaUpdate
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.library.model.LibraryFilter
import java.time.LocalDate
import java.time.ZoneId

class MangaRepositoryImpl(
    private val handler: MangaDatabaseHandler,
) : MangaRepository {

    override suspend fun getMangaById(id: Long): Manga {
        return handler.awaitOne { mangasQueries.getMangaById(id, MangaMapper::mapManga) }
    }

    override suspend fun getMangaByIdAsFlow(id: Long): Flow<Manga> {
        return handler.subscribeToOne { mangasQueries.getMangaById(id, MangaMapper::mapManga) }
    }

    override suspend fun getMangaByUrlAndSourceId(url: String, sourceId: Long): Manga? {
        return handler.awaitOneOrNull {
            mangasQueries.getMangaByUrlAndSource(
                url,
                sourceId,
                MangaMapper::mapManga,
            )
        }
    }

    override fun getMangaByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Manga?> {
        return handler.subscribeToOneOrNull {
            mangasQueries.getMangaByUrlAndSource(
                url,
                sourceId,
                MangaMapper::mapManga,
            )
        }
    }

    override suspend fun getMangaFavorites(): List<Manga> {
        return handler.awaitList { mangasQueries.getFavorites(MangaMapper::mapManga) }
    }

    override suspend fun getReadMangaNotInLibrary(): List<Manga> {
        return handler.awaitList { mangasQueries.getReadMangaNotInLibrary(MangaMapper::mapManga) }
    }

    override suspend fun getLibraryManga(): List<LibraryManga> {
        return handler.awaitList { libraryViewQueries.library(MangaMapper::mapLibraryManga) }
    }

    override fun getLibraryMangaAsFlow(): Flow<List<LibraryManga>> {
        return handler.subscribeToList { libraryViewQueries.library(MangaMapper::mapLibraryManga) }
    }

    override suspend fun getLibraryMangaFiltered(filter: LibraryFilter): List<LibraryManga> {
        return handler.awaitList {
            libraryViewQueries.libraryFiltered(
                filterUnread = filter.filterUnread.ordinal.toLong(),
                filterStarted = filter.filterStarted.ordinal.toLong(),
                filterBookmarked = filter.filterBookmarked.ordinal.toLong(),
                filterCompleted = filter.filterCompleted.ordinal.toLong(),
                filterIntervalCustom = filter.filterIntervalCustom.ordinal.toLong(),
                mapper = MangaMapper::mapLibraryManga,
            )
        }
    }

    override fun getLibraryMangaFilteredAsFlow(filter: LibraryFilter): Flow<List<LibraryManga>> {
        return handler.subscribeToList {
            libraryViewQueries.libraryFiltered(
                filterUnread = filter.filterUnread.ordinal.toLong(),
                filterStarted = filter.filterStarted.ordinal.toLong(),
                filterBookmarked = filter.filterBookmarked.ordinal.toLong(),
                filterCompleted = filter.filterCompleted.ordinal.toLong(),
                filterIntervalCustom = filter.filterIntervalCustom.ordinal.toLong(),
                mapper = MangaMapper::mapLibraryManga,
            )
        }
    }

    override fun getMangaFavoritesBySourceId(sourceId: Long): Flow<List<Manga>> {
        return handler.subscribeToList { mangasQueries.getFavoriteBySourceId(sourceId, MangaMapper::mapManga) }
    }

    override suspend fun getDuplicateLibraryManga(id: Long, title: String): List<Manga> {
        return handler.awaitList {
            mangasQueries.getDuplicateLibraryManga(title, id, MangaMapper::mapManga)
        }
    }

    override suspend fun getDuplicateLibraryMangaByNormalizedTitle(
        normalizedTitle: String,
        excludeId: Long,
    ): List<Manga> {
        return handler.awaitList {
            mangasQueries.getDuplicateLibraryMangaByNormalizedTitle(normalizedTitle, excludeId, MangaMapper::mapManga)
        }
    }

    override suspend fun getDuplicateLibraryMangaByTracker(syncId: Long, remoteId: Long, excludeId: Long): List<Manga> {
        val mangaIds = handler.awaitList { manga_syncQueries.getMangaIdByTrackerId(syncId, remoteId) }
        return mangaIds
            .filter { it != excludeId }
            .mapNotNull { mangaId ->
                handler.awaitOneOrNull { mangasQueries.getMangaById(mangaId, MangaMapper::mapManga) }
            }
            .filter { it.favorite }
    }

    override suspend fun mergeEntries(keepId: Long, deleteId: Long) {
        handler.await(inTransaction = true) {
            // 1. Sync read progress for chapters that exist on both entries (same URL)
            chaptersQueries.syncAllDuplicateChapterProgress(keepMangaId = keepId, deleteMangaId = deleteId)

            // 2. Reparent chapters that are unique to the loser
            chaptersQueries.reparentChapters(keepMangaId = keepId, deleteMangaId = deleteId)

            // 3. Update progress for shared trackers to max, transfer unique trackers
            val loserTracks = manga_syncQueries.getTracksByMangaId(deleteId).executeAsList()
            loserTracks.forEach { loserTrack ->
                manga_syncQueries.updateProgressIfGreater(
                    lastChapterRead = loserTrack.last_chapter_read,
                    mangaId = keepId,
                    syncId = loserTrack.sync_id,
                )
            }
            manga_syncQueries.transferUniqueTrackers(winnerMangaId = keepId, loserMangaId = deleteId)

            // 4. Transfer categories
            mangas_categoriesQueries.transferCategories(keepMangaId = keepId, deleteMangaId = deleteId)

            // 5. Delete loser (CASCADE removes remaining chapters/trackers/categories)
            mangasQueries.delete(deleteId)
        }
    }

    override suspend fun getUpcomingManga(statuses: Set<Long>): Flow<List<Manga>> {
        val epochMillis = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000
        return handler.subscribeToList {
            mangasQueries.getUpcomingManga(epochMillis, statuses, MangaMapper::mapManga)
        }
    }

    override suspend fun resetMangaViewerFlags(): Boolean {
        return try {
            handler.await { mangasQueries.resetViewerFlags() }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun setMangaCategories(mangaId: Long, categoryIds: List<Long>) {
        handler.await(inTransaction = true) {
            mangas_categoriesQueries.deleteMangaCategoryByMangaId(mangaId)
            categoryIds.map { categoryId ->
                mangas_categoriesQueries.insert(mangaId, categoryId)
            }
        }
    }

    override suspend fun insertManga(manga: Manga): Long? {
        return handler.awaitOneOrNullExecutable(inTransaction = true) {
            mangasQueries.insert(
                source = manga.source,
                url = manga.url,
                artist = manga.artist,
                author = manga.author,
                description = manga.description,
                genre = manga.genre,
                title = manga.title,
                status = manga.status,
                thumbnailUrl = manga.thumbnailUrl,
                favorite = manga.favorite,
                lastUpdate = manga.lastUpdate,
                nextUpdate = manga.nextUpdate,
                calculateInterval = manga.fetchInterval.toLong(),
                initialized = manga.initialized,
                viewerFlags = manga.viewerFlags,
                chapterFlags = manga.chapterFlags,
                coverLastModified = manga.coverLastModified,
                dateAdded = manga.dateAdded,
                updateStrategy = manga.updateStrategy,
                version = manga.version,
            )
            mangasQueries.selectLastInsertedRowId()
        }
    }

    override suspend fun updateManga(update: MangaUpdate): Boolean {
        return try {
            partialUpdateManga(update)
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun updateAllManga(mangaUpdates: List<MangaUpdate>): Boolean {
        return try {
            partialUpdateManga(*mangaUpdates.toTypedArray())
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    private suspend fun partialUpdateManga(vararg mangaUpdates: MangaUpdate) {
        handler.await(inTransaction = true) {
            mangaUpdates.forEach { value ->
                mangasQueries.update(
                    source = value.source,
                    url = value.url,
                    artist = value.artist,
                    author = value.author,
                    description = value.description,
                    genre = value.genre?.let(StringListColumnAdapter::encode),
                    title = value.title,
                    status = value.status,
                    thumbnailUrl = value.thumbnailUrl,
                    favorite = value.favorite,
                    lastUpdate = value.lastUpdate,
                    nextUpdate = value.nextUpdate,
                    calculateInterval = value.fetchInterval?.toLong(),
                    initialized = value.initialized,
                    viewer = value.viewerFlags,
                    chapterFlags = value.chapterFlags,
                    coverLastModified = value.coverLastModified,
                    dateAdded = value.dateAdded,
                    mangaId = value.id,
                    updateStrategy = value.updateStrategy?.let(MangaUpdateStrategyColumnAdapter::encode),
                    version = value.version,
                    isSyncing = 0,
                )
            }
        }
    }
}
