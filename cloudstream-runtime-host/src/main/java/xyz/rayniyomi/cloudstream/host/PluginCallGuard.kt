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
