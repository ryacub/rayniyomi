package eu.kanade.tachiyomi.network.interceptor

import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.util.concurrent.CountDownLatch

internal class CloudflareChallengeClient(
    val challengeUrl: String,
    private val isCleared: () -> Boolean,
) : WebViewClient() {

    // OkHttp doesn't support asynchronous interceptors, so the request thread waits on this
    val latch = CountDownLatch(1)

    @Volatile
    var isRenderProcessGone = false
        private set

    @Volatile
    private var challengeFound = false

    // A JavascriptInterface call arrives on a background thread
    @Volatile
    private var challengeFailed = false

    private var listenerScriptHandler: ScriptHandler? = null

    val isSolved: Boolean
        get() = !challengeFailed && isCleared()

    fun attach(view: WebView, origin: String) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.JS_INJECTION_IN_FRAME_AND_WORLD)) {
            // An isolated world keeps the bridge out of the page's reach
            val world = WebViewCompat.getExecutionWorld(view, BRIDGE)
            WebViewCompat.addWebMessageListener(view, BRIDGE, setOf(origin), world) { _, message, _, isMainFrame, _ ->
                if (isMainFrame) message.data?.let(::onChallengeEvent)
            }
            listenerScriptHandler = WebViewCompat.addJavaScriptOnEvent(
                view,
                LISTENER_SCRIPT,
                WebViewCompat.INJECTION_EVENT_DOCUMENT_START,
                setOf(origin),
                world,
            )
        } else {
            view.addJavascriptInterface(
                object {
                    @Suppress("unused")
                    @JavascriptInterface
                    fun postMessage(event: String) = onChallengeEvent(event)
                },
                BRIDGE,
            )
        }
        view.webViewClient = this
    }

    fun detach(view: WebView) {
        // Only destroy can be called on a WebView whose renderer is gone
        if (!isRenderProcessGone) {
            val scriptHandler = listenerScriptHandler
            if (scriptHandler != null) {
                WebViewCompat.removeWebMessageListener(view, WebViewCompat.getExecutionWorld(view, BRIDGE), BRIDGE)
                scriptHandler.remove()
            } else {
                view.removeJavascriptInterface(BRIDGE)
            }
            view.stopLoading()
        }
        view.destroy()
    }

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
        if (isSolved) latch.countDown()

        if (url != challengeUrl) return
        if (!challengeFound) {
            // The first request didn't return the challenge
            latch.countDown()
        } else if (listenerScriptHandler == null) {
            view.evaluateJavascript(LISTENER_SCRIPT, null)
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

private const val BRIDGE = "mihon"

private val LISTENER_SCRIPT = """
    addEventListener("message", ({data}) => {
        if (data?.source === "cloudflare-challenge") {
            $BRIDGE?.postMessage(data.event);
        }
    })
""".trimIndent()
