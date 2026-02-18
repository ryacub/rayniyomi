package xyz.rayniyomi.plugin.lightnovel.backup

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import xyz.rayniyomi.plugin.lightnovel.backup.BackupLightNovel
import xyz.rayniyomi.plugin.lightnovel.data.NovelLibrary
import xyz.rayniyomi.plugin.lightnovel.data.NovelBook
import xyz.rayniyomi.plugin.lightnovel.data.NovelStorage
import java.io.File

@Suppress("ktlint:expecting-comma-in-complex-expression")
class BackupLightNovelRestorer(
    private val context: Context,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val storage = NovelStorage(context)

    suspend fun restoreBackup(backupData: ByteArray): Boolean {
        val backup = runCatching {
            val backupString = backupData.decodeToString()
            json.decodeFromString<BackupLightNovel>(backupString)
        }.getOrElse { e ->
            Log.e(TAG, "Failed to decode backup: ${e.message}")
            return@runCatching false
        }

        Log.i(TAG, "Backup version: ${backup.version}, Library books: ${backup.library.books.size}")

        if (!isVersionCompatible(backup.version)) {
            Log.w(TAG, "Backup version ${backup.version} incompatible (expected ${BackupLightNovel.LATEST_VERSION})")
            return true
        }

        if (!validateLibrary(backup.library)) {
            Log.e(TAG, "Invalid library structure in backup")
            return false
        }

        return restoreLibraryAtomically(backup.library)
    }

    private fun isVersionCompatible(version: Int): Boolean {
        return version in BackupLightNovel.MIN_COMPATIBLE_VERSION..BackupLightNovel.LATEST_VERSION
    }

    private fun validateLibrary(library: NovelLibrary): Boolean {
        return runCatching {
            require(
                library.books.all { book ->
                    book.id.isNotBlank() &&
                        book.title.isNotBlank() &&
                        book.epubFileName.isNotBlank() &&
                        book.lastReadChapter >= 0 &&
                        book.lastReadOffset >= 0 &&
                        book.updatedAt > 0
                },
            )
            true
        }.getOrElse { e ->
            Log.e(TAG, "Library validation failed: ${e.message}")
            false
        }
    }

    private fun restoreLibraryAtomically(library: NovelLibrary): Boolean {
        val rootDir = File(context.filesDir, NovelStorage.ROOT_DIR_NAME)
        val timestamp = System.currentTimeMillis()
        val tempFile = File(rootDir, "library_restore_temp_$timestamp.json")
        val libraryFile = File(rootDir, NovelStorage.LIBRARY_FILE_NAME)

        return runCatching {
            rootDir.mkdirs()
            tempFile.writeText(json.encodeToString(library))

            json.decodeFromString<NovelLibrary>(tempFile.readText())

            val success = tempFile.renameTo(libraryFile)
            if (!success) {
                throw IllegalStateException("Failed to rename temp file to library file")
            }

            Log.i(TAG, "Library restored atomically: ${library.books.size} books")
            true
        }.getOrElse { e ->
            Log.e(TAG, "Failed to restore library atomically: ${e.message}", e)
            tempFile.delete()
            false
        }
    }

    companion object {
        private const val TAG = "BackupLightNovelRestorer"
    }
}
