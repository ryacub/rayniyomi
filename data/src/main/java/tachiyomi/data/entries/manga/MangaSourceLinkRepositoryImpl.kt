package tachiyomi.data.entries.manga

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.entries.manga.model.LinkMangaSourcesResult
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaSourceIdentity
import tachiyomi.domain.entries.manga.model.MangaSourceLink
import tachiyomi.domain.entries.manga.model.MangaSourceLinkWithManga
import tachiyomi.domain.entries.manga.model.RestoreMangaSourceLinkResult
import tachiyomi.domain.entries.manga.repository.MangaSourceLinkRepository

class MangaSourceLinkRepositoryImpl(
    private val handler: MangaDatabaseHandler,
) : MangaSourceLinkRepository {

    override suspend fun getAll(): List<MangaSourceLink> {
        return handler.awaitList {
            mangaSourceLinksQueries.getAll(::mapMangaSourceLink)
        }
    }

    override suspend fun getAllWithManga(): List<MangaSourceLinkWithManga> {
        return handler.await(inTransaction = true) {
            mangaSourceLinksQueries.getAll(::mapMangaSourceLink).executeAsList().mapNotNull { link ->
                val firstManga = mangasQueries.getMangaById(link.firstMangaId, MangaMapper::mapManga)
                    .executeAsOneOrNull()
                    ?: return@mapNotNull null
                val secondManga = mangasQueries.getMangaById(link.secondMangaId, MangaMapper::mapManga)
                    .executeAsOneOrNull()
                    ?: return@mapNotNull null
                MangaSourceLinkWithManga(link, firstManga, secondManga)
            }
        }
    }

    override suspend fun getForManga(mangaId: Long): MangaSourceLink? {
        return handler.awaitOneOrNull {
            mangaSourceLinksQueries.getForManga(mangaId, ::mapMangaSourceLink)
        }
    }

    override fun getForMangaAsFlow(mangaId: Long): Flow<MangaSourceLink?> {
        return handler.subscribeToOneOrNull {
            mangaSourceLinksQueries.getForManga(mangaId, ::mapMangaSourceLink)
        }
    }

    override suspend fun link(firstMangaId: Long, secondMangaId: Long): LinkMangaSourcesResult {
        if (firstMangaId == secondMangaId) return LinkMangaSourcesResult.Invalid

        return handler.await(inTransaction = true) {
            linkOriginals(firstMangaId, secondMangaId)
        }
    }

    override suspend fun linkCandidate(firstMangaId: Long, candidate: Manga): LinkMangaSourcesResult {
        return handler.await(inTransaction = true) {
            val firstSourceId = mangaSourceLinksQueries.getMangaSourceId(firstMangaId).executeAsOneOrNull()
                ?: return@await LinkMangaSourcesResult.Invalid
            if (firstSourceId == candidate.source) return@await LinkMangaSourcesResult.Invalid
            val firstIdentityIds = mangaSourceLinksQueries.getMangaIdsBySourceAndUrl(
                firstSourceId,
                mangasQueries.getMangaById(firstMangaId, MangaMapper::mapManga).executeAsOne().url,
            ).executeAsList()
            if (firstIdentityIds.size != 1 || firstIdentityIds.single() != firstMangaId) {
                return@await LinkMangaSourcesResult.Conflict
            }

            val existingFirstLink = mangaSourceLinksQueries.getForManga(firstMangaId, ::mapMangaSourceLink)
                .executeAsOneOrNull()
            val candidateIds = mangaSourceLinksQueries.getMangaIdsBySourceAndUrl(candidate.source, candidate.url)
                .executeAsList()
            if (candidateIds.size > 1) return@await LinkMangaSourcesResult.Conflict
            val existingCandidateId = candidateIds.singleOrNull()
            if (existingFirstLink != null) {
                return@await if (existingCandidateId != null &&
                    existingFirstLink.matches(firstMangaId, existingCandidateId)
                ) {
                    LinkMangaSourcesResult.AlreadyLinked
                } else {
                    LinkMangaSourcesResult.Conflict
                }
            }
            val candidateId = existingCandidateId ?: run {
                mangasQueries.insert(
                    source = candidate.source,
                    url = candidate.url,
                    artist = candidate.artist,
                    author = candidate.author,
                    description = candidate.description,
                    genre = candidate.genre,
                    title = candidate.title,
                    status = candidate.status,
                    thumbnailUrl = candidate.thumbnailUrl,
                    favorite = false,
                    lastUpdate = candidate.lastUpdate,
                    nextUpdate = candidate.nextUpdate,
                    calculateInterval = candidate.fetchInterval.toLong(),
                    initialized = candidate.initialized,
                    viewerFlags = candidate.viewerFlags,
                    chapterFlags = candidate.chapterFlags,
                    coverLastModified = candidate.coverLastModified,
                    dateAdded = candidate.dateAdded,
                    updateStrategy = candidate.updateStrategy,
                    version = candidate.version,
                    memo = candidate.memo,
                )
                mangaSourceLinksQueries.selectLastInsertedRowId().executeAsOne()
            }
            linkOriginals(firstMangaId, candidateId)
        }
    }

    override suspend fun unlink(firstMangaId: Long, secondMangaId: Long): Boolean {
        if (firstMangaId == secondMangaId) return false

        return handler.await(inTransaction = true) {
            val existing = mangaSourceLinksQueries.getForManga(firstMangaId, ::mapMangaSourceLink)
                .executeAsOneOrNull()
                ?: return@await false
            if (!existing.matches(firstMangaId, secondMangaId)) return@await false

            mangaSourceLinksQueries.deleteEndpointsForPair(firstMangaId, secondMangaId)
            mangaSourceLinksQueries.deletePair(firstMangaId, secondMangaId)
            true
        }
    }

    override suspend fun restore(
        first: MangaSourceIdentity,
        second: MangaSourceIdentity,
    ): RestoreMangaSourceLinkResult {
        return handler.await(inTransaction = true) {
            val firstIds = mangaSourceLinksQueries.getMangaIdsBySourceAndUrl(
                first.sourceId,
                first.url,
            ).executeAsList()
            val secondIds = mangaSourceLinksQueries.getMangaIdsBySourceAndUrl(
                second.sourceId,
                second.url,
            ).executeAsList()

            when {
                firstIds.isEmpty() || secondIds.isEmpty() -> RestoreMangaSourceLinkResult.MissingEndpoint
                firstIds.size > 1 || secondIds.size > 1 -> RestoreMangaSourceLinkResult.AmbiguousEndpoint
                else -> linkOriginals(firstIds.single(), secondIds.single()).toRestoreResult()
            }
        }
    }

    private fun Database.linkOriginals(firstMangaId: Long, secondMangaId: Long): LinkMangaSourcesResult {
        if (firstMangaId == secondMangaId) return LinkMangaSourcesResult.Invalid

        val firstExists = mangaSourceLinksQueries.getMangaId(firstMangaId).executeAsOneOrNull()
        val secondExists = mangaSourceLinksQueries.getMangaId(secondMangaId).executeAsOneOrNull()
        if (firstExists == null || secondExists == null) return LinkMangaSourcesResult.Invalid
        val firstSourceId = mangaSourceLinksQueries.getMangaSourceId(firstMangaId).executeAsOneOrNull()
        val secondSourceId = mangaSourceLinksQueries.getMangaSourceId(secondMangaId).executeAsOneOrNull()
        if (firstSourceId == secondSourceId) return LinkMangaSourcesResult.Invalid

        val firstLink = mangaSourceLinksQueries.getForManga(firstMangaId, ::mapMangaSourceLink)
            .executeAsOneOrNull()
        val secondLink = mangaSourceLinksQueries.getForManga(secondMangaId, ::mapMangaSourceLink)
            .executeAsOneOrNull()
        if (firstLink != null || secondLink != null) {
            return if (firstLink != null && firstLink == secondLink && firstLink.matches(firstMangaId, secondMangaId)) {
                LinkMangaSourcesResult.AlreadyLinked
            } else {
                LinkMangaSourcesResult.Conflict
            }
        }

        mangaSourceLinksQueries.insert(firstMangaId, secondMangaId)
        val linkId = mangaSourceLinksQueries.selectLastInsertedRowId().executeAsOne()
        mangaSourceLinksQueries.insertEndpoint(firstMangaId, linkId, endpointOrder = 0)
        mangaSourceLinksQueries.insertEndpoint(secondMangaId, linkId, endpointOrder = 1)
        return LinkMangaSourcesResult.Linked
    }

    private fun MangaSourceLink.matches(firstMangaId: Long, secondMangaId: Long): Boolean =
        (firstMangaId == this.firstMangaId && secondMangaId == this.secondMangaId) ||
            (firstMangaId == this.secondMangaId && secondMangaId == this.firstMangaId)

    private fun LinkMangaSourcesResult.toRestoreResult(): RestoreMangaSourceLinkResult = when (this) {
        LinkMangaSourcesResult.Linked -> RestoreMangaSourceLinkResult.Linked
        LinkMangaSourcesResult.AlreadyLinked -> RestoreMangaSourceLinkResult.AlreadyLinked
        LinkMangaSourcesResult.Conflict,
        LinkMangaSourcesResult.Invalid,
        -> RestoreMangaSourceLinkResult.Conflict
    }

    private fun mapMangaSourceLink(firstMangaId: Long, secondMangaId: Long) =
        MangaSourceLink(firstMangaId, secondMangaId)
}
