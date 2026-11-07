package eu.kanade.tachiyomi.ui.player.loader

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.HosterState
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.getChangedAt
import eu.kanade.tachiyomi.util.lang.Hash
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Orchestrates hoster loading and video selection for the player.
 * Extracted from PlayerViewModel to improve separation of concerns and testability.
 */
class HosterOrchestrator(
    private val scope: CoroutineScope,
    private val onNoVideosAvailable: suspend () -> Unit,
    private val hosterLoadDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(HOSTER_LOAD_PARALLELISM),
) {
    private companion object {
        const val HOSTER_LOAD_PARALLELISM = 4
        const val VIDEO_ID_PREFIX = "video:"
    }

    private val _hosterList = MutableStateFlow<List<Hoster>>(emptyList())
    val hosterList: StateFlow<List<Hoster>> = _hosterList.asStateFlow()

    private val _isLoadingHosters = MutableStateFlow(true)
    val isLoadingHosters: StateFlow<Boolean> = _isLoadingHosters.asStateFlow()

    private val _hosterState = MutableStateFlow<List<HosterState>>(emptyList())
    val hosterState: StateFlow<List<HosterState>> = _hosterState.asStateFlow()

    private val _hosterExpandedList = MutableStateFlow<List<Boolean>>(emptyList())
    val hosterExpandedList: StateFlow<List<Boolean>> = _hosterExpandedList.asStateFlow()

    private val _selectedHosterVideoIndex = MutableStateFlow(Pair(-1, -1))
    val selectedHosterVideoIndex: StateFlow<Pair<Int, Int>> = _selectedHosterVideoIndex.asStateFlow()

    private val _currentVideo = MutableStateFlow<Video?>(null)
    val currentVideo: StateFlow<Video?> = _currentVideo.asStateFlow()

    var onVideoReady: ((Video) -> Unit)? = null
    var onLoadingStateChanged: ((Boolean) -> Unit)? = null
    var onPauseRequested: (() -> Unit)? = null

    @Volatile
    private var getHosterVideoLinksJob: Job? = null

    private val recoveryMutex = Mutex()

    @Volatile
    private var recoveryJob: Job? = null

    // Bumped by every new selection, so a recovery started before it cannot replace it.
    private val selectionGeneration = AtomicLong()

    fun updateIsLoadingHosters(value: Boolean) {
        _isLoadingHosters.update { value }
    }

    fun reset() {
        invalidateRecovery()
        _hosterState.update { emptyList() }
        _hosterList.update { emptyList() }
        _hosterExpandedList.update { emptyList() }
        _selectedHosterVideoIndex.update { Pair(-1, -1) }
    }

    fun cancelHosterVideoLinksJob() {
        invalidateRecovery()
        getHosterVideoLinksJob?.cancel()
    }

    private fun invalidateRecovery(): Long {
        val generation = synchronized(this) { selectionGeneration.incrementAndGet() }
        recoveryJob?.cancel()
        return generation
    }

    fun loadHosters(
        source: AnimeSource,
        hosterList: List<Hoster>,
        hosterIndex: Int,
        videoIndex: Int,
        rememberedServer: String? = null,
    ) {
        val generation = invalidateRecovery()
        val rememberedIndex = rememberedServer?.let { rememberedHosterIndex(hosterList, it) }
        val hasFoundPreferredVideo = AtomicBoolean(false)

        _hosterList.update { hosterList }
        _hosterExpandedList.update {
            List(hosterList.size) { true }
        }

        getHosterVideoLinksJob?.cancel()
        getHosterVideoLinksJob = scope.launch(hosterLoadDispatcher) {
            _hosterState.update {
                hosterList.map { hoster ->
                    if (hoster.lazy) {
                        HosterState.Idle(hoster.hosterName)
                    } else {
                        hoster.videoList?.let { videoList ->
                            HosterState.Ready(
                                hoster.hosterName,
                                videoList,
                                List(videoList.size) { Video.State.QUEUE },
                            )
                        } ?: HosterState.Loading(hoster.hosterName)
                    }
                }
            }

            try {
                coroutineScope {
                    hosterList.mapIndexed { hosterIdx, hoster ->
                        async {
                            val hosterState = EpisodeLoader.loadHosterVideos(
                                source,
                                hoster,
                                force = hosterIdx == rememberedIndex,
                            )

                            _hosterState.updateAt(hosterIdx, hosterState)

                            if (rememberedServer != null && hosterIdx == rememberedIndex) {
                                if (hosterState is HosterState.Ready) {
                                    hasFoundPreferredVideo.set(
                                        tryRememberedServer(source, hosterIdx, rememberedServer, generation),
                                    )
                                }
                                return@async
                            }

                            if (rememberedIndex == null && hosterState is HosterState.Ready) {
                                if (hosterIdx == hosterIndex) {
                                    hosterState.videoList.getOrNull(videoIndex)?.let {
                                        hasFoundPreferredVideo.set(true)
                                        val success = loadVideo(source, it, hosterIndex, videoIndex)
                                        if (!success) {
                                            hasFoundPreferredVideo.set(false)
                                        }
                                    }
                                }

                                val prefIndex = hosterState.videoList.indexOfFirst { it.preferred }
                                if (prefIndex != -1 && hosterIndex == -1) {
                                    if (hasFoundPreferredVideo.compareAndSet(false, true)) {
                                        if (selectedHosterVideoIndex.value == Pair(-1, -1)) {
                                            val success = hosterState.videoList.getOrNull(prefIndex)
                                                ?.let { preferredVideo ->
                                                    loadVideo(
                                                        source,
                                                        preferredVideo,
                                                        hosterIdx,
                                                        prefIndex,
                                                    )
                                                }
                                                ?: false
                                            if (!success) {
                                                hasFoundPreferredVideo.set(false)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }.awaitAll()

                    if (hasFoundPreferredVideo.compareAndSet(false, true)) {
                        val fallbackGeneration = generation.takeIf { rememberedIndex != null }
                        if (!loadBestAvailableVideo(source, fallbackGeneration) && !isStale(fallbackGeneration)) {
                            onNoVideosAvailable()
                        }
                    }
                }
            } catch (e: CancellationException) {
                _hosterState.update {
                    hosterList.map { HosterState.Idle(it.hosterName) }
                }

                throw e
            }
        }
    }

    private suspend fun tryRememberedServer(
        source: AnimeSource,
        index: Int,
        server: String,
        generation: Long,
    ): Boolean {
        if (isStale(generation)) return false
        val hoster = hosterState.value.getOrNull(index) as? HosterState.Ready ?: return false
        val videoIndex = if (server.startsWith(VIDEO_ID_PREFIX)) {
            hoster.videoList.indices.singleOrNull { videoIdentity(hoster.videoList[it]) == server } ?: return false
        } else {
            HosterLoader.selectBestVideo(listOf(hoster)).second
        }
        val video = hoster.videoList.getOrNull(videoIndex) ?: return false
        return loadVideo(source, video, index, videoIndex, generation)
    }

    private fun rememberedHosterIndex(hosters: List<Hoster>, server: String): Int? {
        if (server.startsWith(VIDEO_ID_PREFIX)) {
            return 0.takeIf { hosters.singleOrNull()?.hosterName == Hoster.NO_HOSTER_LIST }
        }
        return hosters.indices.singleOrNull { hosterIdentity(hosters[it]) == server }
    }

    private fun serverIdentity(hosterIndex: Int, hoster: HosterState.Ready, video: Video): String? {
        val hosters = hosterList.value
        val selectedHoster = hosters.getOrNull(hosterIndex) ?: return null
        val identity = hosterIdentity(selectedHoster)
        if (identity != null) return identity.takeIf { rememberedHosterIndex(hosters, it) == hosterIndex }
        if (hosters.size != 1 || selectedHoster.hosterName != Hoster.NO_HOSTER_LIST) return null
        return videoIdentity(video)?.takeIf { key -> hoster.videoList.count { videoIdentity(it) == key } == 1 }
    }

    private fun hosterIdentity(hoster: Hoster): String? = hoster.hosterName
        .takeIf { it.isNotBlank() && it != Hoster.NO_HOSTER_LIST }
        ?.let { "hoster:${Hash.sha256(it)}" }

    private fun videoIdentity(video: Video): String? = video.videoTitle
        .takeIf(String::isNotBlank)
        ?.let { "$VIDEO_ID_PREFIX${Hash.sha256(it)}" }

    /** Marks [failedVideo] failed and loads the next server. Ignores a video that is no longer current. */
    fun onVideoLoadFailed(failedVideo: Video, source: AnimeSource?) {
        val job = scope.launch(hosterLoadDispatcher) {
            recoveryMutex.withLock { recoverFrom(failedVideo, source) }
        }
        recoveryJob = job
    }

    private suspend fun recoverFrom(failedVideo: Video, source: AnimeSource?) {
        val generation = synchronized(this) {
            if (_currentVideo.value !== failedVideo) return
            val (hosterIndex, videoIndex) = _selectedHosterVideoIndex.value
            val failedHoster = _hosterState.value.getOrNull(hosterIndex) as? HosterState.Ready ?: return
            // The selection moves before the video loads, so check that it still points at the failed one.
            if (failedHoster.videoList.getOrNull(videoIndex) !== failedVideo) return

            _hosterState.updateAt(hosterIndex, failedHoster.getChangedAt(videoIndex, failedVideo, Video.State.ERROR))
            _currentVideo.update { null }
            _selectedHosterVideoIndex.update { Pair(-1, -1) }
            selectionGeneration.get()
        }

        while (true) {
            val checkedState = _hosterState.value
            if (loadBestAvailableVideo(source, generation)) return
            // A hoster can start or finish loading while a candidate resolves.
            if (_hosterState.value !== checkedState) continue
            if (checkedState.any { it is HosterState.Loading }) {
                _hosterState.first { it !== checkedState }
                continue
            }

            if (isStale(generation)) return
            onLoadingStateChanged?.invoke(false)
            // A lazy hoster the user has not loaded may still work.
            if (checkedState.none { it is HosterState.Idle }) {
                onNoVideosAvailable()
            }
            return
        }
    }

    /** Returns true when a video loaded, or when a newer selection than [generation] took over. */
    private suspend fun loadBestAvailableVideo(source: AnimeSource?, generation: Long? = null): Boolean {
        while (true) {
            if (isStale(generation)) return true
            val (hosterIndex, videoIndex) = HosterLoader.selectBestVideo(hosterState.value)
            if (hosterIndex == -1) return false

            val video = readyVideoAt(hosterIndex, videoIndex) ?: return false
            if (loadVideo(source, video, hosterIndex, videoIndex, generation)) return true
        }
    }

    /** With a [generation], the load is dropped if a newer selection started while it resolved. */
    private suspend fun loadVideo(
        source: AnimeSource?,
        video: Video,
        hosterIndex: Int,
        videoIndex: Int,
        generation: Long? = null,
    ): Boolean {
        var currentHosterIndex = hosterIndex
        var currentVideoIndex = videoIndex
        var currentVideo = video
        val oldSelectedIndex = _selectedHosterVideoIndex.value

        if (isStale(generation)) return false
        onLoadingStateChanged?.invoke(true)

        while (true) {
            val selectedHosterState =
                (_hosterState.value.getOrNull(currentHosterIndex) as? HosterState.Ready)
                    ?: return false
            val videoState = selectedHosterState.videoState.getOrNull(currentVideoIndex)
            if (videoState == null) {
                return false
            }

            _selectedHosterVideoIndex.update { Pair(currentHosterIndex, currentVideoIndex) }
            _hosterState.updateAt(
                currentHosterIndex,
                selectedHosterState.getChangedAt(currentVideoIndex, currentVideo, Video.State.LOAD_VIDEO),
            )

            onPauseRequested?.invoke()

            val resolvedVideo = try {
                if (videoState != Video.State.READY) {
                    HosterLoader.getResolvedVideo(source, currentVideo)
                } else {
                    currentVideo
                }
            } catch (e: CancellationException) {
                revertLoadingState(currentHosterIndex, currentVideoIndex, currentVideo, videoState)
                throw e
            }

            if (resolvedVideo == null || resolvedVideo.videoUrl.isEmpty()) {
                // A resolver that ignored cancellation must not touch a newer selection's state.
                if (isStale(generation)) {
                    revertLoadingState(currentHosterIndex, currentVideoIndex, currentVideo, videoState)
                    return false
                }
                if (this.currentVideo.value == null) {
                    _hosterState.updateAt(
                        currentHosterIndex,
                        selectedHosterState.getChangedAt(currentVideoIndex, currentVideo, Video.State.ERROR),
                    )

                    val (newHosterIdx, newVideoIdx) = HosterLoader.selectBestVideo(hosterState.value)
                    if (newHosterIdx == -1) {
                        if (_hosterState.value.any { it is HosterState.Loading }) {
                            _selectedHosterVideoIndex.update { Pair(-1, -1) }
                        }
                        return false
                    }

                    val newVideo = readyVideoAt(newHosterIdx, newVideoIdx)
                    if (newVideo == null) {
                        return false
                    }
                    currentHosterIndex = newHosterIdx
                    currentVideoIndex = newVideoIdx
                    currentVideo = newVideo
                    continue
                } else {
                    _selectedHosterVideoIndex.update { oldSelectedIndex }
                    _hosterState.updateAt(
                        currentHosterIndex,
                        selectedHosterState.getChangedAt(currentVideoIndex, currentVideo, Video.State.ERROR),
                    )
                    return false
                }
            }

            synchronized(this) {
                if (isStale(generation)) {
                    revertLoadingState(currentHosterIndex, currentVideoIndex, currentVideo, videoState)
                    return false
                }
                _hosterState.updateAt(
                    currentHosterIndex,
                    selectedHosterState.getChangedAt(currentVideoIndex, resolvedVideo, Video.State.READY),
                )

                _currentVideo.update { resolvedVideo }

                onVideoReady?.invoke(resolvedVideo)
            }
            return true
        }
    }

    private fun isStale(generation: Long?): Boolean =
        generation != null && selectionGeneration.get() != generation

    private fun revertLoadingState(hosterIndex: Int, videoIndex: Int, video: Video, previousState: Video.State) {
        val hoster = _hosterState.value.getOrNull(hosterIndex) as? HosterState.Ready ?: return
        // After an episode change the slot can hold another video.
        if (hoster.videoList.getOrNull(videoIndex) !== video) return
        if (hoster.videoState.getOrNull(videoIndex) != Video.State.LOAD_VIDEO) return
        _hosterState.updateAt(hosterIndex, hoster.getChangedAt(videoIndex, video, previousState))
    }

    fun onVideoClicked(
        hosterIndex: Int,
        videoIndex: Int,
        currentSource: AnimeSource?,
        onSuccess: () -> Unit,
        onFailure: () -> Unit,
        onSelection: (String?) -> Unit = {},
    ) {
        val hosterState = _hosterState.value.getOrNull(hosterIndex) as? HosterState.Ready ?: return
        val video = hosterState.videoList.getOrNull(videoIndex)
            ?: return

        val videoState = hosterState.videoState
            .getOrNull(videoIndex)
            ?: return

        if (videoState == Video.State.ERROR) {
            return
        }

        invalidateRecovery()
        onSelection(serverIdentity(hosterIndex, hosterState, video))
        scope.launch(hosterLoadDispatcher) {
            val success = loadVideo(currentSource, video, hosterIndex, videoIndex)
            if (success) {
                onSuccess()
            } else {
                onFailure()
            }
        }
    }

    fun onHosterClicked(index: Int, currentSource: AnimeSource?) {
        when (hosterState.value.getOrNull(index) ?: return) {
            is HosterState.Ready -> {
                val isExpanded = _hosterExpandedList.value.getOrNull(index) ?: return
                _hosterExpandedList.updateAt(index, !isExpanded)
            }
            is HosterState.Idle -> {
                val source = currentSource ?: return
                val hoster = hosterList.value.getOrNull(index) ?: return
                val hosterName = hoster.hosterName
                _hosterState.updateAt(index, HosterState.Loading(hosterName))

                scope.launch(hosterLoadDispatcher) {
                    val hosterState = EpisodeLoader.loadHosterVideos(
                        source = source,
                        hoster = hoster,
                        force = true,
                    )
                    _hosterState.updateAt(index, hosterState)
                }
            }
            is HosterState.Loading, is HosterState.Error -> {}
        }
    }

    private fun readyVideoAt(hosterIndex: Int, videoIndex: Int): Video? {
        return (_hosterState.value.getOrNull(hosterIndex) as? HosterState.Ready)
            ?.videoList
            ?.getOrNull(videoIndex)
    }

    private fun <T> MutableStateFlow<List<T>>.updateAt(index: Int, newValue: T) {
        this.update { values ->
            values.toMutableList().apply {
                if (index in indices) {
                    this[index] = newValue
                }
            }
        }
    }
}
