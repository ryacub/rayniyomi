package xyz.rayniyomi.plugin.lightnovel.ui

import android.app.Application
import androidx.annotation.StringRes
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
import xyz.rayniyomi.plugin.lightnovel.R
import xyz.rayniyomi.plugin.lightnovel.data.NovelBook
import xyz.rayniyomi.plugin.lightnovel.data.NovelStorage
import xyz.rayniyomi.plugin.lightnovel.epub.EpubContent
import xyz.rayniyomi.plugin.lightnovel.epub.EpubTextExtractor

internal data class ReaderUiState(
    val title: String = "",
    val chapterIndicator: String = "",
    val chapterText: String = "",
    val previousEnabled: Boolean = false,
    val nextEnabled: Boolean = false,
    val restoreOffset: Int = 0,
    val isLoading: Boolean = true,
)

internal class ReaderViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val storage = NovelStorage(application)
    private lateinit var book: NovelBook
    private lateinit var content: EpubContent

    private var currentChapterIndex: Int = 0
    private var pendingOffset: Int = 0

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val _fatalInitErrors = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val fatalInitErrors: SharedFlow<Int> = _fatalInitErrors.asSharedFlow()

    fun initialize(bookId: String?, restoredChapter: Int?, restoredOffset: Int?) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val error = loadReaderContent(bookId, restoredChapter, restoredOffset)
            if (error != null) {
                _fatalInitErrors.tryEmit(error)
            } else {
                updateChapterUi()
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun onPreviousClick() {
        if (!::content.isInitialized || uiState.value.isLoading) return
        if (currentChapterIndex <= 0) return
        persistProgress(pendingOffset)
        currentChapterIndex -= 1
        pendingOffset = 0
        updateChapterUi()
    }

    fun onNextClick() {
        if (!::content.isInitialized || uiState.value.isLoading) return
        if (currentChapterIndex >= content.chapters.lastIndex) return
        persistProgress(pendingOffset)
        currentChapterIndex += 1
        pendingOffset = 0
        updateChapterUi()
    }

    fun onPersistOffset(offset: Int) {
        if (!::content.isInitialized || uiState.value.isLoading) return
        pendingOffset = offset
        persistProgress(offset)
    }

    fun onPause() {
        if (!::content.isInitialized) return
        persistProgress(pendingOffset)
    }

    fun saveInstanceState(outState: android.os.Bundle) {
        outState.putInt(KEY_CURRENT_CHAPTER, currentChapterIndex)
        outState.putInt(KEY_PENDING_OFFSET, pendingOffset)
    }

    private fun updateChapterUi() {
        val chapter = content.chapters[currentChapterIndex]
        val chapterIndicator = getApplication<Application>().getString(
            R.string.chapter_format,
            currentChapterIndex + 1,
            content.chapters.size,
        )
        _uiState.update {
            it.copy(
                title = content.title,
                chapterIndicator = chapterIndicator,
                chapterText = chapter.text,
                previousEnabled = currentChapterIndex > 0,
                nextEnabled = currentChapterIndex < content.chapters.lastIndex,
                restoreOffset = pendingOffset,
                isLoading = false,
            )
        }
    }

    @StringRes
    private fun loadReaderContent(
        bookId: String?,
        restoredChapter: Int?,
        restoredOffset: Int?,
    ): Int? {
        if (bookId == null) return R.string.reader_open_failed

        book = storage.getBook(bookId)
            ?: return R.string.reader_open_failed

        val bookFile = storage.getBookFile(book)
        content = runCatching { EpubTextExtractor.parse(bookFile) }
            .getOrElse { return R.string.reader_parse_failed }

        if (content.chapters.isEmpty()) return R.string.reader_no_chapters

        currentChapterIndex = (restoredChapter ?: book.lastReadChapter).coerceIn(0, content.chapters.lastIndex)
        pendingOffset = restoredOffset ?: if (currentChapterIndex == book.lastReadChapter) book.lastReadOffset else 0
        return null
    }

    private fun persistProgress(offset: Int) {
        if (!::book.isInitialized) return
        val chapterLength = uiState.value.chapterText.length
        if (chapterLength == 0) return

        val boundedOffset = offset.coerceIn(0, chapterLength)
        val updatedBook = book.copy(
            lastReadChapter = currentChapterIndex,
            lastReadOffset = boundedOffset,
        )
        book = updatedBook

        viewModelScope.launch(Dispatchers.IO) {
            storage.updateProgress(
                bookId = updatedBook.id,
                chapterIndex = updatedBook.lastReadChapter,
                charOffset = updatedBook.lastReadOffset,
            )
        }
    }

    companion object {
        const val KEY_CURRENT_CHAPTER = "key_current_chapter"
        const val KEY_PENDING_OFFSET = "key_pending_offset"
    }
}
