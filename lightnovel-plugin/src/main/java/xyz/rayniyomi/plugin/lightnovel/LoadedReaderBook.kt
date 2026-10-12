package xyz.rayniyomi.plugin.lightnovel

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.rayniyomi.plugin.lightnovel.data.NovelBook
import xyz.rayniyomi.plugin.lightnovel.data.NovelStorage
import xyz.rayniyomi.plugin.lightnovel.epub.EpubContent
import xyz.rayniyomi.plugin.lightnovel.epub.EpubTextExtractor
import java.io.File

internal data class LoadedReaderBook(
    val storage: NovelStorage,
    val book: NovelBook,
    val content: EpubContent,
)

internal suspend fun loadReaderBook(
    context: Context,
    bookId: String,
    parse: (File) -> EpubContent = EpubTextExtractor::parse,
): LoadedReaderBook? = withContext(Dispatchers.IO) {
    val storage = NovelStorage(context)
    storage.getBook(bookId)?.let { book ->
        LoadedReaderBook(storage, book, parse(storage.getBookFile(book)))
    }
}
