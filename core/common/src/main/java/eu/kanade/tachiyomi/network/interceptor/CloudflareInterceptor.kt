package eu.kanade.tachiyomi.network.interceptor

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
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
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class CloudflareInterceptor(
    private val context: Context,
    private val cookieManager: AndroidCookieJar,
    defaultUserAgentProvider: () -> String,
) : WebViewInterceptor(context, defaultUserAgentProvider) {

    private val executor = ContextCompat.getMainExecutor(context)

    // Requests to a host share its lock while a challenge is solved under it alone, so concurrent requests wait for
    // one WebView instead of each opening their own
    private val hostLocks = ConcurrentHashMap<String, ReentrantReadWriteLock>()

    private val listenerScript = """
        addEventListener("message", ({data}) => {
            if (data?.source === "cloudflare-challenge") {
                mihon?.postMessage(data.event);
            }
        })
    """.trimIndent()

    override fun shouldIntercept(response: Response): Boolean {
        // https://developers.cloudflare.com/cloudflare-challenges/challenge-types/challenge-pages/detect-response/
        return response.header("cf-mitigated") == "challenge" && response.header("Server") in SERVER_CHECK
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val lock = hostLocks.computeIfAbsent(request.url.host) { ReentrantReadWriteLock() }

        val (response, clearance) = lock.read {
            val response = chain.proceed(request)
            if (!shouldIntercept(response)) return response
            response to clearance(request.url)
        }

        // write releases the read hold of a request made from within another to the same host, like an extension
        // refreshing a token, which would otherwise wait on itself
        lock.write {
            if (isCleared(request.url, clearance)) {
                response.close()
            } else {
                if (!prepareWebView()) return response
                solve(request, response, clearance)
            }
        }

        return chain.proceed(request)
    }

    override fun intercept(chain: Interceptor.Chain, request: Request, response: Response): Response {
        solve(request, response, clearance(request.url))
        return chain.proceed(request)
    }

    private fun clearance(url: HttpUrl): String? {
        return cookieManager.get(url).firstOrNull { it.name == "cf_clearance" }?.value
    }

    private fun isCleared(url: HttpUrl, previousClearance: String?): Boolean {
        val clearance = clearance(url)
        return !clearance.isNullOrBlank() && clearance != previousClearance
    }

    private fun solve(request: Request, response: Response, clearance: String?) {
        try {
            response.close()
            cookieManager.remove(request.url, COOKIE_NAMES, 0)
            resolveWithWebView(request, clearance)
        }
        // Because OkHttp's enqueue only handles IOExceptions, wrap the exception so that
        // we don't crash the entire app
        catch (e: CloudflareBypassException) {
            throw IOException(context.stringResource(MR.strings.information_cloudflare_bypass_failure), e)
        } catch (e: Exception) {
            throw IOException(e)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun resolveWithWebView(originalRequest: Request, originalClearance: String?) {
        var webview: WebView? = null
        var listenerScriptHandler: ScriptHandler? = null
        val useIsolatedWorld = WebViewFeature.isFeatureSupported(WebViewFeature.JS_INJECTION_IN_FRAME_AND_WORLD)
        var isWebViewOutdated = false

        val client = CloudflareChallengeClient(
            challengeUrl = challengeUrl(originalRequest),
            isCleared = { isCleared(originalRequest.url, originalClearance) },
            listenerScriptToEvaluate = listenerScript.takeUnless { useIsolatedWorld },
        )
        val headers = parseHeaders(originalRequest.headers)

        executor.execute {
            val view = createWebView(originalRequest)
            webview = view

            if (useIsolatedWorld) {
                // An isolated world keeps the bridge out of the page's reach
                val world = WebViewCompat.getExecutionWorld(view, "mihon")
                val origins = setOf(originalRequest.url.origin())
                WebViewCompat.addWebMessageListener(view, "mihon", origins, world) { _, message, _, isMainFrame, _ ->
                    if (isMainFrame) message.data?.let(client::onChallengeEvent)
                }
                listenerScriptHandler = WebViewCompat.addJavaScriptOnEvent(
                    view,
                    listenerScript,
                    WebViewCompat.INJECTION_EVENT_DOCUMENT_START,
                    origins,
                    world,
                )
            } else {
                view.addJavascriptInterface(
                    object {
                        @Suppress("unused")
                        @JavascriptInterface
                        fun postMessage(event: String) = client.onChallengeEvent(event)
                    },
                    "mihon",
                )
            }

            view.webViewClient = client
            view.loadUrl(client.challengeUrl, headers)
        }

        client.latch.awaitFor30Seconds()

        executor.execute {
            // Only destroy can be called on a WebView whose renderer is gone
            if (!client.cloudflareBypassed && !client.isRenderProcessGone) {
                isWebViewOutdated = webview?.isOutdated() == true
            }

            webview?.run {
                if (!client.isRenderProcessGone) {
                    if (useIsolatedWorld) {
                        WebViewCompat.removeWebMessageListener(
                            this,
                            WebViewCompat.getExecutionWorld(this, "mihon"),
                            "mihon",
                        )
                    } else {
                        removeJavascriptInterface("mihon")
                    }
                    listenerScriptHandler?.remove()
                    stopLoading()
                }
                destroy()
            }
        }

        if (!client.cloudflareBypassed) {
            // A clearance from a failed challenge would only be rejected again
            cookieManager.remove(originalRequest.url, COOKIE_NAMES, 0)
            if (isWebViewOutdated) {
                context.toast(MR.strings.information_webview_outdated, Toast.LENGTH_LONG)
            }

            throw CloudflareBypassException()
        }
    }
}

// A WebView can only load a page with GET, and loading the url of another method usually reaches an endpoint that
// answers GET without a challenge to solve. The clearance covers the whole site, so it's earned on the site's root.
internal fun challengeUrl(request: Request): String {
    if (request.method == "GET") return request.url.toString()
    return request.url.newBuilder().encodedPath("/").query(null).fragment(null).build().toString()
}

// An origin rule without a port only matches the scheme's default one
private fun HttpUrl.origin(): String {
    return if (port == HttpUrl.defaultPort(scheme)) "$scheme://$host" else "$scheme://$host:$port"
}

private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
private val COOKIE_NAMES = listOf("cf_clearance")

class CloudflareBypassException : Exception()
