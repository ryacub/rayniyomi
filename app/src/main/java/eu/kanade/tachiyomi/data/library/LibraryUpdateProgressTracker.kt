package eu.kanade.tachiyomi.data.library

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
