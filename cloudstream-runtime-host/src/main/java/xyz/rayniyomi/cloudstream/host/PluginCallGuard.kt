package xyz.rayniyomi.cloudstream.host

import kotlin.coroutines.cancellation.CancellationException

class PluginCallException(message: String, cause: Throwable) : Exception(message, cause)

/** Plugins throw errors such as NotImplementedError that the host's Exception handlers do not catch. */
suspend fun <T> guardPluginCall(sourceName: String, block: suspend () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    throw PluginCallException("$sourceName: ${e.message ?: e.javaClass.simpleName}", e)
}

/**
 * Runs a plugin's link loading. An ordinary error after [hasLinks] became true is returned instead
 * of thrown, so the links emitted before it are kept. Cancellation always propagates.
 */
suspend fun guardLinkLoading(
    sourceName: String,
    hasLinks: () -> Boolean,
    block: suspend () -> Unit,
): PluginCallException? = try {
    guardPluginCall(sourceName, block)
    null
} catch (e: PluginCallException) {
    if (hasLinks()) e else throw e
}
