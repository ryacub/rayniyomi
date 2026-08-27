package eu.kanade.tachiyomi.data.translation.catalog

import eu.kanade.tachiyomi.data.translation.TranslationProvider
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TranslationModelCatalogRepositoryTest {

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

    private val claudePageJson = """
        {"data":[{"id":"claude-sonnet-4-5","type":"model","display_name":"Claude Sonnet 4.5"}],
         "has_more":false,"last_id":"claude-sonnet-4-5"}
    """.trimIndent()

    private val openAiCatalogJson = """
        {"object":"list","data":[{"id":"gpt-4o","created":1715367049,"owned_by":"system"}]}
    """.trimIndent()

    private val googleCatalogJson = """
        {"models":[{"name":"models/gemini-2.0-flash","displayName":"Gemini 2.0 Flash",
        "supportedGenerationMethods":["generateContent"]}]}
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
    fun `caches a successful catalog for twenty four hours`() = runTest {
        enqueueCompatibleCatalog()

        repository.load(TranslationProvider.OPENROUTER, OPENROUTER_KEY, forceRefresh = false)
        currentTime += 23 * 60 * 60 * 1_000L
        val result = repository.load(TranslationProvider.OPENROUTER, OPENROUTER_KEY, forceRefresh = false)

        (result as TranslationCatalogResult.Success).fromCache shouldBe true
        server.requestCount shouldBe 1
    }

    @Test
    fun `refreshes manually even when cached snapshot is fresh`() = runTest {
        enqueueCompatibleCatalog()
        repository.load(TranslationProvider.OPENROUTER, OPENROUTER_KEY, forceRefresh = false)

        enqueueCompatibleCatalog()
        val refreshed = repository.load(TranslationProvider.OPENROUTER, OPENROUTER_KEY, forceRefresh = true)

        (refreshed as TranslationCatalogResult.Success).fromCache shouldBe false
        server.requestCount shouldBe 2
    }

    @Test
    fun `requests the complete OpenRouter output modalities and caches paid models`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
            {"data":[
              {"id":"openai/gpt-4o","architecture":{"input_modalities":["image"],"output_modalities":["text"]},"pricing":{"prompt":"1","completion":"1"}},
              {"id":"text-only","architecture":{"input_modalities":["text"],"output_modalities":["text"]},"pricing":{"prompt":"0","completion":"0"}}
            ]}
                """.trimIndent(),
            ),
        )

        val result = repository.load(TranslationProvider.OPENROUTER, OPENROUTER_KEY, forceRefresh = true)

        (result as TranslationCatalogResult.Success).catalog.models.map { it.id } shouldBe
            listOf("openai/gpt-4o", "text-only")
        server.takeRequest().requestUrl?.queryParameter("output_modalities") shouldBe "all"
    }

    @Test
    fun `returns sanitized failure with cached models when refresh fails`() = runTest {
        enqueueCompatibleCatalog()
        repository.load(TranslationProvider.OPENROUTER, OPENROUTER_KEY, forceRefresh = false)

        server.enqueue(MockResponse().setResponseCode(500))
        val result = repository.load(TranslationProvider.OPENROUTER, OPENROUTER_KEY, forceRefresh = true)

        result shouldBe TranslationCatalogResult.Failure(
            reason = "The model list could not be updated. Check the connection.",
            cachedModels = listOf(expectedCompatibleModel()),
        )
    }

    @Test
    fun `keeps cached models when refresh contains no valid entries`() = runTest {
        enqueueCompatibleCatalog()
        repository.load(TranslationProvider.OPENROUTER, OPENROUTER_KEY, forceRefresh = false)

        server.enqueue(MockResponse().setBody("""{"data":[{"id":""}]}"""))
        val result = repository.load(TranslationProvider.OPENROUTER, OPENROUTER_KEY, forceRefresh = true)

        result shouldBe TranslationCatalogResult.Failure(
            reason = "The model list could not be updated. Check the connection.",
            cachedModels = listOf(expectedCompatibleModel()),
        )
    }

    @Test
    fun `rethrows cancellation instead of returning a failure`() = runTest {
        val cancellation = CancellationException("cancelled")
        val cancellingRepository = TranslationModelCatalogRepository(
            executeRequest = { throw cancellation },
        )

        var thrown: CancellationException? = null
        try {
            cancellingRepository.load(TranslationProvider.OPENROUTER, OPENROUTER_KEY, forceRefresh = true)
        } catch (error: CancellationException) {
            thrown = error
        }
        thrown shouldBe cancellation
    }

    @Test
    fun `sends Claude auth headers and page size`() = runTest {
        server.enqueue(MockResponse().setBody(claudePageJson))

        repository.load(TranslationProvider.CLAUDE, CLAUDE_KEY, forceRefresh = false)

        val request = server.takeRequest()
        request.getHeader("x-api-key") shouldBe CLAUDE_KEY
        request.getHeader("anthropic-version") shouldBe ANTHROPIC_VERSION
        request.requestUrl?.queryParameter("limit") shouldBe "1000"
    }

    @Test
    fun `merges Claude pages until has_more is false`() = runTest {
        server.enqueue(claudePage("claude-model-one", hasMore = true))
        server.enqueue(claudePage("claude-model-two", hasMore = false))

        val result = repository.load(TranslationProvider.CLAUDE, CLAUDE_KEY, forceRefresh = false)

        (result as TranslationCatalogResult.Success).catalog.models.map { it.id } shouldBe
            listOf("claude-model-one", "claude-model-two")
        server.requestCount shouldBe 2
        server.takeRequest()
        val secondRequest = server.takeRequest()
        secondRequest.requestUrl?.queryParameter("after_id") shouldBe "claude-model-one"
        secondRequest.requestUrl?.queryParameter("limit") shouldBe "1000"
    }

    @Test
    fun `caps Claude pagination at five requests`() = runTest {
        repeat(MAX_PAGES + 1) { index ->
            server.enqueue(claudePage("claude-model-$index", hasMore = true))
        }

        val result = repository.load(TranslationProvider.CLAUDE, CLAUDE_KEY, forceRefresh = false)

        (result as TranslationCatalogResult.Success).catalog.models.size shouldBe MAX_PAGES
        server.requestCount shouldBe MAX_PAGES
    }

    @Test
    fun `sends OpenAI bearer header`() = runTest {
        server.enqueue(MockResponse().setBody(openAiCatalogJson))

        repository.load(TranslationProvider.OPENAI, OPENAI_KEY, forceRefresh = false)

        val request = server.takeRequest()
        request.getHeader("Authorization") shouldBe "Bearer $OPENAI_KEY"

        (repository.snapshot(TranslationProvider.OPENAI)?.models?.single()?.id) shouldBe "gpt-4o"
    }

    @Test
    fun `sends Google key as query parameter`() = runTest {
        server.enqueue(MockResponse().setBody(googleCatalogJson))

        repository.load(TranslationProvider.GOOGLE, GOOGLE_KEY, forceRefresh = false)

        val request = server.takeRequest()
        request.requestUrl?.queryParameter("key") shouldBe GOOGLE_KEY
        request.requestUrl?.queryParameter("pageSize") shouldBe "1000"

        (repository.snapshot(TranslationProvider.GOOGLE)?.models?.single()?.id) shouldBe "gemini-2.0-flash"
    }

    @Test
    fun `caches each provider separately`() = runTest {
        server.enqueue(MockResponse().setBody(openAiCatalogJson))
        server.enqueue(MockResponse().setBody(googleCatalogJson))

        repository.load(TranslationProvider.OPENAI, OPENAI_KEY, forceRefresh = false)
        repository.load(TranslationProvider.GOOGLE, GOOGLE_KEY, forceRefresh = false)

        repository.snapshot(TranslationProvider.OPENAI)?.models?.map { it.id } shouldBe listOf("gpt-4o")
        repository.snapshot(TranslationProvider.GOOGLE)?.models?.map { it.id } shouldBe listOf("gemini-2.0-flash")
    }

    @Test
    fun `native failure returns failure with cached models`() = runTest {
        server.enqueue(MockResponse().setBody(claudePageJson))

        repository.load(TranslationProvider.CLAUDE, CLAUDE_KEY, forceRefresh = false)
        server.enqueue(MockResponse().setResponseCode(500))
        val result = repository.load(TranslationProvider.CLAUDE, CLAUDE_KEY, forceRefresh = true)

        (result as TranslationCatalogResult.Failure).cachedModels.map { it.id } shouldBe
            listOf("claude-sonnet-4-5")
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

    private fun claudePage(id: String, hasMore: Boolean): MockResponse =
        MockResponse().setBody(
            """
            {"data":[{"id":"$id","type":"model","display_name":"$id"}],
             "has_more":$hasMore,"last_id":"$id"}
            """.trimIndent(),
        )

    private fun enqueueCompatibleCatalog() {
        server.enqueue(
            MockResponse().setBody(compatibleCatalogJson),
        )
    }

    private fun expectedCompatibleModel() = TranslationModelEntry(
        id = "google/gemma-4-26b-a4b-it:free",
        displayName = "Example Free Vision",
        capabilities = TranslationModelCapabilities(
            imageInput = true,
            textOutput = true,
            multilingualOcrAndTranslation = false,
            spatialBounds = false,
            normalizedCoordinates = false,
            originalAndTranslatedFields = false,
            maxOutputTokens = 4_096,
            structuredJsonOutput = true,
            inputModalities = listOf("text", "image"),
            outputModalities = listOf("text"),
            supportedParameters = listOf("response_format"),
        ),
        cost = TranslationModelCost.FREE,
        freeTierEligible = true,
        stability = TranslationModelStability.UNKNOWN,
        dataTerms = null,
        pricing = mapOf("prompt" to "0", "completion" to "0"),
    )

    companion object {
        private const val OPENROUTER_KEY = "openrouter-key"
        private const val CLAUDE_KEY = "claude-key"
        private const val OPENAI_KEY = "openai-key"
        private const val GOOGLE_KEY = "google-key"
        private const val ANTHROPIC_VERSION = "2023-06-01"
        private const val MAX_PAGES = 5
    }
}
