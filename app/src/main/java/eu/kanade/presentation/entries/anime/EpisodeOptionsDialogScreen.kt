package eu.kanade.presentation.entries.anime

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Input
import androidx.compose.material.icons.outlined.NavigateNext
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.HosterState
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.QualitySheetHosterContent
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.QualitySheetVideoContent
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.getChangedAt
import eu.kanade.tachiyomi.ui.player.loader.EpisodeLoader
import eu.kanade.tachiyomi.ui.player.loader.HosterLoader
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchUI
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.interactor.GetEpisode
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

class EpisodeOptionsDialogScreen(
    private val useExternalDownloader: Boolean,
    private val episodeTitle: String,
    private val episodeId: Long,
    private val animeId: Long,
    private val sourceId: Long,
) : Screen {

    @Composable
    override fun Content() {
        val sm = rememberScreenModel {
            EpisodeOptionsDialogScreenModel(
                episodeId = episodeId,
                animeId = animeId,
                sourceId = sourceId,
            )
        }

        val episode by sm.episode.collectAsStateWithLifecycle()
        val anime by sm.anime.collectAsStateWithLifecycle()
        val hosterState by sm.hosterState.collectAsStateWithLifecycle()
        val hosterExpandedList by sm.hosterExpandedList.collectAsStateWithLifecycle()
        val selectedHosterVideoIndex by sm.selectedHosterVideoIndex.collectAsStateWithLifecycle()
        val currentVideo by sm.currentVideo.collectAsStateWithLifecycle()
        val showAllQualities by sm.showAllQualities.collectAsStateWithLifecycle()

        EpisodeOptionsDialog(
            useExternalDownloader = useExternalDownloader,
            episodeTitle = episodeTitle,
            episode = episode,
            anime = anime,
            showAllQualities = showAllQualities,
            resultList = hosterState,
            expandedList = hosterExpandedList,
            currentVideo = currentVideo,
            selectedHosterVideoIndex = selectedHosterVideoIndex,
            onShowAllQualities = sm::onShowAllQualities,
            onClickHoster = sm::onClickHoster,
            onClickVideo = sm::onClickVideo,
            getHosterList = sm::getHosterList,
        )
    }

    companion object {
        var onDismissDialog: () -> Unit = {}
    }
}

class EpisodeOptionsDialogScreenModel(
    episodeId: Long,
    animeId: Long,
    sourceId: Long,
) : ScreenModel {
    private val sourceManager: AnimeSourceManager = Injekt.get()

    private val _hosterState = MutableStateFlow<Result<List<HosterState>>?>(null)
    val hosterState = _hosterState.asStateFlow()
    private val _hosterExpandedList = MutableStateFlow<List<Boolean>>(emptyList())
    val hosterExpandedList = _hosterExpandedList.asStateFlow()
    private val _selectedHosterVideoIndex = MutableStateFlow(Pair(-1, -1))
    val selectedHosterVideoIndex = _selectedHosterVideoIndex.asStateFlow()
    private val _currentVideo = MutableStateFlow<Video?>(null)
    val currentVideo = _currentVideo.asStateFlow()

    private val _episode = MutableStateFlow<Episode?>(null)
    val episode = _episode.asStateFlow()
    private val _anime = MutableStateFlow<Anime?>(null)
    val anime = _anime.asStateFlow()

    @Suppress("ktlint:standard:backing-property-naming")
    private val _hosterList = MutableStateFlow<List<Hoster>>(emptyList())

    @Suppress("ktlint:standard:backing-property-naming")
    private val _source = MutableStateFlow<AnimeSource?>(null)

    private val _showAllQualities = MutableStateFlow(false)
    val showAllQualities = _showAllQualities.asStateFlow()

    init {
        val hasFoundPreferredVideo = AtomicBoolean(false)

        screenModelScope.launchIO {
            val episode = Injekt.get<GetEpisode>().await(episodeId)
            if (episode == null) {
                _hosterState.update { _ ->
                    Result.failure(IllegalStateException("Episode is no longer available"))
                }
                return@launchIO
            }

            val anime = Injekt.get<GetAnime>().await(animeId)
            if (anime == null) {
                _hosterState.update { _ ->
                    Result.failure(IllegalStateException("Anime is no longer available"))
                }
                return@launchIO
            }

            val source = sourceManager.getOrStub(sourceId)

            _episode.update { _ -> episode }
            _anime.update { _ -> anime }
            _source.update { _ -> source }

            val hosterListResult = withIOContext {
                try {
                    Result.success(EpisodeLoader.getHosters(episode, anime, source))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

            if (hosterListResult.isFailure) {
                _hosterState.update { _ -> Result.failure(hosterListResult.exceptionOrNull()!!) }
                return@launchIO
            }

            val hosterList = hosterListResult.getOrThrow()
            _hosterList.update { _ -> hosterList }
            _hosterExpandedList.update { _ ->
                List(hosterList.size) { true }
            }

            val initialHosterState = hosterList.map { hoster ->
                val videoList = hoster.videoList
                if (videoList == null) {
                    HosterState.Loading(hoster.hosterName)
                } else {
                    HosterState.Ready(
                        hoster.hosterName,
                        videoList,
                        List(videoList.size) { Video.State.LOAD_VIDEO },
                    )
                }
            }

            _hosterState.update { _ -> Result.success(initialHosterState) }

            try {
                hosterList.mapIndexed { hosterIdx, hoster ->
                    async {
                        val hosterState = EpisodeLoader.loadHosterVideos(source, hoster)

                        _hosterState.updateAt(hosterIdx, hosterState)

                        if (hosterState is HosterState.Ready) {
                            val prefIndex = hosterState.videoList.indexOfFirst { it.preferred }
                            if (prefIndex != -1) {
                                if (hasFoundPreferredVideo.compareAndSet(false, true)) {
                                    val preferredVideo = EpisodeOptionsDialogStateGuard
                                        .readyVideo(Result.success(listOf(hosterState)), 0, prefIndex)
                                        ?.video
                                    val success = preferredVideo != null &&
                                        loadVideo(source, preferredVideo, hosterIdx, prefIndex)
                                    if (!success) {
                                        hasFoundPreferredVideo.set(false)
                                    }
                                }
                            }
                        }
                    }
                }.awaitAll()

                if (hasFoundPreferredVideo.compareAndSet(false, true)) {
                    val hosterStateList = hosterState.value?.getOrNull()
                    if (hosterStateList == null) {
                        _hosterState.update { _ ->
                            Result.failure(IllegalStateException("Hoster state is unavailable"))
                        }
                        return@launchIO
                    }

                    val (hosterIdx, videoIdx) = HosterLoader.selectBestVideo(hosterStateList)
                    if (hosterIdx == -1) {
                        _hosterState.update { _ ->
                            Result.failure(NoSuchElementException("No available videos"))
                        }
                        return@launchIO
                    }

                    val video = EpisodeOptionsDialogStateGuard
                        .readyVideo(Result.success(hosterStateList), hosterIdx, videoIdx)
                        ?.video
                    if (video == null) {
                        _hosterState.update { _ ->
                            Result.failure(IllegalStateException("Selected video is unavailable"))
                        }
                        return@launchIO
                    }

                    loadVideo(source, video, hosterIdx, videoIdx)
                }
            } catch (e: CancellationException) {
                _hosterState.update { _ ->
                    Result.success(hosterList.map { HosterState.Idle(it.hosterName) })
                }

                throw e
            }
        }
    }

    private suspend fun loadVideo(source: AnimeSource, video: Video, hosterIndex: Int, videoIndex: Int): Boolean {
        val selectedHosterState = EpisodeOptionsDialogStateGuard
            .readyVideo(_hosterState.value, hosterIndex, videoIndex)
            ?.hosterState
            ?: return false
        val videoState = selectedHosterState.videoState.getOrNull(videoIndex) ?: return false

        val oldSelectedIndex = _selectedHosterVideoIndex.value
        _selectedHosterVideoIndex.update { _ -> Pair(hosterIndex, videoIndex) }

        _hosterState.updateAt(
            hosterIndex,
            selectedHosterState.getChangedAt(videoIndex, video, Video.State.LOAD_VIDEO),
        )

        val resolvedVideo = if (videoState != Video.State.READY) {
            HosterLoader.getResolvedVideo(source, video)
        } else {
            video
        }

        if (resolvedVideo == null || resolvedVideo.videoUrl.isEmpty()) {
            if (currentVideo.value == null) {
                _hosterState.updateAt(
                    hosterIndex,
                    selectedHosterState.getChangedAt(videoIndex, video, Video.State.ERROR),
                )

                val hosterStateList = hosterState.value?.getOrNull() ?: return false

                val (newHosterIdx, newVideoIdx) = HosterLoader.selectBestVideo(hosterStateList)
                if (newHosterIdx == -1) {
                    _hosterState.update { _ ->
                        Result.failure(NoSuchElementException("No available videos"))
                    }
                    return false
                }

                val newVideo = EpisodeOptionsDialogStateGuard
                    .readyVideo(Result.success(hosterStateList), newHosterIdx, newVideoIdx)
                    ?.video
                    ?: return false

                return loadVideo(source, newVideo, newHosterIdx, newVideoIdx)
            } else {
                _selectedHosterVideoIndex.update { _ -> oldSelectedIndex }
                _hosterState.updateAt(
                    hosterIndex,
                    selectedHosterState.getChangedAt(videoIndex, video, Video.State.ERROR),
                )
                return false
            }
        }

        _hosterState.updateAt(
            hosterIndex,
            selectedHosterState.getChangedAt(videoIndex, resolvedVideo, Video.State.READY),
        )
        _currentVideo.update { _ -> resolvedVideo }

        return true
    }

    private fun <T> MutableStateFlow<Result<List<T>>?>.updateAt(index: Int, newValue: T) {
        this.update { values ->
            values?.getOrNull()?.let {
                Result.success(
                    it.toMutableList().apply {
                        if (index in indices) {
                            this[index] = newValue
                        }
                    },
                )
            } ?: values
        }
    }

    fun onShowAllQualities(value: Boolean) {
        _showAllQualities.update { _ -> value }
    }

    fun onClickHoster(hosterIndex: Int) {
        val hosterState = hosterState.value?.getOrNull()?.getOrNull(hosterIndex) ?: return

        when (hosterState) {
            is HosterState.Ready -> {
                _hosterExpandedList.update { values ->
                    values.toMutableList().apply {
                        this[hosterIndex] = !hosterExpandedList.value[hosterIndex]
                    }
                }
            }
            is HosterState.Error, is HosterState.Idle -> {
                val hosterName = hosterState.name
                val source = _source.value
                val hoster = EpisodeOptionsDialogStateGuard.hosterAt(
                    sourceAvailable = source != null,
                    hosterList = _hosterList.value,
                    hosterIndex = hosterIndex,
                ) ?: return

                _hosterState.updateAt(hosterIndex, HosterState.Loading(hosterName))

                screenModelScope.launchIO {
                    val newHosterState = EpisodeLoader.loadHosterVideos(
                        source ?: return@launchIO,
                        hoster,
                    )
                    _hosterState.updateAt(hosterIndex, newHosterState)
                }
            }
            is HosterState.Loading -> {}
        }
    }

    fun onClickVideo(hosterIndex: Int, videoIndex: Int) {
        val video = EpisodeOptionsDialogStateGuard
            .readyVideo(_hosterState.value, hosterIndex, videoIndex)
            ?.video
            ?: return
        val source = _source.value ?: return

        screenModelScope.launchIO {
            val success = loadVideo(source, video, hosterIndex, videoIndex)
            if (success) {
                _showAllQualities.update { _ -> false }
            }
        }
    }

    fun getHosterList(): List<Hoster>? {
        val hosterStateList = hosterState.value?.getOrNull() ?: return null
        return _hosterList.value.mapIndexed { index, h ->
            val state = hosterStateList.getOrNull(index)
            if (state is HosterState.Ready) {
                Hoster(
                    hosterName = h.hosterName,
                    hosterUrl = h.hosterUrl,
                    videoList = state.videoList,
                )
            } else {
                Hoster(
                    hosterName = h.hosterName,
                    hosterUrl = h.hosterUrl,
                    videoList = h.videoList,
                )
            }
        }
    }
}

internal object EpisodeOptionsDialogStateGuard {

    data class ReadyVideo(
        val hosterState: HosterState.Ready,
        val video: Video,
    )

    fun readyVideo(
        hosterState: Result<List<HosterState>>?,
        hosterIndex: Int,
        videoIndex: Int,
    ): ReadyVideo? {
        val readyHoster = hosterState
            ?.getOrNull()
            ?.getOrNull(hosterIndex) as? HosterState.Ready
            ?: return null
        val video = readyHoster.videoList.getOrNull(videoIndex) ?: return null
        readyHoster.videoState.getOrNull(videoIndex) ?: return null
        return ReadyVideo(readyHoster, video)
    }

    fun hosterAt(
        sourceAvailable: Boolean,
        hosterList: List<Hoster>,
        hosterIndex: Int,
    ): Hoster? {
        if (!sourceAvailable) return null
        return hosterList.getOrNull(hosterIndex)
    }
}

@Composable
fun EpisodeOptionsDialog(
    useExternalDownloader: Boolean,
    episodeTitle: String,
    episode: Episode?,
    anime: Anime?,
    showAllQualities: Boolean,
    resultList: Result<List<HosterState>>? = null,
    expandedList: List<Boolean>,
    currentVideo: Video?,
    selectedHosterVideoIndex: Pair<Int, Int>,
    onShowAllQualities: (Boolean) -> Unit,
    onClickHoster: (Int) -> Unit,
    onClickVideo: (Int, Int) -> Unit,
    getHosterList: () -> List<Hoster>?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .animateContentSize()
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = TabbedDialogPaddings.Vertical)
            .windowInsetsPadding(WindowInsets.systemBars),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        Text(
            text = episodeTitle,
            modifier = Modifier.padding(horizontal = TabbedDialogPaddings.Horizontal),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            style = MaterialTheme.typography.titleSmall,
        )

        Text(
            text = stringResource(AYMR.strings.choose_video_quality),
            modifier = Modifier.padding(horizontal = TabbedDialogPaddings.Horizontal),
            fontStyle = FontStyle.Italic,
            style = MaterialTheme.typography.bodyMedium,
        )

        val onError: () -> Unit = {
            logcat(LogPriority.ERROR) { "Error getting links" }
            scope.launchUI { context.toast("No available videos") }
            EpisodeOptionsDialogScreen.onDismissDialog()
        }
        if (resultList?.isFailure == true) {
            onError()
        }

        if (resultList == null || episode == null || anime == null || currentVideo == null) {
            LoadingScreen()
        } else {
            val hosterStateList = resultList.getOrNull()
            if (!hosterStateList.isNullOrEmpty()) {
                VideoList(
                    useExternalDownloader = useExternalDownloader,
                    episode = episode,
                    anime = anime,
                    showAllQualities = showAllQualities,
                    hosterStateList = hosterStateList,
                    expandedList = expandedList,
                    currentVideo = currentVideo,
                    selectedHosterVideoIndex = selectedHosterVideoIndex,
                    onShowAllQualities = onShowAllQualities,
                    onClickHoster = onClickHoster,
                    onClickVideo = onClickVideo,
                    getHosterList = getHosterList,
                )
            } else {
                onError()
            }
        }
    }
}

@Composable
private fun VideoList(
    useExternalDownloader: Boolean,
    episode: Episode,
    anime: Anime,
    showAllQualities: Boolean,
    hosterStateList: List<HosterState>,
    expandedList: List<Boolean>,
    currentVideo: Video,
    selectedHosterVideoIndex: Pair<Int, Int>,
    onShowAllQualities: (Boolean) -> Unit,
    onClickHoster: (Int) -> Unit,
    onClickVideo: (Int, Int) -> Unit,
    getHosterList: () -> List<Hoster>?,
) {
    val downloadManager = Injekt.get<AnimeDownloadManager>()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val copiedString = stringResource(AYMR.strings.copied_video_link_to_clipboard)

    AnimatedVisibility(
        visible = !showAllQualities,
        enter = slideInHorizontally(),
        exit = slideOutHorizontally(),
    ) {
        Column {
            if (currentVideo.videoUrl.isNotEmpty() && !showAllQualities) {
                ClickableRow(
                    text = currentVideo.videoTitle,
                    icon = null,
                    onClick = { onShowAllQualities(true) },
                    showDropdownArrow = true,
                )

                val downloadEpisode: (Boolean) -> Unit = {
                    downloadManager.downloadEpisodes(
                        anime,
                        listOf(episode),
                        true,
                        it,
                        currentVideo,
                    )
                }

                QualityOptions(
                    onDownloadClicked = { downloadEpisode(useExternalDownloader) },
                    onExtDownloadClicked = { downloadEpisode(!useExternalDownloader) },
                    onCopyClicked = {
                        clipboardManager.setText(AnnotatedString(currentVideo.videoUrl))
                        scope.launch { context.toast(copiedString) }
                    },
                    onExtPlayerClicked = {
                        scope.launch {
                            MainActivity.startPlayerActivity(
                                context,
                                anime.id,
                                episode.id,
                                true,
                                currentVideo,
                            )
                        }
                    },
                    onIntPlayerClicked = {
                        scope.launch {
                            MainActivity.startPlayerActivity(
                                context,
                                anime.id,
                                episode.id,
                                false,
                                currentVideo,
                                selectedHosterVideoIndex.first,
                                selectedHosterVideoIndex.second,
                                getHosterList(),
                            )
                        }
                    },
                )
            }
        }
    }

    AnimatedVisibility(
        visible = showAllQualities,
        enter = slideInHorizontally(initialOffsetX = { it / 2 }),
        exit = slideOutHorizontally(targetOffsetX = { it / 2 }),
    ) {
        if (showAllQualities) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TabbedDialogPaddings.Horizontal)
                    .heightIn(max = 600.dp),
            ) {
                if (
                    hosterStateList.size == 1 &&
                    hosterStateList.first().name == Hoster.NO_HOSTER_LIST &&
                    hosterStateList.first() is HosterState.Ready
                ) {
                    QualitySheetVideoContent(
                        videoList = (hosterStateList.first() as HosterState.Ready).videoList,
                        videoState = (hosterStateList.first() as HosterState.Ready).videoState,
                        selectedVideoIndex = selectedHosterVideoIndex.second,
                        onClickVideo = onClickVideo,
                    )
                } else {
                    QualitySheetHosterContent(
                        hosterState = hosterStateList,
                        expandedState = expandedList,
                        selectedVideoIndex = selectedHosterVideoIndex,
                        onClickHoster = onClickHoster,
                        onClickVideo = onClickVideo,
                        displayHosters = Pair(false, false),
                    )
                }
            }
        }
    }
}

@Composable
private fun QualityOptions(
    onDownloadClicked: () -> Unit = {},
    onExtDownloadClicked: () -> Unit = {},
    onCopyClicked: () -> Unit = {},
    onExtPlayerClicked: () -> Unit = {},
    onIntPlayerClicked: () -> Unit = {},
) {
    val closeMenu = { EpisodeOptionsDialogScreen.onDismissDialog() }

    Column {
        ClickableRow(
            text = stringResource(MR.strings.copy),
            icon = Icons.Outlined.ContentCopy,
            onClick = { onCopyClicked() },
        )

        ClickableRow(
            text = stringResource(AYMR.strings.action_start_download_internally),
            icon = Icons.Outlined.Download,
            onClick = {
                onDownloadClicked()
                closeMenu()
            },
        )

        ClickableRow(
            text = stringResource(AYMR.strings.action_start_download_externally),
            icon = Icons.Outlined.SystemUpdateAlt,
            onClick = {
                onExtDownloadClicked()
                closeMenu()
            },
        )

        ClickableRow(
            text = stringResource(AYMR.strings.action_play_externally),
            icon = Icons.Outlined.OpenInNew,
            onClick = {
                onExtPlayerClicked()
                closeMenu()
            },
        )

        ClickableRow(
            text = stringResource(AYMR.strings.action_play_internally),
            icon = Icons.Outlined.Input,
            onClick = {
                onIntPlayerClicked()
                closeMenu()
            },
        )
    }
}

@Composable
private fun ClickableRow(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    showDropdownArrow: Boolean = false,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = TabbedDialogPaddings.Horizontal)
            .clickable(role = Role.DropdownList, onClick = onClick)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var textPadding = MaterialTheme.padding.medium

        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.width(MaterialTheme.padding.small))

            textPadding = MaterialTheme.padding.small
        }
        Text(
            text = text,
            modifier = Modifier.padding(vertical = textPadding),
            style = MaterialTheme.typography.bodyMedium,
        )

        if (showDropdownArrow) {
            Icon(
                imageVector = Icons.Outlined.NavigateNext,
                contentDescription = null,
                modifier = Modifier,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
