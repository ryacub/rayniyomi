package eu.kanade.tachiyomi.data.backup.lightnovel

import android.content.Context
import android.net.Uri
import android.os.Bundle
import com.hippo.unifile.UniFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

class LightNovelBackupDataSource(
    private val context: Context,
) {
    fun isPluginInstalled(): Boolean {
        return LightNovelBackupContract.isPluginInstalled(context.packageManager)
    }

    fun readBackupData(backupUri: Uri): ByteArray? {
        val backupFile = UniFile.fromUri(context, backupUri) ?: return null
        val parent = backupFile.parentFile ?: return null
        val backupName = backupFile.name ?: return null
        val sidecarName = LightNovelBackupContract.sidecarNameFor(backupName)
        val sidecarFile = parent.findFile(sidecarName)
            ?: parent.findFile(LightNovelBackupContract.LEGACY_BACKUP_FILE_NAME)
            ?: return null

        return runCatching {
            sidecarFile.openInputStream().use { input -> input?.readBytes() }
        }.getOrElse {
            logcat(LogPriority.WARN, it) { "Light Novel backup file not found or unreadable" }
            null
        }
    }

    suspend fun restoreBackup(backupData: ByteArray): Boolean {
        return withContext(Dispatchers.IO) {
            val extras = Bundle().apply {
                putByteArray(LightNovelBackupContract.CALL_EXTRA_BACKUP_DATA, backupData)
            }
            context.contentResolver.call(
                LightNovelBackupContract.BACKUP_URI,
                LightNovelBackupContract.CALL_METHOD_RESTORE_BACKUP,
                null,
                extras,
            )?.getBoolean(LightNovelBackupContract.CALL_RESULT_SUCCESS, false) == true
        }
    }
}
