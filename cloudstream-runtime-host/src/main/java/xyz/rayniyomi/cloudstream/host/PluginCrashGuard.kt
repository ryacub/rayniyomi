package xyz.rayniyomi.cloudstream.host

import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CopyOnWriteArrayList

internal class LoadedPlugin(
    val key: PluginKey,
    val path: String,
    val packageName: String,
    val owns: (String) -> Boolean,
)

/** Finds the plugin behind a crash on any thread, including errors that ART stored and rethrew later. */
internal class PluginCrashIndex {
    private val plugins = CopyOnWriteArrayList<LoadedPlugin>()

    fun add(plugin: LoadedPlugin) {
        plugins += plugin
    }

    fun find(error: Throwable): PluginKey? {
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        val pending = ArrayDeque(listOf(error))
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            if (!seen.add(current)) continue
            current.message?.let { message -> plugins.firstOrNull { it.path in message } }?.let { return it.key }
            current.stackTrace.firstNotNullOfOrNull { ownerOf(it.className) }?.let { return it.key }
            current.cause?.let(pending::addLast)
            pending.addAll(current.suppressed)
        }
        return null
    }

    private fun ownerOf(className: String): LoadedPlugin? =
        plugins
            .filter { it.packageName.isNotEmpty() && className.startsWith("${it.packageName}.") && it.owns(className) }
            .singleOrNull()
}

internal class PluginCrashGuard(
    private val index: PluginCrashIndex,
    private val quarantine: PluginQuarantine,
    private val next: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, error: Throwable) {
        runCatching { index.find(error)?.let { quarantine.mark(it, error.toString()) } }
        next?.uncaughtException(thread, error)
    }
}

private const val GUARD_SLOT = "xyz.rayniyomi.cloudstream.crashGuard"
private const val NEXT_SLOT = "xyz.rayniyomi.cloudstream.crashGuard.next"

/** Each runtime reload replaces the guard, so the handler chain does not grow across class loaders. */
internal fun installCrashGuard(index: PluginCrashIndex, quarantine: PluginQuarantine) {
    val properties = System.getProperties()
    synchronized(properties) {
        val current = Thread.getDefaultUncaughtExceptionHandler()
        val next = if (current != null && current === properties[GUARD_SLOT]) {
            properties[NEXT_SLOT] as Thread.UncaughtExceptionHandler?
        } else {
            current
        }
        val guard = PluginCrashGuard(index, quarantine, next)
        properties[GUARD_SLOT] = guard
        if (next != null) properties[NEXT_SLOT] = next else properties.remove(NEXT_SLOT)
        Thread.setDefaultUncaughtExceptionHandler(guard)
    }
}
