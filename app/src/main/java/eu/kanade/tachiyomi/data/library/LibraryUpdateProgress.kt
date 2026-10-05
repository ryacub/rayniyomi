package eu.kanade.tachiyomi.data.library

import androidx.work.Data
import androidx.work.WorkInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class LibraryUpdateProgress(
    val activeTitles: List<String>,
    val completed: Int,
    val total: Int,
) {

    fun toWorkData(): Data = Data.Builder()
        .putStringArray(
            KEY_ACTIVE_TITLES,
            activeTitles
                .take(MAX_ACTIVE_TITLES)
                .map { it.take(MAX_TITLE_LENGTH) }
                .toTypedArray(),
        )
        .putInt(KEY_COMPLETED, completed)
        .putInt(KEY_TOTAL, total)
        .build()

    companion object {
        fun from(workInfo: WorkInfo): LibraryUpdateProgress? {
            if (workInfo.state != WorkInfo.State.RUNNING) return null

            val progress = workInfo.progress
            return LibraryUpdateProgress(
                activeTitles = progress.getStringArray(KEY_ACTIVE_TITLES)?.toList().orEmpty(),
                completed = progress.getInt(KEY_COMPLETED, 0),
                total = progress.getInt(KEY_TOTAL, 0),
            )
        }
    }
}

internal fun List<WorkInfo>.toLibraryUpdateProgressOrNull(): LibraryUpdateProgress? =
    firstNotNullOfOrNull(LibraryUpdateProgress::from)

internal class LibraryUpdateProgressTracker<T>(
    private val total: Int,
    private val title: (T) -> String,
    private val onProgress: suspend (List<T>, LibraryUpdateProgress) -> Unit,
) {

    private val mutex = Mutex()
    private val activeEntries = mutableListOf<T>()
    private var completed = 0

    suspend fun entryStarted(entry: T) {
        mutex.withLock {
            activeEntries.add(entry)
            publishProgress()
        }
    }

    suspend fun entryCompleted(entry: T) {
        mutex.withLock {
            activeEntries.remove(entry)
            completed += 1
            publishProgress()
        }
    }

    private suspend fun publishProgress() {
        val entries = activeEntries.toList()
        val progress = LibraryUpdateProgress(
            activeTitles = entries.map(title),
            completed = completed,
            total = total,
        )
        onProgress(entries, progress)
    }
}

internal fun CoroutineScope.launchLibraryUpdateCancellation(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    action: suspend () -> Unit,
): Job = launch(dispatcher) {
    action()
}

private const val KEY_ACTIVE_TITLES = "libraryUpdateActiveTitles"
private const val KEY_COMPLETED = "libraryUpdateCompleted"
private const val KEY_TOTAL = "libraryUpdateTotal"
private const val MAX_ACTIVE_TITLES = 5
private const val MAX_TITLE_LENGTH = 256
