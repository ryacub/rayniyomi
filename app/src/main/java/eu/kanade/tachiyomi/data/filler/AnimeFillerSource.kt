package eu.kanade.tachiyomi.data.filler

interface AnimeFillerSource {
    suspend fun getFillerEpisodes(title: String): Set<Double>?
}
