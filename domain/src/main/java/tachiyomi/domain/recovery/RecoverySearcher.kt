package tachiyomi.domain.recovery

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import tachiyomi.domain.recovery.model.RecoveryCandidate
import tachiyomi.domain.recovery.model.RecoveryEntry
import tachiyomi.domain.recovery.model.RecoveryState
import tachiyomi.domain.recovery.repository.RecoveryRepository

fun interface RecoverySourceSearch {
    suspend fun search(sourceId: Long, query: String): List<RecoveryCandidate>
}

class RecoverySearcher(
    private val repository: RecoveryRepository,
    private val search: RecoverySourceSearch,
    private val maxResultsPerSource: Int = 5,
    parallelism: Int = 4,
) {

    private val permits = Semaphore(parallelism)

    suspend fun run(sessionId: Long, runToken: String) {
        val session = repository.getSession(sessionId) ?: return
        try {
            val claimed = repository.claimForSearch(sessionId, runToken).toSet()
            val entries = repository.getEntries(sessionId).filter { it.id in claimed }
            searchEntries(entries, session.destinationSourceIds, runToken)
        } catch (e: Exception) {
            withContext(NonCancellable) { repository.interruptRun(runToken) }
            throw e
        }
    }

    private suspend fun searchEntries(entries: List<RecoveryEntry>, sourceIds: List<Long>, runToken: String) {
        coroutineScope {
            entries.map { entry ->
                async {
                    val outcome = searchAll(sourceIds, entry.searchQuery)
                    repository.saveSearchResult(entry.id, runToken, outcome.state, outcome.candidates, outcome.errors)
                }
            }.awaitAll()
        }
    }

    private suspend fun searchAll(sourceIds: List<Long>, query: String): Outcome = coroutineScope {
        val perSource = sourceIds.map { sourceId ->
            async {
                permits.withPermit {
                    try {
                        Result.success(search.search(sourceId, query).take(maxResultsPerSource))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
            }
        }.awaitAll()
        val candidates = perSource.mapNotNull { it.getOrNull() }.flatten()
        val errors = sourceIds.zip(perSource).mapNotNull { (sourceId, result) ->
            result.exceptionOrNull()?.let { sourceId to (it.message ?: it::class.simpleName.orEmpty()) }
        }.toMap()
        val state = when {
            candidates.isNotEmpty() -> RecoveryState.NEEDS_REVIEW
            errors.size == sourceIds.size -> RecoveryState.FAILED
            else -> RecoveryState.MISSING
        }
        Outcome(state, candidates, errors)
    }

    private data class Outcome(
        val state: RecoveryState,
        val candidates: List<RecoveryCandidate>,
        val errors: Map<Long, String>,
    )
}
