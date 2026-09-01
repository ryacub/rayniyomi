package eu.kanade.tachiyomi.data.backup.lightnovel

import android.content.pm.PackageManager
import android.net.Uri
import xyz.rayniyomi.lightnovel.contract.LightNovelBackupContract as SharedLightNovelBackupContract

object LightNovelBackupContract {
    const val PLUGIN_PACKAGE_NAME = "xyz.rayniyomi.plugin.lightnovel"
    const val BACKUP_AUTHORITY = SharedLightNovelBackupContract.AUTHORITY
    val BACKUP_URI: Uri = Uri.parse(SharedLightNovelBackupContract.CONTENT_URI)

    const val SIDECAR_SUFFIX = ".lightnovel.tachibk"
    const val LEGACY_BACKUP_FILE_NAME = "lightnovel_backup.tachibk"

    const val CALL_METHOD_RESTORE_BACKUP = SharedLightNovelBackupContract.METHOD_RESTORE_BACKUP
    const val CALL_EXTRA_BACKUP_DATA = SharedLightNovelBackupContract.EXTRA_BACKUP_DATA
    const val CALL_RESULT_SUCCESS = SharedLightNovelBackupContract.RESULT_SUCCESS

    fun sidecarNameFor(backupFileName: String): String {
        return backupFileName.removeSuffix(".tachibk") + SIDECAR_SUFFIX
    }

    fun isPluginInstalled(packageManager: PackageManager): Boolean {
        return runCatching {
            packageManager.getPackageInfo(PLUGIN_PACKAGE_NAME, 0)
            true
        }.getOrElse { false }
    }
}
