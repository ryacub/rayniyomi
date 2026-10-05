package eu.kanade.tachiyomi.data.library

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import java.util.concurrent.CopyOnWriteArrayList

@Serializable
internal enum class LibraryUpdateMedia {
    ANIME,
    MANGA,
}

@Serializable
internal enum class LibraryUpdateSummaryOutcome {
    COMPLETED,
    CANCELLED,
    INTERRUPTED,
}

@Serializable
internal data class LibraryUpdateSummaryEntry(
    val id: Long,
    val title: String,
)

@Serializable
internal data class LibraryUpdateSkippedEntry(
    val entry: LibraryUpdateSummaryEntry,
    val reason: AutoUpdateSkipReason,
)

@Serializable
internal data class LibraryUpdateFailedEntry(
    val entry: LibraryUpdateSummaryEntry,
    val error: String?,
)

@Serializable
internal data class LibraryUpdateResult(
    val updated: List<LibraryUpdateSummaryEntry> = emptyList(),
    val skipped: List<LibraryUpdateSkippedEntry> = emptyList(),
    val failed: List<LibraryUpdateFailedEntry> = emptyList(),
    val skippedLogPath: String? = null,
    val errorLogPath: String? = null,
)

@Serializable
internal data class LibraryUpdateSummary(
    val executionId: String,
    val outcome: LibraryUpdateSummaryOutcome,
    val finishedAt: Long,
    val updated: List<LibraryUpdateSummaryEntry>,
    val skipped: List<LibraryUpdateSkippedEntry>,
    val failed: List<LibraryUpdateFailedEntry>,
    val skippedLogPath: String?,
    val errorLogPath: String?,
) {
    val updatedCount: Int
        get() = updated.size

    val skippedCount: Int
        get() = skipped.size

    val failedCount: Int
        get() = failed.size
}

internal class LibraryUpdateResultAccumulator {
    private val updated = CopyOnWriteArrayList<LibraryUpdateSummaryEntry>()
    private val failed = CopyOnWriteArrayList<LibraryUpdateFailedEntry>()

    fun addUpdated(entry: LibraryUpdateSummaryEntry) {
        updated += entry
    }

    fun addFailed(
        entry: LibraryUpdateSummaryEntry,
        throwable: Throwable,
        errorMessage: (Throwable) -> String?,
    ): String? {
        if (throwable is CancellationException) throw throwable
        val message = errorMessage(throwable)
        failed += LibraryUpdateFailedEntry(entry, message)
        return message
    }

    fun snapshot(
        skipped: List<LibraryUpdateSkippedEntry> = emptyList(),
        skippedLogPath: String? = null,
        errorLogPath: String? = null,
    ): LibraryUpdateResult = LibraryUpdateResult(
        updated = updated.toList(),
        skipped = skipped,
        failed = failed.toList(),
        skippedLogPath = skippedLogPath,
        errorLogPath = errorLogPath,
    )
}

class LibraryUpdateSummaryStore internal constructor(
    preferenceStore: PreferenceStore,
    json: Json,
) {
    private val lock = Any()
    private val activeExecutions = mutableMapOf<LibraryUpdateMedia, String>()
    private val userCancellations = mutableSetOf<Execution>()
    private val animeSummary = preferenceStore.summaryPreference(ANIME_SUMMARY_KEY, json)
    private val mangaSummary = preferenceStore.summaryPreference(MANGA_SUMMARY_KEY, json)

    internal fun get(media: LibraryUpdateMedia): LibraryUpdateSummary? = preference(media).get()

    internal fun changes(media: LibraryUpdateMedia): Flow<LibraryUpdateSummary?> = preference(media).changes()

    internal fun start(media: LibraryUpdateMedia, executionId: String) {
        synchronized(lock) {
            activeExecutions[media] = executionId
            userCancellations.removeAll { it.media == media && it.id != executionId }
            preference(media).delete()
        }
    }

    internal fun requestUserCancellation(media: LibraryUpdateMedia, executionId: String) {
        synchronized(lock) {
            val activeExecution = activeExecutions[media]
            if (activeExecution == null || activeExecution == executionId) {
                userCancellations += Execution(media, executionId)
            }
        }
    }

    internal fun finish(
        media: LibraryUpdateMedia,
        executionId: String,
        result: LibraryUpdateResult,
        finishedAt: Long,
        interrupted: Boolean = false,
    ): LibraryUpdateSummary? {
        synchronized(lock) {
            if (activeExecutions[media] != executionId) return null

            val execution = Execution(media, executionId)
            val outcome = when {
                execution in userCancellations -> LibraryUpdateSummaryOutcome.CANCELLED
                interrupted -> LibraryUpdateSummaryOutcome.INTERRUPTED
                else -> LibraryUpdateSummaryOutcome.COMPLETED
            }
            val summary = LibraryUpdateSummary(
                executionId = executionId,
                outcome = outcome,
                finishedAt = finishedAt,
                updated = result.updated,
                skipped = result.skipped,
                failed = result.failed,
                skippedLogPath = result.skippedLogPath,
                errorLogPath = result.errorLogPath,
            )
            preference(media).set(summary)
            activeExecutions.remove(media)
            userCancellations.remove(execution)
            return summary
        }
    }

    internal fun close(media: LibraryUpdateMedia, executionId: String): Boolean {
        synchronized(lock) {
            val preference = preference(media)
            if (preference.get()?.executionId != executionId) return false
            preference.delete()
            return true
        }
    }

    private fun preference(media: LibraryUpdateMedia): Preference<LibraryUpdateSummary?> = when (media) {
        LibraryUpdateMedia.ANIME -> animeSummary
        LibraryUpdateMedia.MANGA -> mangaSummary
    }

    private data class Execution(
        val media: LibraryUpdateMedia,
        val id: String,
    )
}

private fun PreferenceStore.summaryPreference(
    key: String,
    json: Json,
): Preference<LibraryUpdateSummary?> = getObject(
    key = Preference.appStateKey(key),
    defaultValue = null,
    serializer = json::encodeToString,
    deserializer = json::decodeFromString,
)

private const val ANIME_SUMMARY_KEY = "anime_library_update_summary"
private const val MANGA_SUMMARY_KEY = "manga_library_update_summary"
