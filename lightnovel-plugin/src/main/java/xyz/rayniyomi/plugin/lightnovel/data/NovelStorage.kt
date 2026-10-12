package xyz.rayniyomi.plugin.lightnovel.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.rayniyomi.plugin.lightnovel.epub.EpubTextExtractor
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal object NovelStorageCoordinator {
    private val lock = ReentrantLock(true)
    private val initializedBookDirectories = mutableSetOf<String>()

    fun <T> withLock(action: () -> T): T = lock.withLock(action)

    fun cleanStaleImportsOnce(booksDir: File, referencedFileNames: () -> Set<String>?) = withLock {
        val path = booksDir.absolutePath
        if (!initializedBookDirectories.add(path)) return@withLock
        val referenced = referencedFileNames() ?: return@withLock
        booksDir.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.endsWith(".tmp.epub") && it.name !in referenced }
            .forEach(File::delete)
    }
}

/**
 * Persistent storage for the user's light-novel library.
 *
 * ### Schema versioning
 * Data is stored as a [NovelLibraryEnvelope] that carries a [NovelLibraryEnvelope.schemaVersion].
 * On every read the envelope is automatically migrated to [NovelSchemaMigrations.LATEST_SCHEMA_VERSION]
 * via [NovelSchemaMigrations.migrateToLatest] before the caller sees the data.
 *
 * Queries return an empty library when the on-disk JSON cannot be parsed. Mutations read strictly
 * and fail without replacing an unreadable or newer library index.
 */
class NovelStorage(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val rootDir = File(context.filesDir, ROOT_DIR_NAME).also { it.mkdirs() }
    private val booksDir = File(rootDir, BOOKS_DIR_NAME).also { it.mkdirs() }
    private val libraryFile = File(rootDir, LIBRARY_FILE_NAME)
    init {
        NovelStorageCoordinator.cleanStaleImportsOnce(booksDir) {
            try {
                readLibraryStrictly().books.mapTo(mutableSetOf()) { it.epubFileName }
            } catch (cause: Exception) {
                Log.w(TAG, "Skipping stale import cleanup because the library index is unreadable", cause)
                null
            }
        }
    }

    // -------------------------------------------------------------------------
    // Library reads
    // -------------------------------------------------------------------------

    fun listBooks(): List<NovelBook> = NovelStorageCoordinator.withLock {
        readLibrary().books.sortedByDescending { it.updatedAt }
    }

    fun getBook(bookId: String): NovelBook? = NovelStorageCoordinator.withLock {
        readLibrary().books.firstOrNull { it.id == bookId }
    }

    @Synchronized
    fun getBookFile(book: NovelBook): File {
        return File(booksDir, book.epubFileName)
    }

    // -------------------------------------------------------------------------
    // Import / update
    // -------------------------------------------------------------------------

    fun importEpub(
        uri: Uri,
        onVerifying: () -> Unit = {},
        onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    ): NovelBook {
        val id = UUID.randomUUID().toString()
        val targetFile = File(booksDir, "$id.epub")
        val tempFile = File(booksDir, "$id.tmp.epub")
        validateImportSize(uri)
        val expectedLength = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
            descriptor.length.takeIf { it > 0L }
        }

        try {
            context.contentResolver.openInputStream(uri).use { input ->
                if (input == null) throw SourceUnavailableException("Unable to open EPUB source")
                tempFile.outputStream().use { output ->
                    copyWithProgress(
                        input = input,
                        output = output,
                        expectedLength = expectedLength,
                        onProgress = onProgress,
                    )
                }
            }
            onVerifying()
            val parsedTitle = runCatching { EpubTextExtractor.parse(tempFile).title }
                .getOrElse { throw InvalidEpubException("Unable to parse EPUB metadata", it) }
            val title = parsedTitle.takeIf { it.isNotBlank() } ?: targetFile.nameWithoutExtension

            val newBook = NovelBook(
                id = id,
                title = title,
                epubFileName = targetFile.name,
                lastReadChapter = 0,
                lastReadOffset = 0,
                updatedAt = System.currentTimeMillis(),
            )

            return NovelStorageCoordinator.withLock {
                if (!tempFile.renameTo(targetFile)) {
                    throw IOException("Unable to finalize imported EPUB file")
                }

                val current = readLibraryStrictly().books.toMutableList()
                current.add(newBook)
                writeLibrary(NovelLibrary(current))
                newBook
            }
        } catch (cause: Throwable) {
            tempFile.delete()
            if (!targetFile.exists()) {
                // no-op
            } else {
                targetFile.delete()
            }
            throw mapImportException(cause)
        }
    }

    fun updateProgress(bookId: String, chapterIndex: Int, charOffset: Int) =
        NovelStorageCoordinator.withLock {
            val updated = readLibraryStrictly().books.map { book ->
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

    fun restoreLibrary(library: NovelLibrary): Boolean = NovelStorageCoordinator.withLock {
        runCatching {
            val restoredIds = library.books.mapTo(mutableSetOf()) { it.id }
            val preservedBooks = readLibraryStrictly().books.filter { book ->
                book.id !in restoredIds && getBookFile(book).isFile
            }
            writeLibrary(NovelLibrary(library.books + preservedBooks))
            true
        }.getOrElse { cause ->
            Log.e(TAG, "restoreLibrary: failed to write library: ${cause.message}", cause)
            false
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Reads and migrates the library envelope from disk.
     *
     * On parse or migration errors the corrupted data is logged and an empty library is returned.
     */
    private fun readLibrary(): NovelLibrary {
        return runCatching { readLibraryStrictly() }.getOrElse { cause ->
            when (cause) {
                is SerializationException, is IllegalStateException -> {
                    Log.e(TAG, "readLibrary: corrupt data detected — ${cause.message}", cause)
                    NovelLibrary()
                }
                else -> throw cause
            }
        }
    }

    /** Mutations must not treat an unreadable or unsupported index as an empty library. */
    private fun readLibraryStrictly(): NovelLibrary {
        if (!libraryFile.exists()) {
            return NovelLibrary()
        }
        val text = libraryFile.readText()
        val envelope = json.decodeFromString<NovelLibraryEnvelope>(text)
        return NovelSchemaMigrations.migrateToLatest(envelope).library
    }

    private fun writeLibrary(library: NovelLibrary) {
        val envelope = NovelLibraryEnvelope(
            schemaVersion = NovelSchemaMigrations.LATEST_SCHEMA_VERSION,
            library = library,
        )
        val tmpFile = File(libraryFile.parent, "${libraryFile.name}.tmp")
        try {
            FileOutputStream(tmpFile).use { output ->
                output.write(json.encodeToString(envelope).encodeToByteArray())
                output.fd.sync()
            }
            if (tmpFile.renameTo(libraryFile)) return
            throw IOException("Atomic rename failed: ${tmpFile.absolutePath} -> ${libraryFile.absolutePath}")
        } catch (cause: Throwable) {
            tmpFile.delete()
            throw cause
        }
    }

    private fun validateImportSize(uri: Uri) {
        val length = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
            descriptor.length
        } ?: -1L
        if (length > MAX_IMPORT_SIZE_BYTES) {
            throw ImportTooLargeException(length, MAX_IMPORT_SIZE_BYTES)
        }
    }

    private fun copyWithProgress(
        input: InputStream,
        output: OutputStream,
        expectedLength: Long?,
        onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit,
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytesRead = 0L
        while (true) {
            val remainingWithOverflowByte = MAX_IMPORT_SIZE_BYTES - totalBytesRead + 1L
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remainingWithOverflowByte).toInt())
            if (read < 0) {
                onProgress(totalBytesRead, expectedLength)
                return
            }
            if (totalBytesRead + read > MAX_IMPORT_SIZE_BYTES) {
                throw ImportTooLargeException(totalBytesRead + read, MAX_IMPORT_SIZE_BYTES)
            }
            output.write(buffer, 0, read)
            totalBytesRead += read
            onProgress(totalBytesRead, expectedLength)
        }
    }

    private fun mapImportException(cause: Throwable): Throwable {
        if (cause is ImportTooLargeException) return cause
        if (cause is InvalidEpubException) return cause
        if (cause is SourceUnavailableException) return cause
        if (cause is FileNotFoundException || cause is SecurityException) {
            return SourceUnavailableException("Source unavailable or permission was revoked", cause)
        }
        if (cause is IOException && cause.isLowStorageError()) {
            return LowStorageException("No space left while importing EPUB", cause)
        }
        return cause
    }

    private fun IOException.isLowStorageError(): Boolean {
        val msg = message.orEmpty()
        return msg.contains("ENOSPC", ignoreCase = true) ||
            msg.contains("No space left", ignoreCase = true)
    }

    private companion object {
        const val ROOT_DIR_NAME = "light_novel_plugin"
        const val BOOKS_DIR_NAME = "books"
        const val LIBRARY_FILE_NAME = "library.json"
        const val MAX_IMPORT_SIZE_BYTES = 100L * 1024L * 1024L
        const val TAG = "NovelStorage"
    }
}

class ImportTooLargeException(actualBytes: Long, limitBytes: Long) :
    IllegalArgumentException("Import too large: actual=$actualBytes, limit=$limitBytes")

class SourceUnavailableException(message: String, cause: Throwable? = null) : IOException(message, cause)

class InvalidEpubException(message: String, cause: Throwable? = null) : IOException(message, cause)

class LowStorageException(message: String, cause: Throwable? = null) : IOException(message, cause)
