package xyz.rayniyomi.cloudstream.host

import android.app.Application
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import xyz.rayniyomi.cloudstream.contract.PluginContract

/** Reloads the runtime extension in the host when the manager changes the plugin list. */
internal object RefreshObserver {

    private const val OBSERVER_SLOT = "xyz.rayniyomi.cloudstream.refreshObserver"
    private const val DELAY_MS = 500L

    fun register(application: Application, runtimePackage: String) {
        val handler = Handler(Looper.getMainLooper())
        val coalescer = RefreshCoalescer(DELAY_MS, HandlerScheduler(handler)) {
            application.sendBroadcast(
                Intent(
                    PluginContract.extensionReplacedAction(application.packageName),
                    Uri.parse("package:$runtimePackage"),
                )
                    .setPackage(application.packageName),
            )
        }
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) = coalescer.trigger()
        }
        val resolver = application.contentResolver
        ProcessSlot.replace(
            OBSERVER_SLOT,
            observer,
            release = { resolver.unregisterContentObserver(it as ContentObserver) },
            install = { resolver.registerContentObserver(pluginsUri(runtimePackage), true, it) },
        )
    }

    private class HandlerScheduler(private val handler: Handler) : RefreshCoalescer.Scheduler {
        override fun schedule(delayMs: Long, task: Runnable) {
            handler.postDelayed(task, delayMs)
        }

        override fun cancel(task: Runnable) {
            handler.removeCallbacks(task)
        }
    }
}
