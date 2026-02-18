package xyz.rayniyomi.plugin.lightnovel.backup

import kotlinx.serialization.Serializable
import xyz.rayniyomi.plugin.lightnovel.data.NovelLibrary

@Serializable
data class BackupLightNovel(
    val version: Int = BACKUP_VERSION,
    val timestamp: Long = System.currentTimeMillis(),
    val library: NovelLibrary,
) {
    companion object {
        const val BACKUP_VERSION = 1
        const val LATEST_VERSION = 1
        const val MIN_COMPATIBLE_VERSION = 1
    }
}
