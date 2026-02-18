package eu.kanade.tachiyomi.data.backup.lightnovel

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LightNovelBackupPayload(
    val version: Int = LIGHT_NOVEL_BACKUP_VERSION,
    val timestamp: Long = System.currentTimeMillis(),
    val library: NovelLibraryPayload,
)

@Serializable
data class NovelLibraryPayload(
    val books: List<NovelBookPayload>,
)

@Serializable
data class NovelBookPayload(
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
)

private const val LIGHT_NOVEL_BACKUP_VERSION = 1
