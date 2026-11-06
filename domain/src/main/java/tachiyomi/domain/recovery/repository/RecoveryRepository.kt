package tachiyomi.domain.recovery.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.recovery.model.NewRecoveryEntry
import tachiyomi.domain.recovery.model.RecoveryCandidate
import tachiyomi.domain.recovery.model.RecoveryEntry
import tachiyomi.domain.recovery.model.RecoverySession
import tachiyomi.domain.recovery.model.RecoveryState

interface RecoveryRepository {

    suspend fun createSession(
        destinationSourceIds: List<Long>,
        keepOriginal: Boolean,
        entries: List<NewRecoveryEntry>,
        createdAt: Long,
    ): Long

    suspend fun getSession(sessionId: Long): RecoverySession?

    suspend fun getLatestOpenSession(): RecoverySession?

    suspend fun getEntries(sessionId: Long): List<RecoveryEntry>

    fun subscribeEntries(sessionId: Long): Flow<List<RecoveryEntry>>

    suspend fun setSelected(entryId: Long, selected: Boolean)

    suspend fun claim(
        entryIds: List<Long>,
        from: Set<RecoveryState>,
        to: RecoveryState,
        runToken: String,
    ): List<Long>

    suspend fun saveSearchResult(
        entryId: Long,
        runToken: String,
        state: RecoveryState,
        candidates: List<RecoveryCandidate>,
    ): Boolean

    suspend fun interruptRun(runToken: String): Int

    suspend fun reconcileAfterProcessStart(): Int

    suspend fun <T> withWriteLock(block: suspend () -> T): T
}
