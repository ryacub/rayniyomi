package xyz.rayniyomi.plugin.lightnovel

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import xyz.rayniyomi.plugin.lightnovel.data.NovelBook
import xyz.rayniyomi.plugin.lightnovel.data.NovelStorage
import xyz.rayniyomi.plugin.lightnovel.epub.EpubContent
import xyz.rayniyomi.plugin.lightnovel.epub.EpubTextExtractor
import xyz.rayniyomi.plugin.lightnovel.ui.ReaderScreen

class ReaderActivity : ComponentActivity() {
    private lateinit var storage: NovelStorage
    private lateinit var book: NovelBook
    private lateinit var content: EpubContent

    private var currentChapterIndex by mutableIntStateOf(0)
    private var pendingOffset by mutableIntStateOf(0)

    private var chapterIndicatorText: String by mutableStateOf("")
    private var chapterText: String by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        storage = NovelStorage(this)

        val bookId = intent.getStringExtra(EXTRA_BOOK_ID)
            ?: run {
                Toast.makeText(this, getString(R.string.reader_open_failed), Toast.LENGTH_SHORT).show()
                finish()
                return
            }

        book = storage.getBook(bookId)
            ?: run {
                Toast.makeText(this, getString(R.string.reader_open_failed), Toast.LENGTH_SHORT).show()
                finish()
                return
            }

        val bookFile = storage.getBookFile(book)
        content = runCatching { EpubTextExtractor.parse(bookFile) }
            .getOrElse {
                Toast.makeText(this, getString(R.string.reader_parse_failed), Toast.LENGTH_SHORT).show()
                finish()
                return
            }

        if (content.chapters.isEmpty()) {
            Toast.makeText(this, getString(R.string.reader_no_chapters), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        currentChapterIndex = book.lastReadChapter.coerceIn(0, content.chapters.lastIndex)
        renderChapter(restoreSavedOffset = true)

        setContent {
            ReaderScreen(
                title = content.title,
                chapterIndicator = chapterIndicatorText,
                chapterText = chapterText,
                previousEnabled = currentChapterIndex > 0,
                nextEnabled = currentChapterIndex < content.chapters.lastIndex,
                restoreOffset = pendingOffset,
                onPreviousClick = {
                    if (currentChapterIndex > 0) {
                        persistProgress(pendingOffset)
                        currentChapterIndex -= 1
                        renderChapter(restoreSavedOffset = false)
                    }
                },
                onNextClick = {
                    if (currentChapterIndex < content.chapters.lastIndex) {
                        persistProgress(pendingOffset)
                        currentChapterIndex += 1
                        renderChapter(restoreSavedOffset = false)
                    }
                },
                onPersistOffset = { offset ->
                    pendingOffset = offset
                    persistProgress(offset)
                },
            )
        }
    }

    override fun onPause() {
        super.onPause()
        persistProgress(pendingOffset)
    }

    private fun renderChapter(restoreSavedOffset: Boolean) {
        val chapter = content.chapters[currentChapterIndex]
        chapterIndicatorText = getString(
            R.string.chapter_format,
            currentChapterIndex + 1,
            content.chapters.size,
        )
        chapterText = chapter.text

        pendingOffset = if (restoreSavedOffset && currentChapterIndex == book.lastReadChapter) {
            book.lastReadOffset
        } else {
            0
        }
    }

    private fun persistProgress(offset: Int) {
        if (!::book.isInitialized) return

        val chapterLength = chapterText.length
        if (chapterLength == 0) return

        val boundedOffset = offset.coerceIn(0, chapterLength)

        val updatedBook = book.copy(
            lastReadChapter = currentChapterIndex,
            lastReadOffset = boundedOffset,
        )
        book = updatedBook

        lifecycleScope.launch(Dispatchers.IO) {
            storage.updateProgress(
                bookId = updatedBook.id,
                chapterIndex = updatedBook.lastReadChapter,
                charOffset = updatedBook.lastReadOffset,
            )
        }
    }

    companion object {
        const val EXTRA_BOOK_ID = "extra_book_id"
    }
}
