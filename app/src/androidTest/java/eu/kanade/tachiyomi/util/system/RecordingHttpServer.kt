package eu.kanade.tachiyomi.util.system

import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * A single-purpose loopback HTTP server used to observe the headers a client actually puts on the
 * wire. Loopback matters: Chromium treats `127.0.0.1` as a trustworthy origin, so it sends the
 * `Sec-CH-UA` client hints that this test exists to check.
 */
class RecordingHttpServer(private val body: String = "<html><body>ok</body></html>") : Closeable {

    private val serverSocket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    private val recorded = CopyOnWriteArrayList<RecordedRequest>()
    private val firstRequest = CountDownLatch(1)

    @Volatile
    private var running = true

    val url: String get() = "http://127.0.0.1:${serverSocket.localPort}/"

    init {
        thread(isDaemon = true, name = "RecordingHttpServer") {
            while (running) {
                val socket = try {
                    serverSocket.accept()
                } catch (_: Exception) {
                    break
                }
                thread(isDaemon = true) { serve(socket) }
            }
        }
    }

    private fun serve(socket: Socket) {
        socket.use {
            val reader = BufferedReader(InputStreamReader(it.getInputStream()))
            val requestLine = reader.readLine() ?: return
            val headers = buildMap {
                while (true) {
                    val line = reader.readLine()
                    if (line.isNullOrEmpty()) break
                    val separator = line.indexOf(':')
                    if (separator > 0) {
                        put(
                            line.substring(0, separator).trim().lowercase(),
                            line.substring(separator + 1).trim(),
                        )
                    }
                }
            }

            recorded += RecordedRequest(requestLine, headers)
            firstRequest.countDown()

            val bytes = body.toByteArray()
            it.getOutputStream().apply {
                write(
                    (
                        "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/html; charset=utf-8\r\n" +
                            "Content-Length: ${bytes.size}\r\n" +
                            "Connection: close\r\n\r\n"
                        ).toByteArray(),
                )
                write(bytes)
                flush()
            }
        }
    }

    fun awaitFirstRequest(timeoutSeconds: Long = 30): RecordedRequest {
        check(firstRequest.await(timeoutSeconds, TimeUnit.SECONDS)) {
            "No request reached the test server within ${timeoutSeconds}s"
        }
        return recorded.first()
    }

    override fun close() {
        running = false
        runCatching { serverSocket.close() }
    }

    data class RecordedRequest(
        val requestLine: String,
        val headers: Map<String, String>,
    ) {
        val userAgent: String? get() = headers["user-agent"]
        val secChUa: String? get() = headers["sec-ch-ua"]
    }
}
