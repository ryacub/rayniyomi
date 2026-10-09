package eu.kanade.tachiyomi.ui.player.cast

import android.content.Context
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.api.PendingResult
import com.google.android.gms.common.api.PendingResult.StatusListener
import com.google.android.gms.common.api.Status
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetAddress

class CastManagerTest {

    private lateinit var castManager: CastManager
    private val httpClient = OkHttpClient()
    private val mockContext: Context = mockk(relaxed = true)
    private val mockNetwork: NetworkHelper = mockk(relaxed = true)
    private val mockPlayerPreferences: PlayerPreferences = mockk(relaxed = true)

    @BeforeEach
    fun setup() {
        castManager = CastManager(mockContext, mockNetwork, mockPlayerPreferences)
    }

    @Test
    fun `successful replacement retires old routes and preserves pending append routes`() {
        withProxySession { upstream, proxy, client, session ->
            var completed: StatusListener? = null
            val load = mockk<PendingResult<RemoteMediaClient.MediaChannelResult>>()
            every { load.addStatusListener(any()) } answers { completed = firstArg() }
            every { client.queueLoad(any(), any(), any(), any<Long>(), any()) } returns load
            fun route(): String = proxyRoute(upstream, proxy)
            fun item(url: String): MediaQueueItem = mockk {
                every { media } returns mockk<MediaInfo> { every { contentId } returns url }
            }
            castManager.resetForNewActivity()
            val abandonedUrl = route()
            castManager.loadQueue(listOf(item(abandonedUrl)), 0L)
            assertResponse(upstream, abandonedUrl, 404)
            castManager.onSessionConnected(session)
            val oldUrl = route()
            castManager.loadQueue(listOf(item(oldUrl)), 0L)
            completed!!.onComplete(Status.RESULT_SUCCESS)
            val currentUrl = route()
            castManager.loadQueue(listOf(item(currentUrl)), 0L)
            val pendingUrl = route()
            castManager.appendToQueue(item(pendingUrl))
            val acceptedLoad = completed
            val rejectedUrl = route()
            castManager.loadQueue(listOf(item(rejectedUrl)), 0L)
            completed!!.onComplete(Status.RESULT_INTERNAL_ERROR)
            acceptedLoad!!.onComplete(Status.RESULT_SUCCESS)

            assertResponse(upstream, oldUrl, 404)
            listOf(currentUrl, pendingUrl).forEach { url ->
                assertResponse(upstream, url, 200)
            }
        }
    }

    @Test
    fun `successful stale removal releases only removed routes across repeated queue advances`() {
        withProxySession { upstream, proxy, client, _ ->
            var completed: StatusListener? = null
            val removal = mockk<PendingResult<RemoteMediaClient.MediaChannelResult>>()
            every { removal.addStatusListener(any()) } answers { completed = firstArg() }
            every { client.queueRemoveItems(any(), any()) } returns removal
            val liveUrl = proxyRoute(upstream, proxy, "/live.mp4")
            repeat(2) { index ->
                val url = proxyRoute(upstream, proxy, "/episode-$index.mp4")
                val queuedItem = mockk<MediaQueueItem> {
                    every { itemId } returns index
                    every { media } returns mockk<MediaInfo> { every { contentId } returns url }
                }
                every { client.mediaStatus } returns mockk<MediaStatus> {
                    every { queueItems } returns listOf(queuedItem)
                }
                castManager.removeQueueItems(listOf(index))
                assertResponse(upstream, url, 200)
                completed?.onComplete(Status.RESULT_INTERNAL_ERROR)
                castManager.removeQueueItems(listOf(index))
                val removed = completed
                if (index == 0) {
                    val replacement = mockk<PendingResult<RemoteMediaClient.MediaChannelResult>>()
                    var loaded: StatusListener? = null
                    every { replacement.addStatusListener(any()) } answers { loaded = firstArg() }
                    every { client.queueLoad(any(), any(), any(), any<Long>(), any()) } returns replacement
                    castManager.loadQueue(emptyList(), 0L)
                    loaded!!.onComplete(Status.RESULT_INTERNAL_ERROR)
                }
                removed!!.onComplete(Status.RESULT_SUCCESS)
                assertResponse(upstream, url, 404)
                assertEquals(listOf(liveUrl), proxy.routeUrls())
            }
            assertResponse(upstream, liveUrl, 200)
        }
    }

    private fun withProxySession(block: (MockWebServer, CastStreamProxy, RemoteMediaClient, CastSession) -> Unit) {
        MockWebServer().use { upstream ->
            upstream.start()
            var token = 0
            CastStreamProxy(
                httpClient,
                addressProvider = { InetAddress.getLoopbackAddress() },
                tokenProvider = { "queue-${++token}" },
            ).use { proxy ->
                castManager = CastManager(
                    mockContext,
                    mockNetwork,
                    mockPlayerPreferences,
                    streamProxy = proxy,
                )
                val client = mockk<RemoteMediaClient>(relaxed = true)
                val session = mockk<CastSession>()
                every { session.remoteMediaClient } returns client
                castManager.onSessionConnected(session)
                block(upstream, proxy, client, session)
            }
        }
    }

    private fun proxyRoute(upstream: MockWebServer, proxy: CastStreamProxy, path: String = "/episode.mp4"): String =
        proxy.urlFor(upstream.url(path).toString(), Headers.headersOf("Referer", "https://example.com/"))

    private fun assertResponse(upstream: MockWebServer, url: String, expectedStatus: Int) {
        upstream.enqueue(MockResponse().setBody("episode"))
        httpClient.newCall(Request.Builder().url(url).build()).execute().use {
            assertEquals(expectedStatus, it.code)
        }
    }

    @Test
    fun `castState initial value is DISCONNECTED`() = runTest {
        val state = castManager.castState.first()
        assertEquals(CastState.DISCONNECTED, state)
    }

    @Test
    fun `castState transitions to CONNECTING when a session starts`() = runTest {
        castManager.onSessionStarting()
        assertEquals(CastState.CONNECTING, castManager.castState.first())
    }

    @Test
    fun `castState transitions to CONNECTING when a session resumes`() = runTest {
        castManager.onSessionResuming()
        assertEquals(CastState.CONNECTING, castManager.castState.first())
    }

    @Test
    fun `castState transitions to CONNECTED when onSessionConnected is called`() = runTest {
        val mockSession: CastSession = mockk(relaxed = true)
        castManager.onSessionConnected(mockSession)
        val state = castManager.castState.first()
        assertEquals(CastState.CONNECTED, state)
    }

    @Test
    fun `castState transitions back to DISCONNECTED when onSessionEnded is called`() = runTest {
        val mockSession: CastSession = mockk(relaxed = true)
        castManager.onSessionConnected(mockSession)
        castManager.onSessionEnded()
        val state = castManager.castState.first()
        assertEquals(CastState.DISCONNECTED, state)
    }

    @Test
    fun `castState transitions to DISCONNECTED on session resume failure`() = runTest {
        val mockSession: CastSession = mockk(relaxed = true)
        castManager.onSessionConnected(mockSession)
        castManager.onSessionResumeFailed()
        val state = castManager.castState.first()
        assertEquals(CastState.DISCONNECTED, state)
    }

    @Test
    fun `castState transitions to DISCONNECTED on session start failure`() = runTest {
        castManager.onSessionStarting()
        castManager.onSessionStartFailed()
        assertEquals(CastState.DISCONNECTED, castManager.castState.first())
    }

    @Test
    fun `castState transitions to DISCONNECTED when a session ends`() = runTest {
        castManager.onSessionConnected(mockk(relaxed = true))
        castManager.onSessionEnding()
        assertEquals(CastState.DISCONNECTED, castManager.castState.first())
    }

    @Test
    fun `resetForNewActivity clears stale session reference`() = runTest {
        val mockSession: CastSession = mockk(relaxed = true)
        castManager.onSessionConnected(mockSession)
        castManager.resetForNewActivity()
        // After reset, state should be DISCONNECTED
        val state = castManager.castState.first()
        assertEquals(CastState.DISCONNECTED, state)
    }

    @Test
    fun `setPlaybackRate forwards the rate to the remote media client`() {
        val mockSession: CastSession = mockk(relaxed = true)
        castManager.onSessionConnected(mockSession)

        castManager.setPlaybackRate(1.5)

        verify { mockSession.remoteMediaClient?.setPlaybackRate(1.5) }
    }

    @Test
    fun `isPlaybackRateSupported is false without a session`() {
        assertEquals(false, castManager.isPlaybackRateSupported())
    }

    @Test
    fun `isDownloadedVideo is true for a content URI`() {
        val video = Video(videoUrl = "content://downloads/episode.mp4")
        assertEquals(true, castManager.isDownloadedVideo(video))
    }

    @Test
    fun `isDownloadedVideo is false for a streaming URL`() {
        val video = Video(videoUrl = "https://example.com/video.mp4")
        assertEquals(false, castManager.isDownloadedVideo(video))
    }
}
