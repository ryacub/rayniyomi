package xyz.rayniyomi.cloudstream.contract

object PluginContract {

    const val PATH_PLUGINS = "plugins"

    const val COLUMN_INTERNAL_NAME = "internal_name"
    const val COLUMN_NAME = "name"
    const val COLUMN_VERSION = "version"
    const val COLUMN_LANGUAGE = "language"
    const val COLUMN_REPOSITORY_URL = "repository_url"
    const val COLUMN_FILE_SHA256 = "file_sha256"

    val COLUMNS = arrayOf(
        COLUMN_INTERNAL_NAME,
        COLUMN_NAME,
        COLUMN_VERSION,
        COLUMN_LANGUAGE,
        COLUMN_REPOSITORY_URL,
        COLUMN_FILE_SHA256,
    )

    const val MANAGER_ACTIVITY = "xyz.rayniyomi.cloudstream.ui.ManagerActivity"

    private const val HOST_PACKAGE = "xyz.rayniyomi"
    private val HOST_SUFFIXES = setOf("", ".dev", ".debug", ".benchmark")

    fun authority(runtimePackage: String): String = "$runtimePackage.plugins"

    fun isHostPackage(packageName: String?): Boolean =
        packageName != null && HOST_SUFFIXES.any { packageName == HOST_PACKAGE + it }

    fun extensionReplacedAction(hostPackage: String): String = "$hostPackage.ACTION_EXTENSION_REPLACED"
}
