package eu.kanade.tachiyomi.data.translation.engine

import eu.kanade.tachiyomi.data.translation.InvalidTranslationResponseException
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

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

    @Test
    fun `missing message is not treated as a no-text page`() = runTest {
        server.enqueue(MockResponse().setBody("""{"choices":[]}"""))
        val engine = OpenRouterTranslationEngine(
            apiKey = "test-key",
            model = "openai/gpt-4o",
            client = OkHttpClient(),
            endpoint = server.url("/chat/completions").toString(),
        )

        assertThrows<InvalidTranslationResponseException> {
            engine.detectAndTranslate(byteArrayOf(1, 2, 3), "English")
        }
    }
}
