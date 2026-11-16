package tachiyomi.domain.entries.manga.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.entries.manga.model.LinkMangaSourcesResult
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaSourceIdentity
import tachiyomi.domain.entries.manga.model.MangaSourceLink
import tachiyomi.domain.entries.manga.model.MangaSourceLinkWithManga
import tachiyomi.domain.entries.manga.model.RestoreMangaSourceLinkResult

interface MangaSourceLinkRepository {

    suspend fun getAll(): List<MangaSourceLink>

    suspend fun getAllWithManga(): List<MangaSourceLinkWithManga>

    suspend fun getForManga(mangaId: Long): MangaSourceLink?

    fun getForMangaAsFlow(mangaId: Long): Flow<MangaSourceLink?>

    suspend fun link(firstMangaId: Long, secondMangaId: Long): LinkMangaSourcesResult

    suspend fun linkCandidate(firstMangaId: Long, candidate: Manga): LinkMangaSourcesResult

    suspend fun unlink(firstMangaId: Long, secondMangaId: Long): Boolean

    suspend fun restore(
        first: MangaSourceIdentity,
        second: MangaSourceIdentity,
    ): RestoreMangaSourceLinkResult
}
