package eu.kanade.tachiyomi.data.track.jellyfin

import android.content.SharedPreferences
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.sourcePreferences
import eu.kanade.tachiyomi.di.AppGraph
import eu.kanade.tachiyomi.di.AppGraphHolder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class JellyfinInterceptorTest {

    @Test
    fun `concurrent requests share one lookup per user and keep their own keys`() {
        val firstUser = "first-user"
        val secondUser = "second-user"
        val firstKey = "first-key"
        val secondKey = "second-key"
        val firstKeyReads = AtomicInteger()
        val duplicateRead = CountDownLatch(1)
        val firstPreferences = mockk<SharedPreferences> {
            every { getString("user_id", "") } returns firstUser
            every { getString("api_key", "") } answers {
                // Keep the first cache miss open long enough for concurrent requests to reach it.
                if (firstKeyReads.incrementAndGet() == 1) {
                    duplicateRead.await(1, TimeUnit.SECONDS)
                } else {
                    duplicateRead.countDown()
                }
                firstKey
            }
        }
        val secondPreferences = mockk<SharedPreferences> {
            every { getString("user_id", "") } returns secondUser
            every { getString("api_key", "") } returns secondKey
        }
        val firstSource = mockk<ConfigurableAnimeSource>()
        val secondSource = mockk<ConfigurableAnimeSource>()
        // IDs for the first two Jellyfin sources at extension version 1.
        val sourceManager = mockk<AnimeSourceManager> {
            every { get(1_100_359_934_660_540_567L) } returns firstSource
            every { get(5_716_273_013_801_838_310L) } returns secondSource
        }
        val previousGraph = AppGraphHolder.graphOrNull
        val interceptor = JellyfinInterceptor()
        val userIds = listOf(firstUser, firstUser, secondUser, firstUser)
        val start = CyclicBarrier(userIds.size)
        val executor = Executors.newFixedThreadPool(userIds.size)

        mockkStatic("eu.kanade.tachiyomi.animesource.ConfigurableAnimeSourceKt")
        try {
            every { firstSource.sourcePreferences() } returns firstPreferences
            every { secondSource.sourcePreferences() } returns secondPreferences
            AppGraphHolder.graph = mockk<AppGraph> { every { animeSourceManager } returns sourceManager }

            val requests = userIds.map { userId ->
                val chain = chainFor(userId)
                executor.submit<String?> {
                    start.await(5, TimeUnit.SECONDS)
                    interceptor.intercept(chain).use { it.request.url.queryParameter("api_key") }
                }
            }

            assertEquals(listOf(firstKey, firstKey, secondKey, firstKey), requests.map { it.get(5, TimeUnit.SECONDS) })
            assertEquals(1, firstKeyReads.get(), "Concurrent requests should share one key lookup for the same user")
        } finally {
            executor.shutdownNow()
            AppGraphHolder.graphOrNull = previousGraph
            unmockkStatic("eu.kanade.tachiyomi.animesource.ConfigurableAnimeSourceKt")
        }
    }

    private fun chainFor(userId: String): Interceptor.Chain {
        val request = Request.Builder().url("https://example.com/Users/$userId").build()
        return mockk {
            every { this@mockk.request() } returns request
            every { proceed(any()) } answers {
                Response.Builder()
                    .request(firstArg())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody())
                    .build()
            }
        }
    }
}
