package eu.kanade.tachiyomi.data.track

import eu.kanade.tachiyomi.data.track.bangumi.Bangumi
import eu.kanade.tachiyomi.data.track.bangumi.BangumiInterceptor
import eu.kanade.tachiyomi.data.track.bangumi.dto.BGMOAuth
import eu.kanade.tachiyomi.data.track.kitsu.Kitsu
import eu.kanade.tachiyomi.data.track.kitsu.KitsuInterceptor
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuOAuth
import eu.kanade.tachiyomi.data.track.shikimori.Shikimori
import eu.kanade.tachiyomi.data.track.shikimori.ShikimoriInterceptor
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMOAuth
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.IOException

class TrackerProviderBoundaryTest {

    @Test
    fun `Bangumi expired token without a refresh token reports an expired login`() {
        val bangumi = mockk<Bangumi>(relaxed = true)
        every { bangumi.restoreToken() } returns BGMOAuth(
            accessToken = "access-token",
            tokenType = "Bearer",
            createdAt = 0,
            expiresIn = 1,
            refreshToken = null,
            userId = null,
        )
        val interceptor = BangumiInterceptor(bangumi)
        val chain = mockChain()

        val exception = assertThrows(IOException::class.java) {
            interceptor.intercept(chain)
        }

        assertEquals("Bangumi: Login has expired", exception.message)
        verify(exactly = 0) { chain.proceed(any()) }
    }

    @Test
    fun `Kitsu token without a refresh token can use an unexpired access token`() {
        val kitsu = mockk<Kitsu>(relaxed = true)
        every { kitsu.restoreToken() } returns KitsuOAuth(
            accessToken = "access-token",
            tokenType = "Bearer",
            createdAt = System.currentTimeMillis() / 1000,
            expiresIn = 86400,
            refreshToken = null,
        )
        val interceptor = KitsuInterceptor(kitsu)
        val chain = mockChain()

        interceptor.intercept(chain)

        verify(exactly = 1) {
            chain.proceed(match { it.header("Authorization") == "Bearer access-token" })
        }
    }

    @Test
    fun `Shikimori token without a refresh token can use an unexpired access token`() {
        val shikimori = mockk<Shikimori>(relaxed = true)
        every { shikimori.restoreToken() } returns SMOAuth(
            accessToken = "access-token",
            tokenType = "Bearer",
            createdAt = System.currentTimeMillis() / 1000,
            expiresIn = 86400,
            refreshToken = null,
        )
        val interceptor = ShikimoriInterceptor(shikimori)
        val chain = mockChain()

        interceptor.intercept(chain)

        verify(exactly = 1) {
            chain.proceed(match { it.header("Authorization") == "Bearer access-token" })
        }
    }

    private fun mockChain(): Interceptor.Chain {
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns Request.Builder()
            .url("https://example.com/api/test")
            .build()
        every { chain.proceed(any()) } returns mockk<Response>(relaxed = true)
        return chain
    }
}
