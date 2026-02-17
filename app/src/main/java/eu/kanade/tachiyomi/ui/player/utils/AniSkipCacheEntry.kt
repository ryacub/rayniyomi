package eu.kanade.tachiyomi.ui.player.utils

import eu.kanade.tachiyomi.animesource.model.TimeStamp
import kotlinx.serialization.Serializable

@Serializable
internal data class AniSkipCacheEntry(
    val malId: Long,
    val episodeNumber: Int,
    val roundedEpisodeLength: Long,
    val createdAt: Long,
    val timestamps: List<TimeStamp>,
)
