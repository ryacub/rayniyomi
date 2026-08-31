package eu.kanade.tachiyomi.ui.player.cast

import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.TreeMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.MILLISECONDS

/** Serves header-dependent media to the Cast receiver through the local network. */
class CastStreamProxy(
    private val client: OkHttpClient,
    private val addressProvider: () -> InetAddress? = ::findLocalAddress,
    private val tokenProvider: () -> String = { UUID.randomUUID().toString() },
) : Closeable {

    private val upstreamClient = client.newBuilder()
        .cache(null)
        .callTimeout(0, MILLISECONDS)
        .build()
    private val lock = Any()
    private var server: ProxyServer? = null

    fun urlFor(
        videoUrl: String,
        headers: Headers,
    ): String {
        val address = addressProvider()
            ?: error("Cannot cast a protected stream without a reachable local address")
        val route = ProxyRoute(videoUrl, headers)

        return synchronized(lock) {
            val activeServer = server ?: ProxyServer(address, upstreamClient).also {
                it.start()
                server = it
            }
            val token = tokenProvider()
            activeServer.replaceRoute(token, route)
            "http://${address.hostAddress}:${activeServer.port}/cast/$token"
        }
    }

    fun stop() {
        synchronized(lock) {
            server?.close()
            server = null
        }
    }

    override fun close() = stop()

    private data class ProxyRoute(
        val url: String,
        val headers: Headers,
    )

    private class ProxyServer(
        address: InetAddress,
        private val client: OkHttpClient,
    ) : Closeable {

        private val routes = ConcurrentHashMap<String, ProxyRoute>()
        private val calls = ConcurrentHashMap.newKeySet<okhttp3.Call>()
        private val sockets = ConcurrentHashMap.newKeySet<Socket>()
        private val executor = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "CastStreamProxy").apply { isDaemon = true }
        }
        private val socket = ServerSocket(0, 16, address)

        val port: Int
            get() = socket.localPort

        fun start() {
            executor.execute {
                while (!socket.isClosed) {
                    try {
                        val clientSocket = socket.accept()
                        executor.execute { handle(clientSocket) }
                    } catch (_: IOException) {
                        if (!socket.isClosed) break
                    }
                }
            }
        }

        fun replaceRoute(token: String, route: ProxyRoute) {
            routes.clear()
            routes[token] = route
        }

        override fun close() {
            routes.clear()
            calls.forEach { it.cancel() }
            calls.clear()
            sockets.forEach { it.close() }
            sockets.clear()
            socket.close()
            executor.shutdownNow()
        }

        private fun handle(socket: Socket) {
            sockets += socket
            socket.use { clientSocket ->
                try {
                    clientSocket.soTimeout = 15_000
                    val reader = BufferedReader(
                        InputStreamReader(clientSocket.getInputStream(), StandardCharsets.ISO_8859_1),
                    )
                    val requestLine = reader.readLine() ?: return
                    val requestParts = requestLine.split(' ', limit = 3)
                    if (requestParts.size != 3) {
                        writeError(clientSocket.getOutputStream(), 400, "Bad Request")
                        return
                    }

                    val requestHeaders = TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER)
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        val separator = line.indexOf(':')
                        if (separator > 0) {
                            requestHeaders[line.substring(0, separator).trim()] =
                                line.substring(separator + 1).trim()
                        }
                    }

                    val method = requestParts[0]
                    if (method != "GET" && method != "HEAD") {
                        writeError(clientSocket.getOutputStream(), 405, "Method Not Allowed")
                        return
                    }

                    val path = requestParts[1].substringBefore('?')
                    val token = path.removePrefix("/cast/")
                    val route = routes[token]
                    if (!path.startsWith("/cast/") || route == null || token.isEmpty() || token.contains('/')) {
                        writeError(clientSocket.getOutputStream(), 404, "Not Found")
                        return
                    }

                    val requestBuilder = Request.Builder().url(route.url)
                    route.headers.forEach { header ->
                        val name = header.first
                        val value = header.second
                        if (name.lowercase() !in HOP_BY_HOP_HEADERS) {
                            requestBuilder.header(name, value)
                        }
                    }
                    requestHeaders["Range"]?.let { requestBuilder.header("Range", it) }
                    if (method == "HEAD") requestBuilder.head() else requestBuilder.get()

                    val call = client.newCall(requestBuilder.build())
                    calls += call
                    try {
                        call.execute().use { response ->
                            writeResponse(clientSocket.getOutputStream(), response, method == "HEAD")
                        }
                    } finally {
                        calls -= call
                    }
                } catch (_: Exception) {
                    // The receiver can close its socket while the upstream stream is active.
                } finally {
                    sockets -= socket
                }
            }
        }

        private fun writeResponse(
            output: OutputStream,
            response: okhttp3.Response,
            headOnly: Boolean,
        ) {
            output.write(
                "HTTP/1.1 ${response.code} ${response.message}\r\n".toByteArray(StandardCharsets.ISO_8859_1),
            )
            response.headers.forEach { header ->
                val name = header.first
                val value = header.second
                if (name.lowercase() !in HOP_BY_HOP_HEADERS && name.lowercase() != "content-length") {
                    output.write("$name: $value\r\n".toByteArray(StandardCharsets.ISO_8859_1))
                }
            }
            response.body.contentLength().takeIf { it >= 0 }?.let {
                output.write("Content-Length: $it\r\n".toByteArray(StandardCharsets.ISO_8859_1))
            }
            output.write("Connection: close\r\n\r\n".toByteArray(StandardCharsets.ISO_8859_1))
            if (!headOnly) {
                response.body.byteStream().use { input -> input.copyTo(output) }
            }
            output.flush()
        }

        private fun writeError(output: OutputStream, code: Int, message: String) {
            val body = "$code $message\n".toByteArray(StandardCharsets.UTF_8)
            output.write(
                "HTTP/1.1 $code $message\r\nContent-Type: text/plain\r\n".toByteArray(
                    StandardCharsets.ISO_8859_1,
                ),
            )
            output.write(
                "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray(
                    StandardCharsets.ISO_8859_1,
                ),
            )
            output.write(body)
            output.flush()
        }
    }

    companion object {
        private val HOP_BY_HOP_HEADERS = setOf(
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            "transfer-encoding",
            "upgrade",
        )

        private fun findLocalAddress(): InetAddress? {
            return try {
                val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    if (!networkInterface.isUp || networkInterface.isLoopback) continue
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (address is Inet4Address && address.isSiteLocalAddress && !address.isLinkLocalAddress) {
                            return address
                        }
                    }
                }
                null
            } catch (_: IOException) {
                null
            }
        }
    }
}
