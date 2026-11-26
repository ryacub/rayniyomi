package eu.kanade.tachiyomi.data.download.anime

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.data.download.core.DownloadQueueStore
import eu.kanade.tachiyomi.data.download.core.commitQueuePreferences
import eu.kanade.tachiyomi.data.download.core.writeQueueRollbackMarker
import eu.kanade.tachiyomi.di.appGraph
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.interactor.GetEpisode
import tachiyomi.domain.source.anime.service.AnimeSourceManager

/**
 * This class is used to persist active downloads across application restarts.
 */
class AnimeDownloadStore(
    context: Context,
    private val sourceManager: AnimeSourceManager = appGraph.animeSourceManager,
    private val json: Json = appGraph.json,
    private val getAnime: GetAnime = appGraph.getAnime,
    private val getEpisode: GetEpisode = appGraph.getEpisode,
) : DownloadQueueStore<AnimeDownload> {

    /**
     * Preference file where active downloads are stored.
     */
    private val preferences = context.getSharedPreferences("active_downloads_anime", Context.MODE_PRIVATE)
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
    override fun addAll(downloads: List<AnimeDownload>) {
        preferences.edit {
            downloads.forEach { putString(getKey(it), serialize(it, counter++)) }
        }
    }

    /**
     * Removes a download from the store.
     *
     * @param download the download to remove.
     */
    override fun remove(download: AnimeDownload) {
        preferences.edit {
            remove(getKey(download))
        }
    }

    /**
     * Removes a list of downloads from the store.
     *
     * @param downloads the download to remove.
     */
    override fun removeAll(downloads: List<AnimeDownload>) {
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

    override suspend fun replaceAll(downloads: List<AnimeDownload>) {
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
    private fun getKey(download: AnimeDownload): String {
        return download.episode.id.toString()
    }

    /**
     * Returns the list of downloads to restore. It should be called in a background thread.
     */
    suspend fun restore(): List<AnimeDownload> {
        val needsLegacyFlag = !migrationPreferences.getBoolean(KEY_MIGRATION_DONE, false)
        val shouldImportLegacy = needsLegacyFlag && !preferences.getBoolean(KEY_IMPORT_STAMP, false)
        val serializedEntries = preferences.all.values +
            if (shouldImportLegacy) legacyPreferences.all.values else emptyList()

        val objs = serializedEntries
            .mapNotNull { it as? String }
            .mapNotNull { deserialize(it) }
            .sortedBy { it.order }
            .distinctBy { it.episodeId }

        sourceManager.isInitialized.first { it }
        val downloads = mutableListOf<AnimeDownload>()
        if (objs.isNotEmpty()) {
            val cachedAnime = mutableMapOf<Long, Anime?>()
            for ((animeId, episodeId) in objs) {
                val anime = cachedAnime.getOrPut(animeId) {
                    getAnime.await(animeId)
                } ?: continue
                val source = sourceManager.get(anime.source) as? AnimeHttpSource ?: continue
                val episode = getEpisode.await(episodeId) ?: continue
                downloads.add(AnimeDownload(source, anime, episode))
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
    private fun serialize(download: AnimeDownload, order: Int): String {
        val obj = AnimeDownloadObject(download.anime.id, download.episode.id, order)
        return json.encodeToString(obj)
    }

    /**
     * Restore a download from a string.
     *
     * @param string the download as string.
     */
    private fun deserialize(string: String): AnimeDownloadObject? {
        return try {
            json.decodeFromString<AnimeDownloadObject>(string)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val KEY_IMPORT_STAMP = "legacy_import_complete"
        private const val KEY_MIGRATION_DONE = "anime_active_downloads_migrated"
    }
}

/**
 * Class used for download serialization
 *
 * @param animeId the id of the anime.
 * @param episodeId the id of the episode.
 * @param order the order of the download in the queue.
 */
@Serializable
private data class AnimeDownloadObject(val animeId: Long, val episodeId: Long, val order: Int)
