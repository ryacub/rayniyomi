package xyz.rayniyomi.cloudstream.host

import android.app.Application
import android.content.ComponentCallbacks
import android.content.Context

/** The Application a plugin sees. Its activity callbacks go through [PluginCallbacks]. */
internal class PluginApplication(
    private val base: Application,
    private val callbacks: PluginCallbacks,
) : Application() {

    init {
        attachBaseContext(base)
    }

    override fun getApplicationContext(): Context = this

    override fun registerActivityLifecycleCallbacks(callback: ActivityLifecycleCallbacks) = callbacks.add(callback)

    override fun unregisterActivityLifecycleCallbacks(callback: ActivityLifecycleCallbacks) =
        callbacks.remove(callback)

    override fun registerComponentCallbacks(callback: ComponentCallbacks) = base.registerComponentCallbacks(callback)

    override fun unregisterComponentCallbacks(callback: ComponentCallbacks) =
        base.unregisterComponentCallbacks(callback)
}
