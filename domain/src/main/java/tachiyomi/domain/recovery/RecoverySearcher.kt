package tachiyomi.domain.recovery

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import tachiyomi.domain.recovery.model.RecoveryCandidate
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
        val claimed = repository.claimForSearch(sessionId, runToken).toSet()
        val entries = repository.getEntries(sessionId).filter { it.id in claimed }
        coroutineScope {
            entries.map { entry ->
                async {
                    val outcome = searchAll(session.destinationSourceIds, entry.searchQuery)
                    repository.saveSearchResult(entry.id, runToken, outcome.state, outcome.candidates)
                }
            }.awaitAll()
        }
    }

    private suspend fun searchAll(sourceIds: List<Long>, query: String): Outcome = coroutineScope {
        val perSource = sourceIds.map { sourceId ->
            async {
                permits.withPermit {
                    try {
                        search.search(sourceId, query).take(maxResultsPerSource)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }.awaitAll()
        val candidates = perSource.filterNotNull().flatten()
        val state = when {
            candidates.isNotEmpty() -> RecoveryState.NEEDS_REVIEW
            perSource.all { it == null } -> RecoveryState.FAILED
            else -> RecoveryState.MISSING
        }
        Outcome(state, candidates)
    }

    private data class Outcome(val state: RecoveryState, val candidates: List<RecoveryCandidate>)
}
