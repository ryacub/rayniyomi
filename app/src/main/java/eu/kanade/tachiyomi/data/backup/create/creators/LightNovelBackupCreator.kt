package eu.kanade.tachiyomi.data.backup.create.creators

import android.content.Context
import android.database.Cursor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.rayniyomi.plugin.lightnovel.backup.BackupLightNovel
import xyz.rayniyomi.plugin.lightnovel.backup.LightNovelBackupContentProvider.Companion.COLUMNS
import xyz.rayniyomi.plugin.lightnovel.backup.LightNovelBackupContentProvider.Companion.CONTENT_URI
import xyz.rayniyomi.plugin.lightnovel.backup.LightNovelBackupContentProvider.Companion.COLUMN_NAMES
import xyz.rayniyomi.plugin.lightnovel.data.NovelLibrary
import java.io.File

@Suppress("ktlint:parameter-type")
class LightNovelBackupCreator(
    private val context: Context,
) {
    private const val PLUGIN_PACKAGE_NAME = "xyz.rayniyomi.plugin.lightnovel"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val packageManager = context.packageManager

    suspend operator fun invoke(): BackupLightNovel? {
        return withContext(Dispatchers.IO) {
            val pluginInstalled = isPluginInstalled()
            if (!pluginInstalled) {
                Log.i(TAG, "Light Novel plugin not installed, skipping metadata backup")
                return@withContext null
            }

            val libraryCursor = readPluginLibrary()
            libraryCursor?.use { cursor ->
                if (cursor != null && cursor.count > 0) {
                    val books = mutableListOf<xyz.rayniyomi.plugin.lightnovel.data.NovelBook>()
                    while (cursor.moveToNext()) {
                        books.add(
                            xyz.rayniyomi.plugin.lightnovel.data.NovelBook(
                                id = cursor.getString(0),
                                title = cursor.getString(1),
                                epubFileName = cursor.getString(2),
                                lastReadChapter = cursor.getInt(3),
                                lastReadOffset = cursor.getInt(4),
                                updatedAt = cursor.getLong(5),
                            ),
                        )
                    }

                    if (books.isEmpty()) {
                        Log.w(TAG, "Light Novel plugin library was empty")
                        return@withContext null
                    }

                    val library = xyz.rayniyomi.plugin.lightnovel.data.NovelLibrary(books = books)

                    BackupLightNovel(
                        version = BackupLightNovel.BACKUP_VERSION,
                        timestamp = System.currentTimeMillis(),
                        library = library,
                    )
                }
            }
        }
    }

    private fun isPluginInstalled(): Boolean {
        return try {
            packageManager.getPackageInfo(
                PLUGIN_PACKAGE_NAME,
                0,
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "Light Novel plugin not found: ${e.message}")
            false
        }
    }

    private suspend fun readPluginLibrary(): Cursor? {
        return withContext(Dispatchers.IO) {
            try {
                val uri = LightNovelBackupContentProvider.Companion.CONTENT_URI
                context.contentResolver.query(
                    uri,
                    LightNovelBackupContentProvider.Companion.COLUMN_NAMES,
                    null,
                    null,
                    null,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to query Light Novel plugin: ${e.message}", e)
                null
            }
        }
    }

    companion object {
        private const val TAG = "LightNovelBackupCreator"
    }
}
