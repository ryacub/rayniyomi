package xyz.rayniyomi.cloudstream.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer

class OkHttpFetcher(
    private val client: OkHttpClient,
    private val maxBytes: Long = 32L * 1024L * 1024L,
) : PluginManager.Fetcher {

    override suspend fun text(url: String): String = get(url).readUtf8()

    override suspend fun bytes(url: String): ByteArray = get(url).readByteArray()

    private suspend fun get(url: String): Buffer = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code} for $url" }
            check(response.body.contentLength() <= maxBytes) { "$url is larger than $maxBytes bytes" }
            val buffer = Buffer()
            val source = response.body.source()
            while (source.read(buffer, BUFFER_SIZE) != -1L) {
                check(buffer.size <= maxBytes) { "$url is larger than $maxBytes bytes" }
            }
            buffer
        }
    }

    private companion object {
        const val BUFFER_SIZE = 8192L
    }
}
