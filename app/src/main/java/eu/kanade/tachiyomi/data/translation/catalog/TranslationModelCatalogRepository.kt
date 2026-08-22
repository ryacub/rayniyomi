package eu.kanade.tachiyomi.data.translation.catalog

import eu.kanade.tachiyomi.data.translation.TranslationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

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
    private val catalogEndpoint: String = "https://openrouter.ai/api/v1/models",
    private val nowEpochMilliseconds: () -> Long = System::currentTimeMillis,
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val cacheByProvider = mutableMapOf<TranslationProvider, TranslationModelCatalog>()

    fun snapshot(provider: TranslationProvider): TranslationModelCatalog? = cacheByProvider[provider]

    suspend fun load(provider: TranslationProvider, forceRefresh: Boolean): TranslationCatalogResult =
        withContext(Dispatchers.IO) {
            val now = nowEpochMilliseconds()
            val cachedCatalog = cacheByProvider[provider]
            if (!forceRefresh && cachedCatalog != null && !isExpired(cachedCatalog, now)) {
                TranslationCatalogResult.Success(cachedCatalog, fromCache = true)
            } else {
                fetchAndCache(provider, now)
            }
        }

    private fun isExpired(catalog: TranslationModelCatalog, now: Long): Boolean {
        val age = now - catalog.fetchedAtEpochMilliseconds
        return age < 0 || age >= CACHE_DURATION_MILLISECONDS
    }

    private fun fetchAndCache(provider: TranslationProvider, now: Long): TranslationCatalogResult {
        return try {
            val responseBody = client.newCall(catalogRequest(provider)).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) {
                    throw IOException("Provider returned HTTP ${response.code}")
                }
                body
            }
            val parsedCatalog = when (provider) {
                TranslationProvider.OPENROUTER -> OpenRouterCatalogParser.parse(responseBody, now)
                else -> throw UnsupportedOperationException("Catalog endpoint is not configured for ${provider.displayName}")
            }
            val compatibleModels = TranslationModelCatalogFilter.filter(parsedCatalog.models)
            if (compatibleModels.isEmpty()) {
                throw IllegalArgumentException("No compatible free models are available.")
            }
            val catalog = parsedCatalog.copy(models = compatibleModels)
            cacheByProvider[provider] = catalog
            TranslationCatalogResult.Success(catalog, fromCache = false)
        } catch (_: Exception) {
            val cachedModels = cacheByProvider[provider]?.models.orEmpty()
            TranslationCatalogResult.Failure(
                "The model list could not be updated. Check the connection.",
                cachedModels,
            )
        }
    }

    private fun catalogRequest(provider: TranslationProvider): Request = Request.Builder()
        .url(catalogEndpoint)
        .get()
        .build()

    companion object {
        const val CACHE_DURATION_MILLISECONDS = 24L * 60L * 60L * 1_000L
    }
}
