/*
 * Copyright 2024 Abdallah Mehiz
 * https://github.com/abdallahmehiz/mpvKt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.kanade.tachiyomi.ui.player

import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.items.episode.model.toDbEpisode
import eu.kanade.tachiyomi.data.database.models.anime.Episode
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.util.episode.filterDownloadedEpisodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.service.getEpisodeSort

/**
 * Manages episode list initialization, filtering, and navigation state for PlayerViewModel.
 * Extracted to reduce complexity and improve separation of concerns.
 */
internal class PlayerEpisodeListManager(
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
    private val downloadManager: AnimeDownloadManager,
    private val basePreferences: BasePreferences,
) {
    private val _currentPlaylist = MutableStateFlow<List<Episode>>(emptyList())
    val currentPlaylist = _currentPlaylist.asStateFlow()

    private val _hasPreviousEpisode = MutableStateFlow(false)
    val hasPreviousEpisode = _hasPreviousEpisode.asStateFlow()

    private val _hasNextEpisode = MutableStateFlow(false)
    val hasNextEpisode = _hasNextEpisode.asStateFlow()

    private val _currentEpisode = MutableStateFlow<Episode?>(null)
    val currentEpisode = _currentEpisode.asStateFlow()

    var episodeId: Long = -1L

    /**
     * Initialize the episode list for the given anime.
     * Retrieves episodes, sorts them, filters by downloaded-only preference if enabled,
     * and converts to database Episode objects.
     */
    suspend fun initEpisodeList(anime: Anime): List<Episode> {
        val episodes = withContext(Dispatchers.IO) {
            getEpisodesByAnimeId.await(anime.id)
        }

        return episodes
            .sortedWith(getEpisodeSort(anime, sortDescending = false))
            .run {
                if (basePreferences.downloadedOnly().get()) {
                    filterDownloadedEpisodes(anime)
                } else {
                    this
                }
            }
            .map { it.toDbEpisode() }
    }

    /**
     * Filter the episode list based on anime preferences.
     * Filters by seen/unseen, downloaded, bookmarked, and fillermarked status.
     * Always includes the selected episode even if it doesn't match filters.
     */
    fun filterEpisodeList(episodes: List<Episode>, anime: Anime?): List<Episode> {
        val currentAnime = anime ?: return episodes
        val selectedEpisode = episodes.find { it.id == episodeId }
            ?: error("Requested episode of id $episodeId not found in episode list")

        if (!hasActiveFilters(currentAnime)) {
            return episodes
        }

        val episodesForPlayer = episodes.filterNot {
            currentAnime.unseenFilterRaw == Anime.EPISODE_SHOW_SEEN &&
                !it.seen ||
                currentAnime.unseenFilterRaw == Anime.EPISODE_SHOW_UNSEEN &&
                it.seen ||
                currentAnime.downloadedFilterRaw == Anime.EPISODE_SHOW_DOWNLOADED &&
                !downloadManager.isEpisodeDownloaded(
                    it.name,
                    it.scanlator,
                    currentAnime.title,
                    currentAnime.source,
                ) ||
                currentAnime.downloadedFilterRaw == Anime.EPISODE_SHOW_NOT_DOWNLOADED &&
                downloadManager.isEpisodeDownloaded(
                    it.name,
                    it.scanlator,
                    currentAnime.title,
                    currentAnime.source,
                ) ||
                currentAnime.bookmarkedFilterRaw == Anime.EPISODE_SHOW_BOOKMARKED &&
                !it.bookmark ||
                currentAnime.bookmarkedFilterRaw == Anime.EPISODE_SHOW_NOT_BOOKMARKED &&
                it.bookmark ||
                currentAnime.fillermarkedFilterRaw == Anime.EPISODE_SHOW_FILLERMARKED &&
                !it.fillermark ||
                currentAnime.fillermarkedFilterRaw == Anime.EPISODE_SHOW_NOT_FILLERMARKED &&
                it.fillermark
        }.toMutableList()

        if (episodesForPlayer.all { it.id != episodeId }) {
            episodesForPlayer += listOf(selectedEpisode)
        }

        return episodesForPlayer
    }

    private fun hasActiveFilters(anime: Anime): Boolean {
        return anime.unseenFilterRaw != Anime.SHOW_ALL ||
            anime.downloadedFilterRaw != Anime.SHOW_ALL ||
            anime.bookmarkedFilterRaw != Anime.SHOW_ALL ||
            anime.fillermarkedFilterRaw != Anime.SHOW_ALL
    }

    /**
     * Update the episode list with filtering and navigation state updates.
     */
    fun updateEpisodeList(episodeList: List<Episode>, anime: Anime?) {
        _currentPlaylist.update { filterEpisodeList(episodeList, anime) }
    }

    /**
     * Get the index of the current episode in the playlist.
     */
    fun getCurrentEpisodeIndex(): Int {
        return currentPlaylist.value.indexOfFirst { currentEpisode.value?.id == it.id }
    }

    /**
     * Get the ID of the adjacent episode (previous or next).
     * Returns -1L if at boundaries.
     */
    fun getAdjacentEpisodeId(previous: Boolean): Long {
        val currentIndex = getCurrentEpisodeIndex()
        val newIndex = if (previous) currentIndex - 1 else currentIndex + 1

        return when {
            previous && currentIndex == 0 -> -1L
            !previous && currentPlaylist.value.lastIndex == currentIndex -> -1L
            else -> currentPlaylist.value.getOrNull(newIndex)?.id ?: -1L
        }
    }

    /**
     * Update whether there is a next episode available.
     */
    fun updateHasNextEpisode(value: Boolean) {
        _hasNextEpisode.update { value }
    }

    /**
     * Update whether there is a previous episode available.
     */
    fun updateHasPreviousEpisode(value: Boolean) {
        _hasPreviousEpisode.update { value }
    }

    /**
     * Update both navigation states based on current episode index.
     * Convenience method to update has-previous and has-next in one call.
     */
    fun updateNavigationState() {
        val currentIndex = getCurrentEpisodeIndex()
        _hasPreviousEpisode.update { currentIndex != 0 }
        _hasNextEpisode.update { currentIndex != currentPlaylist.value.size - 1 }
    }

    /**
     * Set the current episode.
     */
    fun setCurrentEpisode(episode: Episode) {
        _currentEpisode.update { episode }
    }
}
