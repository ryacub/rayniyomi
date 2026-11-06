package tachiyomi.data.recovery

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.recovery.model.NewRecoveryEntry
import tachiyomi.domain.recovery.model.RecoveryCandidate
import tachiyomi.domain.recovery.model.RecoveryEntry
import tachiyomi.domain.recovery.model.RecoverySession
import tachiyomi.domain.recovery.model.RecoveryState
import tachiyomi.domain.recovery.repository.RecoveryRepository

class AnimeRecoveryRepositoryImpl(
    private val handler: AnimeDatabaseHandler,
) : RecoveryRepository {

    private val writeLock = Mutex()
    private var reconciled = false

    override suspend fun <T> withWriteLock(block: suspend () -> T): T = writeLock.withLock { block() }

    override suspend fun createSession(
        destinationSourceIds: List<Long>,
        keepOriginal: Boolean,
        entries: List<NewRecoveryEntry>,
        createdAt: Long,
    ): Long = handler.await(inTransaction = true) {
        recoveryQueries.insertSession(createdAt, RecoveryCodec.encodeSourceIds(destinationSourceIds), keepOriginal)
        val sessionId = recoveryQueries.selectLastInsertedRowId().executeAsOne()
        entries.forEach { recoveryQueries.insertEntry(sessionId, it.originalId, it.state.name, it.searchQuery) }
        sessionId
    }

    override suspend fun getSession(sessionId: Long): RecoverySession? =
        handler.awaitOneOrNull { recoveryQueries.getSession(sessionId, ::session) }

    override suspend fun getLatestOpenSession(): RecoverySession? =
        handler.awaitOneOrNull {
            recoveryQueries.getLatestOpenSession(RecoveryCodec.names(RecoveryState.finished), ::session)
        }

    override suspend fun getEntries(sessionId: Long): List<RecoveryEntry> =
        handler.awaitList { recoveryQueries.getEntries(sessionId, RecoveryCodec::entry) }

    override fun subscribeEntries(sessionId: Long): Flow<List<RecoveryEntry>> =
        handler.subscribeToList { recoveryQueries.getEntries(sessionId, RecoveryCodec::entry) }

    override suspend fun setSelected(entryId: Long, selected: Boolean) {
        handler.await { recoveryQueries.setSelected(selected, entryId) }
    }

    override suspend fun claim(
        entryIds: List<Long>,
        from: Set<RecoveryState>,
        to: RecoveryState,
        runToken: String,
    ): List<Long> = handler.await(inTransaction = true) {
        val fromStates = RecoveryCodec.names(from)
        val claimable = recoveryQueries.getClaimable(entryIds, fromStates).executeAsList()
        recoveryQueries.claim(to.name, runToken, claimable, fromStates)
        claimable
    }

    override suspend fun saveSearchResult(
        entryId: Long,
        runToken: String,
        state: RecoveryState,
        candidates: List<RecoveryCandidate>,
    ): Boolean = handler.await {
        recoveryQueries.saveSearchResult(
            state = state.name,
            candidates = RecoveryCodec.encodeCandidates(candidates),
            entryId = entryId,
            runToken = runToken,
            searchingState = RecoveryState.SEARCHING.name,
        ).value == 1L
    }

    override suspend fun interruptRun(runToken: String): Int = handler.await {
        recoveryQueries.interruptRun(
            interruptedState = RecoveryState.INTERRUPTED.name,
            runToken = runToken,
            unfinishedStates = RecoveryCodec.names(RecoveryState.running),
        ).value.toInt()
    }

    override suspend fun reconcileAfterProcessStart(): Int = withWriteLock {
        if (reconciled) return@withWriteLock 0
        val count = handler.await {
            recoveryQueries.interruptAll(
                interruptedState = RecoveryState.INTERRUPTED.name,
                unfinishedStates = RecoveryCodec.names(RecoveryState.running),
            ).value.toInt()
        }
        reconciled = true
        count
    }

    private fun session(id: Long, createdAt: Long, destinationSourceIds: String, keepOriginal: Boolean) =
        RecoverySession(
            id = id,
            createdAt = createdAt,
            destinationSourceIds = RecoveryCodec.decodeSourceIds(destinationSourceIds),
            keepOriginal = keepOriginal,
        )
}
