package eu.kanade.tachiyomi.util.system

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.suspendCancellableCoroutine
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.util.Locale
import kotlin.coroutines.resume

object WebViewUtil {
    private const val CHROME_PACKAGE = "com.android.chrome"
    private const val SYSTEM_SETTINGS_PACKAGE = "com.android.settings"

    private val CHROMIUM_PACKAGE_NAME_CLASSES = setOf(
        "org.chromium.base.buildinfo",
        "org.chromium.base.apkinfo",
    )
    private val CHROMIUM_PACKAGE_NAME_METHODS = setOf("getall", "getpackagename", "<init>")

    const val MINIMUM_WEBVIEW_VERSION = 118

    /**
     * Whether the stack trace belongs to the Chromium code that reads the package name to build the
     * `X-Requested-With` header. Callers must pass their own stack trace: reading the main thread's
     * stack instead spoofs the package name for whatever unrelated caller happens to ask while
     * Chromium is initializing.
     */
    fun isChromiumPackageNameCall(stackTrace: Array<StackTraceElement>): Boolean {
        return stackTrace.any { trace ->
            trace.className.lowercase(Locale.ENGLISH) in CHROMIUM_PACKAGE_NAME_CLASSES &&
                trace.methodName.lowercase(Locale.ENGLISH) in CHROMIUM_PACKAGE_NAME_METHODS
        }
    }

    /**
     * Uses the WebView's user agent string to create something similar to what Chrome on Android
     * would return.
     *
     * Example of WebView user agent string:
     *   Mozilla/5.0 (Linux; Android 13; Pixel 7 Build/TQ3A.230901.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/116.0.0.0 Mobile Safari/537.36
     *
     * Example of Chrome on Android:
     *   Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.3
     */
    fun getInferredUserAgent(context: Context): String {
        return WebView(context)
            .getDefaultUserAgentString()
            .replace("; Android .*?\\)".toRegex(), "; Android 10; K)")
            .replace("Version/.* Chrome/".toRegex(), "Chrome/")
    }

    fun getVersion(context: Context): String {
        val webView = WebView.getCurrentWebViewPackage() ?: return "how did you get here?"
        val pm = context.packageManager
        val label = webView.applicationInfo!!.loadLabel(pm)
        val version = webView.versionName
        return "$label $version"
    }

    fun supportsWebView(context: Context): Boolean {
        try {
            // May throw android.webkit.WebViewFactory$MissingWebViewPackageException if WebView
            // is not installed
            CookieManager.getInstance()
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e)
            return false
        }

        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_WEBVIEW)
    }

    fun spoofedPackageName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(CHROME_PACKAGE, PackageManager.GET_META_DATA)

            CHROME_PACKAGE
        } catch (_: PackageManager.NameNotFoundException) {
            SYSTEM_SETTINGS_PACKAGE
        }
    }
}

fun WebView.isOutdated(): Boolean {
    return getWebViewMajorVersion() < WebViewUtil.MINIMUM_WEBVIEW_VERSION
}

suspend fun WebView.getHtml(): String = suspendCancellableCoroutine {
    evaluateJavascript("document.documentElement.outerHTML") { html -> it.resume(html) }
}

@SuppressLint("SetJavaScriptEnabled")
fun WebView.setDefaultSettings() {
    with(settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
        useWideViewPort = true
        loadWithOverviewMode = true
        cacheMode = WebSettings.LOAD_DEFAULT

        // Allow zooming
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
    }

    CookieManager.getInstance().acceptThirdPartyCookies(this)
}

/**
 * Sets the user agent along with matching user agent metadata, which Chromium uses to build the
 * `Sec-CH-UA` client hints. Without this the hints keep advertising the real WebView brand and
 * version, contradicting the spoofed user agent.
 *
 * Non-Chrome user agents are left alone: there is no correct brand/version to advertise for them,
 * and rewriting the metadata would produce a different mismatch rather than remove one.
 */
fun WebView.setUserAgent(userAgent: String) {
    settings.userAgentString = userAgent

    if (!WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) return
    val version = parseChromeUserAgentVersion(userAgent) ?: return

    try {
        val metadata = WebSettingsCompat.getUserAgentMetadata(settings)
        val brandVersionList = metadata.brandVersionList.map { brandVersion ->
            val brand = spoofedBrand(brandVersion.brand) ?: return@map brandVersion

            UserAgentMetadata.BrandVersion.Builder()
                .setBrand(brand)
                .setMajorVersion(version.major)
                .setFullVersion(version.full)
                .build()
        }

        WebSettingsCompat.setUserAgentMetadata(
            settings,
            UserAgentMetadata.Builder(metadata)
                .setBrandVersionList(brandVersionList)
                .setFullVersion(version.full)
                .build(),
        )
    } catch (e: Exception) {
        logcat(LogPriority.ERROR, e) { "Failed to set user agent metadata" }
    }
}

internal data class ChromeUserAgentVersion(val major: String, val full: String)

/**
 * Returns null for any user agent without a `Chrome/N` token, which is what keeps the client-hint
 * rewrite from firing on non-Chrome user agents.
 */
internal fun parseChromeUserAgentVersion(userAgent: String): ChromeUserAgentVersion? {
    val match = CHROME_VERSION_REGEX.find(userAgent) ?: return null
    val components = (match.groupValues[1] + match.groupValues[2]).split('.')
    return ChromeUserAgentVersion(
        major = components[0],
        // Chromium expects a four-component full version, so short ones are padded rather than
        // passed through: a user agent may be hand-edited in Settings to something like Chrome/149.0
        full = List(FULL_VERSION_COMPONENTS) { components.getOrNull(it).orEmpty().ifEmpty { "0" } }
            .joinToString("."),
    )
}

/**
 * Maps a brand reported by the real WebView onto the brand the spoofed user agent claims. Returns
 * null for brands that must be passed through untouched, such as Chromium's `Not?A_Brand` padding.
 */
internal fun spoofedBrand(brand: String): String? = when (brand) {
    WEBVIEW_BRAND -> CHROME_BRAND
    CHROMIUM_BRAND -> CHROMIUM_BRAND
    else -> null
}

private const val FULL_VERSION_COMPONENTS = 4
private const val WEBVIEW_BRAND = "Android WebView"
private const val CHROMIUM_BRAND = "Chromium"
private const val CHROME_BRAND = "Google Chrome"
private val CHROME_VERSION_REGEX = """Chrome/(\d+)(\.[\d.]+)?""".toRegex()

private fun WebView.getWebViewMajorVersion(): Int {
    val uaRegexMatch = """.*Chrome/(\d+)\..*""".toRegex().matchEntire(getDefaultUserAgentString())
    return if (uaRegexMatch != null && uaRegexMatch.groupValues.size > 1) {
        uaRegexMatch.groupValues[1].toInt()
    } else {
        0
    }
}

// Based on https://stackoverflow.com/a/29218966
private fun WebView.getDefaultUserAgentString(): String {
    val originalUA: String = settings.userAgentString

    // Next call to getUserAgentString() will get us the default
    settings.userAgentString = null
    val defaultUserAgentString = settings.userAgentString

    // Revert to original UA string
    settings.userAgentString = originalUA

    return defaultUserAgentString
}
