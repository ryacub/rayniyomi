package xyz.rayniyomi.cloudstream.host

/** System properties preserve observer ownership across extension class-loader replacements. */
internal object ProcessSlot {

    fun <T : Any> replace(key: String, value: T, release: (Any) -> Unit, install: (T) -> Unit) {
        val properties = System.getProperties()
        synchronized(properties) {
            properties.put(key, value)?.let(release)
            install(value)
        }
    }
}
