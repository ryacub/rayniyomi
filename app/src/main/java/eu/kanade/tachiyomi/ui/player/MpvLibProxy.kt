package eu.kanade.tachiyomi.ui.player

import `is`.xyz.mpv.MPVLib

/** Abstraction over MPVLib to allow mocking in unit tests. */
internal interface MPVLibProxy {
    fun setPropertyString(property: String, value: String)
    fun getPropertyString(property: String): String?
    fun command(args: Array<out String>)
}

/** Real implementation delegating directly to the MPVLib JNI class. */
internal class RealMPVLibProxy : MPVLibProxy {
    override fun setPropertyString(property: String, value: String) = MPVLib.setPropertyString(property, value)
    override fun getPropertyString(property: String): String? = MPVLib.getPropertyString(property)
    override fun command(args: Array<out String>) = MPVLib.command(args)
}
