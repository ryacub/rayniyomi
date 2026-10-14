package eu.kanade.tachiyomi.network.interceptor

import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import io.mockk.every
import io.mockk.mockk
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CloudflareChallengeClientTest {

    private val view = mockk<WebView>(relaxed = true)
    private var cleared = false

    private val client = CloudflareChallengeClient(challengeUrl = CHALLENGE_URL, isCleared = { cleared })

    @Test
    fun `a challenge page keeps the solve waiting after its first load`() {
        client.onReceivedHttpError(view, mainFrameRequest(), httpError("cf-mitigated" to "challenge"))
        client.onPageFinished(view, CHALLENGE_URL)

        assertEquals(1L, client.latch.count)
    }

    @Test
    fun `an HTTP error that is not a challenge ends the solve`() {
        client.onReceivedHttpError(view, mainFrameRequest(), httpError())

        assertEquals(0L, client.latch.count)
        assertFalse(client.isSolved)
    }

    @Test
    fun `a sub-frame HTTP error does not end the solve`() {
        client.onReceivedHttpError(view, mainFrameRequest(), httpError("cf-mitigated" to "challenge"))
        client.onReceivedHttpError(view, subFrameRequest(), httpError())

        assertEquals(1L, client.latch.count)
    }

    @Test
    fun `an HTTP error page after the clearance is still a solve`() {
        client.onReceivedHttpError(view, mainFrameRequest(), httpError("cf-mitigated" to "challenge"))
        cleared = true
        client.onReceivedHttpError(view, mainFrameRequest(), httpError())

        assertEquals(0L, client.latch.count)
        assertTrue(client.isSolved)
    }

    @Test
    fun `a page load with a new clearance ends the solve as bypassed`() {
        client.onReceivedHttpError(view, mainFrameRequest(), httpError("cf-mitigated" to "challenge"))
        cleared = true
        client.onPageFinished(view, CHALLENGE_URL)

        assertEquals(0L, client.latch.count)
        assertTrue(client.isSolved)
    }

    @Test
    fun `a failed challenge is not a bypass even when a clearance appears`() {
        client.onReceivedHttpError(view, mainFrameRequest(), httpError("cf-mitigated" to "challenge"))
        client.onChallengeEvent("fail")
        cleared = true
        client.onPageFinished(view, CHALLENGE_URL)

        assertEquals(0L, client.latch.count)
        assertFalse(client.isSolved)
    }

    @Test
    fun `an interactive challenge ends the solve at once`() {
        client.onReceivedHttpError(view, mainFrameRequest(), httpError("cf-mitigated" to "challenge"))
        client.onChallengeEvent("interactiveBegin")

        assertEquals(0L, client.latch.count)
        assertFalse(client.isSolved)
    }

    @Test
    fun `a dead renderer ends the solve without killing the app`() {
        val handled = client.onRenderProcessGone(view, mockk<RenderProcessGoneDetail>(relaxed = true))

        assertTrue(handled)
        assertTrue(client.isRenderProcessGone)
        assertEquals(0L, client.latch.count)
    }

    @Test
    fun `a GET request is solved on its own url`() {
        val request = Request.Builder().url("https://example.org/manga/1?page=2").build()

        assertEquals("https://example.org/manga/1?page=2", challengeUrl(request))
    }

    @Test
    fun `a non-GET request is solved on the site root`() {
        val request = Request.Builder()
            .url("https://example.org/api/search?q=one")
            .post(ByteArray(0).toRequestBody())
            .build()

        assertEquals("https://example.org/", challengeUrl(request))
    }

    private fun mainFrameRequest() = mockk<WebResourceRequest> {
        every { isForMainFrame } returns true
    }

    private fun subFrameRequest() = mockk<WebResourceRequest> {
        every { isForMainFrame } returns false
    }

    private fun httpError(vararg headers: Pair<String, String>) = mockk<WebResourceResponse> {
        every { responseHeaders } returns mapOf(*headers)
    }

    private companion object {
        const val CHALLENGE_URL = "https://example.org/manga/1"
    }
}
