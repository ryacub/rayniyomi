package xyz.rayniyomi.cloudstream.host

import dalvik.system.DexFile
import dalvik.system.PathClassLoader
import java.io.File

/**
 * ART answers findLoadedClass on an exact PathClassLoader by defining the class, so only a subclass can
 * look up loaded classes without loading new ones. A class that failed to link makes the lookup throw.
 */
internal class PluginClassLoader(path: String, parent: ClassLoader) : PathClassLoader(path, parent) {

    fun requireLinked(name: String) {
        findLoadedClass(name)
    }

    fun owns(name: String): Boolean = runCatching { findLoadedClass(name) != null }.getOrDefault(true)
}

internal class PluginLinkException(val classes: List<String>, cause: Throwable) :
    Exception("Plugin classes failed to link: ${classes.joinToString()}", cause)

internal fun requireLinkedClasses(names: List<String>, requireLinked: (String) -> Unit) {
    val failures = names.mapNotNull { name ->
        runCatching { requireLinked(name) }.exceptionOrNull()?.let { name to it }
    }
    if (failures.isNotEmpty()) throw PluginLinkException(failures.map { it.first }, failures.first().second)
}

@Suppress("DEPRECATION")
internal fun dexClassNames(file: File): List<String> {
    val dex = DexFile(file)
    return try {
        dex.entries().toList()
    } finally {
        dex.close()
    }
}
