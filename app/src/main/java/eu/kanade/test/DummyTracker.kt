package eu.kanade.test

import android.graphics.Color
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.MangaTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.model.MangaTrackSearch
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import okhttp3.OkHttpClient
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.domain.track.anime.model.AnimeTrack as DomainAnimeTrack
import tachiyomi.domain.track.manga.model.MangaTrack as DomainMangaTrack

data class DummyTracker(
    override val id: Long,
    override val name: String,
    override val supportsReadingDates: Boolean = false,
    override val supportsPrivateTracking: Boolean = false,
    override val isLoggedIn: Boolean = false,
    override val isLoggedInFlow: Flow<Boolean> = flowOf(false),
    val valLogoColor: Int = Color.rgb(18, 25, 35),
    val valLogo: Int = R.drawable.ic_tracker_anilist,
    val valStatuses: List<Long> = (1L..6L).toList(),
    val valCompletionStatus: Long = 2,
    val valScoreList: ImmutableList<String> = (0..10).map(Int::toString).toImmutableList(),
    val val10PointScore: Double = 5.4,
    val valMangaSearchResults: List<MangaTrackSearch> = listOf(),
    val valAnimeSearchResults: List<AnimeTrackSearch> = listOf(),
) : Tracker, MangaTracker, AnimeTracker {

    override val client: OkHttpClient
        get() = error("DummyTracker has no HTTP client")

    override fun getLogoColor(): Int = valLogoColor

    override fun getLogo(): Int = valLogo

    override fun getCompletionStatus(): Long = valCompletionStatus

    override fun getScoreList(): ImmutableList<String> = valScoreList

    override suspend fun login(username: String, password: String) = Unit

    override fun logout() = Unit

    override fun getUsername(): String = "username"

    override fun getPassword(): String = "passw0rd"

    override fun saveCredentials(username: String, password: String) = Unit

    override fun indexToScore(index: Int): Double = index.toDouble()

    override fun getStatusListManga(): List<Long> = valStatuses

    override fun getStatusListAnime(): List<Long> = valStatuses

    override fun getReadingStatus(): Long = valStatuses.first()

    override fun getRereadingStatus(): Long = valStatuses[3]

    override fun getWatchingStatus(): Long = valStatuses.first()

    override fun getRewatchingStatus(): Long = valStatuses[3]

    override fun displayScore(track: DomainMangaTrack): String = val10PointScore.toString()

    override fun displayScore(track: DomainAnimeTrack): String = val10PointScore.toString()

    override suspend fun update(track: MangaTrack, didReadChapter: Boolean): MangaTrack = track

    override suspend fun update(track: AnimeTrack, didWatchEpisode: Boolean): AnimeTrack = track

    override suspend fun bind(track: MangaTrack, hasReadChapters: Boolean): MangaTrack = track

    override suspend fun bind(track: AnimeTrack, hasSeenEpisodes: Boolean): AnimeTrack = track

    override suspend fun searchManga(query: String): List<MangaTrackSearch> = valMangaSearchResults

    override suspend fun searchAnime(query: String): List<AnimeTrackSearch> = valAnimeSearchResults

    override suspend fun refresh(track: MangaTrack): MangaTrack = track

    override suspend fun refresh(track: AnimeTrack): AnimeTrack = track

    override fun getStatusForManga(status: Long): StringResource? = MR.strings.reading

    override fun getStatusForAnime(status: Long): StringResource? = AYMR.strings.watching
}
