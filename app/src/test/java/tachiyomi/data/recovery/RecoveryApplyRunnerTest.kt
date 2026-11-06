package tachiyomi.data.recovery

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.recovery.RecoveryApplyRunner
import tachiyomi.domain.recovery.RecoveryPreparer
import tachiyomi.domain.recovery.RecoverySearcher
import tachiyomi.domain.recovery.RecoverySourceSearch
import tachiyomi.domain.recovery.model.NewRecoveryEntry
import tachiyomi.domain.recovery.model.RecoveryApplyRequest
import tachiyomi.domain.recovery.model.RecoveryApplyResult
import tachiyomi.domain.recovery.model.RecoveryCandidate
import tachiyomi.domain.recovery.model.RecoveryDestination
import tachiyomi.domain.recovery.model.RecoveryEntry
import tachiyomi.domain.recovery.model.RecoveryIdentity
import tachiyomi.domain.recovery.model.RecoveryItem
import tachiyomi.domain.recovery.model.RecoveryReviewReason
import tachiyomi.domain.recovery.model.RecoverySession
import tachiyomi.domain.recovery.model.RecoveryState
import tachiyomi.domain.recovery.repository.RecoveryApplier
import tachiyomi.domain.recovery.repository.RecoveryRepository

class RecoveryApplyRunnerTest {

    private class LockSpy(private val delegate: RecoveryRepository) : RecoveryRepository by delegate {
        var depth = 0
        override suspend fun <T> withWriteLock(block: suspend () -> T): T = delegate.withWriteLock {
            depth++
            try {
                block()
            } finally {
                depth--
            }
        }
    }

    private class FakePreparer(private val failFor: Set<Long> = emptySet()) : RecoveryPreparer {
        override suspend fun identities(originalIds: List<Long>): Map<Long, RecoveryIdentity> =
            originalIds.associateWith { RecoveryIdentity(sourceId = 1, url = "url-$it") }

        override suspend fun prepare(entry: RecoveryEntry, session: RecoverySession): RecoveryApplyRequest {
            if (entry.originalId in failFor) throw IllegalStateException("source offline")
            val choice = checkNotNull(entry.choice)
            return RecoveryApplyRequest(
                entryId = entry.id,
                runToken = checkNotNull(entry.runToken),
                originalId = entry.originalId,
                destination = RecoveryDestination(
                    sourceId = choice.sourceId,
                    url = choice.url,
                    title = choice.title,
                    items = listOf(RecoveryItem(url = "d1", name = "Chapter 1", number = 1.0)),
                ),
                pairs = entry.pairs,
                tracks = emptyList(),
                keepOriginal = session.keepOriginal,
                now = 5_000,
            )
        }
    }

    private class Covers(private val failFor: Set<Long> = emptySet()) {
        val copied = mutableListOf<Long>()
        suspend fun copy(originalId: Long, destinationId: Long) {
            if (originalId in failFor) throw IllegalStateException("disk full")
            copied += destinationId
        }
    }

    private suspend fun MangaRecoveryDb.readySession(count: Int): Pair<MangaRecoveryRepositoryImpl, Long> {
        val repository = MangaRecoveryRepositoryImpl(handler)
        (1L..count).forEach { insertManga(it) }
        val sessionId = repository.createSession(
            destinationSourceIds = listOf(20L),
            keepOriginal = true,
            entries = (1L..count).map { NewRecoveryEntry(it, "Title $it", RecoveryState.NEEDS_REVIEW) },
            createdAt = 1_000,
        )
        repository.getEntries(sessionId).forEach {
            repository.approve(
                it.id,
                RecoveryCandidate(20, "/m/${it.originalId}", "Title ${it.originalId}"),
                emptyList(),
            )
        }
        return repository to sessionId
    }

    private suspend fun RecoveryRepository.states(sessionId: Long) =
        getEntries(sessionId).sortedBy { it.originalId }.map { it.state }

    @Test
    fun `ready rows apply in one run and each apply holds the write lock`() = runTest {
        MangaRecoveryDb().use { db ->
            val (repository, sessionId) = db.readySession(2)
            val spy = LockSpy(repository)
            val real = MangaRecoveryApplierImpl(db.handler)
            val lockedDuringApply = mutableListOf<Boolean>()
            val applier = RecoveryApplier { request ->
                lockedDuringApply += spy.depth > 0
                real.apply(request)
            }
            val covers = Covers()

            RecoveryApplyRunner(spy, applier, FakePreparer(), covers::copy).run(sessionId, "run")

            assertEquals(listOf(RecoveryState.COMPLETED, RecoveryState.COMPLETED), repository.states(sessionId))
            assertEquals(listOf(true, true), lockedDuringApply)
            assertEquals(2, covers.copied.size)
        }
    }

    @Test
    fun `a row whose destination is another selected original returns to review with its choice`() = runTest {
        MangaRecoveryDb().use { db ->
            val (repository, sessionId) = db.readySession(2)
            val first = repository.getEntries(sessionId).single { it.originalId == 1L }
            repository.reopen(first.id)
            repository.approve(first.id, RecoveryCandidate(1, "url-2", "Title 2"), emptyList())

            RecoveryApplyRunner(repository, MangaRecoveryApplierImpl(db.handler), FakePreparer(), Covers()::copy)
                .run(sessionId, "run")

            val blocked = repository.getEntries(sessionId).single { it.originalId == 1L }
            assertEquals(RecoveryState.NEEDS_REVIEW, blocked.state)
            assertEquals(RecoveryReviewReason.DESTINATION_IS_ORIGINAL.name, blocked.error)
            assertEquals(RecoveryCandidate(1, "url-2", "Title 2"), blocked.choice)
            assertEquals(RecoveryState.COMPLETED, repository.getEntries(sessionId).single { it.originalId == 2L }.state)
        }
    }

    @Test
    fun `a preparation failure fails only that entry and the run continues`() = runTest {
        MangaRecoveryDb().use { db ->
            val (repository, sessionId) = db.readySession(2)

            RecoveryApplyRunner(
                repository,
                MangaRecoveryApplierImpl(db.handler),
                FakePreparer(failFor = setOf(1)),
                Covers()::copy,
            )
                .run(sessionId, "run")

            val entries = repository.getEntries(sessionId).sortedBy { it.originalId }
            assertEquals(listOf(RecoveryState.FAILED, RecoveryState.COMPLETED), entries.map { it.state })
            assertEquals("source offline", entries[0].error)
        }
    }

    @Test
    fun `a cancel before commit marks the claimed rows interrupted`() = runTest {
        MangaRecoveryDb().use { db ->
            val (repository, sessionId) = db.readySession(2)
            val started = CompletableDeferred<Unit>()
            val applier = RecoveryApplier {
                started.complete(Unit)
                awaitCancellation()
            }

            val job =
                launch {
                    RecoveryApplyRunner(repository, applier, FakePreparer(), Covers()::copy).run(sessionId, "run")
                }
            started.await()
            job.cancel()
            job.join()

            assertEquals(listOf(RecoveryState.INTERRUPTED, RecoveryState.INTERRUPTED), repository.states(sessionId))
        }
    }

    @Test
    fun `a cancel right after commit keeps the entry completed and still copies the cover`() = runTest {
        MangaRecoveryDb().use { db ->
            val (repository, sessionId) = db.readySession(2)
            val real = MangaRecoveryApplierImpl(db.handler)
            val covers = Covers()
            val applier = RecoveryApplier { request ->
                real.apply(request)
                throw CancellationException("navigation")
            }

            runCatching { RecoveryApplyRunner(repository, applier, FakePreparer(), covers::copy).run(sessionId, "run") }

            assertEquals(listOf(RecoveryState.COMPLETED, RecoveryState.INTERRUPTED), repository.states(sessionId))
            assertEquals(1, covers.copied.size)
        }
    }

    @Test
    fun `a cover copy failure is reported and the entry stays completed`() = runTest {
        MangaRecoveryDb().use { db ->
            val (repository, sessionId) = db.readySession(1)

            val report = RecoveryApplyRunner(
                repository,
                MangaRecoveryApplierImpl(db.handler),
                FakePreparer(),
                Covers(failFor = setOf(1))::copy,
            ).run(sessionId, "run")

            val entry = repository.getEntries(sessionId).single()
            assertEquals(RecoveryState.COMPLETED, entry.state)
            assertEquals(mapOf(entry.id to "disk full"), report.coverFailures)
        }
    }

    @Test
    fun `a cancel during search marks the claimed rows interrupted`() = runTest {
        MangaRecoveryDb().use { db ->
            val repository = MangaRecoveryRepositoryImpl(db.handler)
            db.insertManga(1)
            val sessionId = repository.createSession(
                listOf(20L),
                true,
                listOf(NewRecoveryEntry(1, "Title 1", RecoveryState.SELECTED)),
                1_000,
            )
            val started = CompletableDeferred<Unit>()
            val search = RecoverySourceSearch { _, _ ->
                started.complete(Unit)
                awaitCancellation()
            }

            val job = launch { RecoverySearcher(repository, search).run(sessionId, "run") }
            started.await()
            job.cancel()
            job.join()

            assertEquals(listOf(RecoveryState.INTERRUPTED), repository.states(sessionId))
        }
    }

    @Test
    fun `claiming in a new process first interrupts work left by the old one`() = runTest {
        MangaRecoveryDb().use { db ->
            val (oldProcess, sessionId) = db.readySession(1)
            assertEquals(1, oldProcess.claimForApply(sessionId, "old").size)

            val newProcess = MangaRecoveryRepositoryImpl(db.handler)
            val claimed = newProcess.claimForApply(sessionId, "new")

            assertEquals(1, claimed.size)
            assertTrue(newProcess.getEntries(sessionId).single().runToken == "new")
        }
    }

    @Test
    fun `a row whose destination a finished row already took returns to review`() = runTest {
        MangaRecoveryDb().use { db ->
            val (repository, sessionId) = db.readySession(2)
            val second = repository.getEntries(sessionId).single { it.originalId == 2L }
            repository.reopen(second.id)
            val runner =
                RecoveryApplyRunner(repository, MangaRecoveryApplierImpl(db.handler), FakePreparer(), Covers()::copy)
            runner.run(sessionId, "run-1")

            repository.approve(second.id, RecoveryCandidate(20, "/m/1", "Title 1"), emptyList())
            runner.run(sessionId, "run-2")

            val held = repository.getEntries(sessionId).single { it.originalId == 2L }
            assertEquals(RecoveryState.NEEDS_REVIEW, held.state)
            assertEquals(RecoveryReviewReason.SHARED_DESTINATION.name, held.error)
            assertEquals(RecoveryState.COMPLETED, repository.getEntries(sessionId).single { it.originalId == 1L }.state)
        }
    }

    @Test
    fun `an unexpected apply error marks the claimed rows interrupted`() = runTest {
        MangaRecoveryDb().use { db ->
            val (repository, sessionId) = db.readySession(2)
            val applier = RecoveryApplier { throw IllegalStateException("disk full") }

            val error = runCatching {
                RecoveryApplyRunner(repository, applier, FakePreparer(), Covers()::copy).run(sessionId, "run")
            }.exceptionOrNull()

            assertEquals("disk full", error?.message)
            assertEquals(listOf(RecoveryState.INTERRUPTED, RecoveryState.INTERRUPTED), repository.states(sessionId))
        }
    }
}
