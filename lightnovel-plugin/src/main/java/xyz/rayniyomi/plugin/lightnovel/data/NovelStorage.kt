package xyz.rayniyomi.plugin.lightnovel.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.rayniyomi.plugin.lightnovel.epub.EpubTextExtractor
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Persistent storage for the user's light-novel library.
 *
 * ### Schema versioning
 * Data is stored as a [NovelLibraryEnvelope] that carries a [NovelLibraryEnvelope.schemaVersion].
 * On every read the envelope is automatically migrated to [NovelSchemaMigrations.LATEST_SCHEMA_VERSION]
 * via [NovelSchemaMigrations.migrateToLatest] before the caller sees the data.
 *
 * ### Corruption resilience
 * If the on-disk JSON cannot be parsed, [checkIntegrity] returns [NovelStorageState.Corrupted]
 * instead of throwing. The host UI can then offer recovery by calling [clearAndRecover], which
 * deletes the library file and resets to a clean empty state without crashing.
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

    // -------------------------------------------------------------------------
    // Integrity checking
    // -------------------------------------------------------------------------

    /**
     * Checks whether the on-disk library can be read without errors.
     *
     * This function never throws; all parse failures are captured and returned as
     * [NovelStorageState.Corrupted].
     *
     * @return [NovelStorageState.Ok] if the file is absent or valid JSON,
     *   [NovelStorageState.Corrupted] otherwise.
     */
    @Synchronized
    fun checkIntegrity(): NovelStorageState {
        if (!libraryFile.exists()) return NovelStorageState.Ok
        val text = libraryFile.readText()
        if (text.isBlank()) {
            return NovelStorageState.Corrupted(reason = "Library file is empty")
        }
        return runCatching {
            val envelope = json.decodeFromString<NovelLibraryEnvelope>(text)
            if (envelope.schemaVersion > NovelSchemaMigrations.LATEST_SCHEMA_VERSION) {
                val maxVersion = NovelSchemaMigrations.LATEST_SCHEMA_VERSION
                return NovelStorageState.Corrupted(
                    reason = "Schema version ${envelope.schemaVersion} exceeds max supported $maxVersion",
                )
            }
            NovelStorageState.Ok
        }.getOrElse { cause ->
            NovelStorageState.Corrupted(reason = cause.message ?: cause.javaClass.simpleName)
        }
    }

    // -------------------------------------------------------------------------
    // Recovery
    // -------------------------------------------------------------------------

    /**
     * Deletes the corrupted library file and resets storage to a clean empty state.
     *
     * After this call [checkIntegrity] will return [NovelStorageState.Ok] and
     * [listBooks] will return an empty list. EPUB files inside [booksDir] are
     * **not** deleted — only the library index is cleared.
     *
     * This function is idempotent: calling it on already-clean storage is safe.
     *
     * @return `true` if the file was deleted successfully or did not exist; `false` if deletion failed.
     */
    @Synchronized
    fun clearAndRecover(): Boolean {
        if (!libraryFile.exists()) return true
        val deleted = libraryFile.delete()
        if (!deleted) {
            Log.e(TAG, "clearAndRecover: failed to delete library file at ${libraryFile.absolutePath}")
        }
        return deleted
    }

    // -------------------------------------------------------------------------
    // Library reads
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Import / update
    // -------------------------------------------------------------------------

    @Synchronized
    fun importEpub(uri: Uri): NovelBook {
        val id = UUID.randomUUID().toString()
        val targetFile = File(booksDir, "$id.epub")
        validateImportSize(uri)

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

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Reads and migrates the library envelope from disk.
     *
     * On any [SerializationException] or [IllegalStateException] the corrupted data is logged
     * and an empty library is returned as a safe fallback. Callers that need to distinguish
     * between "no file" and "corrupt file" should call [checkIntegrity] first.
     */
    private fun readLibrary(): NovelLibrary {
        if (!libraryFile.exists()) {
            return NovelLibrary()
        }
        return runCatching {
            val text = libraryFile.readText()
            val envelope = json.decodeFromString<NovelLibraryEnvelope>(text)
            NovelSchemaMigrations.migrateToLatest(envelope).library
        }.getOrElse { cause ->
            when (cause) {
                is SerializationException, is IllegalStateException -> {
                    Log.e(TAG, "readLibrary: corrupt data detected — ${cause.message}", cause)
                    NovelLibrary()
                }
                else -> throw cause
            }
        }
    }

    private fun writeLibrary(library: NovelLibrary) {
        val envelope = NovelLibraryEnvelope(
            schemaVersion = NovelSchemaMigrations.LATEST_SCHEMA_VERSION,
            library = library,
        )
        val tmpFile = File(libraryFile.parent, "${libraryFile.name}.tmp")
        tmpFile.writeText(json.encodeToString(envelope))
        val renamed = tmpFile.renameTo(libraryFile)
        if (!renamed) {
            tmpFile.delete()
            throw IOException("Atomic rename failed: ${tmpFile.absolutePath} -> ${libraryFile.absolutePath}")
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
