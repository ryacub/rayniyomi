package xyz.rayniyomi.plugin.lightnovel.backup

import android.content.Context
import android.util.Log
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import xyz.rayniyomi.plugin.lightnovel.data.NovelLibrary
import xyz.rayniyomi.plugin.lightnovel.data.NovelStorage
import java.io.File

class BackupLightNovelRestorer(
    private val context: Context,
) {
    private val storage = NovelStorage(context)
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

        return storage.restoreLibrary(backup.library)
    }

    private fun isVersionCompatible(version: Int): Boolean {
        return version in BackupLightNovel.MIN_COMPATIBLE_VERSION..BackupLightNovel.LATEST_VERSION
    }

    private fun validateLibrary(library: NovelLibrary): Boolean {
        return try {
            val ids = mutableSetOf<String>()
            val allValid = library.books.all { book ->
                book.id.isNotBlank() &&
                    ids.add(book.id) &&
                    book.title.isNotBlank() &&
                    book.epubFileName.isNotBlank() &&
                    File(book.epubFileName).name == book.epubFileName &&
                    !book.epubFileName.contains('\\') &&
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

    companion object {
        private const val TAG = "BackupLightNovelRestorer"
    }
}
