package eu.kanade.tachiyomi.util.system

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class WebViewUtilTest {

    private fun trace(className: String, methodName: String) =
        StackTraceElement(className, methodName, null, -1)

    @Test
    fun `chromium package name call is detected for BuildInfo`() {
        val stackTrace = arrayOf(
            trace("org.chromium.base.BuildInfo", "getAll"),
            trace("eu.kanade.tachiyomi.App", "getPackageName"),
        )

        WebViewUtil.isChromiumPackageNameCall(stackTrace) shouldBe true
    }

    @Test
    fun `chromium package name call is detected for ApkInfo`() {
        val stackTrace = arrayOf(
            trace("org.chromium.base.ApkInfo", "<init>"),
            trace("eu.kanade.tachiyomi.App", "getPackageName"),
        )

        WebViewUtil.isChromiumPackageNameCall(stackTrace) shouldBe true
    }

    @Test
    fun `unrelated caller does not get the spoofed package name`() {
        val stackTrace = arrayOf(
            trace("eu.kanade.tachiyomi.data.download.manga.MangaDownloader", "download"),
            trace("eu.kanade.tachiyomi.App", "getPackageName"),
        )

        WebViewUtil.isChromiumPackageNameCall(stackTrace) shouldBe false
    }

    @Test
    fun `chromium class with an unrelated method is not a package name call`() {
        val stackTrace = arrayOf(trace("org.chromium.base.BuildInfo", "toString"))

        WebViewUtil.isChromiumPackageNameCall(stackTrace) shouldBe false
    }

    @Test
    fun `an empty stack trace is not a package name call`() {
        WebViewUtil.isChromiumPackageNameCall(emptyArray()) shouldBe false
    }
}
