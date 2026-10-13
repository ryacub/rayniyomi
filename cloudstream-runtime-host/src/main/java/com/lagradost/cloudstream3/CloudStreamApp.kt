package com.lagradost.cloudstream3

import android.content.Context
import com.lagradost.api.setContext
import java.lang.ref.WeakReference

/** Same public signatures as the members of CloudStream's CloudStreamApp that plugins call. */
class CloudStreamApp {
    companion object {
        private var contextReference: WeakReference<Context>? = null

        var context: Context?
            get() = contextReference?.get()
            internal set(value) {
                contextReference = value?.let(::WeakReference)
                setContext(value)
            }
    }
}
