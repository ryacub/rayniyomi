package xyz.rayniyomi.plugin.lightnovel.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.rayniyomi.plugin.lightnovel.R
import xyz.rayniyomi.plugin.lightnovel.data.ImportTooLargeException
import xyz.rayniyomi.plugin.lightnovel.data.NovelBook
import xyz.rayniyomi.plugin.lightnovel.data.NovelStorage

internal data class MainUiState(
    val books: List<NovelBook> = emptyList(),
    val statusMessage: String = "",
    val isLoading: Boolean = false,
)

internal class MainViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val storage = NovelStorage(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _toastMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessages: SharedFlow<String> = _toastMessages.asSharedFlow()

    init {
        refreshBooks()
    }

    fun restoreStatusMessage(message: String) {
        _uiState.update { it.copy(statusMessage = message) }
    }

    fun refreshBooks() {
        _uiState.update { it.copy(books = storage.listBooks()) }
    }

    fun onImportResult(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = withContext(Dispatchers.IO) {
                runCatching { storage.importEpub(uri) }
            }
            if (result.isFailure) {
                val errorMessageRes = when (result.exceptionOrNull()) {
                    is ImportTooLargeException -> R.string.import_too_large
                    else -> R.string.import_failed
                }
                val message = getApplication<Application>().getString(errorMessageRes)
                _uiState.update { it.copy(statusMessage = message) }
                _toastMessages.tryEmit(message)
            } else {
                _uiState.update { it.copy(statusMessage = "") }
            }
            refreshBooks()
            _uiState.update { it.copy(isLoading = false) }
        }
    }
}
