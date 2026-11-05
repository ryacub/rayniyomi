package xyz.rayniyomi.cloudstream.host

import android.app.Application
import android.os.Build
import android.util.Log
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory
import eu.kanade.tachiyomi.network.NetworkHelper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

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
        val quarantine = PluginQuarantine(File(application.noBackupFilesDir, "cloudstream/quarantine"))
        val crashIndex = PluginCrashIndex()
        installCrashGuard(crashIndex, quarantine)
        val loader = PluginLoader(application, checkNotNull(javaClass.classLoader), crashIndex) { key, error ->
            Log.w(TAG, "CloudStream plugin ${key.internalName} failed in an activity callback and is disabled", error)
            quarantine.mark(key, error.toString())
        }
        val hostVersion = "${versionCode(
            application,
            application.packageName,
        )}-${versionCode(application, runtimePackage)}"
        val copies = mutableSetOf<File>()
        val result = loadIsolated(files.fetch(), name = { it.internalName }) { row ->
            val key = PluginKey(row.internalName, row.fileSha256, hostVersion)
            quarantine.reason(key)?.let { throw PluginQuarantinedException(it) }
            val file = files.copy(row).also(copies::add)
            loader.load(file, key).map { CloudStreamAnimeSource(it, row, runtimePackage) }
        }
        files.deleteAllExcept(copies)

        result.failures.forEach { (plugin, error) -> Log.w(TAG, "CloudStream plugin $plugin failed to load", error) }
        Log.i(TAG, "Loaded ${result.loaded.size} CloudStream sources in ${System.currentTimeMillis() - startedAt} ms")
        return result.loaded
    }

    private fun versionCode(application: Application, packageName: String): Long {
        val info = application.packageManager.getPackageInfo(packageName, 0)
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
    }

    private companion object {
        const val TAG = "CloudStreamRuntime"
    }
}
