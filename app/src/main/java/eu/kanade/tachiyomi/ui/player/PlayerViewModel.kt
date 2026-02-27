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

/**
 * Code is a mix between PlayerViewModel from mpvKt and the former
 * PlayerViewModel from Aniyomi.
 */

package eu.kanade.tachiyomi.ui.player

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.inputmethod.InputMethodManager
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.entries.anime.interactor.SetAnimeViewerFlags
import eu.kanade.domain.source.anime.interactor.GetAnimeIncognitoState
import eu.kanade.domain.track.anime.interactor.TrackEpisode
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.more.settings.screen.player.custombutton.CustomButtonFetchState
import eu.kanade.presentation.more.settings.screen.player.custombutton.getButtons
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SerializableHoster.Companion.toHosterList
import eu.kanade.tachiyomi.animesource.model.TimeStamp
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.database.models.anime.Episode
import eu.kanade.tachiyomi.data.database.models.anime.isRecognizedNumber
import eu.kanade.tachiyomi.data.database.models.anime.toDomainEpisode
import eu.kanade.tachiyomi.data.database.models.manga.isRecognizedNumber
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.data.saver.Image
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.saver.Location
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.ui.player.cast.CastManager
import eu.kanade.tachiyomi.ui.player.cast.CastState
import eu.kanade.tachiyomi.ui.player.controls.components.IndexedSegment
import eu.kanade.tachiyomi.ui.player.loader.EpisodeLoader
import eu.kanade.tachiyomi.ui.player.loader.HosterOrchestrator
import eu.kanade.tachiyomi.ui.player.loader.PlayerMediaOrchestrator
import eu.kanade.tachiyomi.ui.player.settings.GesturePreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.player.utils.AniSkipApi
import eu.kanade.tachiyomi.ui.player.utils.AniSkipCache
import eu.kanade.tachiyomi.ui.player.utils.AniSkipDiskCache
import eu.kanade.tachiyomi.ui.player.utils.AniSkipTrackerKind
import eu.kanade.tachiyomi.ui.player.utils.TrackSelect
import eu.kanade.tachiyomi.ui.player.utils.resolveMalId
import eu.kanade.tachiyomi.ui.reader.SaveImageNotifier
import eu.kanade.tachiyomi.util.editBackground
import eu.kanade.tachiyomi.util.editCover
import eu.kanade.tachiyomi.util.editThumbnail
import eu.kanade.tachiyomi.util.lang.byteSize
import eu.kanade.tachiyomi.util.lang.takeBytes
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.storage.cacheImageDir
import eu.kanade.tachiyomi.util.system.toast
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.Utils
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.custombuttons.interactor.GetCustomButtons
import tachiyomi.domain.custombuttons.model.CustomButton
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.history.anime.interactor.GetNextEpisodes
import tachiyomi.domain.history.anime.interactor.UpsertAnimeHistory
import tachiyomi.domain.history.anime.model.AnimeHistoryUpdate
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.interactor.UpdateEpisode
import tachiyomi.domain.items.episode.model.EpisodeUpdate
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.source.local.entries.anime.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.InputStream
import java.util.Date

class PlayerViewModelProviderFactory(
    private val activity: PlayerActivity,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        return PlayerViewModel(activity, extras.createSavedStateHandle()) as T
    }
}

class PlayerViewModel @JvmOverloads constructor(
    private val activity: PlayerActivity,
    private val savedState: SavedStateHandle,
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val downloadManager: AnimeDownloadManager = Injekt.get(),
    private val imageSaver: ImageSaver = Injekt.get(),
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
    private val trackPreferences: TrackPreferences = Injekt.get(),
    private val trackEpisode: TrackEpisode = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
    private val getNextEpisodes: GetNextEpisodes = Injekt.get(),
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get(),
    private val getAnimeCategories: GetAnimeCategories = Injekt.get(),
    private val getTracks: GetAnimeTracks = Injekt.get(),
    private val upsertHistory: UpsertAnimeHistory = Injekt.get(),
    private val updateEpisode: UpdateEpisode = Injekt.get(),
    private val setAnimeViewerFlags: SetAnimeViewerFlags = Injekt.get(),
    internal val playerPreferences: PlayerPreferences = Injekt.get(),
    internal val gesturePreferences: GesturePreferences = Injekt.get(),
    private val basePreferences: BasePreferences = Injekt.get(),
    private val getCustomButtons: GetCustomButtons = Injekt.get(),
    private val trackSelect: TrackSelect = Injekt.get(),
    private val getIncognitoState: GetAnimeIncognitoState = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    uiPreferences: UiPreferences = Injekt.get(),
    private val aniSkipApi: AniSkipApi = AniSkipApi(),
    private val aniSkipCache: AniSkipCache = AniSkipDiskCache(activity.applicationContext.cacheDir),
    private val castManager: CastManager = Injekt.get(),
) : ViewModel() {

    private val episodeListManager = PlayerEpisodeListManager(
        getEpisodesByAnimeId = getEpisodesByAnimeId,
        downloadManager = downloadManager,
        basePreferences = basePreferences,
    )
    val currentPlaylist = episodeListManager.currentPlaylist
    val hasPreviousEpisode = episodeListManager.hasPreviousEpisode
    val hasNextEpisode = episodeListManager.hasNextEpisode
    val currentEpisode = episodeListManager.currentEpisode

    private val _currentAnime = MutableStateFlow<Anime?>(null)
    val currentAnime = _currentAnime.asStateFlow()

    private val _currentSource = MutableStateFlow<AnimeSource?>(null)
    val currentSource = _currentSource.asStateFlow()

    private val _isEpisodeOnline = MutableStateFlow(false)
    val isEpisodeOnline = _isEpisodeOnline.asStateFlow()

    private val _isLoadingEpisode = MutableStateFlow(false)
    val isLoadingEpisode = _isLoadingEpisode.asStateFlow()

    private val _currentDecoder = MutableStateFlow(getDecoderFromValue(MPVLib.getPropertyString("hwdec")))
    val currentDecoder = _currentDecoder.asStateFlow()

    val mediaTitle = MutableStateFlow("")
    val animeTitle = MutableStateFlow("")

    val isLoading = MutableStateFlow(true)
    val playbackSpeed = MutableStateFlow(playerPreferences.playerSpeed().get())

    private val hosterOrchestrator = HosterOrchestrator(viewModelScope).apply {
        onVideoReady = { video ->
            // Update quality index for SavedState persistence
            qualityIndex = selectedHosterVideoIndex.value
            activity.setVideo(video)
        }
        onLoadingStateChanged = { isLoading ->
            updateIsLoadingEpisode(isLoading)
        }
        onPauseRequested = {
            updatePausedState()
            pause()
        }
    }

    val hosterList = hosterOrchestrator.hosterList
    val isLoadingHosters = hosterOrchestrator.isLoadingHosters
    val hosterState = hosterOrchestrator.hosterState
    val hosterExpandedList = hosterOrchestrator.hosterExpandedList
    val selectedHosterVideoIndex = hosterOrchestrator.selectedHosterVideoIndex
    val currentVideo = hosterOrchestrator.currentVideo

    private val mediaOrchestrator = PlayerMediaOrchestrator(
        scope = viewModelScope,
        context = activity,
        playerPreferences = playerPreferences,
        trackSelect = trackSelect,
    ).apply {
        onTracksLoaded = {
            if (!isLoadingTracks.value) {
                onFinishLoadingTracks()
            }
        }
        onSeekRequested = { seekValue, text ->
            seekToWithText(seekValue, text ?: "")
        }
        onToastRequested = { message ->
            activity.showToast(message)
        }
    }

    val subtitleTracks = mediaOrchestrator.subtitleTracks
    val selectedSubtitles = mediaOrchestrator.selectedSubtitles
    val audioTracks = mediaOrchestrator.audioTracks
    val selectedAudio = mediaOrchestrator.selectedAudio
    val isLoadingTracks = mediaOrchestrator.isLoadingTracks
    val chapters = mediaOrchestrator.chapters
    val currentChapter = mediaOrchestrator.currentChapter
    val skipIntroText = mediaOrchestrator.skipIntroText

    // Cast -->
    val isCasting = castManager.castState
        .map { it == CastState.CONNECTED }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _castProgress = MutableStateFlow(0L)
    val castProgress = _castProgress.asStateFlow()
    // <-- Cast

    private val _pos = MutableStateFlow(0f)
    val pos = _pos.asStateFlow()

    val duration = MutableStateFlow(0f)

    private val _readAhead = MutableStateFlow(0f)
    val readAhead = _readAhead.asStateFlow()

    private val _paused = MutableStateFlow(false)
    val paused = _paused.asStateFlow()

    // False because the video shouldn't start paused
    private val _pausedState = MutableStateFlow<Boolean?>(false)
    val pausedState = _pausedState.asStateFlow()

    private val _controlsShown = MutableStateFlow(!playerPreferences.hideControls().get())
    val controlsShown = _controlsShown.asStateFlow()
    private val _seekBarShown = MutableStateFlow(!playerPreferences.hideControls().get())
    val seekBarShown = _seekBarShown.asStateFlow()
    private val _areControlsLocked = MutableStateFlow(false)
    val areControlsLocked = _areControlsLocked.asStateFlow()

    val playerUpdate = MutableStateFlow<PlayerUpdates>(PlayerUpdates.None)
    val isBrightnessSliderShown = MutableStateFlow(false)
    val isVolumeSliderShown = MutableStateFlow(false)
    val currentBrightness = MutableStateFlow(
        runCatching {
            Settings.System.getFloat(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                .normalize(0f, 255f, 0f, 1f)
        }.getOrElse { 0f },
    )
    val currentVolume = MutableStateFlow(activity.audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    val currentMPVVolume = MutableStateFlow(MPVLib.getPropertyInt("volume"))
    var volumeBoostCap: Int = MPVLib.getPropertyInt("volume-max")

    // Pair(startingPosition, seekAmount)
    val gestureSeekAmount = MutableStateFlow<Pair<Int, Int>?>(null)

    val sheetShown = MutableStateFlow(Sheets.None)
    val panelShown = MutableStateFlow(Panels.None)
    val dialogShown = MutableStateFlow<Dialogs>(Dialogs.None)

    private val _dismissSheet = MutableStateFlow(false)
    val dismissSheet = _dismissSheet.asStateFlow()

    private val _seekText = MutableStateFlow<String?>(null)
    val seekText = _seekText.asStateFlow()
    private val _doubleTapSeekAmount = MutableStateFlow(0)
    val doubleTapSeekAmount = _doubleTapSeekAmount.asStateFlow()
    private val _isSeekingForwards = MutableStateFlow(false)
    val isSeekingForwards = _isSeekingForwards.asStateFlow()

    private var timerJob: Job? = null
    private val _remainingTime = MutableStateFlow(0)
    val remainingTime = _remainingTime.asStateFlow()

    val cachePath: String = activity.cacheDir.path

    private val _customButtons = MutableStateFlow<CustomButtonFetchState>(CustomButtonFetchState.Loading)
    val customButtons = _customButtons.asStateFlow()

    private val _primaryButtonTitle = MutableStateFlow("")
    val primaryButtonTitle = _primaryButtonTitle.asStateFlow()

    private val _primaryButton = MutableStateFlow<CustomButton?>(null)
    val primaryButton = _primaryButton.asStateFlow()

    init {
        viewModelScope.launchIO {
            try {
                val buttons = getCustomButtons.getAll()
                buttons.firstOrNull { it.isFavorite }?.let {
                    _primaryButton.update { _ -> it }
                    // If the button text is not empty, it has been set buy a lua script in which
                    // case we don't want to override it
                    if (_primaryButtonTitle.value.isEmpty()) {
                        setPrimaryCustomButtonTitle(it)
                    }
                }
                activity.setupCustomButtons(buttons)
                _customButtons.update { _ -> CustomButtonFetchState.Success(buttons.toImmutableList()) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                _customButtons.update { _ -> CustomButtonFetchState.Error(e.message ?: "Unable to fetch buttons") }
            }
        }
    }

    /**
     * Starts a sleep timer/cancels the current timer if [seconds] is less than 1.
     */
    fun startTimer(seconds: Int) {
        timerJob?.cancel()
        _remainingTime.value = seconds
        if (seconds < 1) return
        timerJob = viewModelScope.launch {
            for (time in seconds downTo 0) {
                _remainingTime.value = time
                delay(1000)
            }
            pause()
            withUIContext { Injekt.get<Application>().toast(AYMR.strings.toast_sleep_timer_ended) }
        }
    }

    fun isEpisodeOnline(): Boolean? {
        val anime = currentAnime.value ?: return null
        val episode = currentEpisode.value ?: return null
        val source = currentSource.value ?: return null
        return source is AnimeHttpSource &&
            !EpisodeLoader.isDownload(
                episode.toDomainEpisode()!!,
                anime,
            )
    }

    fun updateIsLoadingEpisode(value: Boolean) {
        _isLoadingEpisode.update { _ -> value }
    }

    private fun updateEpisodeList(episodeList: List<Episode>) {
        episodeListManager.updateEpisodeList(episodeList, currentAnime.value)
    }

    fun getDecoder() {
        _currentDecoder.update { getDecoderFromValue(activity.player.hwdecActive) }
    }

    fun updateDecoder(decoder: Decoder) {
        MPVLib.setPropertyString("hwdec", decoder.value)
    }

    fun loadTracks() {
        mediaOrchestrator.loadTracks()
    }

    /**
     * When all subtitle/audio tracks are loaded, select the preferred one based on preferences,
     * or select the first one in the list if trackSelect fails.
     */
    fun onFinishLoadingTracks() {
        val preferredSubtitle = trackSelect.getPreferredTrackIndex(subtitleTracks.value)
        (preferredSubtitle ?: subtitleTracks.value.firstOrNull())?.let {
            activity.player.sid = it.id
            activity.player.secondarySid = -1
        }

        val preferredAudio = trackSelect.getPreferredTrackIndex(audioTracks.value, subtitle = false)
        (preferredAudio ?: audioTracks.value.getOrNull(1))?.let {
            activity.player.aid = it.id
        }

        mediaOrchestrator.setLoadingTracks(true)
        updateIsLoadingEpisode(false)
        setPausedState()
    }

    fun loadChapters() {
        mediaOrchestrator.loadChapters()
    }

    fun updateChapters(chapters: List<IndexedSegment>) {
        mediaOrchestrator.updateChapters(chapters)
    }

    fun setLoadingTracks(loading: Boolean) {
        mediaOrchestrator.setLoadingTracks(loading)
    }

    fun updateChapter(index: Long) {
        val currentChapters = chapters.value

        val isValidIndex = index != -1L && currentChapters.isNotEmpty()
        if (!isValidIndex) return

        val selectedChapter = currentChapters.getOrNull(index.toInt()) ?: return
        mediaOrchestrator.setChapter(selectedChapter.start)
    }

    fun selectChapter(index: Int) {
        val time = chapters.value[index].start
        seekTo(time.toInt())
    }

    fun addAudio(uri: Uri) {
        mediaOrchestrator.addAudio(uri)
    }

    fun selectAudio(id: Int) {
        mediaOrchestrator.selectAudio(id)
    }

    fun updateAudio(id: Int) {
        mediaOrchestrator.updateAudioState(id)
    }

    fun addSubtitle(uri: Uri) {
        mediaOrchestrator.addSubtitle(uri)
    }

    fun selectSub(id: Int) {
        mediaOrchestrator.selectSub(id)
    }

    fun updateSubtitle(sid: Int, secondarySid: Int) {
        mediaOrchestrator.updateSubtitleState(sid, secondarySid)
    }

    fun updatePlayBackPos(pos: Float) {
        onSecondReached(pos.toInt(), duration.value.toInt())
        _pos.update { pos }
    }

    fun updateReadAhead(value: Long) {
        _readAhead.update { value.toFloat() }
    }

    private fun updatePausedState() {
        if (pausedState.value == null) {
            _pausedState.update { _ -> paused.value }
        }
    }

    private fun setPausedState() {
        pausedState.value?.let {
            if (it) {
                pause()
            } else {
                unpause()
            }

            _pausedState.update { _ -> null }
        }
    }

    fun pauseUnpause() {
        if (paused.value) {
            unpause()
        } else {
            pause()
        }
    }

    fun pause() {
        activity.player.paused = true
        _paused.update { true }
        runCatching {
            activity.setPictureInPictureParams(activity.createPipParams())
        }
    }

    fun unpause() {
        activity.player.paused = false
        _paused.update { false }
    }

    private val showStatusBar = playerPreferences.showSystemStatusBar().get()
    fun showControls() {
        if (sheetShown.value != Sheets.None ||
            panelShown.value != Panels.None ||
            dialogShown.value != Dialogs.None
        ) {
            return
        }
        if (showStatusBar) {
            activity.windowInsetsController.show(WindowInsetsCompat.Type.statusBars())
        }
        _controlsShown.update { true }
    }

    fun hideControls() {
        activity.windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
        _controlsShown.update { false }
    }

    fun hideSeekBar() {
        _seekBarShown.update { false }
    }

    fun showSeekBar() {
        if (sheetShown.value != Sheets.None) return
        _seekBarShown.update { true }
    }

    fun lockControls() {
        _areControlsLocked.update { true }
    }

    fun unlockControls() {
        _areControlsLocked.update { false }
    }

    fun dismissSheet() {
        _dismissSheet.update { _ -> true }
    }

    private fun resetDismissSheet() {
        _dismissSheet.update { _ -> false }
    }

    fun showSheet(sheet: Sheets) {
        sheetShown.update { sheet }
        if (sheet == Sheets.None) {
            resetDismissSheet()
            showControls()
        } else {
            hideControls()
            panelShown.update { Panels.None }
            dialogShown.update { Dialogs.None }
        }
    }

    fun showPanel(panel: Panels) {
        panelShown.update { panel }
        if (panel == Panels.None) {
            showControls()
        } else {
            hideControls()
            sheetShown.update { Sheets.None }
            dialogShown.update { Dialogs.None }
        }
    }

    fun showDialog(dialog: Dialogs) {
        dialogShown.update { dialog }
        if (dialog == Dialogs.None) {
            showControls()
        } else {
            hideControls()
            sheetShown.update { Sheets.None }
            panelShown.update { Panels.None }
        }
    }

    fun seekBy(offset: Int, precise: Boolean = false) {
        MPVLib.command(arrayOf("seek", offset.toString(), if (precise) "relative+exact" else "relative"))
    }

    fun seekTo(position: Int, precise: Boolean = true) {
        if (position !in 0..(activity.player.duration ?: 0)) return
        MPVLib.command(arrayOf("seek", position.toString(), if (precise) "absolute" else "absolute+keyframes"))
    }

    fun changeBrightnessTo(
        brightness: Float,
    ) {
        currentBrightness.update { _ -> brightness.coerceIn(-0.75f, 1f) }
        activity.window.attributes = activity.window.attributes.apply {
            screenBrightness = brightness.coerceIn(0f, 1f)
        }
    }

    fun displayBrightnessSlider() {
        isBrightnessSliderShown.update { true }
    }

    val maxVolume = activity.audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    fun changeVolumeBy(change: Int) {
        val mpvVolume = MPVLib.getPropertyInt("volume")
        if (volumeBoostCap > 0 && currentVolume.value == maxVolume) {
            if (mpvVolume == 100 && change < 0) changeVolumeTo(currentVolume.value + change)
            val finalMPVVolume = (mpvVolume + change).coerceAtLeast(100)
            if (finalMPVVolume in 100..volumeBoostCap + 100) {
                changeMPVVolumeTo(finalMPVVolume)
                return
            }
        }
        changeVolumeTo(currentVolume.value + change)
    }

    fun changeVolumeTo(volume: Int) {
        val newVolume = volume.coerceIn(0..maxVolume)
        activity.audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            newVolume,
            0,
        )
        currentVolume.update { newVolume }
    }

    fun changeMPVVolumeTo(volume: Int) {
        MPVLib.setPropertyInt("volume", volume)
    }

    fun setMPVVolume(volume: Int) {
        if (volume != currentMPVVolume.value) displayVolumeSlider()
        currentMPVVolume.update { volume }
    }

    fun displayVolumeSlider() {
        isVolumeSliderShown.update { true }
    }

    fun setAutoPlay(value: Boolean) {
        val textRes = if (value) {
            AYMR.strings.enable_auto_play
        } else {
            AYMR.strings.disable_auto_play
        }
        playerUpdate.update { PlayerUpdates.ShowTextResource(textRes) }
        playerPreferences.autoplayEnabled().set(value)
    }

    @Suppress("DEPRECATION")
    fun changeVideoAspect(aspect: VideoAspect) {
        var ratio = -1.0
        var pan = 1.0
        when (aspect) {
            VideoAspect.Crop -> {
                pan = 1.0
            }

            VideoAspect.Fit -> {
                pan = 0.0
                MPVLib.setPropertyDouble("panscan", 0.0)
            }

            VideoAspect.Stretch -> {
                val dm = DisplayMetrics()
                activity.windowManager.defaultDisplay.getRealMetrics(dm)
                ratio = dm.widthPixels / dm.heightPixels.toDouble()
                pan = 0.0
            }
        }
        MPVLib.setPropertyDouble("panscan", pan)
        MPVLib.setPropertyDouble("video-aspect-override", ratio)
        playerPreferences.aspectState().set(aspect)
        playerUpdate.update { PlayerUpdates.AspectRatio }
    }

    fun cycleScreenRotations() {
        activity.requestedOrientation = when (activity.requestedOrientation) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            -> {
                playerPreferences.defaultPlayerOrientationType().set(PlayerOrientation.SensorPortrait)
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }

            else -> {
                playerPreferences.defaultPlayerOrientationType().set(PlayerOrientation.SensorLandscape)
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }
    }

    fun handleLuaInvocation(property: String, value: String) {
        val data = value
            .removePrefix("\"")
            .removeSuffix("\"")
            .ifEmpty { return }

        when (property.substringAfterLast("/")) {
            "show_text" -> playerUpdate.update { PlayerUpdates.ShowText(data) }
            "toggle_ui" -> {
                when (data) {
                    "show" -> showControls()
                    "toggle" -> {
                        if (controlsShown.value) hideControls() else showControls()
                    }
                    "hide" -> {
                        sheetShown.update { Sheets.None }
                        panelShown.update { Panels.None }
                        dialogShown.update { Dialogs.None }
                        hideControls()
                    }
                }
            }
            "show_panel" -> {
                when (data) {
                    "subtitle_settings" -> showPanel(Panels.SubtitleSettings)
                    "subtitle_delay" -> showPanel(Panels.SubtitleDelay)
                    "audio_delay" -> showPanel(Panels.AudioDelay)
                    "video_filters" -> showPanel(Panels.VideoFilters)
                }
            }
            "set_button_title" -> {
                _primaryButtonTitle.update { _ -> data }
            }
            "reset_button_title" -> {
                _customButtons.value.getButtons().firstOrNull { it.isFavorite }?.let {
                    setPrimaryCustomButtonTitle(it)
                }
            }
            "switch_episode" -> {
                when (data) {
                    "n" -> changeEpisode(false)
                    "p" -> changeEpisode(true)
                }
            }
            "launch_int_picker" -> {
                val (title, nameFormat, start, stop, step, pickerProperty) = data.split("|")
                val defaultValue = MPVLib.getPropertyInt(pickerProperty)
                showDialog(
                    Dialogs.IntegerPicker(
                        defaultValue = defaultValue,
                        minValue = start.toInt(),
                        maxValue = stop.toInt(),
                        step = step.toInt(),
                        nameFormat = nameFormat,
                        title = title,
                        onChange = { MPVLib.setPropertyInt(pickerProperty, it) },
                        onDismissRequest = { showDialog(Dialogs.None) },
                    ),
                )
            }
            "pause" -> {
                when (data) {
                    "pause" -> pause()
                    "unpause" -> unpause()
                    "pauseunpause" -> pauseUnpause()
                }
            }
            "seek_to_with_text" -> {
                val (seekValue, text) = data.split("|", limit = 2)
                seekToWithText(seekValue.toInt(), text)
            }
            "seek_by_with_text" -> {
                val (seekValue, text) = data.split("|", limit = 2)
                seekByWithText(seekValue.toInt(), text)
            }
            "seek_by" -> seekByWithText(data.toInt(), null)
            "seek_to" -> seekToWithText(data.toInt(), null)
            "toggle_button" -> {
                fun showButton() {
                    if (_primaryButton.value == null) {
                        _primaryButton.update {
                            customButtons.value.getButtons().firstOrNull { it.isFavorite }
                        }
                    }
                }

                when (data) {
                    "show" -> showButton()
                    "hide" -> _primaryButton.update { null }
                    "toggle" -> if (_primaryButton.value == null) showButton() else _primaryButton.update { null }
                }
            }

            "software_keyboard" -> when (data) {
                "show" -> forceShowSoftwareKeyboard()
                "hide" -> forceHideSoftwareKeyboard()
                "toggle" -> if (inputMethodManager.isActive) {
                    forceHideSoftwareKeyboard()
                } else {
                    forceShowSoftwareKeyboard()
                }
            }
        }

        MPVLib.setPropertyString(property, "")
    }

    private operator fun <T> List<T>.component6(): T = get(5)

    private val inputMethodManager = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    private fun forceShowSoftwareKeyboard() {
        inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
    }

    private fun forceHideSoftwareKeyboard() {
        inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0)
    }

    private val doubleTapToSeekDuration = gesturePreferences.skipLengthPreference().get()
    private val preciseSeek = gesturePreferences.playerSmoothSeek().get()
    private val showSeekBar = gesturePreferences.showSeekBar().get()

    private fun seekToWithText(seekValue: Int, text: String?) {
        _isSeekingForwards.value = seekValue > 0
        _doubleTapSeekAmount.value = seekValue - pos.value.toInt()
        _seekText.update { _ -> text }
        seekTo(seekValue, preciseSeek)
        if (showSeekBar) showSeekBar()
    }

    private fun seekByWithText(value: Int, text: String?) {
        _doubleTapSeekAmount.update { if (value < 0 && it < 0 || pos.value + value > duration.value) 0 else it + value }
        _seekText.update { text }
        _isSeekingForwards.value = value > 0
        seekBy(value, preciseSeek)
        if (showSeekBar) showSeekBar()
    }

    fun updateSeekAmount(amount: Int) {
        _doubleTapSeekAmount.update { _ -> amount }
    }

    fun updateSeekText(value: String?) {
        _seekText.update { _ -> value }
    }

    fun leftSeek() {
        if (pos.value > 0) {
            _doubleTapSeekAmount.value -= doubleTapToSeekDuration
        }
        _isSeekingForwards.value = false
        seekBy(-doubleTapToSeekDuration, preciseSeek)
        if (showSeekBar) showSeekBar()
    }

    fun rightSeek() {
        if (pos.value < duration.value) {
            _doubleTapSeekAmount.value += doubleTapToSeekDuration
        }
        _isSeekingForwards.value = true
        seekBy(doubleTapToSeekDuration, preciseSeek)
        if (showSeekBar) showSeekBar()
    }

    fun resetHosterState() {
        _pausedState.update { false }
        hosterOrchestrator.reset()
    }

    fun changeEpisode(previous: Boolean, autoPlay: Boolean = false) {
        if (previous && !hasPreviousEpisode.value) {
            activity.showToast(activity.stringResource(AYMR.strings.no_prev_episode))
            return
        }

        if (!previous && !hasNextEpisode.value) {
            activity.showToast(activity.stringResource(AYMR.strings.no_next_episode))
            return
        }

        activity.changeEpisode(
            episodeId = getAdjacentEpisodeId(previous = previous),
            autoPlay = autoPlay,
        )
    }

    /**
     * Internal episode switching logic moved from PlayerActivity.
     * Handles episode loading, hoster setup, and error handling.
     */
    fun changeEpisode(
        episodeId: Long?,
        autoPlay: Boolean,
        isInPictureInPictureMode: Boolean,
        onError: (Throwable) -> Unit,
    ) {
        sheetShown.update { Sheets.None }
        panelShown.update { Panels.None }
        pause()
        isLoading.update { true }
        resetHosterState()

        viewModelScope.launch {
            updateIsLoadingEpisode(true)
            updateIsLoadingHosters(true)
            cancelHosterVideoLinksJob()

            val pipEpisodeToasts = playerPreferences.pipEpisodeToasts().get()
            val switchMethod = loadEpisode(episodeId)

            updateIsLoadingHosters(false)

            when (switchMethod) {
                null -> {
                    if (currentAnime.value != null && !autoPlay) {
                        withUIContext {
                            Injekt.get<Application>().toast(AYMR.strings.no_next_episode)
                        }
                    }
                    isLoading.update { false }
                }

                else -> {
                    if (switchMethod.hosterList != null) {
                        when {
                            switchMethod.hosterList.isEmpty() -> {
                                val error = ExceptionWithStringResource(
                                    "Hoster list is empty",
                                    AYMR.strings.no_hosters,
                                )
                                withUIContext { onError(error) }
                            }
                            else -> {
                                loadHosters(
                                    source = switchMethod.source,
                                    hosterList = switchMethod.hosterList,
                                    hosterIndex = -1,
                                    videoIndex = -1,
                                )
                            }
                        }
                    } else {
                        logcat(LogPriority.ERROR) { "Error getting links" }
                    }

                    if (isInPictureInPictureMode && pipEpisodeToasts) {
                        withUIContext {
                            Injekt.get<Application>().toast(switchMethod.episodeTitle)
                        }
                    }
                }
            }
        }

        updateNavigationState()
    }

    fun handleLeftDoubleTap() {
        when (gesturePreferences.leftDoubleTapGesture().get()) {
            SingleActionGesture.Seek -> {
                leftSeek()
            }
            SingleActionGesture.PlayPause -> {
                pauseUnpause()
            }
            SingleActionGesture.Custom -> {
                MPVLib.command(arrayOf("keypress", CustomKeyCodes.DoubleTapLeft.keyCode))
            }
            SingleActionGesture.None -> {}
            SingleActionGesture.Switch -> changeEpisode(true)
        }
    }

    fun handleCenterDoubleTap() {
        when (gesturePreferences.centerDoubleTapGesture().get()) {
            SingleActionGesture.PlayPause -> {
                pauseUnpause()
            }
            SingleActionGesture.Custom -> {
                MPVLib.command(arrayOf("keypress", CustomKeyCodes.DoubleTapCenter.keyCode))
            }
            SingleActionGesture.Seek -> {}
            SingleActionGesture.None -> {}
            SingleActionGesture.Switch -> {}
        }
    }

    fun handleRightDoubleTap() {
        when (gesturePreferences.rightDoubleTapGesture().get()) {
            SingleActionGesture.Seek -> {
                rightSeek()
            }
            SingleActionGesture.PlayPause -> {
                pauseUnpause()
            }
            SingleActionGesture.Custom -> {
                MPVLib.command(arrayOf("keypress", CustomKeyCodes.DoubleTapRight.keyCode))
            }
            SingleActionGesture.None -> {}
            SingleActionGesture.Switch -> changeEpisode(false)
        }
    }

    override fun onCleared() {
        if (currentEpisode.value != null) {
            saveWatchingProgress(currentEpisode.value!!)
            episodeToDownload?.let {
                downloadManager.addDownloadsToStartOfQueue(listOf(it))
            }
        }
    }

    // ====== OLD ======

    private val eventChannel = Channel<Event>()
    val eventFlow = eventChannel.receiveAsFlow()

    private val incognitoMode: Boolean by lazy { getIncognitoState.await(currentAnime.value?.source) }
    private val downloadAheadAmount = downloadPreferences.autoDownloadWhileWatching().get()

    internal val relativeTime = uiPreferences.relativeTime().get()
    internal val dateFormat = UiPreferences.dateFormat(uiPreferences.dateFormat().get())

    /**
     * The position in the current video. Used to restore from process kill.
     */
    private var episodePosition = savedState.get<Long>("episode_position")
        set(value) {
            savedState["episode_position"] = value
            field = value
        }

    /**
     * The current video's quality index. Used to restore from process kill.
     */
    private var qualityIndex = savedState.get<Pair<Int, Int>>("quality_index") ?: Pair(-1, -1)
        set(value) {
            savedState["quality_index"] = value
            field = value
        }

    /**
     * ID of the currently loaded episode.
     *
     * This property is the source of truth for persisted state. Setting it updates
     * savedState so the episode can be restored after process death.
     */
    private var episodeId = savedState.get<Long>("episode_id") ?: -1L
        set(value) {
            savedState["episode_id"] = value
            field = value
            episodeListManager.setEpisodeId(value)
        }

    private var episodeToDownload: AnimeDownload? = null

    fun getCurrentEpisodeIndex(): Int {
        return episodeListManager.getCurrentEpisodeIndex()
    }

    private fun getAdjacentEpisodeId(previous: Boolean): Long {
        return episodeListManager.getAdjacentEpisodeId(previous)
    }

    fun updateHasNextEpisode(value: Boolean) {
        episodeListManager.updateHasNextEpisode(value)
    }

    fun updateHasPreviousEpisode(value: Boolean) {
        episodeListManager.updateHasPreviousEpisode(value)
    }

    fun updateNavigationState() {
        episodeListManager.updateNavigationState()
    }

    fun showEpisodeListDialog() {
        if (currentAnime.value != null) {
            showDialog(Dialogs.EpisodeList)
        }
    }

    /**
     * Called when the activity is saved and not changing configurations. It updates the database
     * to persist the current progress of the active episode.
     */
    fun onSaveInstanceStateNonConfigurationChange() {
        val currentEpisode = currentEpisode.value ?: return
        viewModelScope.launchNonCancellable {
            saveEpisodeProgress(currentEpisode)
        }
    }

    // ====== Initialize anime, episode, hoster, and video list ======

    fun updateIsLoadingHosters(value: Boolean) {
        hosterOrchestrator.updateIsLoadingHosters(value)
    }

    /**
     * Whether this viewModel is initialized with the correct episode.
     */
    private fun needsInit(animeId: Long, episodeId: Long): Boolean {
        return currentAnime.value?.id != animeId || currentEpisode.value?.id != episodeId
    }

    data class InitResult(
        val hosterList: List<Hoster>?,
        val videoIndex: Pair<Int, Int>,
        val position: Long?,
    )

    private var currentHosterList: List<Hoster>? = null

    class ExceptionWithStringResource(
        message: String,
        val stringResource: StringResource,
    ) : Exception(message)

    suspend fun init(
        animeId: Long,
        initialEpisodeId: Long,
        hostList: String,
        hostIndex: Int,
        vidIndex: Int,
    ): Pair<InitResult, Result<Boolean>> {
        val defaultResult = InitResult(currentHosterList, qualityIndex, null)
        if (!needsInit(animeId, initialEpisodeId)) return Pair(defaultResult, Result.success(true))
        return try {
            val anime = getAnime.await(animeId)
            if (anime != null) {
                _currentAnime.update { _ -> anime }
                animeTitle.update { _ -> anime.title }
                sourceManager.isInitialized.first { it }
                episodeId = initialEpisodeId

                checkTrackers(anime)

                updateEpisodeList(initEpisodeList(anime))

                val episode = currentPlaylist.value.first { it.id == episodeId }
                val source = sourceManager.getOrStub(anime.source)

                episodeListManager.setCurrentEpisode(episode)
                _currentSource.update { _ -> source }

                updateEpisode(episode)

                updateNavigationState()

                // Write to mpv table
                MPVLib.setPropertyString("user-data/current-anime/anime-title", anime.title)
                MPVLib.setPropertyInt("user-data/current-anime/intro-length", getAnimeSkipIntroLength())
                MPVLib.setPropertyString(
                    "user-data/current-anime/category",
                    getAnimeCategories.await(anime.id).joinToString {
                        it.name
                    },
                )

                val currentEp = currentEpisode.value
                    ?: throw ExceptionWithStringResource("No episode loaded", AYMR.strings.no_episode_loaded)
                if (hostList.isNotBlank()) {
                    currentHosterList = hostList.toHosterList().ifEmpty {
                        currentHosterList = null
                        throw ExceptionWithStringResource(
                            "Hoster selected from empty list",
                            AYMR.strings.select_hoster_from_empty_list,
                        )
                    }
                    qualityIndex = Pair(hostIndex, vidIndex)
                } else {
                    EpisodeLoader.getHosters(currentEp.toDomainEpisode()!!, anime, source)
                        .takeIf { it.isNotEmpty() }
                        ?.also { currentHosterList = it }
                        ?: run {
                            currentHosterList = null
                            throw ExceptionWithStringResource("Hoster list is empty", AYMR.strings.no_hosters)
                        }
                }

                val result = InitResult(
                    hosterList = currentHosterList,
                    videoIndex = qualityIndex,
                    position = episodePosition,
                )
                Pair(result, Result.success(true))
            } else {
                // Unlikely but okay
                Pair(defaultResult, Result.success(false))
            }
        } catch (e: Throwable) {
            Pair(defaultResult, Result.failure(e))
        }
    }

    private fun updateEpisode(episode: Episode) {
        mediaTitle.update { _ -> episode.name }
        _isEpisodeOnline.update { _ -> isEpisodeOnline() == true }
        MPVLib.setPropertyDouble("user-data/current-anime/episode-number", episode.episode_number.toDouble())
    }

    private suspend fun initEpisodeList(anime: Anime): List<Episode> {
        return episodeListManager.initEpisodeList(anime)
    }

    private var hasTrackers: Boolean = false
    private suspend fun checkTrackers(anime: Anime) {
        val tracks = withIOContext { getTracks.await(anime.id) }
        hasTrackers = tracks.isNotEmpty()
    }

    fun cancelHosterVideoLinksJob() {
        hosterOrchestrator.cancelHosterVideoLinksJob()
    }

    /**
     * Set the video list for hosters.
     */
    fun loadHosters(source: AnimeSource, hosterList: List<Hoster>, hosterIndex: Int, videoIndex: Int) {
        hosterOrchestrator.loadHosters(source, hosterList, hosterIndex, videoIndex)
    }

    fun onVideoClicked(hosterIndex: Int, videoIndex: Int) {
        hosterOrchestrator.onVideoClicked(
            hosterIndex,
            videoIndex,
            currentSource.value,
            onSuccess = {
                if (sheetShown.value == Sheets.QualityTracks) {
                    dismissSheet()
                }
            },
            onFailure = {
                updateIsLoadingEpisode(false)
            },
        )
    }

    fun onHosterClicked(index: Int) {
        hosterOrchestrator.onHosterClicked(index, currentSource.value)
    }

    data class EpisodeLoadResult(
        val hosterList: List<Hoster>?,
        val episodeTitle: String,
        val source: AnimeSource,
    )

    suspend fun loadEpisode(episodeId: Long?): EpisodeLoadResult? {
        val anime = currentAnime.value ?: return null
        val source = sourceManager.getOrStub(anime.source)

        val chosenEpisode = currentPlaylist.value.firstOrNull { ep -> ep.id == episodeId } ?: return null

        episodeListManager.setCurrentEpisode(chosenEpisode)
        updateEpisode(chosenEpisode)

        return withIOContext {
            try {
                val currentEpisode =
                    currentEpisode.value
                        ?: throw ExceptionWithStringResource("No episode loaded", AYMR.strings.no_episode_loaded)
                currentHosterList = EpisodeLoader.getHosters(
                    currentEpisode.toDomainEpisode()!!,
                    anime,
                    source,
                )

                this@PlayerViewModel.episodeId = currentEpisode.id!!
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { e.message ?: "Error getting links" }
            }

            EpisodeLoadResult(
                hosterList = currentHosterList,
                episodeTitle = anime.title + " - " + chosenEpisode.name,
                source = source,
            )
        }
    }

    /**
     * Called every time a second is reached in the player. Used to mark the flag of episode being
     * seen, update tracking services, enqueue downloaded episode deletion and download next episode.
     */
    private fun onSecondReached(position: Int, duration: Int) {
        if (isLoadingEpisode.value) return
        val currentEp = currentEpisode.value ?: return
        if (episodeId == -1L) return
        if (duration == 0) return

        val seconds = position * 1000L
        val totalSeconds = duration * 1000L
        // Save last second seen and mark as seen if needed
        currentEp.last_second_seen = seconds
        currentEp.total_seconds = totalSeconds

        episodePosition = seconds

        val progress = playerPreferences.progressPreference().get()
        val shouldTrack = !incognitoMode || hasTrackers
        if (seconds >= totalSeconds * progress && shouldTrack) {
            viewModelScope.launchNonCancellable {
                updateEpisodeProgressOnComplete(currentEp)
            }
        }

        saveWatchingProgress(currentEp)

        val inDownloadRange = seconds.toDouble() / totalSeconds > 0.35
        if (inDownloadRange) {
            downloadNextEpisodes()
        }
    }

    private suspend fun updateEpisodeProgressOnComplete(currentEp: Episode) {
        currentEp.seen = true
        updateTrackEpisodeSeen(currentEp)
        deleteEpisodeIfNeeded(currentEp)

        val markDuplicateAsSeen = libraryPreferences.markDuplicateSeenEpisodeAsSeen().get()
            .contains(LibraryPreferences.MARK_DUPLICATE_EPISODE_SEEN_EXISTING)
        if (!markDuplicateAsSeen) return

        val duplicateUnseenEpisodes = currentPlaylist.value
            .mapNotNull { episode ->
                if (
                    !episode.seen &&
                    episode.isRecognizedNumber &&
                    episode.episode_number == currentEp.episode_number
                ) {
                    EpisodeUpdate(id = episode.id!!, seen = true)
                } else {
                    null
                }
            }
        updateEpisode.awaitAll(duplicateUnseenEpisodes)
    }

    private fun downloadNextEpisodes() {
        if (downloadAheadAmount == 0) return
        val anime = currentAnime.value ?: return

        // Only download ahead if current + next episode is already downloaded too to avoid jank
        if (getCurrentEpisodeIndex() == currentPlaylist.value.lastIndex) return
        val currentEpisode = currentEpisode.value ?: return

        val nextEpisode = currentPlaylist.value[getCurrentEpisodeIndex() + 1]
        val episodesAreDownloaded =
            EpisodeLoader.isDownload(currentEpisode.toDomainEpisode()!!, anime) &&
                EpisodeLoader.isDownload(nextEpisode.toDomainEpisode()!!, anime)

        viewModelScope.launchIO {
            if (!episodesAreDownloaded) {
                return@launchIO
            }
            val episodesToDownload = getNextEpisodes.await(anime.id, nextEpisode.id!!)
                .take(downloadAheadAmount)
            downloadManager.downloadEpisodes(anime, episodesToDownload)
        }
    }

    /**
     * Determines if deleting option is enabled and nth to last episode actually exists.
     * If both conditions are satisfied enqueues episode for delete
     * @param chosenEpisode current episode, which is going to be marked as seen.
     */
    private fun deleteEpisodeIfNeeded(chosenEpisode: Episode) {
        // Determine which episode should be deleted and enqueue
        val currentEpisodePosition = currentPlaylist.value.indexOf(chosenEpisode)
        val removeAfterSeenSlots = downloadPreferences.removeAfterReadSlots().get()
        val episodeToDelete = currentPlaylist.value.getOrNull(
            currentEpisodePosition - removeAfterSeenSlots,
        )
        // If episode is completely seen no need to download it
        episodeToDownload = null

        // Check if deleting option is enabled and episode exists
        if (removeAfterSeenSlots != -1 && episodeToDelete != null) {
            enqueueDeleteSeenEpisodes(episodeToDelete)
        }
    }

    fun saveCurrentEpisodeWatchingProgress() {
        currentEpisode.value?.let { saveWatchingProgress(it) }
    }

    /**
     * Called when episode is changed in player or when activity is paused.
     */
    private fun saveWatchingProgress(episode: Episode) {
        viewModelScope.launchNonCancellable {
            saveEpisodeProgress(episode)
            saveEpisodeHistory(episode)
        }
    }

    /**
     * Saves this [episode] progress (last second seen and whether it's seen).
     * If incognito mode isn't on or has at least 1 tracker
     */
    private suspend fun saveEpisodeProgress(episode: Episode) {
        if (!incognitoMode || hasTrackers) {
            updateEpisode.await(
                EpisodeUpdate(
                    id = episode.id!!,
                    seen = episode.seen,
                    bookmark = episode.bookmark,
                    fillermark = episode.fillermark,
                    lastSecondSeen = episode.last_second_seen,
                    totalSeconds = episode.total_seconds,
                ),
            )
        }
    }

    /**
     * Saves this [episode] last seen history if incognito mode isn't on.
     */
    private suspend fun saveEpisodeHistory(episode: Episode) {
        if (!incognitoMode) {
            val episodeId = episode.id!!
            val seenAt = Date()
            upsertHistory.await(
                AnimeHistoryUpdate(episodeId, seenAt),
            )
        }
    }

    /**
     * Bookmarks the currently active episode.
     */
    fun bookmarkEpisode(episodeId: Long?, bookmarked: Boolean) {
        viewModelScope.launchNonCancellable {
            updateEpisode.await(
                EpisodeUpdate(
                    id = episodeId!!,
                    bookmark = bookmarked,
                ),
            )
        }
    }

    /**
     * Fillermarks the currently active episode.
     */
    fun fillermarkEpisode(episodeId: Long?, fillermarked: Boolean) {
        viewModelScope.launchNonCancellable {
            updateEpisode.await(
                EpisodeUpdate(
                    id = episodeId!!,
                    fillermark = fillermarked,
                ),
            )
        }
    }

    fun takeScreenshot(cachePath: String, showSubtitles: Boolean): InputStream? {
        val filename = cachePath + "/${System.currentTimeMillis()}_mpv_screenshot_tmp.png"
        val subtitleFlag = if (showSubtitles) "subtitles" else "video"

        MPVLib.command(arrayOf("screenshot-to-file", filename, subtitleFlag))
        val tempFile = File(filename).takeIf { it.exists() } ?: return null
        val newFile = File("$cachePath/mpv_screenshot.png")

        newFile.delete()
        tempFile.renameTo(newFile)
        return newFile.takeIf { it.exists() }?.inputStream()
    }

    /**
     * Saves the screenshot on the pictures directory and notifies the UI of the result.
     * There's also a notification to allow sharing the image somewhere else or deleting it.
     */
    fun saveImage(imageStream: () -> InputStream, timePos: Int?) {
        val anime = currentAnime.value ?: return

        val context = Injekt.get<Application>()
        val notifier = SaveImageNotifier(context)
        notifier.onClear()

        val seconds = timePos?.let { Utils.prettyTime(it) } ?: return
        val filename = generateFilename(anime, seconds) ?: return

        // Pictures directory.
        val relativePath = DiskUtil.buildValidFilename(anime.title)

        // Copy file in background.
        viewModelScope.launchNonCancellable {
            try {
                val uri = imageSaver.save(
                    image = Image.Page(
                        inputStream = imageStream,
                        name = filename,
                        location = Location.Pictures(relativePath),
                    ),
                )
                notifier.onComplete(uri)
                eventChannel.send(Event.SavedImage(SaveImageResult.Success(uri)))
            } catch (e: Throwable) {
                notifier.onError(e.message)
                eventChannel.send(Event.SavedImage(SaveImageResult.Error(e)))
            }
        }
    }

    /**
     * Shares the screenshot and notifies the UI with the path of the file to share.
     * The image must be first copied to the internal partition because there are many possible
     * formats it can come from, like a zipped chapter, in which case it's not possible to directly
     * get a path to the file and it has to be decompressed somewhere first. Only the last shared
     * image will be kept so it won't be taking lots of internal disk space.
     */
    fun shareImage(imageStream: () -> InputStream, timePos: Int?) {
        val anime = currentAnime.value ?: return

        val context = Injekt.get<Application>()
        val destDir = context.cacheImageDir

        val seconds = timePos?.let { Utils.prettyTime(it) } ?: return
        val filename = generateFilename(anime, seconds) ?: return

        try {
            viewModelScope.launchIO {
                destDir.deleteRecursively()
                val uri = imageSaver.save(
                    image = Image.Page(
                        inputStream = imageStream,
                        name = filename,
                        location = Location.Cache,
                    ),
                )
                eventChannel.send(Event.ShareImage(uri, seconds))
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e)
        }
    }

    /**
     * Sets the screenshot as art and notifies the UI of the result.
     */
    fun setAsArt(artType: ArtType, imageStream: () -> InputStream) {
        val anime = currentAnime.value ?: return
        val episode = currentEpisode.value ?: return

        viewModelScope.launchNonCancellable {
            val result = try {
                when (artType) {
                    ArtType.Cover -> anime.editCover(Injekt.get(), imageStream())
                    ArtType.Background -> anime.editBackground(Injekt.get(), imageStream())
                    ArtType.Thumbnail -> episode.editThumbnail(anime, Injekt.get(), imageStream())
                }

                if (anime.isLocal() || anime.favorite) {
                    SetAsArt.Success
                } else {
                    SetAsArt.AddToLibraryFirst
                }
            } catch (e: Exception) {
                SetAsArt.Error
            }
            eventChannel.send(Event.SetArtResult(result, artType))
        }
    }

    /**
     * Results of the save image feature.
     */
    sealed class SaveImageResult {
        class Success(val uri: Uri) : SaveImageResult()
        class Error(val error: Throwable) : SaveImageResult()
    }

    private fun updateTrackEpisodeSeen(episode: Episode) {
        if (basePreferences.incognitoMode().get() || !hasTrackers) return
        if (!trackPreferences.autoUpdateTrack().get()) return

        val anime = currentAnime.value ?: return
        val context = Injekt.get<Application>()

        viewModelScope.launchNonCancellable {
            trackEpisode.await(context, anime.id, episode.episode_number.toDouble())
        }
    }

    /**
     * Enqueues this [episode] to be deleted when [deletePendingEpisodes] is called. The download
     * manager handles persisting it across process deaths.
     */
    private fun enqueueDeleteSeenEpisodes(episode: Episode) {
        if (!episode.seen) return
        val anime = currentAnime.value ?: return
        viewModelScope.launchNonCancellable {
            downloadManager.enqueueEpisodesToDelete(listOf(episode.toDomainEpisode()!!), anime)
        }
    }

    /**
     * Deletes all the pending episodes. This operation will run in a background thread and errors
     * are ignored.
     */
    fun deletePendingEpisodes() {
        viewModelScope.launchNonCancellable {
            downloadManager.deletePendingEpisodes()
        }
    }

    /**
     * Returns the skipIntroLength used by this anime or the default one.
     */
    fun getAnimeSkipIntroLength(): Int {
        val default = gesturePreferences.defaultIntroLength().get()
        val anime = currentAnime.value ?: return default
        val skipIntroLength = anime.skipIntroLength
        val skipIntroDisable = anime.skipIntroDisable
        return when {
            skipIntroDisable -> 0
            skipIntroLength <= 0 -> default
            else -> anime.skipIntroLength
        }
    }

    /**
     * Updates the skipIntroLength for the open anime.
     */
    fun setAnimeSkipIntroLength(skipIntroLength: Long) {
        val anime = currentAnime.value ?: return
        if (!anime.favorite) return
        // Skip unnecessary database operation
        if (skipIntroLength == getAnimeSkipIntroLength().toLong()) return
        viewModelScope.launchIO {
            setAnimeViewerFlags.awaitSetSkipIntroLength(anime.id, skipIntroLength)
            _currentAnime.update { _ -> getAnime.await(anime.id) }
        }
    }

    /**
     * Generate a filename for the given [anime] and [timePos]
     */
    private fun generateFilename(
        anime: Anime,
        timePos: String,
    ): String? {
        val episode = currentEpisode.value ?: return null
        val filenameSuffix = " - $timePos"
        return DiskUtil.buildValidFilename(
            "${anime.title} - ${episode.name}".takeBytes(
                DiskUtil.MAX_FILE_NAME_BYTES - filenameSuffix.byteSize(),
            ),
        ) + filenameSuffix
    }

    /**
     * Returns the response of the AniSkipApi for this episode.
     * just works if tracking is enabled.
     */
    suspend fun aniSkipResponse(playerDuration: Int?): List<TimeStamp>? {
        val animeId = currentAnime.value?.id ?: return null
        val duration = playerDuration ?: return null
        val episodeNumber = currentEpisode.value?.episode_number?.toDouble() ?: return null
        val tracks = getTracks.await(animeId)
        if (tracks.isEmpty()) {
            logcat { "AniSkip: No tracks found for anime $animeId" }
            return null
        }

        val trackerManager = Injekt.get<TrackerManager>()
        val malId = resolveMalId(
            tracks = tracks,
            trackerKindForId = { trackerId ->
                when (trackerManager.get(trackerId)) {
                    is MyAnimeList -> AniSkipTrackerKind.MAL
                    is Anilist -> AniSkipTrackerKind.ANILIST
                    else -> AniSkipTrackerKind.OTHER
                }
            },
            anilistToMalResolver = aniSkipApi::getMalIdFromAL,
        ) ?: return null

        aniSkipCache.get(
            malId = malId,
            episodeNumber = episodeNumber,
            episodeLength = duration.toLong(),
        )?.let { return it }

        val fetched = aniSkipApi.getResult(
            malId = malId.toInt(),
            episodeNumber = episodeNumber,
            episodeLength = duration.toLong(),
        ) ?: return null

        aniSkipCache.put(
            malId = malId,
            episodeNumber = episodeNumber,
            episodeLength = duration.toLong(),
            timestamps = fetched,
        )
        return fetched
    }

    val introSkipEnabled = playerPreferences.enableSkipIntro().get()

    private val defaultWaitingTime = playerPreferences.waitingTimeIntroSkip().get()
    var waitingSkipIntro = defaultWaitingTime

    fun setChapter(position: Float) {
        mediaOrchestrator.setChapter(position)
    }

    fun onSkipIntro() {
        mediaOrchestrator.onSkipIntro(pos.value)
    }

    fun setPrimaryCustomButtonTitle(button: CustomButton) {
        _primaryButtonTitle.update { _ -> button.name }
    }

    // Cast -->
    fun canCast(video: Video): Boolean =
        !video.videoUrl.startsWith("content://") && !video.videoUrl.startsWith("file://")

    fun resumeFromCast(positionMs: Long) {
        MPVLib.command(arrayOf("set", "time-pos", "${positionMs / 1000.0}"))
    }

    fun updateCastProgress(posMs: Long) {
        _castProgress.value = posMs
    }

    fun onCastEpisodeFinished() {
        changeEpisode(previous = false, autoPlay = true)
    }
    // <-- Cast

    sealed class Event {
        data class SetArtResult(val result: SetAsArt, val artType: ArtType) : Event()
        data class SavedImage(val result: SaveImageResult) : Event()
        data class ShareImage(val uri: Uri, val seconds: String) : Event()
    }
}

fun CustomButton.execute() {
    MPVLib.command(arrayOf("script-message", "call_button_$id"))
}

fun CustomButton.executeLongPress() {
    MPVLib.command(arrayOf("script-message", "call_button_${id}_long"))
}

fun Float.normalize(inMin: Float, inMax: Float, outMin: Float, outMax: Float): Float {
    return (this - inMin) * (outMax - outMin) / (inMax - inMin) + outMin
}
