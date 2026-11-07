package com.lagradost.cloudstream3.syncproviders.providers

/**
 * Declares the AniList response types that .cs3 plugins reference by name.
 * The outer class is intentionally empty. Jackson loads it as the enclosing class.
 * Property names match the AniList JSON keys, so no property annotations are needed.
 */
class AniListApi {

    data class Title(
        val english: String?,
        val romaji: String?,
    )

    data class CoverImage(
        val medium: String?,
        val large: String?,
        val extraLarge: String?,
    )

    data class LikePageInfo(
        val total: Int?,
        val currentPage: Int?,
        val lastPage: Int?,
        val perPage: Int?,
        val hasNextPage: Boolean?,
    )

    data class RecommendationConnection(
        val edges: List<Any?> = emptyList(),
        val nodes: List<Any?> = emptyList(),
    )

    data class SeasonNextAiringEpisode(
        val episode: Int?,
        val timeUntilAiring: Int?,
    )
}
