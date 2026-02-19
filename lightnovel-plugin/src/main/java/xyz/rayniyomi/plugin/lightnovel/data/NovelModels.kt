package xyz.rayniyomi.plugin.lightnovel.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A single novel book entry stored in the library.
 *
 * Schema history:
 * - v1: original fields (id, title, epubFileName, lastReadChapter, lastReadOffset, updatedAt)
 * - v2: added [readingTimeMinutes] (defaults to 0 for backwards compatibility)
 */
@Serializable
data class NovelBook(
    val id: String,
    val title: String,
    @SerialName("epub_file_name")
    val epubFileName: String,
    @SerialName("last_read_chapter")
    val lastReadChapter: Int = 0,
    @SerialName("last_read_offset")
    val lastReadOffset: Int = 0,
    @SerialName("updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    /** Cumulative reading time in minutes. Added in schema v2; defaults to 0 for v1 data. */
    @SerialName("reading_time_minutes")
    val readingTimeMinutes: Long = 0L,
)

/**
 * The in-memory representation of the user's novel library (list of books).
 *
 * This is embedded inside [NovelLibraryEnvelope] when persisted so that the schema version
 * is tracked alongside the data.
 */
@Serializable
data class NovelLibrary(
    val books: List<NovelBook> = emptyList(),
)

/**
 * Versioned envelope that wraps [NovelLibrary] on disk.
 *
 * Persisting the [schemaVersion] alongside the data allows the storage layer to detect
 * when an older file must be migrated forward before use.
 *
 * @property schemaVersion The schema version at which this data was last written.
 *   Consumers should run [NovelSchemaMigrations.migrateToLatest] before operating on the library.
 * @property library The actual library content.
 */
@Serializable
data class NovelLibraryEnvelope(
    @SerialName("schema_version")
    val schemaVersion: Int = NovelSchemaMigrations.LATEST_SCHEMA_VERSION,
    val library: NovelLibrary = NovelLibrary(),
)
