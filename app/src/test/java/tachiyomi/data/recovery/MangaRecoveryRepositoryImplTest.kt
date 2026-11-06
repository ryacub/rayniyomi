package tachiyomi.data.recovery

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.recovery.model.NewRecoveryEntry
import tachiyomi.domain.recovery.model.RecoveryCandidate
import tachiyomi.domain.recovery.model.RecoveryState

class MangaRecoveryRepositoryImplTest {

    private fun MangaRecoveryDb.repository() = MangaRecoveryRepositoryImpl(handler)

    private suspend fun MangaRecoveryRepositoryImpl.newSession(
        vararg originals: Pair<Long, RecoveryState>,
        createdAt: Long = 1_000,
    ): Long = createSession(
        destinationSourceIds = listOf(20L, 30L),
        keepOriginal = true,
        entries = originals.map { (id, state) ->
            NewRecoveryEntry(originalId = id, searchQuery = "Title $id", state = state)
        },
        createdAt = createdAt,
    )

    private suspend fun MangaRecoveryRepositoryImpl.stateOf(sessionId: Long, originalId: Long): RecoveryState =
        getEntries(sessionId).single { it.originalId == originalId }.state

    @Test
    fun `a created session reloads with its sources, retention choice, and entries`() = runTest {
        MangaRecoveryDb().use { db ->
            db.insertManga(1)
            db.insertManga(2)
            val repository = db.repository()

            val sessionId = repository.newSession(1L to RecoveryState.SELECTED, 2L to RecoveryState.UNSUPPORTED)

            val session = repository.getSession(sessionId)!!
            assertEquals(listOf(20L, 30L), session.destinationSourceIds)
            assertTrue(session.keepOriginal)
            assertEquals(1_000L, session.createdAt)
            val entries = repository.getEntries(sessionId).sortedBy { it.originalId }
            assertEquals(listOf(1L, 2L), entries.map { it.originalId })
            assertEquals(listOf(RecoveryState.SELECTED, RecoveryState.UNSUPPORTED), entries.map { it.state })
            assertEquals("Title 1", entries[0].searchQuery)
            assertTrue(entries.all { it.selected && it.candidates.isEmpty() && it.choice == null })
        }
    }

    @Test
    fun `a search claim takes only selected entries in a searchable state and stamps the run token`() = runTest {
        MangaRecoveryDb().use { db ->
            (1L..3L).forEach(db::insertManga)
            val repository = db.repository()
            val sessionId = repository.newSession(
                1L to RecoveryState.SELECTED,
                2L to RecoveryState.UNSUPPORTED,
                3L to RecoveryState.SELECTED,
            )
            val entries = repository.getEntries(sessionId)
            val third = entries.single { it.originalId == 3L }
            repository.setSelected(third.id, selected = false)

            val claimed = repository.claimForSearch(sessionId, "run-a")

            val first = repository.getEntries(sessionId).single { it.originalId == 1L }
            assertEquals(listOf(first.id), claimed)
            assertEquals(RecoveryState.SEARCHING, first.state)
            assertEquals("run-a", first.runToken)
            assertEquals(RecoveryState.UNSUPPORTED, repository.stateOf(sessionId, 2L))
            assertEquals(RecoveryState.SELECTED, repository.stateOf(sessionId, 3L))
        }
    }

    @Test
    fun `a search result from the owning run is saved and releases the token`() = runTest {
        MangaRecoveryDb().use { db ->
            db.insertManga(1)
            val repository = db.repository()
            val sessionId = repository.newSession(1L to RecoveryState.SELECTED)
            val entryId = repository.getEntries(sessionId).single().id
            db.driver.claim(listOf(entryId), RecoveryState.SEARCHING, "run-a")
            val candidate = RecoveryCandidate(sourceId = 20, url = "/m/1", title = "Title 1")

            val saved = repository.saveSearchResult(entryId, "run-a", RecoveryState.NEEDS_REVIEW, listOf(candidate))

            val entry = repository.getEntries(sessionId).single()
            assertTrue(saved)
            assertEquals(RecoveryState.NEEDS_REVIEW, entry.state)
            assertEquals(listOf(candidate), entry.candidates)
            assertNull(entry.runToken)
        }
    }

    @Test
    fun `a search result from another run changes nothing`() = runTest {
        MangaRecoveryDb().use { db ->
            db.insertManga(1)
            val repository = db.repository()
            val sessionId = repository.newSession(1L to RecoveryState.SELECTED)
            val entryId = repository.getEntries(sessionId).single().id
            db.driver.claim(listOf(entryId), RecoveryState.SEARCHING, "run-a")

            val saved = repository.saveSearchResult(
                entryId,
                "run-b",
                RecoveryState.NEEDS_REVIEW,
                listOf(RecoveryCandidate(sourceId = 20, url = "/m/1", title = "Title 1")),
            )

            val entry = repository.getEntries(sessionId).single()
            assertFalse(saved)
            assertEquals(RecoveryState.SEARCHING, entry.state)
            assertTrue(entry.candidates.isEmpty())
            assertEquals("run-a", entry.runToken)
        }
    }

    @Test
    fun `interrupting a run marks only its unfinished entries interrupted`() = runTest {
        MangaRecoveryDb().use { db ->
            (1L..3L).forEach(db::insertManga)
            val repository = db.repository()
            val sessionId = repository.newSession(
                1L to RecoveryState.SELECTED,
                2L to RecoveryState.COMPLETED,
                3L to RecoveryState.SELECTED,
            )
            val ids = repository.getEntries(sessionId).associate { it.originalId to it.id }
            db.driver.claim(listOf(ids.getValue(1L)), RecoveryState.APPLYING, "run-a")
            db.driver.claim(listOf(ids.getValue(2L)), RecoveryState.COMPLETED, "run-a")
            db.driver.claim(listOf(ids.getValue(3L)), RecoveryState.SEARCHING, "run-b")

            val interrupted = repository.interruptRun("run-a")

            assertEquals(1, interrupted)
            assertEquals(RecoveryState.INTERRUPTED, repository.stateOf(sessionId, 1L))
            assertEquals(RecoveryState.COMPLETED, repository.stateOf(sessionId, 2L))
            assertEquals(RecoveryState.SEARCHING, repository.stateOf(sessionId, 3L))
        }
    }

    @Test
    fun `process start reconcile interrupts stale work once and never touches completed entries`() = runTest {
        MangaRecoveryDb().use { db ->
            (1L..4L).forEach(db::insertManga)
            val repository = db.repository()
            val sessionId = repository.newSession(
                1L to RecoveryState.SELECTED,
                2L to RecoveryState.SELECTED,
                3L to RecoveryState.COMPLETED,
                4L to RecoveryState.SELECTED,
            )
            val ids = repository.getEntries(sessionId).associate { it.originalId to it.id }
            db.driver.claim(listOf(ids.getValue(1L)), RecoveryState.SEARCHING, "old")
            db.driver.claim(listOf(ids.getValue(2L)), RecoveryState.APPLYING, "old")

            val first = repository.reconcileAfterProcessStart()
            db.driver.claim(listOf(ids.getValue(4L)), RecoveryState.APPLYING, "live")
            val second = repository.reconcileAfterProcessStart()

            assertEquals(2, first)
            assertEquals(0, second)
            assertEquals(RecoveryState.INTERRUPTED, repository.stateOf(sessionId, 1L))
            assertEquals(RecoveryState.INTERRUPTED, repository.stateOf(sessionId, 2L))
            assertEquals(RecoveryState.COMPLETED, repository.stateOf(sessionId, 3L))
            assertEquals(RecoveryState.APPLYING, repository.stateOf(sessionId, 4L))
        }
    }

    @Test
    fun `the latest open session skips sessions whose entries are all finished`() = runTest {
        MangaRecoveryDb().use { db ->
            (1L..3L).forEach(db::insertManga)
            val repository = db.repository()
            val open = repository.newSession(1L to RecoveryState.FAILED, createdAt = 1_000)
            repository.newSession(2L to RecoveryState.COMPLETED, 3L to RecoveryState.UNSUPPORTED, createdAt = 2_000)

            assertEquals(open, repository.getLatestOpenSession()?.id)
        }
    }

    @Test
    fun `deleting the original entry removes its recovery row`() = runTest {
        MangaRecoveryDb().use { db ->
            db.insertManga(1)
            db.insertManga(2)
            val repository = db.repository()
            val sessionId = repository.newSession(1L to RecoveryState.SELECTED, 2L to RecoveryState.SELECTED)

            db.driver.exec("DELETE FROM mangas WHERE _id = 1")

            assertEquals(listOf(2L), repository.getEntries(sessionId).map { it.originalId })
        }
    }
}
