package eu.kanade.tachiyomi.ui.player.loader

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.HosterState
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.getChangedAt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Orchestrates hoster loading and video selection for the player.
 * Extracted from PlayerViewModel to improve separation of concerns and testability.
 */
class HosterOrchestrator(
    private val scope: CoroutineScope,
) {
    private companion object {
        const val HOSTER_LOAD_PARALLELISM = 4
    }

    private val hosterLoadDispatcher = Dispatchers.IO.limitedParallelism(HOSTER_LOAD_PARALLELISM)

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

    private var getHosterVideoLinksJob: Job? = null

    fun updateIsLoadingHosters(value: Boolean) {
        _isLoadingHosters.update { value }
    }

    fun reset() {
        _hosterState.update { emptyList() }
        _hosterList.update { emptyList() }
        _hosterExpandedList.update { emptyList() }
        _selectedHosterVideoIndex.update { Pair(-1, -1) }
    }

    fun cancelHosterVideoLinksJob() {
        getHosterVideoLinksJob?.cancel()
    }

    fun loadHosters(source: AnimeSource, hosterList: List<Hoster>, hosterIndex: Int, videoIndex: Int) {
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
                    } else if (hoster.videoList == null) {
                        HosterState.Loading(hoster.hosterName)
                    } else {
                        val videoList = hoster.videoList!!
                        HosterState.Ready(
                            hoster.hosterName,
                            videoList,
                            List(videoList.size) { Video.State.QUEUE },
                        )
                    }
                }
            }

            try {
                coroutineScope {
                    hosterList.mapIndexed { hosterIdx, hoster ->
                        async {
                            val hosterState = EpisodeLoader.loadHosterVideos(source, hoster)

                            _hosterState.updateAt(hosterIdx, hosterState)

                            if (hosterState is HosterState.Ready) {
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
                                            val success =
                                                loadVideo(
                                                    source,
                                                    hosterState.videoList[prefIndex],
                                                    hosterIdx,
                                                    prefIndex,
                                                )
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
                        val (hosterIdx, videoIdx) = HosterLoader.selectBestVideo(hosterState.value)
                        if (hosterIdx == -1) {
                            throw Exception("No available videos")
                        }

                        val video = (hosterState.value[hosterIdx] as HosterState.Ready).videoList[videoIdx]

                        loadVideo(source, video, hosterIdx, videoIdx)
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

    private suspend fun loadVideo(source: AnimeSource?, video: Video, hosterIndex: Int, videoIndex: Int): Boolean {
        var currentHosterIndex = hosterIndex
        var currentVideoIndex = videoIndex
        var currentVideo = video
        val oldSelectedIndex = _selectedHosterVideoIndex.value

        onLoadingStateChanged?.invoke(true)

        while (true) {
            val selectedHosterState =
                (_hosterState.value.getOrNull(currentHosterIndex) as? HosterState.Ready)
                    ?: return false

            _selectedHosterVideoIndex.update { Pair(currentHosterIndex, currentVideoIndex) }
            _hosterState.updateAt(
                currentHosterIndex,
                selectedHosterState.getChangedAt(currentVideoIndex, currentVideo, Video.State.LOAD_VIDEO),
            )

            onPauseRequested?.invoke()

            val resolvedVideo = if (selectedHosterState.videoState[currentVideoIndex] != Video.State.READY) {
                HosterLoader.getResolvedVideo(source, currentVideo)
            } else {
                currentVideo
            }

            if (resolvedVideo == null || resolvedVideo.videoUrl.isEmpty()) {
                if (this.currentVideo.value == null) {
                    _hosterState.updateAt(
                        currentHosterIndex,
                        selectedHosterState.getChangedAt(currentVideoIndex, currentVideo, Video.State.ERROR),
                    )

                    val (newHosterIdx, newVideoIdx) = HosterLoader.selectBestVideo(hosterState.value)
                    if (newHosterIdx == -1) {
                        if (_hosterState.value.any { it is HosterState.Loading }) {
                            _selectedHosterVideoIndex.update { Pair(-1, -1) }
                            return false
                        } else {
                            throw Exception("No available videos")
                        }
                    }

                    val newVideo = (hosterState.value[newHosterIdx] as HosterState.Ready).videoList[newVideoIdx]
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

            _hosterState.updateAt(
                currentHosterIndex,
                selectedHosterState.getChangedAt(currentVideoIndex, resolvedVideo, Video.State.READY),
            )

            _currentVideo.update { resolvedVideo }

            onVideoReady?.invoke(resolvedVideo)
            return true
        }
    }

    fun onVideoClicked(
        hosterIndex: Int,
        videoIndex: Int,
        currentSource: AnimeSource?,
        onSuccess: () -> Unit,
        onFailure: () -> Unit,
    ) {
        val hosterState = _hosterState.value[hosterIndex] as? HosterState.Ready
        val video = hosterState?.videoList
            ?.getOrNull(videoIndex)
            ?: return

        val videoState = hosterState.videoState
            .getOrNull(videoIndex)
            ?: return

        if (videoState == Video.State.ERROR) {
            return
        }

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
        when (hosterState.value[index]) {
            is HosterState.Ready -> {
                _hosterExpandedList.updateAt(index, !_hosterExpandedList.value[index])
            }
            is HosterState.Idle -> {
                val hosterName = hosterList.value[index].hosterName
                _hosterState.updateAt(index, HosterState.Loading(hosterName))

                scope.launch(hosterLoadDispatcher) {
                    val hosterState = EpisodeLoader.loadHosterVideos(
                        source = currentSource!!,
                        hoster = hosterList.value[index],
                        force = true,
                    )
                    _hosterState.updateAt(index, hosterState)
                }
            }
            is HosterState.Loading, is HosterState.Error -> {}
        }
    }

    private fun <T> MutableStateFlow<List<T>>.updateAt(index: Int, newValue: T) {
        this.update { values ->
            values.toMutableList().apply {
                this[index] = newValue
            }
        }
    }
}
