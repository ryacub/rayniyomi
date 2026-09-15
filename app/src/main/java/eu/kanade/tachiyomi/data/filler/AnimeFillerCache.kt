package eu.kanade.tachiyomi.data.filler

import androidx.annotation.WorkerThread
import eu.kanade.tachiyomi.util.lang.Hash
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File

internal data class AnimeFillerCachedResult(
    val matched: Boolean,
    val fillerEpisodes: Set<Double>,
)

internal interface AnimeFillerCache {
    @WorkerThread
    fun get(title: String, now: Long): AnimeFillerCachedResult?

    @WorkerThread
    fun put(title: String, result: AnimeFillerCachedResult, now: Long)
}

internal class AnimeFillerDiskCache(
    private val cacheDirectory: File,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : AnimeFillerCache {

    @WorkerThread
    override fun get(title: String, now: Long): AnimeFillerCachedResult? {
        val file = entryFile(title)
        if (!file.exists()) return null

        val entry = runCatching {
            json.decodeFromString<AnimeFillerCacheEntry>(file.readText())
        }.getOrElse {
            file.delete()
            return null
        }

        if (now - entry.createdAt < 0L || now - entry.createdAt >= TTL_MS) {
            file.delete()
            return null
        }

        return AnimeFillerCachedResult(
            matched = entry.matched,
            fillerEpisodes = entry.fillerEpisodes.toSet(),
        )
    }

    @WorkerThread
    override fun put(title: String, result: AnimeFillerCachedResult, now: Long) {
        val file = entryFile(title)
        val entry = AnimeFillerCacheEntry(
            matched = result.matched,
            fillerEpisodes = result.fillerEpisodes.toList(),
            createdAt = now,
        )
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(AnimeFillerCacheEntry.serializer(), entry))
        }.onFailure {
            logcat(LogPriority.WARN, it) { "Failed to cache anime filler data" }
        }
    }

    private fun entryFile(title: String): File {
        val key = Hash.sha256("v1:${AnimeFillerListParser.normalizeTitle(title)}")
        return File(cacheDirectory, "$key.json")
    }

    companion object {
        const val TTL_MS = 7 * 24 * 60 * 60 * 1_000L
    }
}

@Serializable
private data class AnimeFillerCacheEntry(
    val matched: Boolean,
    val fillerEpisodes: List<Double>,
    val createdAt: Long,
)
