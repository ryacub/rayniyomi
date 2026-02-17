package eu.kanade.tachiyomi.ui.player.utils

import eu.kanade.tachiyomi.animesource.model.TimeStamp
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.days

interface AniSkipCache {
    fun get(
        malId: Long,
        episodeNumber: Int,
        episodeLength: Long,
    ): List<TimeStamp>?

    fun put(
        malId: Long,
        episodeNumber: Int,
        episodeLength: Long,
        timestamps: List<TimeStamp>,
    )
}

class AniSkipDiskCache(
    private val cacheDir: File,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val ttlMs: Long = 7.days.inWholeMilliseconds,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) : AniSkipCache {
    override fun get(
        malId: Long,
        episodeNumber: Int,
        episodeLength: Long,
    ): List<TimeStamp>? {
        val file = entryFile(malId, episodeNumber, episodeLength)
        if (!file.exists()) return null

        val entry = try {
            json.decodeFromString<AniSkipCacheEntry>(file.readText())
        } catch (_: Exception) {
            file.delete()
            return null
        }

        if (nowProvider() - entry.createdAt > ttlMs) {
            file.delete()
            return null
        }

        return entry.timestamps
    }

    override fun put(
        malId: Long,
        episodeNumber: Int,
        episodeLength: Long,
        timestamps: List<TimeStamp>,
    ) {
        val file = entryFile(malId, episodeNumber, episodeLength)
        val entry = AniSkipCacheEntry(
            malId = malId,
            episodeNumber = episodeNumber,
            roundedEpisodeLength = roundedEpisodeLength(episodeLength),
            createdAt = nowProvider(),
            timestamps = timestamps,
        )
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(AniSkipCacheEntry.serializer(), entry))
        }
    }

    private fun entryFile(
        malId: Long,
        episodeNumber: Int,
        episodeLength: Long,
    ): File {
        val roundedLength = roundedEpisodeLength(episodeLength)
        return File(cacheDir, "aniskip_${malId}_${episodeNumber}_$roundedLength.json")
    }
}

internal fun roundedEpisodeLength(episodeLength: Long): Long {
    if (episodeLength <= 0L) return 0L
    return (episodeLength / 5.0).roundToLong() * 5L
}
