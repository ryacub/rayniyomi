package com.lagradost.cloudstream3.plugins

import android.content.Context
import android.content.res.Resources

/** Same public signatures as CloudStream's app-module Plugin, which .cs3 plugins extend. */
abstract class Plugin : BasePlugin() {

    @Throws(Throwable::class)
    open fun load(context: Context) {
        load()
    }

    var resources: Resources? = null

    var openSettings: ((context: Context) -> Unit)? = null
}
