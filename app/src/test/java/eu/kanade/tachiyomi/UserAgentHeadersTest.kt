package eu.kanade.tachiyomi

import eu.kanade.tachiyomi.data.track.anilist.AnilistInterceptor
import eu.kanade.tachiyomi.data.track.anilist.dto.ALOAuth
import eu.kanade.tachiyomi.data.track.jellyfin.JellyfinInterceptor
import eu.kanade.tachiyomi.data.track.kavita.KavitaInterceptor
import eu.kanade.tachiyomi.data.track.kitsu.KitsuInterceptor
import eu.kanade.tachiyomi.data.track.komga.KomgaApi
import eu.kanade.tachiyomi.data.track.mangaupdates.MangaUpdatesInterceptor
import eu.kanade.tachiyomi.data.track.shikimori.ShikimoriInterceptor
import eu.kanade.tachiyomi.data.track.simkl.SimklInterceptor
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UserAgentHeadersTest {

    @Test
    fun `ShikimoriInterceptor sets User-Agent with Rayniyomi branding`() {
        val ua = captureUserAgentFromInterceptor {
            val shikimori = mockk<eu.kanade.tachiyomi.data.track.shikimori.Shikimori>(relaxed = true)
            every { shikimori.restoreToken() } returns mockk(relaxed = true)
            ShikimoriInterceptor(shikimori)
        }
        assertUserAgentIsRayniyomi(ua)
    }

    @Test
    fun `KavitaInterceptor sets User-Agent with Rayniyomi branding`() {
        val ua = captureUserAgentFromInterceptor {
            val kavita = mockk<eu.kanade.tachiyomi.data.track.kavita.Kavita>(relaxed = true)
            KavitaInterceptor(kavita)
        }
        assertUserAgentIsRayniyomi(ua)
    }

    @Test
    fun `JellyfinInterceptor sets User-Agent with Rayniyomi branding`() {
        // Use a URL with api_key already present — Jellyfin takes the early return path
        // and calls chain.proceed(uaRequest) immediately, no sourceManager lookup needed.
        val ua = captureUserAgentFromInterceptor(
            request = Request.Builder()
                .url("https://example.com/api/test?api_key=test_key")
                .build(),
        ) {
            JellyfinInterceptor()
        }
        assertUserAgentIsRayniyomi(ua)
    }

    @Test
    fun `KitsuInterceptor sets User-Agent with Rayniyomi branding`() {
        val ua = captureUserAgentFromInterceptor {
            val kitsu = mockk<eu.kanade.tachiyomi.data.track.kitsu.Kitsu>(relaxed = true)
            every { kitsu.restoreToken() } returns mockk(relaxed = true)
            KitsuInterceptor(kitsu)
        }
        assertUserAgentIsRayniyomi(ua)
    }

    @Test
    fun `AnilistInterceptor sets User-Agent with Rayniyomi branding`() {
        val ua = captureUserAgentFromInterceptor {
            val anilist = mockk<eu.kanade.tachiyomi.data.track.anilist.Anilist>(relaxed = true)
            // loadOAuth() relaxed mock returns null; supply a non-expired token explicitly
            val nonExpiredOAuth = ALOAuth(
                accessToken = "mock_access_token",
                tokenType = "Bearer",
                expires = Long.MAX_VALUE / 2000, // far future; setter multiplies by 1000, stays safe
                expiresIn = 99999999L,
            )
            every { anilist.loadOAuth() } returns nonExpiredOAuth
            AnilistInterceptor(anilist, "mock_token")
        }
        assertUserAgentIsRayniyomi(ua)
    }

    @Test
    fun `MangaUpdatesInterceptor sets User-Agent with Rayniyomi branding`() {
        val ua = captureUserAgentFromInterceptor {
            val mangaUpdates = mockk<eu.kanade.tachiyomi.data.track.mangaupdates.MangaUpdates>(relaxed = true)
            every { mangaUpdates.restoreSession() } returns "mock_token"
            MangaUpdatesInterceptor(mangaUpdates)
        }
        assertUserAgentIsRayniyomi(ua)
    }

    @Test
    fun `SimklInterceptor sets User-Agent with Rayniyomi branding`() {
        val ua = captureUserAgentFromInterceptor {
            val simkl = mockk<eu.kanade.tachiyomi.data.track.simkl.Simkl>(relaxed = true)
            every { simkl.restoreToken() } returns mockk(relaxed = true)
            SimklInterceptor(simkl)
        }
        assertUserAgentIsRayniyomi(ua)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `KomgaApi sets User-Agent with Rayniyomi branding in headers builder`() {
        // KomgaApi.headers is private+lazy; access via the Kotlin lazy-delegate backing field
        // so we test the real class without needing Injekt (headers has no dependencies).
        val komgaApi = KomgaApi(0L, mockk(relaxed = true))
        val delegateField = KomgaApi::class.java.getDeclaredField("headers\$delegate")
        delegateField.isAccessible = true
        val headers = (delegateField.get(komgaApi) as Lazy<Headers>).value

        val ua = headers["User-Agent"] ?: error("User-Agent header not set in KomgaApi")
        assertUserAgentIsRayniyomi(ua)
    }

    private fun captureUserAgentFromInterceptor(
        request: Request = mockRequest(),
        createInterceptor: () -> Interceptor,
    ): String {
        val interceptor = createInterceptor()

        // Create a mock chain and capture the request passed to proceed()
        val requestSlot = slot<Request>()
        val mockResponse = mockk<Response>(relaxed = true)

        val chain = mockk<Interceptor.Chain> {
            every { this@mockk.request() } returns request
            every { proceed(capture(requestSlot)) } returns mockResponse
        }

        try {
            interceptor.intercept(chain)
        } catch (e: Exception) {
            // Some interceptors throw if auth is not set up, but that's after the header is added
        }

        val capturedRequest = requestSlot.captured
        return capturedRequest.header("User-Agent") ?: error("User-Agent header not captured")
    }

    private fun mockRequest(): Request {
        return Request.Builder()
            .url("https://example.com/api/test")
            .build()
    }

    private fun assertUserAgentIsRayniyomi(ua: String) {
        assertFalse(ua.contains("Aniyomi"), "User-Agent should not contain 'Aniyomi', but was: $ua")
        assertTrue(ua.startsWith("Rayniyomi v"), "User-Agent should start with 'Rayniyomi v', but was: $ua")
    }
}
