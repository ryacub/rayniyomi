package xyz.rayniyomi.cloudstream.host

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle

/**
 * Runs a plugin's activity callbacks on host threads. When one throws, all of them stop for good: the
 * Application may still dispatch the current event to guards it copied before the detach.
 */
internal class PluginCallbacks(
    private val register: (ActivityLifecycleCallbacks) -> Unit,
    private val unregister: (ActivityLifecycleCallbacks) -> Unit,
    private val onFailure: (Throwable) -> Unit,
) {
    private val guards = LinkedHashMap<ActivityLifecycleCallbacks, ActivityLifecycleCallbacks>()

    @Volatile
    private var detached = false

    fun add(callback: ActivityLifecycleCallbacks) {
        synchronized(guards) {
            if (detached || callback in guards) return
            val guard = Guard(callback)
            guards[callback] = guard
            register(guard)
        }
    }

    fun remove(callback: ActivityLifecycleCallbacks) {
        synchronized(guards) { guards.remove(callback)?.let(unregister) }
    }

    fun detachAll() {
        detach()
    }

    private fun detach(): Boolean = synchronized(guards) {
        val first = !detached
        detached = true
        guards.values.forEach(unregister)
        guards.clear()
        first
    }

    private inline fun guarded(block: () -> Unit) {
        if (detached) return
        try {
            block()
        } catch (e: Throwable) {
            if (detach()) runCatching { onFailure(e) }
        }
    }

    private inner class Guard(private val delegate: ActivityLifecycleCallbacks) : ActivityLifecycleCallbacks {
        override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) =
            guarded { delegate.onActivityPreCreated(activity, savedInstanceState) }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) =
            guarded { delegate.onActivityCreated(activity, savedInstanceState) }

        override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) =
            guarded { delegate.onActivityPostCreated(activity, savedInstanceState) }

        override fun onActivityPreStarted(activity: Activity) = guarded { delegate.onActivityPreStarted(activity) }
        override fun onActivityStarted(activity: Activity) = guarded { delegate.onActivityStarted(activity) }
        override fun onActivityPostStarted(activity: Activity) = guarded { delegate.onActivityPostStarted(activity) }
        override fun onActivityPreResumed(activity: Activity) = guarded { delegate.onActivityPreResumed(activity) }
        override fun onActivityResumed(activity: Activity) = guarded { delegate.onActivityResumed(activity) }
        override fun onActivityPostResumed(activity: Activity) = guarded { delegate.onActivityPostResumed(activity) }
        override fun onActivityPrePaused(activity: Activity) = guarded { delegate.onActivityPrePaused(activity) }
        override fun onActivityPaused(activity: Activity) = guarded { delegate.onActivityPaused(activity) }
        override fun onActivityPostPaused(activity: Activity) = guarded { delegate.onActivityPostPaused(activity) }
        override fun onActivityPreStopped(activity: Activity) = guarded { delegate.onActivityPreStopped(activity) }
        override fun onActivityStopped(activity: Activity) = guarded { delegate.onActivityStopped(activity) }
        override fun onActivityPostStopped(activity: Activity) = guarded { delegate.onActivityPostStopped(activity) }

        override fun onActivityPreSaveInstanceState(activity: Activity, outState: Bundle) =
            guarded { delegate.onActivityPreSaveInstanceState(activity, outState) }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) =
            guarded { delegate.onActivitySaveInstanceState(activity, outState) }

        override fun onActivityPostSaveInstanceState(activity: Activity, outState: Bundle) =
            guarded { delegate.onActivityPostSaveInstanceState(activity, outState) }

        override fun onActivityPreDestroyed(activity: Activity) = guarded { delegate.onActivityPreDestroyed(activity) }
        override fun onActivityDestroyed(activity: Activity) = guarded { delegate.onActivityDestroyed(activity) }
        override fun onActivityPostDestroyed(activity: Activity) =
            guarded { delegate.onActivityPostDestroyed(activity) }
    }
}
