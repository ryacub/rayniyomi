package eu.kanade.tachiyomi.data.filler

import android.content.Context
import eu.kanade.tachiyomi.di.appGraph
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class AnimeFillerListSource internal constructor(
    context: Context? = null,
    private val network: NetworkHelper? = null,
    private val cache: AnimeFillerCache = context?.let {
        AnimeFillerDiskCache(File(it.cacheDir, CACHE_DIRECTORY))
    } ?: error("A cache is required outside Android"),
    private val nowProvider: () -> Long = System::currentTimeMillis,
    private val fetchHtml: suspend (String) -> String = { url ->
        withContext(Dispatchers.IO) {
            requireNotNull(network ?: appGraph.networkHelper)
                .client.newCall(GET(url)).awaitSuccess().use { it.body.string() }
        }
    },
) : AnimeFillerSource {

    private val catalogMutex = Mutex()
    private var catalog: Map<String, List<AnimeFillerCatalogEntry>>? = null
    private var catalogFailedAt: Long? = null

    override suspend fun getFillerEpisodes(title: String): Set<Double>? {
        val normalizedTitle = AnimeFillerListParser.normalizeTitle(title)
        if (normalizedTitle.isEmpty()) return null

        val now = nowProvider()
        return try {
            cache.get(normalizedTitle, now)?.let { result ->
                return result.fillerEpisodes.takeIf { result.matched }
            }

            val candidates = getCatalog()[normalizedTitle].orEmpty()
            if (candidates.size != 1) {
                cache.put(normalizedTitle, AnimeFillerCachedResult(false, emptySet()), now)
                return null
            }

            val detail = AnimeFillerListParser.parseDetail(
                fetchHtml(BASE_URL + candidates.single().path),
            ) ?: return null
            if (AnimeFillerListParser.normalizeTitle(detail.title) != normalizedTitle) return null

            val result = AnimeFillerCachedResult(true, detail.fillerEpisodes)
            cache.put(normalizedTitle, result, now)
            detail.fillerEpisodes
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun getCatalog(): Map<String, List<AnimeFillerCatalogEntry>> = catalogMutex.withLock {
        catalog?.let { return it }
        catalogFailedAt?.let {
            check(nowProvider() - it !in 0 until CATALOG_RETRY_MS) { "Anime filler catalog failed recently" }
        }
        try {
            val entries = AnimeFillerListParser.parseCatalog(fetchHtml(CATALOG_URL))
                ?: error("Anime filler catalog is malformed")
            entries.groupBy { AnimeFillerListParser.normalizeTitle(it.title) }
                .also {
                    catalog = it
                    catalogFailedAt = null
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            catalogFailedAt = nowProvider()
            throw e
        }
    }

    companion object {
        private const val BASE_URL = "https://www.animefillerlist.com"
        private const val CATALOG_URL = "$BASE_URL/shows"
        private const val CACHE_DIRECTORY = "anime_filler"
        private const val CATALOG_RETRY_MS = 10 * 60 * 1000L
    }
}
