package eu.kanade.tachiyomi.data.translation.catalog

import eu.kanade.tachiyomi.data.translation.TranslationProvider
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TranslationCatalogPrefetchTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: TranslationModelCatalogRepository
    private var currentTime = 1_000L

    private val compatibleCatalogJson = """
        {"data":[{
          "id":"google/gemma-4-26b-a4b-it:free",
          "name":"Example Free Vision",
          "context_length":16384,
          "architecture":{"input_modalities":["text","image"],"output_modalities":["text"]},
          "top_provider":{"max_completion_tokens":4096},
          "supported_parameters":["response_format"],
          "pricing":{"prompt":"0","completion":"0"}
        }]}
    """.trimIndent()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = repositoryWithEndpoints(server)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `blank key or NONE provider issues no request`() = runTest {
        var resolved: String? = null

        TranslationCatalogPrefetch.refreshAndResolveAutomatic(
            repository = repository,
            provider = TranslationProvider.NONE,
            apiKey = VALID_KEY,
            choiceType = TranslationModelChoiceType.AUTOMATIC,
            setModelId = { resolved = it },
        )
        TranslationCatalogPrefetch.refreshAndResolveAutomatic(
            repository = repository,
            provider = TranslationProvider.OPENROUTER,
            apiKey = "",
            choiceType = TranslationModelChoiceType.AUTOMATIC,
            setModelId = { resolved = it },
        )

        server.requestCount shouldBe 0
        resolved shouldBe null
    }

    @Test
    fun `success with AUTOMATIC persists the first compatible model id`() = runTest {
        server.enqueue(MockResponse().setBody(compatibleCatalogJson))
        var resolved: String? = null

        TranslationCatalogPrefetch.refreshAndResolveAutomatic(
            repository = repository,
            provider = TranslationProvider.OPENROUTER,
            apiKey = VALID_KEY,
            choiceType = TranslationModelChoiceType.AUTOMATIC,
            setModelId = { resolved = it },
        )

        server.requestCount shouldBe 1
        resolved shouldBe "google/gemma-4-26b-a4b-it:free"
    }

    @Test
    fun `success with PINNED does not touch the persisted model`() = runTest {
        server.enqueue(MockResponse().setBody(compatibleCatalogJson))
        var resolved: String? = null

        TranslationCatalogPrefetch.refreshAndResolveAutomatic(
            repository = repository,
            provider = TranslationProvider.OPENROUTER,
            apiKey = VALID_KEY,
            choiceType = TranslationModelChoiceType.PINNED,
            setModelId = { resolved = it },
        )

        server.requestCount shouldBe 1
        resolved shouldBe null
    }

    @Test
    fun `server failure leaves the persisted model untouched`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        var resolved: String? = null

        TranslationCatalogPrefetch.refreshAndResolveAutomatic(
            repository = repository,
            provider = TranslationProvider.OPENROUTER,
            apiKey = VALID_KEY,
            choiceType = TranslationModelChoiceType.AUTOMATIC,
            setModelId = { resolved = it },
        )

        server.requestCount shouldBe 1
        resolved shouldBe null
    }

    private fun repositoryWithEndpoints(server: MockWebServer): TranslationModelCatalogRepository =
        TranslationModelCatalogRepository(
            catalogEndpoints = mapOf(
                TranslationProvider.OPENROUTER to server.url("/api/v1/models").toString(),
                TranslationProvider.CLAUDE to server.url("/v1/models").toString(),
                TranslationProvider.OPENAI to server.url("/v1/models").toString(),
                TranslationProvider.GOOGLE to server.url("/v1beta/models").toString(),
            ),
            nowEpochMilliseconds = { currentTime },
        )

    companion object {
        private const val VALID_KEY = "openrouter-key"
    }
}
