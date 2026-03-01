package eu.kanade.tachiyomi.data.track

import kotlinx.coroutines.delay
import okhttp3.Response

private const val HTTP_TOO_MANY_REQUESTS = 429
private const val RATE_LIMIT_DEFAULT_WAIT_SECONDS = 5L
private const val MAX_RETRY_WAIT_SECONDS = 60L

internal suspend fun retryOnceOn429Result(request: suspend () -> Response): Result<Response> {
    return runCatching {
        val firstResponse = request()
        if (firstResponse.code != HTTP_TOO_MANY_REQUESTS) {
            return@runCatching firstResponse
        }

        val retryAfterSeconds = firstResponse.header("Retry-After")
            ?.toLongOrNull()
            ?.coerceIn(0L, MAX_RETRY_WAIT_SECONDS)
            ?: RATE_LIMIT_DEFAULT_WAIT_SECONDS

        firstResponse.close()
        delay(retryAfterSeconds * 1000)

        request()
    }
}

internal suspend fun retryOnceOn429OrThrow(request: suspend () -> Response): Response {
    return retryOnceOn429Result(request).getOrThrow()
}
