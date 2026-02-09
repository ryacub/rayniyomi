package eu.kanade.tachiyomi.ui.player.loader

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.ui.player.model.VideoTrack
import eu.kanade.tachiyomi.ui.player.controls.components.SeekBar.IndexedSegment
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.player.utils.TrackSelect
import eu.kanade.tachiyomi.util.system.getFileName
import eu.kanade.tachiyomi.util.system.openContentFd
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat
import tachiyomi.i18n.MR

class PlayerMediaOrchestrator(
    private val scope: CoroutineScope,
    private val context: Context,
    private val playerPreferences: PlayerPreferences,
    private val trackSelect: TrackSelect,
) {
    private val _isLoadingTracks = MutableStateFlow(true)
    val isLoadingTracks: StateFlow<Boolean> = _isLoadingTracks.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<VideoTrack>>(emptyList())
    val audioTracks: StateFlow<List<VideoTrack>> = _audioTracks.asStateFlow()

    private val _subtitleTracks = MutableStateFlow<List<VideoTrack>>(emptyList())
    val subtitleTracks: StateFlow<List<VideoTrack>> = _subtitleTracks.asStateFlow()

    private val _selectedAudio = MutableStateFlow(-1)
    val selectedAudio: StateFlow<Int> = _selectedAudio.asStateFlow()

    private val _selectedSubtitles = MutableStateFlow(Pair(-1, -1))
    val selectedSubtitles: StateFlow<Pair<Int, Int>> = _selectedSubtitles.asStateFlow()

    private val _chapters = MutableStateFlow<List<IndexedSegment>>(emptyList())
    val chapters: StateFlow<List<IndexedSegment>> = _chapters.asStateFlow()

    private val _currentChapter = MutableStateFlow<IndexedSegment?>(null)
    val currentChapter: StateFlow<IndexedSegment?> = _currentChapter.asStateFlow()

    private val _skipIntroText = MutableStateFlow<String?>(null)
    val skipIntroText: StateFlow<String?> = _skipIntroText.asStateFlow()

    var onTracksLoaded: (() -> Unit)? = null

    private var trackLoadingJob: Job? = null

    fun loadTracks() {
        trackLoadingJob?.cancel()
        trackLoadingJob = scope.launch {
            val possibleTrackTypes = listOf("audio", "sub")
            val subTracks = mutableListOf<VideoTrack>()
            val audioTracks = mutableListOf(
                VideoTrack(-1, context.getString(MR.strings.off.resourceId), null),
            )
            try {
                val tracksCount = MPVLib.getPropertyInt("track-list/count") ?: 0
                for (i in 0..<tracksCount) {
                    val type = MPVLib.getPropertyString("track-list/$i/type")
                    if (!possibleTrackTypes.contains(type) || type == null) continue
                    when (type) {
                        "sub" -> subTracks.add(
                            VideoTrack(
                                MPVLib.getPropertyInt("track-list/$i/id")!!,
                                MPVLib.getPropertyString("track-list/$i/title") ?: "",
                                MPVLib.getPropertyString("track-list/$i/lang"),
                            ),
                        )
                        "audio" -> audioTracks.add(
                            VideoTrack(
                                MPVLib.getPropertyInt("track-list/$i/id")!!,
                                MPVLib.getPropertyString("track-list/$i/title") ?: "",
                                MPVLib.getPropertyString("track-list/$i/lang"),
                            ),
                        )
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Couldn't load tracks: ${e.message}" }
                return@launch
            }
            _subtitleTracks.update { subTracks }
            _audioTracks.update { audioTracks }

            onTracksLoaded?.invoke()
        }
    }

    fun setLoadingTracks(loading: Boolean) {
        _isLoadingTracks.update { loading }
    }

    fun selectAudio(id: Int) {
        MPVLib.setPropertyInt("aid", id)
        _selectedAudio.update { id }
    }

    fun selectSub(id: Int) {
        val selectedSubs = _selectedSubtitles.value
        val newPair = when (id) {
            selectedSubs.first -> Pair(selectedSubs.second, -1)
            selectedSubs.second -> Pair(selectedSubs.first, -1)
            else -> {
                if (selectedSubs.first != -1) {
                    Pair(selectedSubs.first, id)
                } else {
                    Pair(id, -1)
                }
            }
        }
        _selectedSubtitles.update { newPair }
        MPVLib.setPropertyInt("sid", newPair.first)
        MPVLib.setPropertyInt("secondary-sid", newPair.second)
    }

    fun addAudio(uri: Uri) {
        val url = uri.toString()
        val isContentUri = url.startsWith("content://")
        val path = (if (isContentUri) uri.openContentFd(context) else url)
            ?: return
        val name = if (isContentUri) uri.getFileName(context) else null
        if (name == null) {
            MPVLib.command(arrayOf("audio-add", path, "cached"))
        } else {
            MPVLib.command(arrayOf("audio-add", path, "cached", name))
        }
    }

    fun addSubtitle(uri: Uri) {
        val url = uri.toString()
        val isContentUri = url.startsWith("content://")
        val path = (if (isContentUri) uri.openContentFd(context) else url)
            ?: return
        val name = if (isContentUri) uri.getFileName(context) else null
        if (name == null) {
            MPVLib.command(arrayOf("sub-add", path, "cached"))
        } else {
            MPVLib.command(arrayOf("sub-add", path, "cached", name))
        }
    }

    fun loadChapters() {
        val chapters = mutableListOf<IndexedSegment>()
        val count = MPVLib.getPropertyInt("chapter-list/count") ?: 0
        for (i in 0 until count) {
            val title = MPVLib.getPropertyString("chapter-list/$i/title")
            val time = MPVLib.getPropertyInt("chapter-list/$i/time") ?: 0
            chapters.add(
                IndexedSegment(
                    name = title,
                    start = time.toFloat(),
                    index = i,
                ),
            )
        }
        _chapters.update { chapters.sortedBy { it.start } }
    }

    fun updateChapters(chapters: List<IndexedSegment>) {
        _chapters.update { chapters }
    }

    private val introSkipEnabled = playerPreferences.enableSkipIntro().get()
    private val autoSkip = playerPreferences.autoSkipIntro().get()
    private val netflixStyle = playerPreferences.enableNetflixStyleIntroSkip().get()
    private val defaultWaitingTime = playerPreferences.waitingTimeIntroSkip().get()
    private var waitingSkipIntro = defaultWaitingTime

    var onSeekRequested: ((Int, String?) -> Unit)? = null
    var onToastRequested: ((String) -> Unit)? = null

    fun setChapter(position: Float) {
        getCurrentChapter(position)?.let { (chapterIndex, chapter) ->
            if (_currentChapter.value != chapter) {
                _currentChapter.update { chapter }
            }

            if (!introSkipEnabled) {
                return
            }

            if (chapter.chapterType == eu.kanade.tachiyomi.animesource.model.ChapterType.Other) {
                _skipIntroText.update { null }
                waitingSkipIntro = defaultWaitingTime
            } else {
                val nextChapterPos = _chapters.value.getOrNull(chapterIndex + 1)?.start ?: position

                if (netflixStyle) {
                    // show a toast with the seconds before the skip
                    if (waitingSkipIntro == defaultWaitingTime) {
                        onToastRequested?.invoke(
                            "Skip Intro: ${context.getString(
                                tachiyomi.i18n.aniyomi.AYMR.strings.player_aniskip_dontskip_toast.resourceId,
                                chapter.name,
                                waitingSkipIntro,
                            )}",
                        )
                    }
                    showSkipIntroButton(chapter, nextChapterPos, waitingSkipIntro)
                    waitingSkipIntro--
                } else if (autoSkip) {
                    onSeekRequested?.invoke(
                        nextChapterPos.toInt(),
                        context.getString(
                            tachiyomi.i18n.aniyomi.AYMR.strings.player_intro_skipped.resourceId,
                            chapter.name,
                        ),
                    )
                } else {
                    updateSkipIntroButton(chapter.chapterType)
                }
            }
        }
    }

    private fun updateSkipIntroButton(chapterType: eu.kanade.tachiyomi.animesource.model.ChapterType) {
        val skipButtonString = with(eu.kanade.tachiyomi.ui.player.utils.ChapterUtils.Companion) {
            chapterType.getStringRes()
        }

        _skipIntroText.update {
            skipButtonString?.let {
                context.getString(
                    tachiyomi.i18n.aniyomi.AYMR.strings.player_skip_action.resourceId,
                    context.getString(it.resourceId),
                )
            }
        }
    }

    private fun showSkipIntroButton(chapter: IndexedSegment, nextChapterPos: Float, waitingTime: Int) {
        if (waitingTime > -1) {
            if (waitingTime > 0) {
                _skipIntroText.update {
                    context.getString(tachiyomi.i18n.aniyomi.AYMR.strings.player_aniskip_dontskip.resourceId)
                }
            } else {
                onSeekRequested?.invoke(
                    nextChapterPos.toInt(),
                    context.getString(
                        tachiyomi.i18n.aniyomi.AYMR.strings.player_aniskip_skip.resourceId,
                        chapter.name,
                    ),
                )
            }
        } else {
            // when waitingTime is -1, it means that the user cancelled the skip
            updateSkipIntroButton(chapter.chapterType)
        }
    }

    fun onSkipIntro(position: Float) {
        getCurrentChapter(position)?.let { (chapterIndex, chapter) ->
            // this stops the counter
            if (waitingSkipIntro > 0 && netflixStyle) {
                waitingSkipIntro = -1
                return
            }

            val nextChapterPos = _chapters.value.getOrNull(chapterIndex + 1)?.start ?: position

            onSeekRequested?.invoke(
                nextChapterPos.toInt(),
                context.getString(
                    tachiyomi.i18n.aniyomi.AYMR.strings.player_aniskip_skip.resourceId,
                    chapter.name,
                ),
            )
        }
    }

    private fun getCurrentChapter(position: Float): IndexedValue<IndexedSegment>? {
        return _chapters.value.withIndex()
            .filter { it.value.start <= position }
            .maxByOrNull { it.value.start }
    }

    fun updateSubtitleState(sid: Int, secondarySid: Int) {
        _selectedSubtitles.update { Pair(sid, secondarySid) }
    }

    fun updateAudioState(aid: Int) {
        _selectedAudio.update { aid }
    }
}
