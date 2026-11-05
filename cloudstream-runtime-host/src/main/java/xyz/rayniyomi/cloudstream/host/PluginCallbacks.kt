package xyz.rayniyomi.cloudstream.host

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle

/** Runs a plugin's activity callbacks on host threads, and detaches all of them when one throws. */
internal class PluginCallbacks(
    private val register: (ActivityLifecycleCallbacks) -> Unit,
    private val unregister: (ActivityLifecycleCallbacks) -> Unit,
    private val onFailure: (Throwable) -> Unit,
) {
    private val guards = LinkedHashMap<ActivityLifecycleCallbacks, ActivityLifecycleCallbacks>()

    fun add(callback: ActivityLifecycleCallbacks) {
        val guard = Guard(callback)
        synchronized(guards) { guards[callback] = guard }
        register(guard)
    }

    fun remove(callback: ActivityLifecycleCallbacks) {
        synchronized(guards) { guards.remove(callback) }?.let(unregister)
    }

    fun detachAll() {
        val all = synchronized(guards) { guards.values.toList().also { guards.clear() } }
        all.forEach(unregister)
    }

    private inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            detachAll()
            onFailure(e)
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
