package eu.kanade.tachiyomi.data.backup.lightnovel

import android.content.pm.PackageManager
import android.net.Uri

object LightNovelBackupContract {
    const val PLUGIN_PACKAGE_NAME = "xyz.rayniyomi.plugin.lightnovel"
    const val BACKUP_AUTHORITY = "xyz.rayniyomi.plugin.lightnovel.backup"
    val BACKUP_URI: Uri = Uri.parse("content://$BACKUP_AUTHORITY/library")

    const val SIDECAR_SUFFIX = ".lightnovel.tachibk"
    const val LEGACY_BACKUP_FILE_NAME = "lightnovel_backup.tachibk"

    const val CALL_METHOD_RESTORE_BACKUP = "restore_backup"
    const val CALL_EXTRA_BACKUP_DATA = "backup_data"
    const val CALL_RESULT_SUCCESS = "success"

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
