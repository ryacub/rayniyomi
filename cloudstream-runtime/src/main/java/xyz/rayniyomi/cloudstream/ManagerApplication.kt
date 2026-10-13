package xyz.rayniyomi.cloudstream

import android.app.Application
import android.net.Uri
import okhttp3.OkHttpClient
import xyz.rayniyomi.cloudstream.contract.PluginContract
import xyz.rayniyomi.cloudstream.data.OkHttpFetcher
import xyz.rayniyomi.cloudstream.data.PluginManager
import xyz.rayniyomi.cloudstream.data.PluginStore
import java.io.File

class ManagerApplication : Application() {

    val pluginsUri: Uri by lazy {
        Uri.Builder()
            .scheme("content")
            .authority(PluginContract.authority(packageName))
            .appendPath(PluginContract.PATH_PLUGINS)
            .build()
    }

    val store: PluginStore by lazy {
        PluginStore(File(filesDir, "state.json")) { contentResolver.notifyChange(pluginsUri, null) }
    }

    val manager: PluginManager by lazy {
        PluginManager(store, File(filesDir, "plugins"), OkHttpFetcher(OkHttpClient()))
    }
}
