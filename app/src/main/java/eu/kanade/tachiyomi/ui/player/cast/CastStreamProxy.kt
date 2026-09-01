package eu.kanade.tachiyomi.ui.player.cast

import androidx.core.net.toUri
import com.hippo.unifile.UniFile
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
    private val localFileProvider: (String) -> UniFile? = { null },
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
        return urlFor(ProxyRoute.Remote(videoUrl, headers))
    }

    internal fun localMediaFor(videoUrl: String): LocalProxyMedia {
        val file = localFileProvider(videoUrl)
            ?.takeIf { it.exists() && it.isFile }
            ?: error("Downloaded video is not available: $videoUrl")
        val contentType = file.type?.lowercase()
            ?.takeIf { it in CASTABLE_VIDEO_TYPES }
            ?: error(
                "Cannot cast downloaded video: unsupported container " +
                    (file.type ?: file.name ?: "unknown"),
            )
        return LocalProxyMedia(
            url = urlFor(ProxyRoute.Local(file, contentType)),
            contentType = contentType,
        )
    }

    private fun urlFor(route: ProxyRoute): String {
        val address = addressProvider()
            ?: error("Cannot cast a protected stream without a reachable local address")

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

    internal data class LocalProxyMedia(
        val url: String,
        val contentType: String,
    )

    private sealed interface ProxyRoute {
        data class Remote(val url: String, val headers: Headers) : ProxyRoute
        data class Local(val file: UniFile, val contentType: String) : ProxyRoute
    }

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

                    when (route) {
                        is ProxyRoute.Remote -> {
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
                        }
                        is ProxyRoute.Local -> {
                            writeLocalResponse(
                                output = clientSocket.getOutputStream(),
                                file = route.file,
                                contentType = route.contentType,
                                rangeHeader = requestHeaders["Range"],
                                headOnly = method == "HEAD",
                            )
                        }
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

        private fun writeLocalResponse(
            output: OutputStream,
            file: UniFile,
            contentType: String,
            rangeHeader: String?,
            headOnly: Boolean,
        ) {
            val fileLength = file.length()
            val range = rangeHeader?.let { parseRange(it, fileLength) }
            if (rangeHeader != null && range == null) {
                output.write(
                    "HTTP/1.1 416 Range Not Satisfiable\r\n".toByteArray(StandardCharsets.ISO_8859_1),
                )
                output.write("Content-Range: bytes */$fileLength\r\n".toByteArray(StandardCharsets.ISO_8859_1))
                output.write("Connection: close\r\n\r\n".toByteArray(StandardCharsets.ISO_8859_1))
                output.flush()
                return
            }

            val start = range?.first ?: 0L
            val contentLength = range?.second ?: fileLength
            val status = if (range == null) "200 OK" else "206 Partial Content"
            output.write("HTTP/1.1 $status\r\n".toByteArray(StandardCharsets.ISO_8859_1))
            output.write("Content-Type: $contentType\r\n".toByteArray(StandardCharsets.ISO_8859_1))
            output.write("Accept-Ranges: bytes\r\n".toByteArray(StandardCharsets.ISO_8859_1))
            if (range != null) {
                output.write(
                    "Content-Range: bytes $start-${start + contentLength - 1}/$fileLength\r\n"
                        .toByteArray(StandardCharsets.ISO_8859_1),
                )
            }
            output.write("Content-Length: $contentLength\r\n".toByteArray(StandardCharsets.ISO_8859_1))
            output.write("Connection: close\r\n\r\n".toByteArray(StandardCharsets.ISO_8859_1))
            if (!headOnly) {
                file.openInputStream().use { input ->
                    skipFully(input, start)
                    copyBytes(input, output, contentLength)
                }
            }
            output.flush()
        }

        private fun parseRange(value: String, fileLength: Long): Pair<Long, Long>? {
            if (fileLength < 0 || !value.startsWith("bytes=") || value.drop(6).contains(',')) return null
            val range = value.substringAfter('=').split('-', limit = 2)
            if (range.size != 2 || fileLength == 0L) return null
            val startText = range[0]
            val endText = range[1]
            return when {
                startText.isEmpty() -> {
                    val suffixLength = endText.toLongOrNull()?.takeIf { it > 0 } ?: return null
                    val length = suffixLength.coerceAtMost(fileLength)
                    fileLength - length to length
                }

                else -> {
                    val start = startText.toLongOrNull()?.takeIf { it in 0 until fileLength } ?: return null
                    val end = endText.toLongOrNull()?.coerceAtMost(fileLength - 1) ?: (fileLength - 1)
                    if (end < start) null else start to (end - start + 1)
                }
            }
        }

        private fun skipFully(input: java.io.InputStream, bytes: Long) {
            var remaining = bytes
            while (remaining > 0) {
                val skipped = input.skip(remaining)
                if (skipped > 0) {
                    remaining -= skipped
                } else if (input.read() == -1) {
                    throw IOException("Downloaded video ended before the requested range")
                } else {
                    remaining--
                }
            }
        }

        private fun copyBytes(input: java.io.InputStream, output: OutputStream, byteCount: Long) {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var remaining = byteCount
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read == -1) break
                output.write(buffer, 0, read)
                remaining -= read
            }
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

        private val CASTABLE_VIDEO_TYPES = setOf(
            "video/mp4",
            "video/webm",
            "video/mp2t",
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
