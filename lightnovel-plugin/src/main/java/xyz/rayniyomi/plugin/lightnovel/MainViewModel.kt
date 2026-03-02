package xyz.rayniyomi.plugin.lightnovel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.rayniyomi.plugin.lightnovel.data.ImportTooLargeException
import xyz.rayniyomi.plugin.lightnovel.data.InvalidEpubException
import xyz.rayniyomi.plugin.lightnovel.data.LowStorageException
import xyz.rayniyomi.plugin.lightnovel.data.NovelBook
import xyz.rayniyomi.plugin.lightnovel.data.NovelImportStatusTracker
import xyz.rayniyomi.plugin.lightnovel.data.NovelStorage
import xyz.rayniyomi.plugin.lightnovel.data.SourceUnavailableException

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val storage = NovelStorage(application)
    private val tracker = NovelImportStatusTracker()
    private val importMutex = Mutex()

    private val mutableBooks = MutableStateFlow<List<NovelBook>>(emptyList())
    val books: StateFlow<List<NovelBook>> = mutableBooks.asStateFlow()
    val importStatus = tracker.status

    private var importJob: Job? = null
    private var lastImportUri: Uri? = null

    init {
        refreshBooks()
    }

    fun isImportRunning(): Boolean = importJob?.isActive == true

    fun retryLastImport(): Boolean {
        val uri = lastImportUri ?: return false
        return importEpub(uri)
    }

    fun importEpub(uri: Uri): Boolean {
        if (isImportRunning()) return false
        lastImportUri = uri
        importJob = viewModelScope.launch(Dispatchers.IO) {
            importMutex.withLock {
                tracker.onPreparing()
                val stallJob = launch {
                    while (isActive) {
                        delay(1_000L)
                        tracker.updateStalledIfNeeded()
                    }
                }
                try {
                    storage.importEpub(
                        uri = uri,
                        onVerifying = { tracker.onVerifying() },
                        onProgress = { bytesRead, totalBytes ->
                            tracker.onImportProgress(bytesRead, totalBytes)
                        },
                    )
                    tracker.onCompleted()
                    refreshBooks()
                } catch (e: Throwable) {
                    when (e) {
                        is LowStorageException -> tracker.onPausedLowStorage(mapImportError(e))
                        else -> tracker.onFailed(mapImportError(e))
                    }
                } finally {
                    stallJob.cancel()
                }
            }
        }
        return true
    }

    fun refreshBooks() {
        viewModelScope.launch(Dispatchers.IO) {
            mutableBooks.value = storage.listBooks()
        }
    }

    private fun mapImportError(cause: Throwable): String {
        val context = getApplication<Application>()
        return when (cause) {
            is ImportTooLargeException -> context.getString(R.string.import_too_large)
            is InvalidEpubException -> context.getString(R.string.import_invalid_epub)
            is SourceUnavailableException -> context.getString(R.string.import_source_unavailable)
            is LowStorageException -> context.getString(R.string.import_low_storage)
            else -> context.getString(R.string.import_failed)
        }
    }
}
