package eu.kanade.tachiyomi.data.track.myanimelist

import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALOAuth
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.io.IOException

class MyAnimeListInterceptor(private val myanimelist: MyAnimeList) : Interceptor {

    private val json: Json by injectLazy()

    private var oauth: MALOAuth? = myanimelist.loadOAuth()
    private val tokenExpired get() = myanimelist.getIfAuthExpired()

    override fun intercept(chain: Interceptor.Chain): Response {
        if (tokenExpired) {
            throw MALTokenExpired()
        }
        val originalRequest = chain.request()

        if (oauth?.isExpired() == true) {
            refreshToken(chain)
        }

        if (oauth == null) {
            throw IOException("MAL: User is not authenticated")
        }

        // Add the authorization header to the original request
        val authRequest = originalRequest.newBuilder()
            .addHeader("Authorization", "Bearer ${oauth!!.accessToken}")
            .build()

        val response = chain.proceed(authRequest)

        // Handle rate limiting with retry and backoff
        if (response.code == 429) {
            response.close()
            val retryAfter = response.header("Retry-After")?.toLongOrNull() ?: RATE_LIMIT_DEFAULT_WAIT_SECONDS
            val waitMs = retryAfter * 1000
            Thread.sleep(waitMs)
            return chain.proceed(authRequest)
        }

        return response
    }

    /**
     * Called when the user authenticates with MyAnimeList for the first time. Sets the refresh token
     * and the oauth object.
     */
    fun setAuth(oauth: MALOAuth?) {
        this.oauth = oauth
        myanimelist.saveOAuth(oauth)
    }

    private fun refreshToken(chain: Interceptor.Chain): MALOAuth = synchronized(this) {
        if (tokenExpired) throw MALTokenExpired()
        oauth?.takeUnless { it.isExpired() }?.let { return@synchronized it }

        val currentOAuth = oauth ?: throw MALTokenRefreshFailed()

        val response = try {
            chain.proceed(MyAnimeListApi.refreshTokenRequest(currentOAuth))
        } catch (e: IOException) {
            throw MALTokenRefreshFailed()
        }

        if (response.code == 401) {
            response.close()
            myanimelist.setAuthExpired()
            throw MALTokenExpired()
        }

        return runCatching {
            if (response.isSuccessful) {
                with(json) { response.parseAs<MALOAuth>() }
            } else {
                response.close()
                null
            }
        }
            .getOrNull()
            ?.also {
                this.oauth = it
                myanimelist.saveOAuth(it)
            }
            ?: throw MALTokenRefreshFailed()
    }

    companion object {
        private const val RATE_LIMIT_DEFAULT_WAIT_SECONDS = 5L
    }
}

class MALTokenRefreshFailed : IOException("MAL: Failed to refresh account token")
class MALTokenExpired : IOException("MAL: Login has expired")
