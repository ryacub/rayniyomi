package xyz.rayniyomi.cloudstream.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import xyz.rayniyomi.cloudstream.ManagerApplication
import xyz.rayniyomi.cloudstream.contract.PluginContract
import xyz.rayniyomi.cloudstream.data.InstalledPlugin
import java.io.FileNotFoundException

class PluginProvider : ContentProvider() {

    private val application: ManagerApplication
        get() = checkNotNull(context).applicationContext as ManagerApplication

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        requireHostCaller()
        val cursor = MatrixCursor(PluginContract.COLUMNS)
        servedPlugins().forEach {
            cursor.addRow(
                arrayOf<Any>(it.internalName, it.name, it.version, it.language, it.repositoryUrl, it.fileSha256),
            )
        }
        return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        requireHostCaller()
        if (mode != "r") throw FileNotFoundException("Plugin files are read-only")
        val plugin = servedPlugins().find { it.internalName == uri.lastPathSegment }
            ?: throw FileNotFoundException("No enabled plugin ${uri.lastPathSegment}")
        return ParcelFileDescriptor.open(
            application.manager.pluginFile(plugin.internalName),
            ParcelFileDescriptor.MODE_READ_ONLY,
        )
    }

    private fun servedPlugins(): List<InstalledPlugin> =
        application.store.state.value.providerPlugins().filter {
            application.manager.pluginFile(it.internalName).isFile
        }

    private fun requireHostCaller() {
        val caller = callingPackage
        if (!PluginContract.isHostPackage(caller)) throw SecurityException("$caller may not read CloudStream plugins")
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException()

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()
}
