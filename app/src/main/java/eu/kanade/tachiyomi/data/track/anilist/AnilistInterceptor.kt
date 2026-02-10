package eu.kanade.tachiyomi.data.track.anilist

import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.track.anilist.dto.ALOAuth
import eu.kanade.tachiyomi.data.track.anilist.dto.isExpired
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class AnilistInterceptor(val anilist: Anilist, private var token: String?) : Interceptor {

    /**
     * OAuth object used for authenticated requests.
     *
     * Anilist returns the date without milliseconds. We fix that and make the token expire 1 minute
     * before its original expiration date.
     */
    private var oauth: ALOAuth? = null
        set(value) {
            field = value?.copy(expires = value.expires * 1000 - 60 * 1000)
        }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        if (token.isNullOrEmpty()) {
            throw IOException("Not authenticated with AniList")
        }
        if (oauth == null) {
            oauth = anilist.loadOAuth()
        }

        val currentOAuth = oauth
        // Refresh access token if null or expired.
        if (currentOAuth == null) {
            throw IOException("AniList: No authentication token")
        }
        if (currentOAuth.isExpired()) {
            anilist.logout()
            throw IOException("AniList: Login has expired")
        }

        // Add the authorization header to the original request.
        val authRequest = originalRequest.newBuilder()
            .addHeader("Authorization", "Bearer ${currentOAuth.accessToken}")
            .header("User-Agent", "Aniyomi v${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
            .build()

        val response = chain.proceed(authRequest)

        // Handle rate limiting with single retry and server-specified backoff
        if (response.code == 429) {
            val retryAfter = response.header("Retry-After")?.toLongOrNull() ?: RATE_LIMIT_DEFAULT_WAIT_SECONDS
            response.close()
            Thread.sleep(retryAfter.coerceAtMost(MAX_RETRY_WAIT_SECONDS) * 1000)
            return chain.proceed(authRequest)
        }

        return response
    }

    /**
     * Called when the user authenticates with Anilist for the first time. Sets the refresh token
     * and the oauth object.
     */
    fun setAuth(oauth: ALOAuth?) {
        token = oauth?.accessToken
        this.oauth = oauth
        anilist.saveOAuth(oauth)
    }

    companion object {
        private const val RATE_LIMIT_DEFAULT_WAIT_SECONDS = 5L
        private const val MAX_RETRY_WAIT_SECONDS = 60L
    }
}
