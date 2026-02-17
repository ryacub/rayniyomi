package eu.kanade.tachiyomi.ui.player.utils

import androidx.annotation.WorkerThread
import eu.kanade.tachiyomi.animesource.model.TimeStamp
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.days

interface AniSkipCache {
    @WorkerThread
    fun get(
        malId: Long,
        episodeNumber: Double,
        episodeLength: Long,
    ): List<TimeStamp>?

    @WorkerThread
    fun put(
        malId: Long,
        episodeNumber: Double,
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
    @WorkerThread
    override fun get(
        malId: Long,
        episodeNumber: Double,
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

    @WorkerThread
    override fun put(
        malId: Long,
        episodeNumber: Double,
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
        }.onFailure {
            logcat(LogPriority.WARN, it)
        }
    }

    private fun entryFile(
        malId: Long,
        episodeNumber: Double,
        episodeLength: Long,
    ): File {
        val roundedLength = roundedEpisodeLength(episodeLength)
        val normalizedEpisodeNumber = normalizedEpisodeNumber(episodeNumber)
        return File(cacheDir, "aniskip_${malId}_${normalizedEpisodeNumber}_$roundedLength.json")
    }
}

internal fun normalizedEpisodeNumber(episodeNumber: Double): String {
    return if (episodeNumber % 1.0 == 0.0) {
        episodeNumber.toInt().toString()
    } else {
        episodeNumber.toString()
    }
}

internal fun roundedEpisodeLength(episodeLength: Long): Long {
    if (episodeLength <= 0L) return 0L
    return (episodeLength / 5.0).roundToLong() * 5L
}
