package eu.kanade.tachiyomi.data.download.manga

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import eu.kanade.tachiyomi.data.download.core.DownloadQueueStore
import eu.kanade.tachiyomi.data.download.core.commitQueuePreferences
import eu.kanade.tachiyomi.data.download.core.writeQueueRollbackMarker
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.di.appGraph
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.interactor.GetChapter
import tachiyomi.domain.source.manga.service.MangaSourceManager

/**
 * This class is used to persist active downloads across application restarts.
 */
class MangaDownloadStore(
    context: Context,
    private val sourceManager: MangaSourceManager = appGraph.mangaSourceManager,
    private val json: Json = appGraph.json,
    private val getManga: GetManga = appGraph.getManga,
    private val getChapter: GetChapter = appGraph.getChapter,
) : DownloadQueueStore<MangaDownload> {

    /**
     * Preference file where active downloads are stored.
     */
    private val preferences = context.getSharedPreferences("active_downloads_manga", Context.MODE_PRIVATE)
    private val legacyPreferences = context.getSharedPreferences("active_downloads", Context.MODE_PRIVATE)
    private val migrationPreferences = context.getSharedPreferences("active_downloads_migration", Context.MODE_PRIVATE)

    /**
     * Counter used to keep the queue order.
     */
    private var counter = 0

    /**
     * Adds a list of downloads to the store.
     *
     * @param downloads the list of downloads to add.
     */
    override fun addAll(downloads: List<MangaDownload>) {
        preferences.edit {
            downloads.forEach { putString(getKey(it), serialize(it, counter++)) }
        }
    }

    /**
     * Removes a download from the store.
     *
     * @param download the download to remove.
     */
    override fun remove(download: MangaDownload) {
        preferences.edit {
            remove(getKey(download))
        }
    }

    /**
     * Removes a list of downloads from the store.
     *
     * @param downloads the download to remove.
     */
    override fun removeAll(downloads: List<MangaDownload>) {
        preferences.edit {
            downloads.forEach { remove(getKey(it)) }
        }
    }

    /**
     * Removes all the downloads from the store.
     */
    override fun clear() {
        preferences.edit { writeQueue(emptyList()) }
        counter = 0
    }

    override suspend fun replaceAll(downloads: List<MangaDownload>) {
        val entries = downloads.mapIndexed { order, download -> getKey(download) to serialize(download, order) }
        commitQueuePreferences { preferences.edit().apply { writeQueue(entries) } }
        counter = downloads.size
    }

    private fun SharedPreferences.Editor.writeQueue(entries: List<Pair<String, String>>) {
        clear()
        putBoolean(KEY_IMPORT_STAMP, true)
        entries.forEach { (key, value) -> putString(key, value) }
    }

    /**
     * Returns the preference's key for the given download.
     *
     * @param download the download.
     */
    private fun getKey(download: MangaDownload): String {
        return download.chapter.id.toString()
    }

    /**
     * Returns the list of downloads to restore. It should be called in a background thread.
     */
    suspend fun restore(): List<MangaDownload> {
        val needsLegacyFlag = !migrationPreferences.getBoolean(KEY_MIGRATION_DONE, false)
        val shouldImportLegacy = needsLegacyFlag && !preferences.getBoolean(KEY_IMPORT_STAMP, false)
        val serializedEntries = preferences.all.values +
            if (shouldImportLegacy) legacyPreferences.all.values else emptyList()

        val objs = serializedEntries
            .mapNotNull { it as? String }
            .mapNotNull { deserialize(it) }
            .sortedBy { it.order }
            .distinctBy { it.chapterId }

        sourceManager.isInitialized.first { it }
        val downloads = mutableListOf<MangaDownload>()
        if (objs.isNotEmpty()) {
            val cachedManga = mutableMapOf<Long, Manga?>()
            for ((mangaId, chapterId) in objs) {
                val manga = cachedManga.getOrPut(mangaId) {
                    getManga.await(mangaId)
                } ?: continue
                val source = sourceManager.get(manga.source) as? HttpSource ?: continue
                val chapter = getChapter.await(chapterId) ?: continue
                downloads.add(MangaDownload(source, manga, chapter))
            }
        }

        // Persist the entries and the import stamp together before publishing the restored queue.
        replaceAll(downloads)
        if (needsLegacyFlag) {
            // Keep the old marker current so a rollback does not import the legacy entries again.
            writeQueueRollbackMarker { migrationPreferences.edit().putBoolean(KEY_MIGRATION_DONE, true) }
        }
        return downloads
    }

    /**
     * Converts a download to a string.
     *
     * @param download the download to serialize.
     */
    private fun serialize(download: MangaDownload, order: Int): String {
        val obj = DownloadObject(download.manga.id, download.chapter.id, order)
        return json.encodeToString(obj)
    }

    /**
     * Restore a download from a string.
     *
     * @param string the download as string.
     */
    private fun deserialize(string: String): DownloadObject? {
        return try {
            json.decodeFromString<DownloadObject>(string)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val KEY_IMPORT_STAMP = "legacy_import_complete"
        private const val KEY_MIGRATION_DONE = "manga_active_downloads_migrated"
    }
}

/**
 * Class used for download serialization
 *
 * @param mangaId the id of the manga.
 * @param chapterId the id of the chapter.
 * @param order the order of the download in the queue.
 */
@Serializable
private data class DownloadObject(val mangaId: Long, val chapterId: Long, val order: Int)
