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

package eu.kanade.tachiyomi.ui.player.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kanade.presentation.more.settings.screen.player.custombutton.getButtons
import eu.kanade.presentation.theme.playerRippleConfiguration
import eu.kanade.tachiyomi.ui.player.Dialogs
import eu.kanade.tachiyomi.ui.player.Panels
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import eu.kanade.tachiyomi.ui.player.PlayerUpdates
import eu.kanade.tachiyomi.ui.player.PlayerViewModel
import eu.kanade.tachiyomi.ui.player.Sheets
import eu.kanade.tachiyomi.ui.player.VideoAspect
import eu.kanade.tachiyomi.ui.player.cast.CastManager
import eu.kanade.tachiyomi.ui.player.cast.CastState
import eu.kanade.tachiyomi.ui.player.cast.components.CastMiniController
import eu.kanade.tachiyomi.ui.player.controls.components.BrightnessOverlay
import eu.kanade.tachiyomi.ui.player.controls.components.BrightnessSlider
import eu.kanade.tachiyomi.ui.player.controls.components.ControlsButton
import eu.kanade.tachiyomi.ui.player.controls.components.SeekbarWithTimers
import eu.kanade.tachiyomi.ui.player.controls.components.TextPlayerUpdate
import eu.kanade.tachiyomi.ui.player.controls.components.VolumeSlider
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.toFixed
import eu.kanade.tachiyomi.ui.player.settings.AudioPreferences
import eu.kanade.tachiyomi.ui.player.settings.GesturePreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences
import `is`.xyz.mpv.MPVLib
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import tachiyomi.source.local.entries.anime.LocalAnimeSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Suppress("CompositionLocalAllowlist")
val LocalPlayerButtonsClickEvent = staticCompositionLocalOf { {} }

@Composable
fun PlayerControls(
    viewModel: PlayerViewModel,
    onBackPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.padding
    val playerPreferences = remember { Injekt.get<PlayerPreferences>() }
    val gesturePreferences = remember { Injekt.get<GesturePreferences>() }
    val audioPreferences = remember { Injekt.get<AudioPreferences>() }
    val subtitlePreferences = remember { Injekt.get<SubtitlePreferences>() }
    val interactionSource = remember { MutableInteractionSource() }

    val controlsShown by viewModel.controlsShown.collectAsStateWithLifecycle()
    val areControlsLocked by viewModel.areControlsLocked.collectAsStateWithLifecycle()
    val seekBarShown by viewModel.seekBarShown.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isLoadingEpisode by viewModel.isLoadingEpisode.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val position by viewModel.pos.collectAsStateWithLifecycle()
    val paused by viewModel.paused.collectAsStateWithLifecycle()
    val gestureSeekAmount by viewModel.gestureSeekAmount.collectAsStateWithLifecycle()
    val doubleTapSeekAmount by viewModel.doubleTapSeekAmount.collectAsStateWithLifecycle()
    val seekText by viewModel.seekText.collectAsStateWithLifecycle()
    val currentChapter by viewModel.currentChapter.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val currentBrightness by viewModel.currentBrightness.collectAsStateWithLifecycle()

    val castManager = remember { Injekt.get<CastManager>() }
    val castState by castManager.castState.collectAsStateWithLifecycle()
    val isCasting = (castState == CastState.CONNECTED)
    val castProgress by viewModel.castProgress.collectAsStateWithLifecycle()
    val currentVideo by viewModel.currentVideo.collectAsStateWithLifecycle()
    val currentEpisode by viewModel.currentEpisode.collectAsStateWithLifecycle()
    val currentAnime by viewModel.currentAnime.collectAsStateWithLifecycle()

    val playerTimeToDisappear by playerPreferences.playerTimeToDisappear().collectAsStateWithLifecycle()
    var isSeeking by remember { mutableStateOf(false) }
    var resetControls by remember { mutableStateOf(true) }

    val customButtons by viewModel.customButtons.collectAsStateWithLifecycle()
    val customButton by viewModel.primaryButton.collectAsStateWithLifecycle()

    LaunchedEffect(
        controlsShown,
        paused,
        isSeeking,
        resetControls,
    ) {
        if (controlsShown && !paused && !isSeeking) {
            delay(playerTimeToDisappear.toLong())
            viewModel.hideControls()
        }
    }

    val transparentOverlay by animateFloatAsState(
        if (controlsShown && !areControlsLocked) .8f else 0f,
        animationSpec = playerControlsExitAnimationSpec(),
        label = "controls_transparent_overlay",
    )
    GestureHandler(
        viewModel = viewModel,
        interactionSource = interactionSource,
    )
    DoubleTapToSeekOvals(doubleTapSeekAmount, seekText, interactionSource)
    CompositionLocalProvider(
        LocalRippleConfiguration provides playerRippleConfiguration,
        LocalPlayerButtonsClickEvent provides { resetControls = !resetControls },
        LocalContentColor provides Color.White,
    ) {
        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Ltr,
        ) {
            ConstraintLayout(
                modifier = modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            Pair(0f, Color.Black),
                            Pair(.2f, Color.Transparent),
                            Pair(.7f, Color.Transparent),
                            Pair(1f, Color.Black),
                        ),
                        alpha = transparentOverlay,
                    )
                    .padding(horizontal = MaterialTheme.padding.medium),
            ) {
                val (topLeftControls, topRightControls) = createRefs()
                val (volumeSlider, brightnessSlider) = createRefs()
                val unlockControlsButton = createRef()
                val (bottomRightControls, bottomLeftControls) = createRefs()
                val centerControls = createRef()
                val seekbar = createRef()
                val (playerUpdates) = createRefs()

                val hasPreviousEpisode by viewModel.hasPreviousEpisode.collectAsStateWithLifecycle()
                val hasNextEpisode by viewModel.hasNextEpisode.collectAsStateWithLifecycle()
                val isBrightnessSliderShown by viewModel.isBrightnessSliderShown.collectAsStateWithLifecycle()
                val isVolumeSliderShown by viewModel.isVolumeSliderShown.collectAsStateWithLifecycle()
                val brightness by viewModel.currentBrightness.collectAsStateWithLifecycle()
                val volume by viewModel.currentVolume.collectAsStateWithLifecycle()
                val mpvVolume by viewModel.currentMPVVolume.collectAsStateWithLifecycle()
                val swapVolumeAndBrightness by gesturePreferences.swapVolumeBrightness().collectAsStateWithLifecycle()
                val reduceMotion by playerPreferences.reduceMotion().collectAsStateWithLifecycle()

                LaunchedEffect(volume, mpvVolume, isVolumeSliderShown) {
                    delay(2000)
                    if (isVolumeSliderShown) viewModel.isVolumeSliderShown.update { false }
                }
                LaunchedEffect(brightness, isBrightnessSliderShown) {
                    delay(2000)
                    if (isBrightnessSliderShown) viewModel.isBrightnessSliderShown.update { false }
                }
                AnimatedVisibility(
                    isBrightnessSliderShown,
                    enter =
                    if (!reduceMotion) {
                        slideInHorizontally(playerControlsEnterAnimationSpec()) {
                            if (swapVolumeAndBrightness) -it else it
                        } +
                            fadeIn(
                                playerControlsEnterAnimationSpec(),
                            )
                    } else {
                        fadeIn(playerControlsEnterAnimationSpec())
                    },
                    exit =
                    if (!reduceMotion) {
                        slideOutHorizontally(playerControlsExitAnimationSpec()) {
                            if (swapVolumeAndBrightness) -it else it
                        } +
                            fadeOut(
                                playerControlsExitAnimationSpec(),
                            )
                    } else {
                        fadeOut(playerControlsExitAnimationSpec())
                    },
                    modifier = Modifier.constrainAs(brightnessSlider) {
                        if (swapVolumeAndBrightness) {
                            start.linkTo(parent.start, spacing.medium)
                        } else {
                            end.linkTo(parent.end, spacing.medium)
                        }
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                    },
                ) {
                    BrightnessSlider(
                        brightness = brightness,
                        positiveRange = 0f..1f,
                        negativeRange = 0f..0.75f,
                    )
                }

                AnimatedVisibility(
                    isVolumeSliderShown,
                    enter =
                    if (!reduceMotion) {
                        slideInHorizontally(playerControlsEnterAnimationSpec()) {
                            if (swapVolumeAndBrightness) it else -it
                        } +
                            fadeIn(
                                playerControlsEnterAnimationSpec(),
                            )
                    } else {
                        fadeIn(playerControlsEnterAnimationSpec())
                    },
                    exit =
                    if (!reduceMotion) {
                        slideOutHorizontally(playerControlsExitAnimationSpec()) {
                            if (swapVolumeAndBrightness) it else -it
                        } +
                            fadeOut(
                                playerControlsExitAnimationSpec(),
                            )
                    } else {
                        fadeOut(playerControlsExitAnimationSpec())
                    },
                    modifier = Modifier.constrainAs(volumeSlider) {
                        if (swapVolumeAndBrightness) {
                            end.linkTo(parent.end, spacing.medium)
                        } else {
                            start.linkTo(parent.start, spacing.medium)
                        }
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                    },
                ) {
                    val boostCap by audioPreferences.volumeBoostCap().collectAsStateWithLifecycle()
                    val displayVolumeAsPercentage by playerPreferences.displayVolPer().collectAsStateWithLifecycle()
                    VolumeSlider(
                        volume = volume,
                        mpvVolume = mpvVolume,
                        range = 0..viewModel.maxVolume,
                        boostRange = if (boostCap > 0) 0..audioPreferences.volumeBoostCap().get() else null,
                        displayAsPercentage = displayVolumeAsPercentage,
                    )
                }

                val currentPlayerUpdate by viewModel.playerUpdate.collectAsStateWithLifecycle()
                val aspectRatio by playerPreferences.aspectState().collectAsStateWithLifecycle()
                LaunchedEffect(currentPlayerUpdate, aspectRatio) {
                    if (currentPlayerUpdate is PlayerUpdates.DoubleSpeed ||
                        currentPlayerUpdate is PlayerUpdates.SpeedBoost ||
                        currentPlayerUpdate is PlayerUpdates.None
                    ) {
                        return@LaunchedEffect
                    }
                    delay(2000)
                    viewModel.playerUpdate.update { PlayerUpdates.None }
                }
                AnimatedVisibility(
                    currentPlayerUpdate !is PlayerUpdates.None,
                    enter = fadeIn(playerControlsEnterAnimationSpec()),
                    exit = fadeOut(playerControlsExitAnimationSpec()),
                    modifier = Modifier.constrainAs(playerUpdates) {
                        linkTo(parent.start, parent.end)
                        linkTo(parent.top, parent.bottom, bias = 0.2f)
                    },
                ) {
                    when (currentPlayerUpdate) {
                        // is PlayerUpdates.DoubleSpeed -> DoubleSpeedPlayerUpdate()
                        is PlayerUpdates.SpeedBoost -> TextPlayerUpdate(
                            stringResource(AYMR.strings.player_speed_boost_active),
                        )
                        is PlayerUpdates.AspectRatio -> TextPlayerUpdate(stringResource(aspectRatio.titleRes))
                        is PlayerUpdates.ShowText -> TextPlayerUpdate(
                            (currentPlayerUpdate as PlayerUpdates.ShowText).value,
                        )
                        is PlayerUpdates.ShowTextResource -> TextPlayerUpdate(
                            stringResource((currentPlayerUpdate as PlayerUpdates.ShowTextResource).textResource),
                        )
                        else -> {}
                    }
                }

                AnimatedVisibility(
                    controlsShown && areControlsLocked,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.constrainAs(unlockControlsButton) {
                        top.linkTo(parent.top, spacing.medium)
                        start.linkTo(parent.start, spacing.medium)
                    },
                ) {
                    ControlsButton(
                        Icons.Filled.Lock,
                        onClick = { viewModel.unlockControls() },
                    )
                }
                AnimatedVisibility(
                    visible =
                    (controlsShown && !areControlsLocked || gestureSeekAmount != null) ||
                        isLoading ||
                        isLoadingEpisode,
                    enter = fadeIn(playerControlsEnterAnimationSpec()),
                    exit = fadeOut(playerControlsExitAnimationSpec()),
                    modifier = Modifier.constrainAs(centerControls) {
                        end.linkTo(parent.absoluteRight)
                        start.linkTo(parent.absoluteLeft)
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                    },
                ) {
                    val showLoadingCircle by playerPreferences.showLoadingCircle().collectAsStateWithLifecycle()
                    MiddlePlayerControls(
                        hasPrevious = hasPreviousEpisode,
                        onSkipPrevious = { viewModel.changeEpisode(true) },
                        hasNext = hasNextEpisode,
                        onSkipNext = { viewModel.changeEpisode(false) },
                        isLoading = isLoading,
                        isLoadingEpisode = isLoadingEpisode,
                        controlsShown = controlsShown,
                        areControlsLocked = areControlsLocked,
                        showLoadingCircle = showLoadingCircle,
                        paused = paused,
                        gestureSeekAmount = gestureSeekAmount,
                        onPlayPauseClick = viewModel::pauseUnpause,
                        enter = fadeIn(playerControlsEnterAnimationSpec()),
                        exit = fadeOut(playerControlsExitAnimationSpec()),
                    )
                }
                AnimatedVisibility(
                    visible = (controlsShown || seekBarShown) && !areControlsLocked,
                    enter = if (!reduceMotion) {
                        slideInVertically(playerControlsEnterAnimationSpec()) { it } +
                            fadeIn(playerControlsEnterAnimationSpec())
                    } else {
                        fadeIn(playerControlsEnterAnimationSpec())
                    },
                    exit = if (!reduceMotion) {
                        slideOutVertically(playerControlsExitAnimationSpec()) { it } +
                            fadeOut(playerControlsExitAnimationSpec())
                    } else {
                        fadeOut(playerControlsExitAnimationSpec())
                    },
                    modifier = Modifier.constrainAs(seekbar) {
                        bottom.linkTo(parent.bottom, spacing.medium)
                    },
                ) {
                    if (isCasting) {
                        CastMiniController(
                            episodeName = currentEpisode?.name ?: stringResource(AYMR.strings.cast_unknown_episode),
                            animeName = currentAnime?.title ?: stringResource(AYMR.strings.cast_unknown_anime),
                            isPlaying = !paused,
                            positionMs = castProgress,
                            durationMs = (duration * 1000).toLong(),
                            onPlayPause = viewModel::pauseUnpause,
                            onSeek = { newPositionMs ->
                                viewModel.updateCastProgress(newPositionMs)
                                castManager.seekTo(newPositionMs)
                            },
                            onDisconnect = {
                                castManager.disconnect()
                            },
                        )
                    } else {
                        val invertDuration by playerPreferences.invertDuration().collectAsStateWithLifecycle()
                        val readAhead by viewModel.readAhead.collectAsStateWithLifecycle()
                        val preciseSeeking by gesturePreferences.playerSmoothSeek().collectAsStateWithLifecycle()
                        SeekbarWithTimers(
                            position = position,
                            duration = duration,
                            readAheadValue = readAhead,
                            onValueChange = {
                                isSeeking = true
                                viewModel.updatePlayBackPos(it)
                                viewModel.seekTo(it.toInt(), preciseSeeking)
                            },
                            onValueChangeFinished = { isSeeking = false },
                            timersInverted = Pair(false, invertDuration),
                            durationTimerOnCLick = { playerPreferences.invertDuration().set(!invertDuration) },
                            positionTimerOnClick = {},
                            chapters = chapters.map { it.toSegment() }.toImmutableList(),
                        )
                    }
                }
                val mediaTitle by viewModel.mediaTitle.collectAsStateWithLifecycle()
                val animeTitle by viewModel.animeTitle.collectAsStateWithLifecycle()
                AnimatedVisibility(
                    controlsShown && !areControlsLocked,
                    enter = if (!reduceMotion) {
                        slideInHorizontally(playerControlsEnterAnimationSpec()) { -it } +
                            fadeIn(playerControlsEnterAnimationSpec())
                    } else {
                        fadeIn(playerControlsEnterAnimationSpec())
                    },
                    exit = if (!reduceMotion) {
                        slideOutHorizontally(playerControlsExitAnimationSpec()) { -it } +
                            fadeOut(playerControlsExitAnimationSpec())
                    } else {
                        fadeOut(playerControlsExitAnimationSpec())
                    },
                    modifier = Modifier.constrainAs(topLeftControls) {
                        top.linkTo(parent.top, spacing.medium)
                        start.linkTo(parent.start)
                        width = Dimension.fillToConstraints
                        end.linkTo(topRightControls.start)
                    },
                ) {
                    TopLeftPlayerControls(
                        animeTitle = animeTitle,
                        mediaTitle = mediaTitle,
                        onTitleClick = { viewModel.showEpisodeListDialog() },
                        onBackClick = onBackPress,
                    )
                }
                // Top right controls
                val autoPlayEnabled by playerPreferences.autoplayEnabled().collectAsStateWithLifecycle()
                val isEpisodeOnline by viewModel.isEpisodeOnline.collectAsStateWithLifecycle()
                AnimatedVisibility(
                    controlsShown && !areControlsLocked,
                    enter = if (!reduceMotion) {
                        slideInHorizontally(playerControlsEnterAnimationSpec()) { it } +
                            fadeIn(playerControlsEnterAnimationSpec())
                    } else {
                        fadeIn(playerControlsEnterAnimationSpec())
                    },
                    exit = if (!reduceMotion) {
                        slideOutHorizontally(playerControlsExitAnimationSpec()) { it } +
                            fadeOut(playerControlsExitAnimationSpec())
                    } else {
                        fadeOut(playerControlsExitAnimationSpec())
                    },
                    modifier = Modifier.constrainAs(topRightControls) {
                        top.linkTo(parent.top, spacing.medium)
                        end.linkTo(parent.end)
                    },
                ) {
                    TopRightPlayerControls(
                        autoPlayEnabled = autoPlayEnabled,
                        onToggleAutoPlay = { viewModel.setAutoPlay(it) },
                        onSubtitlesClick = { viewModel.showSheet(Sheets.SubtitleTracks) },
                        onSubtitlesLongClick = { viewModel.showPanel(Panels.SubtitleSettings) },
                        onAudioClick = { viewModel.showSheet(Sheets.AudioTracks) },
                        onAudioLongClick = { viewModel.showPanel(Panels.AudioDelay) },
                        onQualityClick = { viewModel.showSheet(Sheets.QualityTracks) },
                        isEpisodeOnline = isEpisodeOnline,
                        canCast = currentVideo?.let { viewModel.canCast(it) } ?: false,
                        castState = castState,
                        onMoreClick = { viewModel.showSheet(Sheets.More) },
                        onMoreLongClick = { viewModel.showPanel(Panels.VideoFilters) },
                    )
                }
                // Bottom right controls
                val skipIntroButton by viewModel.skipIntroText.collectAsStateWithLifecycle()
                val customButtonTitle by viewModel.primaryButtonTitle.collectAsStateWithLifecycle()
                AnimatedVisibility(
                    controlsShown && !areControlsLocked,
                    enter = if (!reduceMotion) {
                        slideInHorizontally(playerControlsEnterAnimationSpec()) { it } +
                            fadeIn(playerControlsEnterAnimationSpec())
                    } else {
                        fadeIn(playerControlsEnterAnimationSpec())
                    },
                    exit = if (!reduceMotion) {
                        slideOutHorizontally(playerControlsExitAnimationSpec()) { it } +
                            fadeOut(playerControlsExitAnimationSpec())
                    } else {
                        fadeOut(playerControlsExitAnimationSpec())
                    },
                    modifier = Modifier.constrainAs(bottomRightControls) {
                        bottom.linkTo(seekbar.top)
                        end.linkTo(seekbar.end)
                    },
                ) {
                    val activity = LocalContext.current as PlayerActivity
                    BottomRightPlayerControls(
                        customButton = customButton,
                        customButtonTitle = customButtonTitle,
                        skipIntroButton = skipIntroButton,
                        onPressSkipIntroButton = viewModel::onSkipIntro,
                        isPipAvailable = activity.isPipSupportedAndEnabled,
                        onPipClick = {
                            if (!viewModel.isLoadingEpisode.value) {
                                activity.enterPictureInPictureMode(activity.createPipParams())
                            }
                        },
                        onAspectClick = {
                            viewModel.changeVideoAspect(
                                when (aspectRatio) {
                                    VideoAspect.Fit -> VideoAspect.Stretch
                                    VideoAspect.Stretch -> VideoAspect.Crop
                                    VideoAspect.Crop -> VideoAspect.Fit
                                },
                            )
                        },
                    )
                }
                // Bottom left controls
                val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
                AnimatedVisibility(
                    controlsShown && !areControlsLocked,
                    enter = if (!reduceMotion) {
                        slideInHorizontally(playerControlsEnterAnimationSpec()) { -it } +
                            fadeIn(playerControlsEnterAnimationSpec())
                    } else {
                        fadeIn(playerControlsEnterAnimationSpec())
                    },
                    exit = if (!reduceMotion) {
                        slideOutHorizontally(playerControlsExitAnimationSpec()) { -it } +
                            fadeOut(playerControlsExitAnimationSpec())
                    } else {
                        fadeOut(playerControlsExitAnimationSpec())
                    },
                    modifier = Modifier.constrainAs(bottomLeftControls) {
                        bottom.linkTo(seekbar.top)
                        start.linkTo(seekbar.start)
                        width = Dimension.fillToConstraints
                        end.linkTo(bottomRightControls.start)
                    },
                ) {
                    BottomLeftPlayerControls(
                        playbackSpeed,
                        currentChapter = currentChapter?.toSegment(),
                        onLockControls = viewModel::lockControls,
                        onCycleRotation = viewModel::cycleScreenRotations,
                        onPlaybackSpeedChange = {
                            MPVLib.setPropertyDouble("speed", it.toDouble())
                        },
                        onOpenSheet = viewModel::showSheet,
                    )
                }
            }
        }

        val sheetShown by viewModel.sheetShown.collectAsStateWithLifecycle()
        val dismissSheet by viewModel.dismissSheet.collectAsStateWithLifecycle()
        val subtitles by viewModel.subtitleTracks.collectAsStateWithLifecycle()
        val selectedSubtitles by viewModel.selectedSubtitles.collectAsStateWithLifecycle()
        val audioTracks by viewModel.audioTracks.collectAsStateWithLifecycle()
        val selectedAudio by viewModel.selectedAudio.collectAsStateWithLifecycle()
        val isLoadingHosters by viewModel.isLoadingHosters.collectAsStateWithLifecycle()
        val hosterState by viewModel.hosterState.collectAsStateWithLifecycle()
        val expandedState by viewModel.hosterExpandedList.collectAsStateWithLifecycle()
        val selectedHosterVideoIndex by viewModel.selectedHosterVideoIndex.collectAsStateWithLifecycle()
        val decoder by viewModel.currentDecoder.collectAsStateWithLifecycle()
        val speed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
        val sleepTimerTimeRemaining by viewModel.remainingTime.collectAsStateWithLifecycle()
        val showSubtitles by subtitlePreferences.screenshotSubtitles().collectAsStateWithLifecycle()
        val currentSource by viewModel.currentSource.collectAsStateWithLifecycle()
        val showFailedHosters by playerPreferences.showFailedHosters().collectAsStateWithLifecycle()
        val emptyHosters by playerPreferences.showEmptyHosters().collectAsStateWithLifecycle()

        PlayerSheets(
            sheetShown = sheetShown,
            subtitles = subtitles.toImmutableList(),
            selectedSubtitles = selectedSubtitles.toList().toImmutableList(),
            onAddSubtitle = viewModel::addSubtitle,
            onSelectSubtitle = viewModel::selectSub,
            audioTracks = audioTracks.toImmutableList(),
            selectedAudio = selectedAudio,
            onAddAudio = viewModel::addAudio,
            onSelectAudio = viewModel::selectAudio,

            isLoadingHosters = isLoadingHosters,

            hosterState = hosterState,
            expandedState = expandedState,
            selectedVideoIndex = selectedHosterVideoIndex,
            onClickHoster = viewModel::onHosterClicked,
            onClickVideo = viewModel::onVideoClicked,
            displayHosters = Pair(showFailedHosters, emptyHosters),

            chapter = currentChapter?.toSegment(),
            chapters = chapters.map { it.toSegment() }.toImmutableList(),
            onSeekToChapter = {
                viewModel.selectChapter(it)
                viewModel.dismissSheet()
                viewModel.unpause()
            },
            decoder = decoder,
            onUpdateDecoder = viewModel::updateDecoder,
            speed = speed,
            onSpeedChange = { MPVLib.setPropertyDouble("speed", it.toFixed(2).toDouble()) },
            sleepTimerTimeRemaining = sleepTimerTimeRemaining,
            onStartSleepTimer = viewModel::startTimer,
            buttons = customButtons.getButtons().toImmutableList(),

            isLocalSource = currentSource?.id == LocalAnimeSource.ID,
            showSubtitles = showSubtitles,
            onToggleShowSubtitles = { subtitlePreferences.screenshotSubtitles().set(it) },
            cachePath = viewModel.cachePath,
            onSetAsArt = viewModel::setAsArt,
            onShare = { viewModel.shareImage(it, viewModel.pos.value.toInt()) },
            onSave = { viewModel.saveImage(it, viewModel.pos.value.toInt()) },
            takeScreenshot = viewModel::takeScreenshot,
            onDismissScreenshot = {
                viewModel.showSheet(Sheets.None)
                viewModel.unpause()
            },
            onOpenPanel = viewModel::showPanel,
            onDismissRequest = { viewModel.showSheet(Sheets.None) },
            dismissSheet = dismissSheet,
        )
        val panel by viewModel.panelShown.collectAsStateWithLifecycle()
        PlayerPanels(
            panelShown = panel,
            onDismissRequest = { viewModel.showPanel(Panels.None) },
        )

        val activity = LocalContext.current as PlayerActivity
        val dialog by viewModel.dialogShown.collectAsStateWithLifecycle()
        val anime by viewModel.currentAnime.collectAsStateWithLifecycle()
        val playlist by viewModel.currentPlaylist.collectAsStateWithLifecycle()

        PlayerDialogs(
            dialogShown = dialog,
            episodeDisplayMode = anime?.displayMode,
            episodeList = playlist,
            currentEpisodeIndex = viewModel.getCurrentEpisodeIndex(),
            dateRelativeTime = viewModel.relativeTime,
            dateFormat = viewModel.dateFormat,
            onBookmarkClicked = viewModel::bookmarkEpisode,
            onFillermarkClicked = viewModel::fillermarkEpisode,
            onEpisodeClicked = {
                viewModel.showDialog(Dialogs.None)
                activity.changeEpisode(it)
            },
            onDismissRequest = { viewModel.showDialog(Dialogs.None) },
        )

        BrightnessOverlay(
            brightness = currentBrightness,
        )
    }
}

fun <T> playerControlsExitAnimationSpec(): FiniteAnimationSpec<T> = tween(
    durationMillis = 300,
    easing = FastOutSlowInEasing,
)

fun <T> playerControlsEnterAnimationSpec(): FiniteAnimationSpec<T> = tween(
    durationMillis = 100,
    easing = LinearOutSlowInEasing,
)
