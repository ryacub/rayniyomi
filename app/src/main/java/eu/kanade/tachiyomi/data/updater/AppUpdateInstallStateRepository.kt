package eu.kanade.tachiyomi.data.updater

import android.content.Context
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.notification.NotificationHandler
import eu.kanade.tachiyomi.util.storage.getUriCompat
import java.io.File

internal class AppUpdateInstallStateRepository(private val context: Context) {

    enum class InstallState {
        IDLE,
        DOWNLOADING,
        FAILED,
        DOWNLOADED,
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        clearIfAppVersionChanged()
    }

    fun markDownloading(expectedVersion: String?) {
        deleteDownloadFile()
        prefs.edit()
            .putString(KEY_APP_VERSION, currentAppVersion())
            .putString(KEY_STATE, InstallState.DOWNLOADING.name)
            .putString(KEY_EXPECTED_VERSION, expectedVersion)
            .remove(KEY_FILE_PATH)
            .remove(KEY_COMPLETED_AT)
            .putBoolean(KEY_COMPLETED, false)
            .apply()
    }

    fun markFailed() {
        deleteDownloadFile()
        prefs.edit()
            .putString(KEY_STATE, InstallState.FAILED.name)
            .remove(KEY_FILE_PATH)
            .remove(KEY_COMPLETED_AT)
            .putBoolean(KEY_COMPLETED, false)
            .apply()
    }

    fun markDownloaded(file: File, expectedVersion: String?) {
        prefs.edit()
            .putString(KEY_APP_VERSION, currentAppVersion())
            .putString(KEY_STATE, InstallState.DOWNLOADED.name)
            .putString(KEY_EXPECTED_VERSION, expectedVersion)
            .putString(KEY_FILE_PATH, file.absolutePath)
            .putLong(KEY_COMPLETED_AT, System.currentTimeMillis())
            .putBoolean(KEY_COMPLETED, true)
            .apply()
    }

    fun clearAll() {
        deleteDownloadFile()
        prefs.edit()
            .putString(KEY_STATE, InstallState.IDLE.name)
            .remove(KEY_EXPECTED_VERSION)
            .remove(KEY_FILE_PATH)
            .remove(KEY_COMPLETED_AT)
            .putBoolean(KEY_COMPLETED, false)
            .apply()
    }

    fun hasValidDownloadedArtifact(expectedVersion: String? = null): Boolean {
        return getValidDownloadedArtifact(expectedVersion) != null
    }

    fun installDownloadedArtifact(expectedVersion: String? = null): Boolean {
        val artifact = getValidDownloadedArtifact(expectedVersion) ?: return false
        return runCatching {
            NotificationHandler.installApkPendingActivity(
                context,
                artifact.file.getUriCompat(context),
            ).send()
            true
        }.getOrElse {
            clearAll()
            false
        }
    }

    private fun getValidDownloadedArtifact(expectedVersion: String?): DownloadedArtifact? {
        clearIfAppVersionChanged()

        val state = prefs.getString(KEY_STATE, InstallState.IDLE.name)
            ?.let(InstallState::valueOf)
            ?: InstallState.IDLE
        if (state != InstallState.DOWNLOADED || !prefs.getBoolean(KEY_COMPLETED, false)) {
            return null
        }

        val storedExpectedVersion = prefs.getString(KEY_EXPECTED_VERSION, null)
        if (expectedVersion != null && storedExpectedVersion != expectedVersion) {
            clearAll()
            return null
        }

        val path = prefs.getString(KEY_FILE_PATH, null) ?: run {
            clearAll()
            return null
        }
        val file = File(path)
        if (!AppUpdateInstallArtifactValidator.isValidDownloadedFile(file, MIN_APK_BYTES)) {
            clearAll()
            return null
        }

        return DownloadedArtifact(file = file, expectedVersion = storedExpectedVersion)
    }

    private fun clearIfAppVersionChanged() {
        val storedVersion = prefs.getString(KEY_APP_VERSION, null) ?: return
        if (storedVersion != currentAppVersion()) {
            clearAll()
        }
    }

    private fun currentAppVersion(): String {
        return "${BuildConfig.VERSION_NAME}:${BuildConfig.COMMIT_SHA}"
    }

    private fun deleteDownloadFile() {
        val path = prefs.getString(KEY_FILE_PATH, null) ?: return
        runCatching { File(path).delete() }
    }

    private data class DownloadedArtifact(
        val file: File,
        val expectedVersion: String?,
    )

    companion object {
        private const val PREFS_NAME = "app_update_install_state"
        private const val KEY_STATE = "state"
        private const val KEY_EXPECTED_VERSION = "expected_version"
        private const val KEY_FILE_PATH = "file_path"
        private const val KEY_COMPLETED = "completed"
        private const val KEY_COMPLETED_AT = "completed_at"
        private const val KEY_APP_VERSION = "app_version"
        private const val MIN_APK_BYTES = 32 * 1024L
    }
}

internal object AppUpdateInstallArtifactValidator {
    fun isValidDownloadedFile(file: File, minBytes: Long): Boolean {
        return file.exists() && file.length() >= minBytes
    }
}
