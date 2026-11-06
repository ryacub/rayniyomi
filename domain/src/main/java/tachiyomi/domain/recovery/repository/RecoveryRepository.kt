package tachiyomi.domain.recovery.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.recovery.model.NewRecoveryEntry
import tachiyomi.domain.recovery.model.RecoveryCandidate
import tachiyomi.domain.recovery.model.RecoveryEntry
import tachiyomi.domain.recovery.model.RecoveryItemPair
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

    suspend fun getLatestOpenSession(originalIds: List<Long>): RecoverySession?

    suspend fun getEntries(sessionId: Long): List<RecoveryEntry>

    fun subscribeEntries(sessionId: Long): Flow<List<RecoveryEntry>>

    suspend fun setSelected(entryId: Long, selected: Boolean)

    suspend fun saveSearchResult(
        entryId: Long,
        runToken: String,
        state: RecoveryState,
        candidates: List<RecoveryCandidate>,
        searchErrors: Map<Long, String> = emptyMap(),
    ): Boolean

    suspend fun claimForSearch(sessionId: Long, runToken: String): List<Long>

    suspend fun claimForApply(sessionId: Long, runToken: String): List<Long>

    suspend fun approve(entryId: Long, choice: RecoveryCandidate, pairs: List<RecoveryItemPair>): Boolean

    suspend fun reopen(entryId: Long): Boolean

    suspend fun skip(entryId: Long): Boolean

    suspend fun setQuery(entryId: Long, query: String): Boolean

    suspend fun holdForReview(entryId: Long, reason: String): Boolean

    suspend fun failRun(entryId: Long, runToken: String, message: String): Boolean

    suspend fun interruptRun(runToken: String): Int

    suspend fun reconcileAfterProcessStart(): Int

    suspend fun <T> withWriteLock(block: suspend () -> T): T
}
