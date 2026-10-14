package eu.kanade.tachiyomi.network.interceptor

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import android.widget.Toast
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.util.system.isOutdated
import eu.kanade.tachiyomi.util.system.toast
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class CloudflareInterceptor(
    private val context: Context,
    private val cookieManager: AndroidCookieJar,
    defaultUserAgentProvider: () -> String,
) : WebViewInterceptor(context, defaultUserAgentProvider) {

    private val executor = ContextCompat.getMainExecutor(context)

    private val hostChallenges = ConcurrentHashMap<String, HostChallenge>()

    override fun shouldIntercept(response: Response): Boolean {
        // https://developers.cloudflare.com/cloudflare-challenges/challenge-types/challenge-pages/detect-response/
        return response.header("cf-mitigated") == "challenge" && response.header("Server") in SERVER_CHECK
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = hostChallenges.computeIfAbsent(request.url.host) { HostChallenge() }
        val attemptsBeforeRequest = host.attemptCount()

        val response = chain.proceed(request)
        if (!shouldIntercept(response)) return response

        val attempted = host.solveUnlessAttemptedSince(attemptsBeforeRequest) {
            prepareWebView().also { if (it) solve(request, response) }
        }
        if (!attempted) return response

        response.close()
        val retried = chain.proceed(request)
        if (!shouldIntercept(retried)) return retried

        retried.close()
        throw bypassFailure(CloudflareBypassException())
    }

    private fun bypassFailure(cause: CloudflareBypassException): IOException {
        return IOException(context.stringResource(MR.strings.information_cloudflare_bypass_failure), cause)
    }

    override fun intercept(chain: Interceptor.Chain, request: Request, response: Response): Response {
        solve(request, response)
        return chain.proceed(request)
    }

    private fun clearance(url: HttpUrl): String? {
        return cookieManager.get(url).firstOrNull { it.name == "cf_clearance" }?.value
    }

    private fun solve(request: Request, response: Response) {
        try {
            response.close()
            val clearance = clearance(request.url)
            cookieManager.remove(request.url, COOKIE_NAMES, 0)
            resolveWithWebView(request, clearance)
        }
        // Because OkHttp's enqueue only handles IOExceptions, wrap the exception so that
        // we don't crash the entire app
        catch (e: CloudflareBypassException) {
            throw bypassFailure(e)
        } catch (e: Exception) {
            throw IOException(e)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun resolveWithWebView(originalRequest: Request, originalClearance: String?) {
        val client = CloudflareChallengeClient(challengeUrl(originalRequest)) {
            val clearance = clearance(originalRequest.url)
            !clearance.isNullOrBlank() && clearance != originalClearance
        }
        val headers = parseHeaders(originalRequest.headers)
        var webview: WebView? = null

        executor.execute {
            webview = createWebView(originalRequest).also {
                client.attach(it, originalRequest.url.origin())
                it.loadUrl(client.challengeUrl, headers)
            }
        }

        client.latch.awaitFor30Seconds()
        val solved = client.isSolved

        executor.execute {
            val view = webview ?: return@execute
            if (!solved && !client.isRenderProcessGone && view.isOutdated()) {
                context.toast(MR.strings.information_webview_outdated, Toast.LENGTH_LONG)
            }
            client.detach(view)
        }

        if (!solved) {
            // A clearance from a failed challenge would only be rejected again
            cookieManager.remove(originalRequest.url, COOKIE_NAMES, 0)
            throw CloudflareBypassException()
        }
    }
}

internal class HostChallenge {

    // Held across chain.proceed, the lock deadlocks with a same-host request made on another thread
    private val lock = ReentrantLock()

    @Volatile
    private var attempts = 0

    fun attemptCount(): Int = attempts

    fun solveUnlessAttemptedSince(attemptsBefore: Int, solve: () -> Boolean): Boolean = lock.withLock {
        if (attempts != attemptsBefore) return true
        try {
            solve().also { if (it) attempts++ }
        } catch (e: Exception) {
            attempts++
            throw e
        }
    }
}

// A WebView loads only GET, and the clearance covers the whole site, so other methods solve on the site's root
internal fun challengeUrl(request: Request): String {
    if (request.method == "GET") return request.url.toString()
    return request.url.newBuilder().encodedPath("/").query(null).fragment(null).build().toString()
}

// An origin rule without a port only matches the scheme's default one, and an IPv6 host needs its brackets
internal fun HttpUrl.origin(): String {
    return newBuilder().username("").password("").encodedPath("/").query(null).fragment(null).build()
        .toString()
        .removeSuffix("/")
}

private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
private val COOKIE_NAMES = listOf("cf_clearance")

class CloudflareBypassException : Exception()
