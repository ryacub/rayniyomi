package tachiyomi.domain.recovery

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import tachiyomi.domain.recovery.model.RecoveryApplyResult
import tachiyomi.domain.recovery.model.RecoveryEntry
import tachiyomi.domain.recovery.model.RecoveryIdentity
import tachiyomi.domain.recovery.model.RecoverySession
import tachiyomi.domain.recovery.model.RecoveryState
import tachiyomi.domain.recovery.repository.RecoveryApplier
import tachiyomi.domain.recovery.repository.RecoveryRepository

data class RecoveryRunReport(
    val coverFailures: Map<Long, String> = emptyMap(),
)

class RecoveryApplyRunner(
    private val repository: RecoveryRepository,
    private val applier: RecoveryApplier,
    private val preparer: RecoveryPreparer,
    private val copyCover: suspend (originalId: Long, destinationId: Long) -> Unit,
) {

    suspend fun run(sessionId: Long, runToken: String): RecoveryRunReport {
        val session = repository.getSession(sessionId) ?: return RecoveryRunReport()
        holdBlockedRows(sessionId)
        var claimed = emptySet<Long>()
        val coverFailures = mutableMapOf<Long, String>()
        val coversDone = mutableSetOf<Long>()
        try {
            claimed = repository.claimForApply(sessionId, runToken).toSet()
            repository.getEntries(sessionId).filter { it.id in claimed }.forEach { entry ->
                currentCoroutineContext().ensureActive()
                val result = applyOne(entry, session)
                if (result is RecoveryApplyResult.Completed) {
                    copyCoverOf(entry, result.destinationId, coverFailures)
                    coversDone += entry.id
                }
            }
        } catch (e: Exception) {
            withContext(NonCancellable) {
                repository.interruptRun(runToken)
                repository.getEntries(sessionId)
                    .filter { it.id in claimed && it.id !in coversDone && it.state == RecoveryState.COMPLETED }
                    .forEach { entry -> entry.resultId?.let { copyCoverOf(entry, it, coverFailures) } }
            }
            throw e
        }
        return RecoveryRunReport(coverFailures)
    }

    private suspend fun applyOne(entry: RecoveryEntry, session: RecoverySession): RecoveryApplyResult {
        val request = try {
            preparer.prepare(entry, session)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.message ?: e::class.simpleName.orEmpty()
            repository.failRun(entry.id, checkNotNull(entry.runToken), message)
            return RecoveryApplyResult.Failed(message)
        }
        return repository.withWriteLock { applier.apply(request) }
    }

    private suspend fun copyCoverOf(entry: RecoveryEntry, destinationId: Long, failures: MutableMap<Long, String>) {
        try {
            copyCover(entry.originalId, destinationId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failures[entry.id] = e.message ?: e::class.simpleName.orEmpty()
        }
    }

    private suspend fun holdBlockedRows(sessionId: Long) {
        val rows = repository.getEntries(sessionId).filter {
            it.state == RecoveryState.COMPLETED || (it.selected && it.state !in RecoveryState.finished)
        }
        val identities = preparer.identities(rows.map { it.originalId })
        val blocked = RecoveryBatchValidator.blocked(
            rows.mapNotNull { entry ->
                val original = identities[entry.originalId] ?: return@mapNotNull null
                val destination = entry.choice
                    ?.takeIf { entry.state in RecoveryState.applicable || entry.state == RecoveryState.COMPLETED }
                    ?.let { RecoveryIdentity(it.sourceId, it.url) }
                RecoveryBatchValidator.Row(entry.id, original, destination)
            },
        )
        val pending = rows.filter { it.state != RecoveryState.COMPLETED }.map { it.id }.toSet()
        blocked.filterKeys {
            it in pending
        }.forEach { (entryId, reason) -> repository.holdForReview(entryId, reason.name) }
    }
}
