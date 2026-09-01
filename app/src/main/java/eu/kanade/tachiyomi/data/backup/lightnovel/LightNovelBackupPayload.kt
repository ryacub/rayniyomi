package eu.kanade.tachiyomi.data.backup.lightnovel

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import xyz.rayniyomi.lightnovel.contract.LightNovelBackupContract as SharedLightNovelBackupContract

@Serializable
data class LightNovelBackupPayload(
    val version: Int = SharedLightNovelBackupContract.LATEST_BACKUP_VERSION,
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
    @SerialName(SharedLightNovelBackupContract.COLUMN_EPUB_FILE_NAME)
    val epubFileName: String,
    @SerialName(SharedLightNovelBackupContract.COLUMN_LAST_READ_CHAPTER)
    val lastReadChapter: Int = 0,
    @SerialName(SharedLightNovelBackupContract.COLUMN_LAST_READ_OFFSET)
    val lastReadOffset: Int = 0,
    @SerialName(SharedLightNovelBackupContract.COLUMN_UPDATED_AT)
    val updatedAt: Long = System.currentTimeMillis(),
)
