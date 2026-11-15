package eu.kanade.tachiyomi.data.backup.restore.restorers

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.data.entries.manga.MangaRepositoryImpl
import tachiyomi.data.entries.manga.MangaSourceLinkRepositoryImpl
import tachiyomi.domain.entries.manga.model.LinkMangaSourcesResult
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaSourceIdentity
import tachiyomi.domain.entries.manga.model.MangaSourceLink
import tachiyomi.domain.entries.manga.model.RestoreMangaSourceLinkResult

class MangaSourceLinkRepositoryTest {

    @Test
    fun `link keeps the first endpoint and rejects same source or reused endpoints`() = runTest {
        InMemoryMangaDb().use { db ->
            db.insertManga(id = 1, url = "first", title = "First", source = 1)
            db.insertManga(id = 2, url = "second", title = "Second", source = 2)
            db.insertManga(id = 3, url = "third", title = "Third", source = 3)
            db.insertManga(id = 4, url = "fourth", title = "Fourth", source = 3)
            val repository = MangaSourceLinkRepositoryImpl(db.handler)

            assertEquals(LinkMangaSourcesResult.Linked, repository.link(1, 2))
            assertEquals(MangaSourceLink(1, 2), repository.getForManga(2))
            assertEquals(LinkMangaSourcesResult.AlreadyLinked, repository.link(2, 1))
            assertEquals(LinkMangaSourcesResult.Conflict, repository.link(1, 3))
            assertEquals(LinkMangaSourcesResult.Invalid, repository.link(3, 4))
            assertEquals(listOf(MangaSourceLink(1, 2)), repository.getAll())
        }
    }

    @Test
    fun `link candidate materializes an unread nonfavorite in the same link operation`() = runTest {
        InMemoryMangaDb().use { db ->
            db.insertManga(id = 1, url = "first", title = "First", source = 1)
            val repository = MangaSourceLinkRepositoryImpl(db.handler)
            val mangaRepository = MangaRepositoryImpl(db.handler)
            val candidate = Manga.create().copy(
                source = 2,
                url = "second",
                title = "Second",
                favorite = true,
                initialized = true,
            )

            assertEquals(LinkMangaSourcesResult.Linked, repository.linkCandidate(1, candidate))
            val saved = mangaRepository.getMangaByUrlAndSourceId("second", 2)
            assertEquals(false, saved?.favorite)
            assertEquals(MangaSourceLink(1, saved!!.id), repository.getForManga(1))
        }
    }

    @Test
    fun `ambiguous initiating identity does not materialize the candidate`() = runTest {
        InMemoryMangaDb().use { db ->
            db.insertManga(id = 1, url = "duplicate", title = "First", source = 1)
            db.insertManga(id = 2, url = "duplicate", title = "Duplicate", source = 1)
            val repository = MangaSourceLinkRepositoryImpl(db.handler)
            val candidate = Manga.create().copy(source = 2, url = "candidate", title = "Candidate")

            assertEquals(LinkMangaSourcesResult.Conflict, repository.linkCandidate(1, candidate))
            assertEquals(null, MangaRepositoryImpl(db.handler).getMangaByUrlAndSourceId("candidate", 2))
            assertEquals(emptyList<MangaSourceLink>(), repository.getAll())
        }
    }

    @Test
    fun `deleting one original removes the pair and leaves the other endpoint reusable`() = runTest {
        InMemoryMangaDb().use { db ->
            db.insertManga(id = 1, url = "first", title = "First", source = 1)
            db.insertManga(id = 2, url = "second", title = "Second", source = 2)
            db.insertManga(id = 3, url = "third", title = "Third", source = 3)
            val repository = MangaSourceLinkRepositoryImpl(db.handler)

            assertEquals(LinkMangaSourcesResult.Linked, repository.link(1, 2))
            db.driver.execute(null, "DELETE FROM mangas WHERE _id = 1", 0)
            assertEquals(null, repository.getForManga(2))
            assertEquals(LinkMangaSourcesResult.Linked, repository.link(2, 3))
            assertEquals(listOf(MangaSourceLink(2, 3)), repository.getAll())
        }
    }

    @Test
    fun `restore resolves endpoints by source and url and is idempotent`() = runTest {
        InMemoryMangaDb().use { db ->
            db.insertManga(id = 1, url = "first", title = "First", source = 11)
            db.insertManga(id = 2, url = "second", title = "Second", source = 12)
            db.insertManga(id = 3, url = "other", title = "Other", source = 13)
            db.insertManga(id = 4, url = "ambiguous", title = "Ambiguous A", source = 14)
            db.insertManga(id = 5, url = "ambiguous", title = "Ambiguous B", source = 14)
            val repository = MangaSourceLinkRepositoryImpl(db.handler)
            val first = MangaSourceIdentity(sourceId = 11, url = "first")
            val second = MangaSourceIdentity(sourceId = 12, url = "second")

            assertEquals(RestoreMangaSourceLinkResult.Linked, repository.restore(first, second))
            assertEquals(RestoreMangaSourceLinkResult.AlreadyLinked, repository.restore(second, first))
            assertEquals(
                RestoreMangaSourceLinkResult.Conflict,
                repository.restore(first, MangaSourceIdentity(sourceId = 13, url = "other")),
            )
            assertEquals(
                RestoreMangaSourceLinkResult.MissingEndpoint,
                repository.restore(first, MangaSourceIdentity(sourceId = 15, url = "missing")),
            )
            assertEquals(
                RestoreMangaSourceLinkResult.AmbiguousEndpoint,
                repository.restore(MangaSourceIdentity(sourceId = 14, url = "ambiguous"), second),
            )
            assertEquals(listOf(MangaSourceLink(1, 2)), repository.getAll())
        }
    }
}
