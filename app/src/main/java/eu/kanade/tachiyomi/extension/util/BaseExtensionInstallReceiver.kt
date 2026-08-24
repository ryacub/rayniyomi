package eu.kanade.tachiyomi.extension.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * The custom broadcast actions that identify extension installation events for one media type.
 */
internal class NotifyActions(
    val added: String,
    val replaced: String,
    val removed: String,
)

/**
 * Broadcast receiver that listens for the system's packages installed, updated or removed, and only
 * notifies the given [listener] when the package is an extension.
 *
 * Registration is programmatic only: create the receiver and call [register]. Do not declare the
 * receiver in the manifest because the framework cannot instantiate an abstract subclass without a
 * no-argument constructor.
 *
 * Subclasses provide the media-specific parts: [load] calls its loader entry point directly as an
 * object call, and the dispatch hooks narrow [R] to the concrete load results.
 *
 * @param listener The listener that should be notified of extension installation events.
 * @param scope The scope that owns the receiver's background work. [unregister] provides
 * best-effort quiescence: it prevents future deliveries and cancels pending work at suspension
 * points, but does not synchronously stop an already-running callback past a suspension point.
 * `goAsync()` is not used because its PendingResult bookkeeping adds complexity while async work
 * already escapes `onReceive` today.
 * @param actions The custom broadcast actions for the media type.
 */
internal abstract class BaseExtensionInstallReceiver<L, R>(
    private val listener: L,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val actions: NotifyActions,
) : BroadcastReceiver() {

    /**
     * Returns true when [result] is the transient error state that permits a replace retry.
     */
    protected abstract fun isError(result: R): Boolean

    /**
     * Loads the extension for [pkgName]. Implementations must call their loader entry point
     * directly as an object call so tests can mock the loader object.
     */
    protected abstract suspend fun load(context: Context, pkgName: String?): R

    /**
     * Notifies [listener] of an installation when [result] carries an installed extension.
     */
    protected abstract fun dispatchInstalled(listener: L, result: R)

    /**
     * Notifies [listener] of an update when [result] carries an installed extension.
     */
    protected abstract fun dispatchUpdated(listener: L, result: R)

    /**
     * Notifies [listener] of an untrusted extension when [result] carries one.
     */
    protected abstract fun dispatchUntrusted(listener: L, result: R)

    /**
     * Notifies [listener] that the package [pkgName] was uninstalled.
     */
    protected abstract fun dispatchRemoved(listener: L, pkgName: String)

    fun register(context: Context) {
        ContextCompat.registerReceiver(context, this, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /**
     * Unregisters this receiver and cancels its pending work.
     */
    fun unregister(context: Context) {
        runCatching { context.unregisterReceiver(this) }
        scope.cancel()
    }

    private val filter = IntentFilter().apply {
        addAction(Intent.ACTION_PACKAGE_ADDED)
        addAction(Intent.ACTION_PACKAGE_REPLACED)
        addAction(Intent.ACTION_PACKAGE_REMOVED)
        addAction(actions.added)
        addAction(actions.replaced)
        addAction(actions.removed)
        addDataScheme("package")
    }

    /**
     * Called when one of the events of the [filter] is received. When the package is an extension,
     * it's loaded in background and it notifies the [listener] when finished.
     */
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED, actions.added -> {
                if (isReplacing(intent)) return

                scope.launch {
                    val result = getExtensionFromIntent(context, intent)
                    dispatchInstalled(listener, result)
                    dispatchUntrusted(listener, result)
                }
            }
            Intent.ACTION_PACKAGE_REPLACED, actions.replaced -> {
                scope.launch {
                    val result = loadWithRetryOnReplace(context, intent)
                    dispatchUpdated(listener, result)
                    dispatchUntrusted(listener, result)
                }
            }
            Intent.ACTION_PACKAGE_REMOVED, actions.removed -> {
                if (isReplacing(intent)) return

                val pkgName = getPackageNameFromIntent(intent)
                if (pkgName != null) {
                    dispatchRemoved(listener, pkgName)
                }
            }
        }
    }

    /**
     * Returns true if this package is performing an update.
     *
     * @param intent The intent that triggered the event.
     */
    private fun isReplacing(intent: Intent): Boolean {
        return intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
    }

    /**
     * Loads an extension for a PACKAGE_REPLACED event, retrying on transient PackageManager
     * unavailability. Android's ActivityThread can report the package as REMOVED mid-replace
     * because app info isn't committed to the process cache yet; retrying after a short delay
     * lets PM settle before we give up.
     */
    private suspend fun loadWithRetryOnReplace(context: Context, intent: Intent?): R {
        repeat(REPLACE_RETRY_COUNT) { attempt ->
            val result = getExtensionFromIntent(context, intent)
            if (!isError(result)) return result
            val pkgName = getPackageNameFromIntent(intent) ?: return result
            logcat(LogPriority.WARN) {
                "Extension $pkgName not found after replace (attempt ${attempt + 1}/$REPLACE_RETRY_COUNT), retrying..."
            }
            delay(REPLACE_RETRY_DELAY_MS)
        }
        return getExtensionFromIntent(context, intent)
    }

    /**
     * Returns the extension triggered by the given intent.
     *
     * @param context The application context.
     * @param intent The intent containing the package name of the extension.
     */
    private suspend fun getExtensionFromIntent(context: Context, intent: Intent?): R {
        val pkgName = getPackageNameFromIntent(intent)
        if (pkgName == null) {
            logcat(LogPriority.WARN) { "Package name not found" }
        }
        return load(context, pkgName)
    }

    /**
     * Returns the package name of the installed, updated or removed application.
     */
    private fun getPackageNameFromIntent(intent: Intent?): String? {
        return intent?.data?.encodedSchemeSpecificPart ?: return null
    }

    companion object {
        private const val REPLACE_RETRY_COUNT = 3
        private const val REPLACE_RETRY_DELAY_MS = 500L
    }
}

/**
 * Sends the broadcast [action] for the extension package [pkgName] to this app's receivers.
 */
internal fun notify(context: Context, pkgName: String, action: String) {
    Intent(action).apply {
        data = "package:$pkgName".toUri()
        `package` = context.packageName
        context.sendBroadcast(this)
    }
}
