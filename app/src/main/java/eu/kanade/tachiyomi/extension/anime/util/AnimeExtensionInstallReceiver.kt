package eu.kanade.tachiyomi.extension.anime.util

import android.content.Context
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.anime.model.AnimeLoadResult
import eu.kanade.tachiyomi.extension.util.BaseExtensionInstallReceiver
import eu.kanade.tachiyomi.extension.util.NotifyActions
import eu.kanade.tachiyomi.extension.util.notify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Broadcast receiver that listens for the system's packages installed, updated or removed, and only
 * notifies the given [listener] when the package is an anime extension.
 *
 * @param listener The listener that should be notified of extension installation events.
 * @param scope The scope that owns the receiver's background work. [unregister] provides
 * best-effort quiescence: it prevents future deliveries and cancels pending work at suspension
 * points, but does not synchronously stop an already-running callback past a suspension point.
 */
internal class AnimeExtensionInstallReceiver(
    listener: Listener,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : BaseExtensionInstallReceiver<AnimeExtensionInstallReceiver.Listener, AnimeLoadResult>(
    listener,
    scope,
    ACTIONS,
) {

    override fun isError(result: AnimeLoadResult): Boolean = result is AnimeLoadResult.Error

    override suspend fun load(context: Context, pkgName: String?): AnimeLoadResult =
        pkgName?.let { AnimeExtensionLoader.loadExtensionFromPkgName(context, it) }
            ?: AnimeLoadResult.Error("Failed to load extension: package name is missing")

    override fun dispatchInstalled(listener: Listener, result: AnimeLoadResult) {
        if (result is AnimeLoadResult.Success) listener.onExtensionInstalled(result.extension)
    }

    override fun dispatchUpdated(listener: Listener, result: AnimeLoadResult) {
        if (result is AnimeLoadResult.Success) listener.onExtensionUpdated(result.extension)
    }

    override fun dispatchUntrusted(listener: Listener, result: AnimeLoadResult) {
        if (result is AnimeLoadResult.Untrusted) listener.onExtensionUntrusted(result.extension)
    }

    override fun dispatchError(listener: Listener, result: AnimeLoadResult) {
        if (result is AnimeLoadResult.Error) listener.onExtensionLoadError(result.message)
    }

    override fun dispatchRemoved(listener: Listener, pkgName: String) {
        listener.onPackageUninstalled(pkgName)
    }

    /**
     * Listener that receives extension installation events.
     */
    interface Listener {
        fun onExtensionInstalled(extension: AnimeExtension.Installed)
        fun onExtensionUpdated(extension: AnimeExtension.Installed)
        fun onExtensionUntrusted(extension: AnimeExtension.Untrusted)
        fun onExtensionLoadError(message: String)
        fun onPackageUninstalled(pkgName: String)
    }

    companion object {
        private val ACTIONS = NotifyActions(
            added = ACTION_EXTENSION_ADDED,
            replaced = ACTION_EXTENSION_REPLACED,
            removed = ACTION_EXTENSION_REMOVED,
        )

        private const val ACTION_EXTENSION_ADDED = "${BuildConfig.APPLICATION_ID}.ACTION_EXTENSION_ADDED"
        private const val ACTION_EXTENSION_REPLACED = "${BuildConfig.APPLICATION_ID}.ACTION_EXTENSION_REPLACED"
        private const val ACTION_EXTENSION_REMOVED = "${BuildConfig.APPLICATION_ID}.ACTION_EXTENSION_REMOVED"

        fun notifyAdded(context: Context, pkgName: String) {
            notify(context, pkgName, ACTION_EXTENSION_ADDED)
        }

        fun notifyReplaced(context: Context, pkgName: String) {
            notify(context, pkgName, ACTION_EXTENSION_REPLACED)
        }

        fun notifyRemoved(context: Context, pkgName: String) {
            notify(context, pkgName, ACTION_EXTENSION_REMOVED)
        }
    }
}
