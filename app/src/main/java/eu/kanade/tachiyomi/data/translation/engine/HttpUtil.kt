package eu.kanade.tachiyomi.data.translation.engine

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Execute an OkHttp request asynchronously with coroutine cancellation support.
 *
 * When the coroutine is cancelled, the underlying HTTP call is also cancelled,
 * preventing wasted API credits from in-flight requests.
 */
internal suspend fun executeAsync(client: OkHttpClient, request: Request): String {
    return suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) {
                    continuation.resumeWithException(e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string()
                        ?: throw IOException("Empty response body")
                    if (!response.isSuccessful) {
                        throw IOException("API error ${response.code}: $body")
                    }
                    continuation.resume(body)
                } catch (e: Exception) {
                    if (!continuation.isCancelled) {
                        continuation.resumeWithException(e)
                    }
                }
            }
        })
    }
}
