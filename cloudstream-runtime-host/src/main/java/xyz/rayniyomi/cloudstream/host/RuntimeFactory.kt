package xyz.rayniyomi.cloudstream.host

import android.app.Application
import android.util.Log
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory
import eu.kanade.tachiyomi.network.NetworkHelper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/** Runs in the host process. The runtime APK's Entry creates it from assets/host-runtime.jar. */
class RuntimeFactory(private val runtimePackage: String) : AnimeSourceFactory {

    override fun createSources(): List<AnimeSource> = try {
        build()
    } catch (e: Throwable) {
        Log.e(TAG, "CloudStream runtime failed to start", e)
        emptyList()
    }

    private fun build(): List<AnimeSource> {
        val startedAt = System.currentTimeMillis()
        val application = Injekt.get<Application>()
        CloudStreamRuntime.start(application, Injekt.get<NetworkHelper>())
        RefreshObserver.register(application, runtimePackage)

        val files = InstalledPluginFiles(application, runtimePackage)
        val loader = PluginLoader(application, checkNotNull(javaClass.classLoader))
        val copies = mutableSetOf<File>()
        val result = loadIsolated(files.fetch(), name = { it.internalName }) { row ->
            val file = files.copy(row).also(copies::add)
            loader.load(file).map { CloudStreamAnimeSource(it, row, runtimePackage) }
        }
        files.deleteAllExcept(copies)

        result.failures.forEach { (plugin, error) -> Log.w(TAG, "CloudStream plugin $plugin failed to load", error) }
        Log.i(TAG, "Loaded ${result.loaded.size} CloudStream sources in ${System.currentTimeMillis() - startedAt} ms")
        return result.loaded
    }

    private companion object {
        const val TAG = "CloudStreamRuntime"
    }
}
