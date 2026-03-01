package eu.kanade.tachiyomi.data.download.anime.model

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.download.model.DownloadBlockedReason
import eu.kanade.tachiyomi.data.download.model.DownloadDisplayStatus
import eu.kanade.tachiyomi.data.download.model.DownloadPriority
import eu.kanade.tachiyomi.data.download.model.DownloadStatusSnapshot
import eu.kanade.tachiyomi.network.ProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.interactor.GetEpisode
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.atomic.AtomicLong

data class AnimeDownload(
    val source: AnimeHttpSource,
    val anime: Anime,
    val episode: Episode,
    val changeDownloader: Boolean = false,
    var video: Video? = null,
    var priority: DownloadPriority = DownloadPriority.NORMAL,
) : ProgressListener, DownloadStatusSnapshot {

    @Transient
    private val _statusFlow = MutableStateFlow(State.NOT_DOWNLOADED)

    @Transient
    val statusFlow = _statusFlow.asStateFlow()
    var status: State
        get() = _statusFlow.value
        set(status) {
            _statusFlow.value = status
        }

    @Transient
    private val _displayStatusFlow = MutableStateFlow(DownloadDisplayStatus.PREPARING)

    @Transient
    val displayStatusFlow = _displayStatusFlow.asStateFlow()
    override var displayStatus: DownloadDisplayStatus
        get() = _displayStatusFlow.value
        set(value) {
            _displayStatusFlow.value = value
        }

    @Transient
    var blockedReason: DownloadBlockedReason? = null

    override val isRunningTransfer: Boolean
        get() = status == State.DOWNLOADING

    @Transient
    private val lastProgressAtAtomic = AtomicLong(0L)
    override var lastProgressAt: Long
        get() = lastProgressAtAtomic.get()
        set(value) {
            lastProgressAtAtomic.set(value)
        }

    @Transient
    override var retryAttempt: Int = 0

    @Transient
    var lastErrorCode: String? = null

    @Transient
    override var lastErrorReason: String? = null

    @Transient
    private val progressStateFlow = MutableStateFlow(0)

    @Transient
    val progressFlow = progressStateFlow.asStateFlow()
    var progress: Int
        get() = progressStateFlow.value
        set(value) {
            progressStateFlow.value = value
        }

    /**
     * Updates the status of the download
     *
     * @param bytesRead the updated TOTAL number of bytes read (not a partial increment)
     * @param contentLength the updated content length
     * @param done whether progress has completed or not
     */
    override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
        val newProgress = if (contentLength > 0) {
            (100 * bytesRead / contentLength).toInt()
        } else {
            -1
        }
        if (progress != newProgress) progress = newProgress
    }

    enum class State(val value: Int) {
        NOT_DOWNLOADED(0),
        QUEUE(1),
        DOWNLOADING(2),
        DOWNLOADED(3),
        ERROR(4),
    }

    companion object {
        suspend fun fromEpisodeId(
            episodeId: Long,
            getEpisode: GetEpisode = Injekt.get(),
            getAnimeById: GetAnime = Injekt.get(),
            sourceManager: AnimeSourceManager = Injekt.get(),
        ): AnimeDownload? {
            val episode = getEpisode.await(episodeId) ?: return null
            val anime = getAnimeById.await(episode.animeId) ?: return null
            val source = sourceManager.get(anime.source) as? AnimeHttpSource ?: return null

            return AnimeDownload(source, anime, episode)
        }
    }
}
