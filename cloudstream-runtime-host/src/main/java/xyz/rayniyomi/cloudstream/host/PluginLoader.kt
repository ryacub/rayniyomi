package xyz.rayniyomi.cloudstream.host

import android.app.Application
import android.util.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import java.io.File

internal class PluginLoader(
    private val application: Application,
    private val parent: ClassLoader,
    private val crashIndex: PluginCrashIndex,
    private val onCallbackFailure: (PluginKey, Throwable) -> Unit,
) {

    fun load(file: File, key: PluginKey): List<MainAPI> {
        val manifest = Cs3Manifest.read(file)
        require(!manifest.requiresResources) { "${manifest.name} needs plugin resources, which are not supported" }

        val loader = PluginClassLoader(file.absolutePath, parent)
        crashIndex.add(
            LoadedPlugin(key, file.absolutePath, manifest.pluginClassName.substringBeforeLast('.', ""), loader::owns),
        )
        val callbacks = PluginCallbacks(
            register = application::registerActivityLifecycleCallbacks,
            unregister = application::unregisterActivityLifecycleCallbacks,
            onFailure = { onCallbackFailure(key, it) },
        )
        try {
            val plugin = loader.loadClass(manifest.pluginClassName).getDeclaredConstructor().newInstance() as BasePlugin
            plugin.filename = file.absolutePath
            if (plugin is Plugin) plugin.load(PluginApplication(application, callbacks)) else plugin.load()
            requireLinkedClasses(classNames(file), loader::requireLinked)
        } catch (e: Throwable) {
            callbacks.detachAll()
            throw e
        }

        return APIHolder.allProviders
            .filter { it.sourcePlugin == file.absolutePath }
            .toList()
            .filter { api -> api.supportedTypes.any { it in ANIME_TYPES } }
    }

    private fun classNames(file: File): List<String> = runCatching { dexClassNames(file) }
        .onFailure { Log.w(TAG, "Cannot list the classes of ${file.name}, so their link check is skipped", it) }
        .getOrDefault(emptyList())

    private companion object {
        const val TAG = "CloudStreamRuntime"
    }
}
