package xyz.rayniyomi.cloudstream.host

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.UnsafeSSL
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.insecureApp
import eu.kanade.tachiyomi.network.NetworkHelper

internal object CloudStreamRuntime {

    private const val ACTIVITY_CALLBACKS_SLOT = "xyz.rayniyomi.cloudstream.activityCallbacks"

    // insecureApp gets the host client too, so it keeps the host's certificate checks.
    @OptIn(UnsafeSSL::class)
    fun start(application: Application, network: NetworkHelper) {
        CloudStreamApp.context = application
        val headers = mapOf("user-agent" to network.defaultUserAgentProvider())
        listOf(app, insecureApp).forEach {
            it.baseClient = network.client
            it.defaultHeaders = headers
        }
        ProcessSlot.replace(
            ACTIVITY_CALLBACKS_SLOT,
            ResumedActivityTracker(),
            release = {
                application.unregisterActivityLifecycleCallbacks(it as Application.ActivityLifecycleCallbacks)
            },
            install = application::registerActivityLifecycleCallbacks,
        )
    }

    private class ResumedActivityTracker : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            CommonActivity.activity = activity
        }

        override fun onActivityPaused(activity: Activity) {
            if (CommonActivity.activity === activity) CommonActivity.activity = null
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
