package aniyomi.util

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.di.AppGraph
import eu.kanade.tachiyomi.di.AppGraphHolder
import eu.kanade.tachiyomi.source.MangaSource
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.IOException

class ReSmushItDataSaverTest {
    private val previousGraph = AppGraphHolder.graphOrNull
    private val imageUrl = "https://source.test/page.png?token=a&part=1"
    private val preferences = mockk<SourcePreferences> {
        every { dataSaver().get() } returns SourcePreferences.DataSaver.RESMUSH_IT
        every { dataSaverExcludedSources().get() } returns emptySet()
        every { dataSaverIgnoreJpeg().get() } returns false
        every { dataSaverIgnoreGif().get() } returns false
        every { dataSaverImageQuality().get() } returns 80
    }
    private val source = mockk<MangaSource> { every { id } returns 1L }

    @AfterEach
    fun tearDown() {
        AppGraphHolder.graphOrNull = previousGraph
    }

    @Test
    fun `invalid ReSmush response keeps the original image URL`() {
        var request: Request? = null
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            request = chain.request()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"error":401,"dest":true}""".toResponseBody())
                .build()
        }.build()
        AppGraphHolder.graph = mockk<AppGraph> { every { networkHelper.client } returns client }

        DataSaver(source, preferences).compress(imageUrl) shouldBe imageUrl
        request?.url?.scheme shouldBe "https"
        request?.url?.queryParameter("img") shouldBe imageUrl
        request?.header("Referer") shouldBe "https://github.com/ryacub/rayniyomi"
    }

    @Test
    fun `ReSmush network failure keeps the original image URL`() {
        val client = OkHttpClient.Builder().addInterceptor {
            throw IOException("optimization timed out")
        }.build()
        AppGraphHolder.graph = mockk<AppGraph> { every { networkHelper.client } returns client }

        DataSaver(source, preferences).compress(imageUrl) shouldBe imageUrl
    }
}
