package eu.kanade.tachiyomi.data.backup.create.creators

import android.content.Context
import android.database.Cursor
import eu.kanade.tachiyomi.data.backup.lightnovel.LightNovelBackupContract
import eu.kanade.tachiyomi.data.backup.lightnovel.LightNovelBackupPayload
import eu.kanade.tachiyomi.data.backup.lightnovel.NovelBookPayload
import eu.kanade.tachiyomi.data.backup.lightnovel.NovelLibraryPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

class LightNovelBackupCreator(
    private val context: Context,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val packageManager = context.packageManager

    suspend operator fun invoke(): ByteArray? {
        return withContext(Dispatchers.IO) {
            if (!LightNovelBackupContract.isPluginInstalled(packageManager)) return@withContext null

            val libraryCursor = readPluginLibrary()
            libraryCursor?.use { cursor ->
                val books = mutableListOf<NovelBookPayload>()
                while (cursor.moveToNext()) {
                    books += NovelBookPayload(
                        id = cursor.getStringOrEmpty(COLUMN_ID),
                        title = cursor.getStringOrEmpty(COLUMN_TITLE),
                        epubFileName = cursor.getStringOrEmpty(COLUMN_EPUB_FILE_NAME),
                        lastReadChapter = cursor.getIntOrZero(COLUMN_LAST_READ_CHAPTER),
                        lastReadOffset = cursor.getIntOrZero(COLUMN_LAST_READ_OFFSET),
                        updatedAt = cursor.getLongOrZero(COLUMN_UPDATED_AT),
                    )
                }

                if (books.isEmpty()) return@withContext null

                val payload = LightNovelBackupPayload(
                    library = NovelLibraryPayload(books = books),
                )
                return@withContext json.encodeToString(
                    LightNovelBackupPayload.serializer(),
                    payload,
                ).encodeToByteArray()
            }
            null
        }
    }

    private suspend fun readPluginLibrary(): Cursor? {
        return withContext(Dispatchers.IO) {
            try {
                context.contentResolver.query(
                    CONTENT_URI,
                    COLUMNS,
                    null,
                    null,
                    null,
                )
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to query Light Novel plugin" }
                null
            }
        }
    }

    companion object {
        private const val TAG = "LightNovelBackupCreator"

        private const val PATH_LIBRARY = "library"
        private val CONTENT_URI = android.net.Uri.parse(
            "content://${LightNovelBackupContract.BACKUP_AUTHORITY}/$PATH_LIBRARY",
        )

        private const val COLUMN_ID = "id"
        private const val COLUMN_TITLE = "title"
        private const val COLUMN_EPUB_FILE_NAME = "epub_file_name"
        private const val COLUMN_LAST_READ_CHAPTER = "last_read_chapter"
        private const val COLUMN_LAST_READ_OFFSET = "last_read_offset"
        private const val COLUMN_UPDATED_AT = "updated_at"

        private val COLUMNS = arrayOf(
            COLUMN_ID,
            COLUMN_TITLE,
            COLUMN_EPUB_FILE_NAME,
            COLUMN_LAST_READ_CHAPTER,
            COLUMN_LAST_READ_OFFSET,
            COLUMN_UPDATED_AT,
        )
    }
}

private fun Cursor.columnIndex(name: String): Int = getColumnIndex(name).takeIf { it >= 0 } ?: -1

private fun Cursor.getStringOrEmpty(name: String): String {
    val index = columnIndex(name)
    return if (index >= 0) getString(index).orEmpty() else ""
}

private fun Cursor.getIntOrZero(name: String): Int {
    val index = columnIndex(name)
    return if (index >= 0) getInt(index) else 0
}

private fun Cursor.getLongOrZero(name: String): Long {
    val index = columnIndex(name)
    return if (index >= 0) getLong(index) else 0L
}
