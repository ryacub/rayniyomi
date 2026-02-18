package eu.kanade.tachiyomi.data.backup.create.creators

import android.content.Context
import android.database.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.api.get
import xyz.rayniyomi.plugin.lightnovel.backup.BackupLightNovel
import xyz.rayniyomi.plugin.lightnovel.backup.LightNovelBackupContentProvider
import xyz.rayniyomi.plugin.lightnovel.data.NovelBook

class LightNovelBackupCreator(
    private val context: Context,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val packageManager = context.packageManager

    operator fun invoke(): BackupLightNovel? {
        val pluginInstalled = isPluginInstalled()
        if (!pluginInstalled) {
            return null
        }

        return withContext(Dispatchers.IO) {
            val libraryCursor = readPluginLibrary()
            libraryCursor?.use { cursor ->
                if (cursor != null && cursor.count > 0) {
                    val books = mutableListOf<NovelBook>()
                    while (cursor.moveToNext()) {
                        val book = NovelBook(
                            id = cursor.getString(0),
                            title = cursor.getString(1),
                            epubFileName = cursor.getString(2),
                            lastReadChapter = cursor.getInt(3),
                            lastReadOffset = cursor.getInt(4),
                            updatedAt = cursor.getLong(5),
                        )
                        books.add(book)
                    }

                    val library = xyz.rayniyomi.plugin.lightnovel.data.NovelLibrary(books = books)
                    BackupLightNovel(
                        version = BackupLightNovel.BACKUP_VERSION,
                        timestamp = System.currentTimeMillis(),
                        library = library,
                    )
                } else {
                    null
                }
            }
        }
    }

    private fun isPluginInstalled(): Boolean {
        return try {
            packageManager.getPackageInfo(
                LightNovelBackupContentProvider.AUTHORITY,
                0,
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun readPluginLibrary(): Cursor? {
        return withContext(Dispatchers.IO) {
            try {
                val uri = LightNovelBackupContentProvider.CONTENT_URI
                context.contentResolver.query(
                    uri,
                    null,
                    null,
                    null,
                    null,
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
