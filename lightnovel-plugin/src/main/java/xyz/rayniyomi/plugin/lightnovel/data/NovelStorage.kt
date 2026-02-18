package xyz.rayniyomi.plugin.lightnovel.data

import android.content.Context
import android.net.Uri
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.rayniyomi.plugin.lightnovel.epub.EpubTextExtractor
import java.io.File

class NovelStorage(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val rootDir = File(context.filesDir, ROOT_DIR_NAME).also { it.mkdirs() }
    private val booksDir = File(rootDir, BOOKS_DIR_NAME).also { it.mkdirs() }
    private val libraryFile = File(rootDir, LIBRARY_FILE_NAME)

    @Synchronized
    fun listBooks(): List<NovelBook> {
        return readLibrary().books.sortedByDescending { it.updatedAt }
    }

    @Synchronized
    fun getBook(bookId: String): NovelBook? {
        return readLibrary().books.firstOrNull { it.id == bookId }
    }

    @Synchronized
    fun getBookFile(book: NovelBook): File {
        return File(booksDir, book.epubFileName)
    }

    @Synchronized
    fun importEpub(uri: Uri): NovelBook {
        val id = System.currentTimeMillis().toString()
        val targetFile = File(booksDir, "$id.epub")

        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open EPUB input stream" }
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val parsedTitle = runCatching { EpubTextExtractor.parse(targetFile).title }.getOrNull()
        val title = parsedTitle?.takeIf { it.isNotBlank() } ?: targetFile.nameWithoutExtension

        val newBook = NovelBook(
            id = id,
            title = title,
            epubFileName = targetFile.name,
            lastReadChapter = 0,
            lastReadOffset = 0,
            updatedAt = System.currentTimeMillis(),
        )

        val current = readLibrary().books.toMutableList()
        current.add(newBook)
        writeLibrary(NovelLibrary(current))
        return newBook
    }

    @Synchronized
    fun updateProgress(bookId: String, chapterIndex: Int, charOffset: Int) {
        val updated = readLibrary().books.map { book ->
            if (book.id == bookId) {
                book.copy(
                    lastReadChapter = chapterIndex.coerceAtLeast(0),
                    lastReadOffset = charOffset.coerceAtLeast(0),
                    updatedAt = System.currentTimeMillis(),
                )
            } else {
                book
            }
        }
        writeLibrary(NovelLibrary(updated))
    }

    private fun readLibrary(): NovelLibrary {
        if (!libraryFile.exists()) {
            return NovelLibrary()
        }
        return runCatching {
            json.decodeFromString<NovelLibrary>(libraryFile.readText())
        }.getOrElse { NovelLibrary() }
    }

    private fun writeLibrary(library: NovelLibrary) {
        libraryFile.writeText(json.encodeToString(library))
    }

    private companion object {
        const val ROOT_DIR_NAME = "light_novel_plugin"
        const val BOOKS_DIR_NAME = "books"
        const val LIBRARY_FILE_NAME = "library.json"
    }
}
