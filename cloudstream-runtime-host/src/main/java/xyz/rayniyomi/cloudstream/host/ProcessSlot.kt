package xyz.rayniyomi.cloudstream.host

/**
 * Each host reload creates a new class loader, so static fields do not survive it.
 * System properties are process-wide, so they hold the one live listener of each kind.
 */
internal object ProcessSlot {

    fun <T : Any> replace(key: String, value: T, release: (Any) -> Unit, install: (T) -> Unit) {
        val properties = System.getProperties()
        synchronized(properties) {
            properties.put(key, value)?.let(release)
            install(value)
        }
    }
}
