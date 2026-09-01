package xyz.rayniyomi.plugin.lightnovel.backup

import kotlinx.serialization.Serializable
import xyz.rayniyomi.lightnovel.contract.LightNovelBackupContract
import xyz.rayniyomi.plugin.lightnovel.data.NovelLibrary

@Serializable
data class BackupLightNovel(
    val version: Int = LightNovelBackupContract.LATEST_BACKUP_VERSION,
    val timestamp: Long = System.currentTimeMillis(),
    val library: NovelLibrary,
) {
    companion object {
        const val BACKUP_VERSION = LightNovelBackupContract.BACKUP_VERSION
        const val LATEST_VERSION = LightNovelBackupContract.LATEST_BACKUP_VERSION
        const val MIN_COMPATIBLE_VERSION = LightNovelBackupContract.MIN_COMPATIBLE_BACKUP_VERSION
    }
}
