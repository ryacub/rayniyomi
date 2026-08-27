package eu.kanade.tachiyomi.data.translation.engine

import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OpenRouterTranslationEngineTest {

    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `sends the exact selected model ID`() = runTest {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"[]"}}]}"""))
        val selectedModel = "openai/gpt-4o"
        val engine = OpenRouterTranslationEngine(
            apiKey = "test-key",
            model = selectedModel,
            client = OkHttpClient(),
            endpoint = server.url("/chat/completions").toString(),
        )

        engine.detectAndTranslate(byteArrayOf(1, 2, 3), "English")

        server.takeRequest().body.readUtf8() shouldContain "\"model\":\"$selectedModel\""
    }
}
