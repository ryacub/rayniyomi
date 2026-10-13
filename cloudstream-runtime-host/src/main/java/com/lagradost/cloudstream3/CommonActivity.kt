package com.lagradost.cloudstream3

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.lang.ref.WeakReference

/** Same public signatures as the members of CloudStream's CommonActivity that plugins call. */
object CommonActivity {

    private var activityReference: WeakReference<Activity>? = null

    var activity: Activity?
        get() = activityReference?.get()
        set(value) {
            activityReference = value?.let(::WeakReference)
        }

    fun showToast(message: String?, duration: Int? = null) {
        val context = CloudStreamApp.context ?: return
        if (message.isNullOrBlank()) return
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, duration ?: Toast.LENGTH_SHORT).show()
        }
    }
}
