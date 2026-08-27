package eu.kanade.tachiyomi.data.translation.catalog

import eu.kanade.tachiyomi.data.translation.TranslationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

sealed class TranslationCatalogResult {
    data class Success(
        val catalog: TranslationModelCatalog,
        val fromCache: Boolean,
    ) : TranslationCatalogResult()

    data class Failure(
        val reason: String,
        val cachedModels: List<TranslationModelEntry>,
    ) : TranslationCatalogResult()
}

class TranslationModelCatalogRepository(
    private val client: OkHttpClient = OkHttpClient(),
    private val catalogEndpoints: Map<TranslationProvider, String> = DEFAULT_ENDPOINTS,
    private val nowEpochMilliseconds: () -> Long = System::currentTimeMillis,
    private val executeRequest: (Request) -> Response = { request ->
        client.newCall(request).execute()
    },
) {

    private val cacheByProvider = ConcurrentHashMap<TranslationProvider, TranslationModelCatalog>()

    fun snapshot(provider: TranslationProvider): TranslationModelCatalog? = cacheByProvider[provider]

    suspend fun load(
        provider: TranslationProvider,
        apiKey: String,
        forceRefresh: Boolean,
    ): TranslationCatalogResult = withContext(Dispatchers.IO) {
        val now = nowEpochMilliseconds()
        val cachedCatalog = cacheByProvider[provider]
        if (!forceRefresh && cachedCatalog != null && !isExpired(cachedCatalog, now)) {
            TranslationCatalogResult.Success(cachedCatalog, fromCache = true)
        } else {
            fetchAndCache(provider, apiKey, now)
        }
    }

    private fun isExpired(catalog: TranslationModelCatalog, now: Long): Boolean {
        val age = now - catalog.fetchedAtEpochMilliseconds
        return age < 0 || age >= CACHE_DURATION_MILLISECONDS
    }

    private fun fetchAndCache(
        provider: TranslationProvider,
        apiKey: String,
        now: Long,
    ): TranslationCatalogResult {
        return try {
            val endpoint = requireNotNull(catalogEndpoints[provider]) {
                "Catalog endpoint is not configured for ${provider.displayName}"
            }
            val models = when (provider) {
                TranslationProvider.OPENROUTER ->
                    OpenRouterCatalogParser.parse(executeAndRead(openRouterRequest(endpoint)), now).models
                TranslationProvider.CLAUDE ->
                    fetchClaudeModels(endpoint, apiKey)
                TranslationProvider.OPENAI ->
                    OpenAICatalogParser.parse(executeAndRead(openAiRequest(endpoint, apiKey)), now).models
                TranslationProvider.GOOGLE ->
                    GeminiCatalogParser.parse(executeAndRead(googleRequest(endpoint, apiKey)), now).models
                TranslationProvider.NONE ->
                    throw IllegalArgumentException("No translation provider is selected.")
            }
            val storedModels = if (provider == TranslationProvider.OPENROUTER) {
                models
            } else {
                TranslationModelCatalogFilter.filter(models, provider)
            }
            if (storedModels.isEmpty() && provider != TranslationProvider.OPENROUTER) {
                throw IllegalArgumentException("No compatible models are available.")
            }
            val catalog = TranslationModelCatalog(
                provider = provider,
                fetchedAtEpochMilliseconds = now,
                models = storedModels,
            )
            cacheByProvider[provider] = catalog
            TranslationCatalogResult.Success(catalog, fromCache = false)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            val cachedModels = cacheByProvider[provider]?.models.orEmpty()
            TranslationCatalogResult.Failure(
                "The model list could not be updated. Check the connection.",
                cachedModels,
            )
        }
    }

    /**
     * Walks Claude's cursor pagination with after_id until has_more is false or the
     * request cap is reached.
     */
    private fun fetchClaudeModels(endpoint: String, apiKey: String): List<TranslationModelEntry> {
        val models = mutableListOf<TranslationModelEntry>()
        var afterId: String? = null
        repeat(MAX_PAGES) {
            val url = endpoint.toHttpUrl().newBuilder()
                .setQueryParameter("limit", CLAUDE_PAGE_LIMIT.toString())
                .apply { afterId?.let { setQueryParameter("after_id", it) } }
                .build()
            val page = ClaudeCatalogParser.parsePage(executeAndRead(claudeRequest(url, apiKey)))
            models += page.models
            afterId = page.lastId
            if (!page.hasMore || page.lastId == null) return models
        }
        return models
    }

    private fun openRouterRequest(endpoint: String) = Request.Builder()
        .url(
            endpoint.toHttpUrl().newBuilder()
                .setQueryParameter("output_modalities", "all")
                .build(),
        )
        .get()
        .build()

    private fun claudeRequest(url: okhttp3.HttpUrl, apiKey: String) = Request.Builder()
        .url(url)
        .header("x-api-key", apiKey)
        .header("anthropic-version", ANTHROPIC_VERSION)
        .get()
        .build()

    private fun openAiRequest(endpoint: String, apiKey: String) = Request.Builder()
        .url(endpoint)
        .header("Authorization", "Bearer $apiKey")
        .get()
        .build()

    private fun googleRequest(endpoint: String, apiKey: String): Request {
        val url = endpoint.toHttpUrl().newBuilder()
            .setQueryParameter("pageSize", GOOGLE_PAGE_SIZE.toString())
            .setQueryParameter("key", apiKey)
            .build()
        return Request.Builder()
            .url(url)
            .get()
            .build()
    }

    private fun executeAndRead(request: Request): String =
        executeRequest(request).use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                throw IOException("Provider returned HTTP ${response.code}")
            }
            body
        }

    companion object {
        const val CACHE_DURATION_MILLISECONDS = 24L * 60L * 60L * 1_000L

        private const val ANTHROPIC_VERSION = "2023-06-01"
        private const val CLAUDE_PAGE_LIMIT = 1_000
        private const val GOOGLE_PAGE_SIZE = 1_000
        private const val MAX_PAGES = 5

        private val DEFAULT_ENDPOINTS = mapOf(
            TranslationProvider.OPENROUTER to "https://openrouter.ai/api/v1/models",
            TranslationProvider.CLAUDE to "https://api.anthropic.com/v1/models",
            TranslationProvider.OPENAI to "https://api.openai.com/v1/models",
            TranslationProvider.GOOGLE to
                "https://generativelanguage.googleapis.com/v1beta/models",
        )
    }
}
