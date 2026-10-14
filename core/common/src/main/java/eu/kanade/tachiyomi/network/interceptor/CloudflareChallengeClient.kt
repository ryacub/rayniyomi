package eu.kanade.tachiyomi.network.interceptor

import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.util.concurrent.CountDownLatch

internal class CloudflareChallengeClient(
    val challengeUrl: String,
    private val isCleared: () -> Boolean,
    private val listenerScriptToEvaluate: String?,
) : WebViewClient() {

    // OkHttp doesn't support asynchronous interceptors, so the request thread waits on this
    val latch = CountDownLatch(1)

    @Volatile
    var cloudflareBypassed = false
        private set

    @Volatile
    var isRenderProcessGone = false
        private set

    @Volatile
    private var challengeFound = false

    // A JavascriptInterface call arrives on a background thread
    @Volatile
    private var challengeFailed = false

    fun onChallengeEvent(event: String) {
        when (event) {
            // The challenge cannot be solved non-interactively
            "interactiveBegin" -> latch.countDown()
            "fail" -> {
                challengeFailed = true
                latch.countDown()
            }
        }
    }

    override fun onPageFinished(view: WebView, url: String) {
        if (!challengeFailed && isCleared()) {
            cloudflareBypassed = true
            latch.countDown()
        }

        if (url != challengeUrl) return
        if (!challengeFound) {
            // The first request didn't return the challenge
            latch.countDown()
        } else {
            listenerScriptToEvaluate?.let { view.evaluateJavascript(it, null) }
        }
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?,
    ) {
        if (request?.isForMainFrame != true) return
        if (errorResponse?.responseHeaders?.get("cf-mitigated") == "challenge") {
            challengeFound = true
        } else {
            latch.countDown()
        }
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        // Not handling it kills the app, failing the bypass is enough
        logcat(LogPriority.ERROR) {
            "WebView renderer gone during Cloudflare bypass: crashed=${detail.didCrash()}, " +
                "priority=${detail.rendererPriorityAtExit()}, url=$challengeUrl"
        }
        isRenderProcessGone = true
        latch.countDown()
        return true
    }
}
