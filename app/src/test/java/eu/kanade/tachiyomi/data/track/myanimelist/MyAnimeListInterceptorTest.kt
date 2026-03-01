package eu.kanade.tachiyomi.data.track.myanimelist

import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALOAuth
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test

class MyAnimeListInterceptorTest {

    @Test
    fun `429 response does not trigger interceptor retry`() {
        val myAnimeList = mockk<MyAnimeList>(relaxed = true)
        every { myAnimeList.getIfAuthExpired() } returns false
        every { myAnimeList.loadOAuth() } returns MALOAuth(
            tokenType = "Bearer",
            refreshToken = "refresh",
            accessToken = "token",
            expiresIn = 3600,
            createdAt = System.currentTimeMillis() / 1000,
        )

        val interceptor = MyAnimeListInterceptor(myAnimeList)

        val chain = mockk<Interceptor.Chain>()
        val originalRequest = Request.Builder().url("https://api.myanimelist.net/v2/users/@me").build()
        val capturedRequest = slot<Request>()

        every { chain.request() } returns originalRequest
        every { chain.proceed(capture(capturedRequest)) } answers {
            buildResponse(capturedRequest.captured, code = 429)
        }

        val response = interceptor.intercept(chain)

        response.code shouldBe 429
        capturedRequest.captured.header("Authorization") shouldBe "Bearer token"
        verify(exactly = 1) { chain.proceed(any()) }
    }

    private fun buildResponse(request: Request, code: Int): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("HTTP $code")
            .body("{}".toResponseBody())
            .build()
    }
}
