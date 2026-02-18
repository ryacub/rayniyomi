package xyz.rayniyomi.plugin.lightnovel

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.rayniyomi.plugin.lightnovel.data.NovelBook
import xyz.rayniyomi.plugin.lightnovel.data.NovelStorage
import xyz.rayniyomi.plugin.lightnovel.databinding.ActivityReaderBinding
import xyz.rayniyomi.plugin.lightnovel.epub.EpubContent
import xyz.rayniyomi.plugin.lightnovel.epub.EpubTextExtractor

class ReaderActivity : AppCompatActivity() {
    private lateinit var binding: ActivityReaderBinding
    private lateinit var storage: NovelStorage
    private lateinit var book: NovelBook
    private lateinit var content: EpubContent

    private var currentChapterIndex = 0

    private var saveJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        binding.previousButton.setOnClickListener {
            if (currentChapterIndex > 0) {
                persistProgress()
                currentChapterIndex -= 1
                renderChapter(restoreSavedOffset = false)
            }
        }

        binding.nextButton.setOnClickListener {
            if (currentChapterIndex < content.chapters.lastIndex) {
                persistProgress()
                currentChapterIndex += 1
                renderChapter(restoreSavedOffset = false)
            }
        }

        binding.chapterScroll.setOnScrollChangeListener { _, _, _, _, _ ->
            schedulePersist()
        }

        renderChapter(restoreSavedOffset = true)
    }

    override fun onPause() {
        super.onPause()
        persistProgress()
    }

    override fun onDestroy() {
        super.onDestroy()
        saveJob?.cancel()
    }

    private fun renderChapter(restoreSavedOffset: Boolean) {
        val chapter = content.chapters[currentChapterIndex]

        binding.bookTitle.text = content.title
        binding.chapterIndicator.text = getString(
            R.string.chapter_format,
            currentChapterIndex + 1,
            content.chapters.size,
        )
        binding.chapterText.text = chapter.text

        binding.previousButton.isEnabled = currentChapterIndex > 0
        binding.nextButton.isEnabled = currentChapterIndex < content.chapters.lastIndex

        val restoreOffset = if (restoreSavedOffset && currentChapterIndex == book.lastReadChapter) {
            book.lastReadOffset
        } else {
            0
        }

        binding.chapterScroll.post {
            val maxScroll = maxScrollY()
            if (maxScroll <= 0) {
                return@post
            }
            val chapterLength = chapter.text.length.coerceAtLeast(1)
            val ratio = restoreOffset.toFloat() / chapterLength
            val targetY = (ratio * maxScroll).toInt().coerceIn(0, maxScroll)
            binding.chapterScroll.scrollTo(0, targetY)
        }
    }

    private fun schedulePersist() {
        saveJob?.cancel()
        saveJob = lifecycleScope.launch {
            delay(250)
            persistProgress()
        }
    }

    private fun persistProgress() {
        saveJob?.cancel()
        saveJob = null

        if (!::book.isInitialized) {
            return
        }

        val chapterText = binding.chapterText.text?.toString().orEmpty()
        if (chapterText.isEmpty()) {
            return
        }

        val maxScroll = maxScrollY().coerceAtLeast(1)
        val currentScroll = binding.chapterScroll.scrollY.coerceIn(0, maxScroll)
        val ratio = currentScroll.toFloat() / maxScroll.toFloat()
        val charOffset = (ratio * chapterText.length).toInt().coerceIn(0, chapterText.length)

        val updatedBook = book.copy(
            lastReadChapter = currentChapterIndex,
            lastReadOffset = charOffset,
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

    private fun maxScrollY(): Int {
        val child = binding.chapterScroll.getChildAt(0) ?: return 0
        return (child.height - binding.chapterScroll.height).coerceAtLeast(0)
    }

    companion object {
        const val EXTRA_BOOK_ID = "extra_book_id"
    }
}
