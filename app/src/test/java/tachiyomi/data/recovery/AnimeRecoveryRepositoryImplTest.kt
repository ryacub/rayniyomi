package tachiyomi.data.recovery

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.recovery.model.NewRecoveryEntry
import tachiyomi.domain.recovery.model.RecoveryCandidate
import tachiyomi.domain.recovery.model.RecoveryState

class AnimeRecoveryRepositoryImplTest {

    @Test
    fun `an anime session keeps search results and reconciles stale work after process start`() = runTest {
        AnimeRecoveryDb().use { db ->
            db.insertAnime(1)
            db.insertAnime(2)
            val repository = AnimeRecoveryRepositoryImpl(db.handler)
            val sessionId = repository.createSession(
                destinationSourceIds = listOf(20L),
                keepOriginal = false,
                entries = listOf(
                    NewRecoveryEntry(originalId = 1, searchQuery = "Title 1", state = RecoveryState.SELECTED),
                    NewRecoveryEntry(originalId = 2, searchQuery = "Title 2", state = RecoveryState.SELECTED),
                ),
                createdAt = 1_000,
            )
            val ids = repository.getEntries(sessionId).associate { it.originalId to it.id }
            val candidate = RecoveryCandidate(sourceId = 20, url = "/a/1", title = "Title 1")
            repository.claim(ids.values.toList(), setOf(RecoveryState.SELECTED), RecoveryState.SEARCHING, "old")
            repository.saveSearchResult(ids.getValue(1L), "old", RecoveryState.NEEDS_REVIEW, listOf(candidate))

            val reconciled = repository.reconcileAfterProcessStart()

            val entries = repository.getEntries(sessionId).associateBy { it.originalId }
            assertEquals(1, reconciled)
            assertEquals(false, repository.getSession(sessionId)?.keepOriginal)
            assertEquals(listOf(candidate), entries.getValue(1L).candidates)
            assertEquals(RecoveryState.NEEDS_REVIEW, entries.getValue(1L).state)
            assertEquals(RecoveryState.INTERRUPTED, entries.getValue(2L).state)
        }
    }
}
