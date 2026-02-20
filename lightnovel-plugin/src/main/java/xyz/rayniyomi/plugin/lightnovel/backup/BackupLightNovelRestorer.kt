package xyz.rayniyomi.plugin.lightnovel.backup

import android.content.Context
import android.util.Log
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.rayniyomi.plugin.lightnovel.data.NovelLibrary
import xyz.rayniyomi.plugin.lightnovel.data.NovelLibraryEnvelope
import xyz.rayniyomi.plugin.lightnovel.data.NovelSchemaMigrations
import java.io.File
import java.io.FileOutputStream

class BackupLightNovelRestorer(
    private val context: Context,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun restoreBackup(backupData: ByteArray): Boolean {
        val backup = try {
            val backupString = backupData.decodeToString()
            json.decodeFromString<BackupLightNovel>(backupString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode backup: ${e.message}")
            return false
        }

        if (!isVersionCompatible(backup.version)) {
            Log.w(TAG, "Backup version ${backup.version} incompatible (expected ${BackupLightNovel.LATEST_VERSION})")
            return false
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
        return try {
            val allValid = library.books.all { book ->
                book.id.isNotBlank() &&
                    book.title.isNotBlank() &&
                    book.epubFileName.isNotBlank() &&
                    book.lastReadChapter >= 0 &&
                    book.lastReadOffset >= 0 &&
                    book.updatedAt >= 0
            }
            if (!allValid) {
                Log.e(TAG, "Library validation failed: invalid book data found")
            }
            allValid
        } catch (e: Exception) {
            Log.e(TAG, "Library validation failed: ${e.message}")
            false
        }
    }

    private fun restoreLibraryAtomically(library: NovelLibrary): Boolean {
        val rootDir = File(context.filesDir, "light_novel_plugin")
        val timestamp = System.currentTimeMillis()
        val tempFile = File(rootDir, "library_restore_temp_$timestamp.json")
        val libraryFile = File(rootDir, "library.json")

        return runCatching {
            require(rootDir.mkdirs() || rootDir.exists()) {
                "Failed to create root directory: ${rootDir.absolutePath}"
            }

            val envelope = NovelLibraryEnvelope(
                schemaVersion = NovelSchemaMigrations.LATEST_SCHEMA_VERSION,
                library = library,
            )
            tempFile.writeText(json.encodeToString(envelope))

            FileOutputStream(tempFile).use { it.fd.sync() }

            json.decodeFromString<NovelLibraryEnvelope>(tempFile.readText())

            val success = tempFile.renameTo(libraryFile)
            if (!success) {
                throw IllegalStateException("Failed to rename temp file to library file")
            }

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
