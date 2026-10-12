package tachiyomi.domain.items.episode.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.items.episode.repository.EpisodeRepository

class GetWatchTimeByAnimeId(
    private val episodeRepository: EpisodeRepository,
) {

    suspend fun await(): Map<Long, Long> {
        return try {
            episodeRepository.getWatchTimeByAnimeId()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyMap()
        }
    }
}
