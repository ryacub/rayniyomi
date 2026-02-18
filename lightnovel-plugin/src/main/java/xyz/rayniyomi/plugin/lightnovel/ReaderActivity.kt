package xyz.rayniyomi.plugin.lightnovel

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
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

    private val saveHandler = Handler(Looper.getMainLooper())
    private val saveRunnable = Runnable { persistProgress() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        storage = NovelStorage(this)

        val bookId = intent.getStringExtra(EXTRA_BOOK_ID)
            ?: run {
                finish()
                return
            }

        book = storage.getBook(bookId)
            ?: run {
                finish()
                return
            }

        val bookFile = storage.getBookFile(book)
        content = runCatching { EpubTextExtractor.parse(bookFile) }
            .getOrElse {
                finish()
                return
            }

        if (content.chapters.isEmpty()) {
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
        saveHandler.removeCallbacks(saveRunnable)
        saveHandler.postDelayed(saveRunnable, 250)
    }

    private fun persistProgress() {
        saveHandler.removeCallbacks(saveRunnable)

        val chapterText = binding.chapterText.text?.toString().orEmpty()
        if (chapterText.isEmpty()) {
            return
        }

        val maxScroll = maxScrollY().coerceAtLeast(1)
        val currentScroll = binding.chapterScroll.scrollY.coerceIn(0, maxScroll)
        val ratio = currentScroll.toFloat() / maxScroll.toFloat()
        val charOffset = (ratio * chapterText.length).toInt().coerceIn(0, chapterText.length)

        storage.updateProgress(
            bookId = book.id,
            chapterIndex = currentChapterIndex,
            charOffset = charOffset,
        )

        book = book.copy(
            lastReadChapter = currentChapterIndex,
            lastReadOffset = charOffset,
        )
    }

    private fun maxScrollY(): Int {
        val child = binding.chapterScroll.getChildAt(0) ?: return 0
        return (child.height - binding.chapterScroll.height).coerceAtLeast(0)
    }

    companion object {
        const val EXTRA_BOOK_ID = "extra_book_id"
    }
}
