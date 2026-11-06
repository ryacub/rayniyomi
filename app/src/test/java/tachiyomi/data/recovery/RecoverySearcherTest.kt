package tachiyomi.data.recovery

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.recovery.RecoverySearcher
import tachiyomi.domain.recovery.RecoverySourceSearch
import tachiyomi.domain.recovery.model.NewRecoveryEntry
import tachiyomi.domain.recovery.model.RecoveryCandidate
import tachiyomi.domain.recovery.model.RecoveryItemPair
import tachiyomi.domain.recovery.model.RecoveryState
import tachiyomi.domain.recovery.repository.RecoveryRepository
import java.io.IOException

class RecoverySearcherTest {

    private class FakeSearch(
        private val results: Map<Long, (String) -> List<RecoveryCandidate>>,
    ) : RecoverySourceSearch {
        val queries = mutableListOf<Pair<Long, String>>()
        override suspend fun search(sourceId: Long, query: String): List<RecoveryCandidate> {
            synchronized(queries) { queries += sourceId to query }
            return results.getValue(sourceId)(query)
        }
    }

    private fun candidate(sourceId: Long, url: String) = RecoveryCandidate(sourceId = sourceId, url = url, title = url)

    private suspend fun MangaRecoveryDb.session(vararg states: RecoveryState): Pair<MangaRecoveryRepositoryImpl, Long> {
        val repository = MangaRecoveryRepositoryImpl(handler)
        states.indices.forEach { insertManga(it + 1L) }
        val sessionId = repository.createSession(
            destinationSourceIds = listOf(20L, 30L),
            keepOriginal = true,
            entries = states.mapIndexed { index, state -> NewRecoveryEntry(index + 1L, "Title ${index + 1}", state) },
            createdAt = 1_000,
        )
        return repository to sessionId
    }

    private suspend fun MangaRecoveryRepositoryImpl.entry(sessionId: Long, originalId: Long) =
        getEntries(sessionId).single { it.originalId == originalId }

    @Test
    fun `one search result waits for review and is never approved`() = runTest {
        MangaRecoveryDb().use { db ->
            val (repository, sessionId) = db.session(RecoveryState.SELECTED)
            val search = FakeSearch(mapOf(20L to { listOf(candidate(20, "/m/1")) }, 30L to { emptyList() }))

            RecoverySearcher(repository, search).run(sessionId, "run")

            val entry = repository.entry(sessionId, 1)
            assertEquals(RecoveryState.NEEDS_REVIEW, entry.state)
            assertEquals(listOf(candidate(20, "/m/1")), entry.candidates)
            assertEquals(null, entry.choice)
        }
    }

    @Test
    fun `a failing source keeps the results of the other source`() = runTest {
        MangaRecoveryDb().use { db ->
            val (repository, sessionId) = db.session(RecoveryState.SELECTED)
            val search = FakeSearch(
                mapOf(
                    20L to { throw IOException("timeout") },
                    30L to { listOf(candidate(30, "/m/9")) },
                ),
            )

            RecoverySearcher(repository, search).run(sessionId, "run")

            val entry = repository.entry(sessionId, 1)
            assertEquals(RecoveryState.NEEDS_REVIEW, entry.state)
            assertEquals(listOf(candidate(30, "/m/9")), entry.candidates)
            assertEquals(mapOf(20L to "timeout"), entry.searchErrors)
        }
    }

    @Test
    fun `no result from any source marks the entry missing`() = runTest {
        MangaRecoveryDb().use { db ->
            val (repository, sessionId) = db.session(RecoveryState.SELECTED)
            val search = FakeSearch(mapOf(20L to { emptyList() }, 30L to { emptyList() }))

            RecoverySearcher(repository, search).run(sessionId, "run")

            assertEquals(RecoveryState.MISSING, repository.entry(sessionId, 1).state)
        }
    }

    @Test
    fun `every source failing marks the entry failed so it can be retried`() = runTest {
        MangaRecoveryDb().use { db ->
            val (repository, sessionId) = db.session(RecoveryState.SELECTED)
            val search = FakeSearch(mapOf(20L to { throw IOException("a") }, 30L to { throw IOException("b") }))

            RecoverySearcher(repository, search).run(sessionId, "run")

            val entry = repository.entry(sessionId, 1)
            assertEquals(RecoveryState.FAILED, entry.state)
            assertEquals(mapOf(20L to "a", 30L to "b"), entry.searchErrors)
        }
    }

    @Test
    fun `unsupported, approved, and deselected entries are not searched`() = runTest {
        MangaRecoveryDb().use { db ->
            val (repository, sessionId) = db.session(
                RecoveryState.UNSUPPORTED,
                RecoveryState.NEEDS_REVIEW,
                RecoveryState.SELECTED,
                RecoveryState.SELECTED,
            )
            repository.approve(repository.entry(sessionId, 2).id, candidate(20, "/m/2"), emptyList())
            repository.setSelected(repository.entry(sessionId, 3).id, selected = false)
            val search = FakeSearch(mapOf(20L to { emptyList() }, 30L to { emptyList() }))

            RecoverySearcher(repository, search).run(sessionId, "run")

            assertEquals(setOf("Title 4"), search.queries.map { it.second }.toSet())
            assertEquals(RecoveryState.READY, repository.entry(sessionId, 2).state)
        }
    }

    @Test
    fun `results keep source order and the per-source limit`() = runTest {
        MangaRecoveryDb().use { db ->
            val (repository, sessionId) = db.session(RecoveryState.SELECTED)
            val search = FakeSearch(
                mapOf(
                    20L to { (1..4).map { candidate(20, "/a/$it") } },
                    30L to { listOf(candidate(30, "/b/1")) },
                ),
            )

            RecoverySearcher(repository, search, maxResultsPerSource = 2).run(sessionId, "run")

            assertEquals(
                listOf(candidate(20, "/a/1"), candidate(20, "/a/2"), candidate(30, "/b/1")),
                repository.entry(sessionId, 1).candidates,
            )
        }
    }

    @Test
    fun `an approved choice keeps its pairs and reopening clears them`() = runTest {
        MangaRecoveryDb().use { db ->
            val (repository, sessionId) = db.session(RecoveryState.NEEDS_REVIEW)
            val entryId = repository.entry(sessionId, 1).id
            val pairs = listOf(RecoveryItemPair("o1", "d1"))

            repository.approve(entryId, candidate(20, "/m/1"), pairs)
            val approved = repository.entry(sessionId, 1)
            repository.reopen(entryId)
            val reopened = repository.entry(sessionId, 1)

            assertEquals(RecoveryState.READY, approved.state)
            assertEquals(candidate(20, "/m/1"), approved.choice)
            assertEquals(pairs, approved.pairs)
            assertEquals(RecoveryState.NEEDS_REVIEW, reopened.state)
            assertEquals(null, reopened.choice)
            assertTrue(reopened.pairs.isEmpty())
        }
    }

    @Test
    fun `apply claims approved entries, including interrupted ones, and search claims the rest`() = runTest {
        MangaRecoveryDb().use { db ->
            val (repository, sessionId) = db.session(
                RecoveryState.NEEDS_REVIEW,
                RecoveryState.NEEDS_REVIEW,
                RecoveryState.SELECTED,
                RecoveryState.SKIPPED,
            )
            val ids = repository.getEntries(sessionId).associate { it.originalId to it.id }
            repository.approve(ids.getValue(1), candidate(20, "/m/1"), emptyList())
            repository.approve(ids.getValue(2), candidate(20, "/m/2"), emptyList())
            db.driver.claim(listOf(ids.getValue(2)), RecoveryState.APPLYING, "old")
            db.driver.claim(listOf(ids.getValue(3)), RecoveryState.SEARCHING, "old")
            repository.interruptRun("old")

            val forSearch = repository.claimForSearch(sessionId, "search")
            val forApply = repository.claimForApply(sessionId, "apply")

            assertEquals(listOf(ids.getValue(1), ids.getValue(2)), forApply.sorted())
            assertEquals(listOf(ids.getValue(3)), forSearch)
        }
    }

    @Test
    fun `an unexpected error while saving results marks the claimed rows interrupted`() = runTest {
        MangaRecoveryDb().use { db ->
            val (repository, sessionId) = db.session(RecoveryState.SELECTED)
            val failing = object : RecoveryRepository by repository {
                override suspend fun saveSearchResult(
                    entryId: Long,
                    runToken: String,
                    state: RecoveryState,
                    candidates: List<RecoveryCandidate>,
                    searchErrors: Map<Long, String>,
                ): Boolean = throw IllegalStateException("disk full")
            }
            val search = FakeSearch(mapOf(20L to { emptyList() }, 30L to { emptyList() }))

            val error = runCatching { RecoverySearcher(failing, search).run(sessionId, "run") }.exceptionOrNull()

            assertEquals("disk full", error?.message)
            assertEquals(RecoveryState.INTERRUPTED, repository.entry(sessionId, 1).state)
        }
    }
}
