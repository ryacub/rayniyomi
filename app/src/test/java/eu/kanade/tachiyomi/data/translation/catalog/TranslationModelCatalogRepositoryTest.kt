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
          "context_length":4096,
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
        repository = TranslationModelCatalogRepository(
            catalogEndpoint = server.url("/api/v1/models").toString(),
            nowEpochMilliseconds = { currentTime },
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `caches a successful catalog for twenty four hours`() = runTest {
        enqueueCompatibleCatalog()

        repository.load(TranslationProvider.OPENROUTER, forceRefresh = false)
        currentTime += 23 * 60 * 60 * 1_000L
        val result = repository.load(TranslationProvider.OPENROUTER, forceRefresh = false)

        (result as TranslationCatalogResult.Success).fromCache shouldBe true
        server.requestCount shouldBe 1
    }

    @Test
    fun `refreshes manually even when cached snapshot is fresh`() = runTest {
        enqueueCompatibleCatalog()
        repository.load(TranslationProvider.OPENROUTER, forceRefresh = false)

        enqueueCompatibleCatalog()
        val refreshed = repository.load(TranslationProvider.OPENROUTER, forceRefresh = true)

        (refreshed as TranslationCatalogResult.Success).fromCache shouldBe false
        server.requestCount shouldBe 2
    }

    @Test
    fun `returns sanitized failure with cached models when refresh fails`() = runTest {
        enqueueCompatibleCatalog()
        repository.load(TranslationProvider.OPENROUTER, forceRefresh = false)

        server.enqueue(MockResponse().setResponseCode(500))
        val result = repository.load(TranslationProvider.OPENROUTER, forceRefresh = true)

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
            cancellingRepository.load(TranslationProvider.OPENROUTER, forceRefresh = true)
        } catch (error: CancellationException) {
            thrown = error
        }
        thrown shouldBe cancellation
    }

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
            multilingualOcrAndTranslation = true,
            spatialBounds = true,
            normalizedCoordinates = true,
            originalAndTranslatedFields = true,
            minimumOutputTokens = 4_096,
            structuredJsonOutput = true,
        ),
        cost = TranslationModelCost.FREE,
        freeTierEligible = true,
        stability = TranslationModelStability.UNKNOWN,
        dataTerms = null,
    )
}
