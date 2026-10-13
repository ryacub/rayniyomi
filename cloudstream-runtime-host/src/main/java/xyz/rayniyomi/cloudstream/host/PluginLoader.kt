package xyz.rayniyomi.cloudstream.host

import android.app.Application
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import dalvik.system.PathClassLoader
import java.io.File

internal class PluginLoader(private val application: Application, private val parent: ClassLoader) {

    fun load(file: File): List<MainAPI> {
        val manifest = Cs3Manifest.read(file)
        require(!manifest.requiresResources) { "${manifest.name} needs plugin resources, which are not supported" }

        val loader = PathClassLoader(file.absolutePath, parent)
        val plugin = loader.loadClass(manifest.pluginClassName).getDeclaredConstructor().newInstance() as BasePlugin
        plugin.filename = file.absolutePath
        if (plugin is Plugin) plugin.load(application) else plugin.load()

        return APIHolder.allProviders
            .filter { it.sourcePlugin == file.absolutePath }
            .toList()
            .filter { api -> api.supportedTypes.any { it in ANIME_TYPES } }
    }
}
