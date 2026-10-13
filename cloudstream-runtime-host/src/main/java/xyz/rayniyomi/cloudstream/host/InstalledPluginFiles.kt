package xyz.rayniyomi.cloudstream.host

import android.app.Application
import android.net.Uri
import xyz.rayniyomi.cloudstream.contract.PluginContract
import java.io.File
import java.security.MessageDigest

internal data class PluginRow(
    val internalName: String,
    val name: String,
    val version: Int,
    val language: String,
    val repositoryUrl: String,
    val fileSha256: String,
)

internal fun pluginsUri(runtimePackage: String): Uri =
    Uri.Builder()
        .scheme("content")
        .authority(PluginContract.authority(runtimePackage))
        .appendPath(PluginContract.PATH_PLUGINS)
        .build()

internal class InstalledPluginFiles(private val application: Application, private val runtimePackage: String) {

    private val directory = File(application.noBackupFilesDir, "cloudstream/plugins")

    fun fetch(): List<PluginRow> {
        val cursor =
            application.contentResolver.query(pluginsUri(runtimePackage), PluginContract.COLUMNS, null, null, null)
                ?: return emptyList()
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(
                        PluginRow(
                            internalName = it.getString(it.getColumnIndexOrThrow(PluginContract.COLUMN_INTERNAL_NAME)),
                            name = it.getString(it.getColumnIndexOrThrow(PluginContract.COLUMN_NAME)),
                            version = it.getInt(it.getColumnIndexOrThrow(PluginContract.COLUMN_VERSION)),
                            language = it.getString(it.getColumnIndexOrThrow(PluginContract.COLUMN_LANGUAGE)),
                            repositoryUrl = it.getString(
                                it.getColumnIndexOrThrow(PluginContract.COLUMN_REPOSITORY_URL),
                            ),
                            fileSha256 = it.getString(it.getColumnIndexOrThrow(PluginContract.COLUMN_FILE_SHA256)),
                        ),
                    )
                }
            }
        }
    }

    fun copy(row: PluginRow): File {
        directory.mkdirs()
        val target = File(directory, "${row.internalName}-${row.fileSha256.take(16)}.cs3")
        if (target.isFile && sha256(target) == row.fileSha256) return target

        val temp = File.createTempFile("plugin", ".tmp", directory)
        try {
            val uri = pluginsUri(runtimePackage).buildUpon().appendPath(row.internalName).build()
            val input =
                requireNotNull(application.contentResolver.openInputStream(uri)) { "No file for ${row.internalName}" }
            input.use { source -> temp.outputStream().use { source.copyTo(it) } }
            check(sha256(temp) == row.fileSha256) { "${row.internalName} does not match its hash" }
            temp.setReadOnly()
            check(temp.renameTo(target) || target.isFile) { "Could not store ${row.internalName}" }
        } finally {
            temp.delete()
        }
        return target
    }

    fun deleteAllExcept(keep: Set<File>) {
        directory.listFiles().orEmpty().filter { it !in keep }.forEach(File::delete)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
