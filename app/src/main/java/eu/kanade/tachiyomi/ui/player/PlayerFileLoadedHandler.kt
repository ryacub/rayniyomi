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

import android.content.Context
import eu.kanade.tachiyomi.animesource.model.ChapterType
import eu.kanade.tachiyomi.animesource.model.TimeStamp
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.ui.player.controls.components.IndexedSegment
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.player.utils.ChapterUtils
import eu.kanade.tachiyomi.ui.player.utils.ChapterUtils.Companion.getStringRes
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Handles the fileLoaded() event pipeline for MPV.
 * Manages video metadata setup, tracks, chapters, and AniSkip integration.
 * Extracted from PlayerActivity to eliminate runBlocking and improve separation.
 */
internal class PlayerFileLoadedHandler(
    private val context: Context,
    private val playerPreferences: PlayerPreferences,
    private val scope: CoroutineScope,
) {
    private val _isLoadingTracks = MutableStateFlow(true)
    val isLoadingTracks: StateFlow<Boolean> = _isLoadingTracks.asStateFlow()

    private val _chapters = MutableStateFlow<List<IndexedSegment>>(emptyList())
    val chapters: StateFlow<List<IndexedSegment>> = _chapters.asStateFlow()

    /**
     * Called when MPV fires the FILE_LOADED event.
     * Orchestrates all setup operations for the newly loaded video file in clear stages.
     */
    suspend fun onFileLoaded(
        currentVideo: Video?,
        animeTitle: String?,
        episodeName: String?,
        episodeNumber: Double?,
        playerDuration: Int?,
        currentChapters: List<IndexedSegment>,
        currentPos: Float,
        aniSkipEnabled: Boolean,
        introSkipEnabled: Boolean,
        disableAniSkipOnChapters: Boolean,
        onVideoAspectUpdate: () -> Unit,
        onChaptersUpdated: (List<IndexedSegment>) -> Unit,
        onSetChapter: (Float) -> Unit,
        aniSkipFetcher: suspend (Int?) -> List<TimeStamp>?,
    ) = withContext(Dispatchers.IO) {
        configurePlayback(currentVideo)
        setupMetadata(animeTitle, episodeName, episodeNumber, onVideoAspectUpdate)
        setupMediaElements(currentVideo, currentChapters, playerDuration, onChaptersUpdated, onSetChapter)
        integrateAniSkip(
            playerDuration = playerDuration,
            currentPos = currentPos,
            aniSkipEnabled = aniSkipEnabled,
            introSkipEnabled = introSkipEnabled,
            disableAniSkipOnChapters = disableAniSkipOnChapters,
            aniSkipFetcher = aniSkipFetcher,
            onChaptersUpdated = onChaptersUpdated,
            onSetChapter = onSetChapter,
        )
    }

    // Pipeline Stage 1: Configure Playback Options

    private fun configurePlayback(video: Video?) {
        setMpvOptions(video)
    }

    // Pipeline Stage 2: Setup Metadata

    private fun setupMetadata(
        animeTitle: String?,
        episodeName: String?,
        episodeNumber: Double?,
        onVideoAspectUpdate: () -> Unit,
    ) {
        setMpvMediaTitle(animeTitle, episodeName, episodeNumber)
        onVideoAspectUpdate()
    }

    // Pipeline Stage 3: Setup Media Elements (Chapters and Tracks)

    private suspend fun setupMediaElements(
        video: Video?,
        currentChapters: List<IndexedSegment>,
        playerDuration: Int?,
        onChaptersUpdated: (List<IndexedSegment>) -> Unit,
        onSetChapter: (Float) -> Unit,
    ) {
        setupChapters(video, currentChapters, playerDuration, onChaptersUpdated, onSetChapter)
        setupTracks(video)
    }

    // Pipeline Stage 4: Integrate AniSkip

    private suspend fun integrateAniSkip(
        playerDuration: Int?,
        currentPos: Float,
        aniSkipEnabled: Boolean,
        introSkipEnabled: Boolean,
        disableAniSkipOnChapters: Boolean,
        aniSkipFetcher: suspend (Int?) -> List<TimeStamp>?,
        onChaptersUpdated: (List<IndexedSegment>) -> Unit,
        onSetChapter: (Float) -> Unit,
    ) {
        if (!shouldIntegrateAniSkip(aniSkipEnabled, introSkipEnabled, disableAniSkipOnChapters)) {
            return
        }

        aniSkipFetcher(playerDuration)?.let { stamps ->
            val mergedChapters = ChapterUtils.mergeChapters(
                currentChapters = chapters.value,
                stamps = stamps,
                duration = playerDuration,
            )
            _chapters.update { mergedChapters }
            onChaptersUpdated(mergedChapters)
            onSetChapter(currentPos)
        }
    }

    private fun shouldIntegrateAniSkip(
        aniSkipEnabled: Boolean,
        introSkipEnabled: Boolean,
        disableAniSkipOnChapters: Boolean,
    ): Boolean {
        return shouldIntegrateAniSkipOnFileLoad(
            aniSkipEnabled = aniSkipEnabled,
            introSkipEnabled = introSkipEnabled,
            disableAniSkipOnChapters = disableAniSkipOnChapters,
            hasExistingChapters = chapters.value.isNotEmpty(),
        )
    }

    // Private helper methods

    private fun setMpvOptions(video: Video?) {
        video ?: return

        // Only check for `MPV_ARGS_TAG` on downloaded videos
        if (listOf("file", "content", "data").none { video.videoUrl.startsWith(it) }) {
            return
        }

        try {
            val metadata = Json.decodeFromString<Map<String, String>>(
                MPVLib.getPropertyString("metadata"),
            )

            val opts = metadata[Video.MPV_ARGS_TAG]
                ?.split(";")
                ?.map { it.split("=", limit = 2) }
                ?: return

            opts.forEach { (option, value) ->
                MPVLib.setPropertyString(option, value)
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to read video metadata" }
        }
    }

    private fun setMpvMediaTitle(animeTitle: String?, episodeName: String?, episodeNumber: Double?) {
        animeTitle ?: return
        episodeName ?: return
        episodeNumber ?: return

        // Write to mpv table
        MPVLib.setPropertyString("user-data/current-anime/episode-title", episodeName)

        val epNumber = episodeNumber.let { number ->
            if (ceil(number) == floor(number)) number.toInt() else number
        }.toString().padStart(2, '0')

        val title = context.stringResource(
            tachiyomi.i18n.aniyomi.AYMR.strings.mpv_media_title,
            animeTitle,
            epNumber,
            episodeName,
        )

        MPVLib.setPropertyString("force-media-title", title)
    }

    private suspend fun setupTracks(video: Video?) = withContext(Dispatchers.IO) {
        _isLoadingTracks.update { true }

        val audioTracks = video?.audioTracks?.takeIf { it.isNotEmpty() }
        val subtitleTracks = video?.subtitleTracks?.takeIf { it.isNotEmpty() }

        // If no external audio or subtitle tracks are present, loadTracks() won't be
        // called and we need to signal completion manually
        if (audioTracks == null && subtitleTracks == null) {
            _isLoadingTracks.update { false }
            return@withContext
        }

        audioTracks?.forEach { audio ->
            MPVLib.command(arrayOf("audio-add", audio.url, "auto", audio.lang))
        }
        subtitleTracks?.forEach { sub ->
            MPVLib.command(arrayOf("sub-add", sub.url, "auto", sub.lang))
        }

        _isLoadingTracks.update { false }
    }

    private fun setupChapters(
        video: Video?,
        currentChapters: List<IndexedSegment>,
        playerDuration: Int?,
        onChaptersUpdated: (List<IndexedSegment>) -> Unit,
        onSetChapter: (Float) -> Unit,
    ) {
        val timestamps = video?.timestamps?.takeIf { it.isNotEmpty() }
            ?.map { timestamp ->
                if (timestamp.name.isEmpty() && timestamp.type != ChapterType.Other) {
                    timestamp.copy(
                        name = timestamp.type.getStringRes()?.let { context.stringResource(it) } ?: "",
                    )
                } else {
                    timestamp
                }
            }
            ?: return

        val mergedChapters = ChapterUtils.mergeChapters(
            currentChapters = currentChapters,
            stamps = timestamps,
            duration = playerDuration,
        )
        _chapters.update { mergedChapters }
        onChaptersUpdated(mergedChapters)
        onSetChapter(0f) // Use initial position
    }

    fun updateIsLoadingTracks(value: Boolean) {
        _isLoadingTracks.update { value }
    }

    fun updateChapters(chapters: List<IndexedSegment>) {
        _chapters.update { chapters }
    }
}

internal fun shouldIntegrateAniSkipOnFileLoad(
    aniSkipEnabled: Boolean,
    introSkipEnabled: Boolean,
    disableAniSkipOnChapters: Boolean,
    hasExistingChapters: Boolean,
): Boolean {
    if (!introSkipEnabled || !aniSkipEnabled) return false
    if (disableAniSkipOnChapters && hasExistingChapters) return false
    return true
}
