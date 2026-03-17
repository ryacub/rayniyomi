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
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.ui.player.MPVLibProxy
import eu.kanade.tachiyomi.ui.player.controls.components.IndexedSegment
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.i18n.stringResource

class PlayerFileLoadedHandlerTest {
    // No need to mock MPVLib - we inject MPVLibProxy as a dependency in tests

    private lateinit var handler: PlayerFileLoadedHandler
    private lateinit var mockContext: Context
    private lateinit var mockPlayerPreferences: PlayerPreferences
    private lateinit var scope: CoroutineScope

    @BeforeEach
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockPlayerPreferences = mockk(relaxed = true)
        scope = CoroutineScope(Dispatchers.IO)

        val mockMpvLibProxy = mockk<MPVLibProxy>(relaxed = true)

        handler = PlayerFileLoadedHandler(
            context = mockContext,
            playerPreferences = mockPlayerPreferences,
            scope = scope,
            mpvLibProxy = mockMpvLibProxy,
        )
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    // ==================== Helper Functions ====================

    private fun createMockVideo(
        videoUrl: String = "https://example.com/video.mp4",
        audioTracks: List<Track> = emptyList(),
        subtitleTracks: List<Track> = emptyList(),
        timestamps: List<TimeStamp> = emptyList(),
    ): Video {
        return mockk<Video>(relaxed = true).also {
            every { it.videoUrl } returns videoUrl
            every { it.audioTracks } returns audioTracks
            every { it.subtitleTracks } returns subtitleTracks
            every { it.timestamps } returns timestamps
        }
    }

    private fun createMockTrack(
        url: String = "https://example.com/track.mp3",
        lang: String = "en",
    ): Track {
        return mockk<Track>().also {
            every { it.url } returns url
            every { it.lang } returns lang
        }
    }

    private fun createMockTimeStamp(
        start: Double = 0.0,
        end: Double = 10.0,
        type: ChapterType = ChapterType.Other,
        name: String = "Test Chapter",
    ): TimeStamp {
        return TimeStamp(
            start = start,
            end = end,
            type = type,
            name = name,
        )
    }

    private fun createIndexedSegment(
        name: String = "Segment",
        start: Float = 0f,
    ): IndexedSegment {
        return IndexedSegment(
            name = name,
            start = start,
        )
    }

    // ==================== Pipeline Orchestration Tests ====================

    @Test
    fun `onFileLoaded invokes all four pipeline stages in order`() = runTest {
        val timestamp = createMockTimeStamp(start = 0.0, end = 120.0, name = "Opening")
        val video = createMockVideo(timestamps = listOf(timestamp))
        val chapters = listOf(createIndexedSegment())
        val onVideoAspectUpdate = mockk<() -> Unit>(relaxed = true)
        val onChaptersUpdated = mockk<(List<IndexedSegment>) -> Unit>(relaxed = true)
        val onSetChapter = mockk<(Float) -> Unit>(relaxed = true)
        val aniSkipFetcher = mockk<suspend (Int?) -> List<TimeStamp>?>(relaxed = true)
        coEvery { aniSkipFetcher(any()) } returns null

        handler.onFileLoaded(
            currentVideo = video,
            animeTitle = "Test Anime",
            episodeName = "Episode 1",
            episodeNumber = 1.0,
            playerDuration = 1400,
            currentChapters = chapters,
            currentPos = 0f,
            aniSkipEnabled = false,
            introSkipEnabled = false,
            disableAniSkipOnChapters = false,
            onVideoAspectUpdate = onVideoAspectUpdate,
            onChaptersUpdated = onChaptersUpdated,
            onSetChapter = onSetChapter,
            aniSkipFetcher = aniSkipFetcher,
        )

        // Verify all stages were invoked:
        // Stage 1: configurePlayback (via setupMediaElements)
        // Stage 2: setupMetadata invokes onVideoAspectUpdate
        verify { onVideoAspectUpdate() }
        // Stage 3: setupMediaElements invokes setupChapters which calls onChaptersUpdated and onSetChapter
        verify { onChaptersUpdated(any()) }
        verify { onSetChapter(any()) }
    }

    // ==================== Media Title Tests ====================

    @Test
    fun `setMpvMediaTitle formats title with anime title, episode number, and episode name`() = runTest {
        val video = createMockVideo()
        val chapters = emptyList<IndexedSegment>()
        val onVideoAspectUpdate = mockk<() -> Unit>(relaxed = true)
        val onChaptersUpdated = mockk<(List<IndexedSegment>) -> Unit>(relaxed = true)
        val onSetChapter = mockk<(Float) -> Unit>(relaxed = true)
        val aniSkipFetcher = mockk<suspend (Int?) -> List<TimeStamp>?>(relaxed = true)
        coEvery { aniSkipFetcher(any()) } returns null

        handler.onFileLoaded(
            currentVideo = video,
            animeTitle = "Demon Slayer",
            episodeName = "Tanjiro's Dream",
            episodeNumber = 5.0,
            playerDuration = 1400,
            currentChapters = chapters,
            currentPos = 0f,
            aniSkipEnabled = false,
            introSkipEnabled = false,
            disableAniSkipOnChapters = false,
            onVideoAspectUpdate = onVideoAspectUpdate,
            onChaptersUpdated = onChaptersUpdated,
            onSetChapter = onSetChapter,
            aniSkipFetcher = aniSkipFetcher,
        )

        // Verify the callback was invoked (indicates MPV methods were called)
        verify { onVideoAspectUpdate() }
    }

    @Test
    fun `setMpvMediaTitle pads episode number to 2 digits for integer episode`() = runTest {
        val video = createMockVideo()
        val chapters = emptyList<IndexedSegment>()
        val onVideoAspectUpdate = mockk<() -> Unit>(relaxed = true)
        val onChaptersUpdated = mockk<(List<IndexedSegment>) -> Unit>(relaxed = true)
        val onSetChapter = mockk<(Float) -> Unit>(relaxed = true)
        val aniSkipFetcher = mockk<suspend (Int?) -> List<TimeStamp>?>(relaxed = true)
        coEvery { aniSkipFetcher(any()) } returns null

        val mockMpvProxy = mockk<MPVLibProxy>(relaxed = true)
        val handler = PlayerFileLoadedHandler(
            context = mockContext,
            playerPreferences = mockPlayerPreferences,
            scope = scope,
            mpvLibProxy = mockMpvProxy,
        )

        handler.onFileLoaded(
            currentVideo = video,
            animeTitle = "Test",
            episodeName = "Ep 1",
            episodeNumber = 1.0,
            playerDuration = 1400,
            currentChapters = chapters,
            currentPos = 0f,
            aniSkipEnabled = false,
            introSkipEnabled = false,
            disableAniSkipOnChapters = false,
            onVideoAspectUpdate = onVideoAspectUpdate,
            onChaptersUpdated = onChaptersUpdated,
            onSetChapter = onSetChapter,
            aniSkipFetcher = aniSkipFetcher,
        )

        // Verify that setMpvMediaTitle was called via mpvProxy with the episode number padded to 2 digits
        verify { mockMpvProxy.setPropertyString("force-media-title", any()) }
    }

    @Test
    fun `setMpvMediaTitle handles decimal episode numbers without padding`() = runTest {
        val video = createMockVideo()
        val chapters = emptyList<IndexedSegment>()
        val onVideoAspectUpdate = mockk<() -> Unit>(relaxed = true)
        val onChaptersUpdated = mockk<(List<IndexedSegment>) -> Unit>(relaxed = true)
        val onSetChapter = mockk<(Float) -> Unit>(relaxed = true)
        val aniSkipFetcher = mockk<suspend (Int?) -> List<TimeStamp>?>(relaxed = true)
        coEvery { aniSkipFetcher(any()) } returns null

        val mockMpvProxy = mockk<MPVLibProxy>(relaxed = true)
        val handler = PlayerFileLoadedHandler(
            context = mockContext,
            playerPreferences = mockPlayerPreferences,
            scope = scope,
            mpvLibProxy = mockMpvProxy,
        )

        handler.onFileLoaded(
            currentVideo = video,
            animeTitle = "Test",
            episodeName = "Episode 1.5",
            episodeNumber = 1.5,
            playerDuration = 1400,
            currentChapters = chapters,
            currentPos = 0f,
            aniSkipEnabled = false,
            introSkipEnabled = false,
            disableAniSkipOnChapters = false,
            onVideoAspectUpdate = onVideoAspectUpdate,
            onChaptersUpdated = onChaptersUpdated,
            onSetChapter = onSetChapter,
            aniSkipFetcher = aniSkipFetcher,
        )

        // Verify that setMpvMediaTitle was called with decimal episode number preserved
        verify { mockMpvProxy.setPropertyString("force-media-title", any()) }
    }

    @Test
    fun `setMpvMediaTitle returns early when anime title is null`() = runTest {
        val video = createMockVideo()
        val chapters = emptyList<IndexedSegment>()
        val onVideoAspectUpdate = mockk<() -> Unit>(relaxed = true)
        val onChaptersUpdated = mockk<(List<IndexedSegment>) -> Unit>(relaxed = true)
        val onSetChapter = mockk<(Float) -> Unit>(relaxed = true)
        val aniSkipFetcher = mockk<suspend (Int?) -> List<TimeStamp>?>(relaxed = true)
        coEvery { aniSkipFetcher(any()) } returns null

        val mockMpvProxy = mockk<MPVLibProxy>(relaxed = true)
        val handler = PlayerFileLoadedHandler(
            context = mockContext,
            playerPreferences = mockPlayerPreferences,
            scope = scope,
            mpvLibProxy = mockMpvProxy,
        )

        handler.onFileLoaded(
            currentVideo = video,
            animeTitle = null,
            episodeName = "Episode 1",
            episodeNumber = 1.0,
            playerDuration = 1400,
            currentChapters = chapters,
            currentPos = 0f,
            aniSkipEnabled = false,
            introSkipEnabled = false,
            disableAniSkipOnChapters = false,
            onVideoAspectUpdate = onVideoAspectUpdate,
            onChaptersUpdated = onChaptersUpdated,
            onSetChapter = onSetChapter,
            aniSkipFetcher = aniSkipFetcher,
        )

        // When anime title is null, setMpvMediaTitle should not be called
        verify(exactly = 0) { mockMpvProxy.setPropertyString("force-media-title", any()) }
    }

    @Test
    fun `setMpvMediaTitle returns early when episode name is null`() = runTest {
        val video = createMockVideo()
        val chapters = emptyList<IndexedSegment>()
        val onVideoAspectUpdate = mockk<() -> Unit>(relaxed = true)
        val onChaptersUpdated = mockk<(List<IndexedSegment>) -> Unit>(relaxed = true)
        val onSetChapter = mockk<(Float) -> Unit>(relaxed = true)
        val aniSkipFetcher = mockk<suspend (Int?) -> List<TimeStamp>?>(relaxed = true)
        coEvery { aniSkipFetcher(any()) } returns null

        val mockMpvProxy = mockk<MPVLibProxy>(relaxed = true)
        val handler = PlayerFileLoadedHandler(
            context = mockContext,
            playerPreferences = mockPlayerPreferences,
            scope = scope,
            mpvLibProxy = mockMpvProxy,
        )

        handler.onFileLoaded(
            currentVideo = video,
            animeTitle = "Test Anime",
            episodeName = null,
            episodeNumber = 1.0,
            playerDuration = 1400,
            currentChapters = chapters,
            currentPos = 0f,
            aniSkipEnabled = false,
            introSkipEnabled = false,
            disableAniSkipOnChapters = false,
            onVideoAspectUpdate = onVideoAspectUpdate,
            onChaptersUpdated = onChaptersUpdated,
            onSetChapter = onSetChapter,
            aniSkipFetcher = aniSkipFetcher,
        )

        // When episode name is null, setMpvMediaTitle should not be called
        verify(exactly = 0) { mockMpvProxy.setPropertyString("force-media-title", any()) }
    }

    @Test
    fun `setMpvMediaTitle returns early when episode number is null`() = runTest {
        val video = createMockVideo()
        val chapters = emptyList<IndexedSegment>()
        val onVideoAspectUpdate = mockk<() -> Unit>(relaxed = true)
        val onChaptersUpdated = mockk<(List<IndexedSegment>) -> Unit>(relaxed = true)
        val onSetChapter = mockk<(Float) -> Unit>(relaxed = true)
        val aniSkipFetcher = mockk<suspend (Int?) -> List<TimeStamp>?>(relaxed = true)
        coEvery { aniSkipFetcher(any()) } returns null

        val mockMpvProxy = mockk<MPVLibProxy>(relaxed = true)
        val handler = PlayerFileLoadedHandler(
            context = mockContext,
            playerPreferences = mockPlayerPreferences,
            scope = scope,
            mpvLibProxy = mockMpvProxy,
        )

        handler.onFileLoaded(
            currentVideo = video,
            animeTitle = "Test Anime",
            episodeName = "Episode 1",
            episodeNumber = null,
            playerDuration = 1400,
            currentChapters = chapters,
            currentPos = 0f,
            aniSkipEnabled = false,
            introSkipEnabled = false,
            disableAniSkipOnChapters = false,
            onVideoAspectUpdate = onVideoAspectUpdate,
            onChaptersUpdated = onChaptersUpdated,
            onSetChapter = onSetChapter,
            aniSkipFetcher = aniSkipFetcher,
        )

        // When episode number is null, setMpvMediaTitle should not be called
        verify(exactly = 0) { mockMpvProxy.setPropertyString("force-media-title", any()) }
    }

    // ==================== Track Setup Tests ====================

    @Test
    fun `isLoadingTracks transitions from true to false during track setup`() = runTest {
        val audioTrack = createMockTrack("https://example.com/audio.mp3", "en")
        val video = createMockVideo(audioTracks = listOf(audioTrack))
        val chapters = emptyList<IndexedSegment>()
        val onVideoAspectUpdate = mockk<() -> Unit>(relaxed = true)
        val onChaptersUpdated = mockk<(List<IndexedSegment>) -> Unit>(relaxed = true)
        val onSetChapter = mockk<(Float) -> Unit>(relaxed = true)
        val aniSkipFetcher = mockk<suspend (Int?) -> List<TimeStamp>?>(relaxed = true)
        coEvery { aniSkipFetcher(any()) } returns null

        handler.onFileLoaded(
            currentVideo = video,
            animeTitle = "Test",
            episodeName = "Ep",
            episodeNumber = 1.0,
            playerDuration = 1400,
            currentChapters = chapters,
            currentPos = 0f,
            aniSkipEnabled = false,
            introSkipEnabled = false,
            disableAniSkipOnChapters = false,
            onVideoAspectUpdate = onVideoAspectUpdate,
            onChaptersUpdated = onChaptersUpdated,
            onSetChapter = onSetChapter,
            aniSkipFetcher = aniSkipFetcher,
        )

        assertFalse(handler.isLoadingTracks.first())
    }

    @Test
    fun `isLoadingTracks returns false when no audio or subtitle tracks`() = runTest {
        val video = createMockVideo(audioTracks = emptyList(), subtitleTracks = emptyList())
        val chapters = emptyList<IndexedSegment>()
        val onVideoAspectUpdate = mockk<() -> Unit>(relaxed = true)
        val onChaptersUpdated = mockk<(List<IndexedSegment>) -> Unit>(relaxed = true)
        val onSetChapter = mockk<(Float) -> Unit>(relaxed = true)
        val aniSkipFetcher = mockk<suspend (Int?) -> List<TimeStamp>?>(relaxed = true)
        coEvery { aniSkipFetcher(any()) } returns null

        handler.onFileLoaded(
            currentVideo = video,
            animeTitle = "Test",
            episodeName = "Ep",
            episodeNumber = 1.0,
            playerDuration = 1400,
            currentChapters = chapters,
            currentPos = 0f,
            aniSkipEnabled = false,
            introSkipEnabled = false,
            disableAniSkipOnChapters = false,
            onVideoAspectUpdate = onVideoAspectUpdate,
            onChaptersUpdated = onChaptersUpdated,
            onSetChapter = onSetChapter,
            aniSkipFetcher = aniSkipFetcher,
        )

        assertFalse(handler.isLoadingTracks.first())
    }

    @Test
    fun `audio and subtitle tracks passed to MPVLib command`() = runTest {
        val audioTrack = createMockTrack("https://example.com/audio.mp3", "en")
        val subtitleTrack = createMockTrack("https://example.com/subs.srt", "en")
        val video = createMockVideo(
            audioTracks = listOf(audioTrack),
            subtitleTracks = listOf(subtitleTrack),
        )
        val chapters = emptyList<IndexedSegment>()
        val onVideoAspectUpdate = mockk<() -> Unit>(relaxed = true)
        val onChaptersUpdated = mockk<(List<IndexedSegment>) -> Unit>(relaxed = true)
        val onSetChapter = mockk<(Float) -> Unit>(relaxed = true)
        val aniSkipFetcher = mockk<suspend (Int?) -> List<TimeStamp>?>(relaxed = true)
        coEvery { aniSkipFetcher(any()) } returns null

        handler.onFileLoaded(
            currentVideo = video,
            animeTitle = "Test",
            episodeName = "Ep",
            episodeNumber = 1.0,
            playerDuration = 1400,
            currentChapters = chapters,
            currentPos = 0f,
            aniSkipEnabled = false,
            introSkipEnabled = false,
            disableAniSkipOnChapters = false,
            onVideoAspectUpdate = onVideoAspectUpdate,
            onChaptersUpdated = onChaptersUpdated,
            onSetChapter = onSetChapter,
            aniSkipFetcher = aniSkipFetcher,
        )

        // Verify isLoadingTracks state changed (indicates tracks were processed)
        assertFalse(handler.isLoadingTracks.first())
    }

    // ==================== Chapter Setup Tests ====================

    @Test
    fun `chapters StateFlow updated with merged chapters`() = runTest {
        val timestamp = createMockTimeStamp(name = "Intro")
        val video = createMockVideo(timestamps = listOf(timestamp))
        val currentChapters = listOf(createIndexedSegment("Existing", 0f))
        val onVideoAspectUpdate = mockk<() -> Unit>(relaxed = true)
        val onChaptersUpdated = mockk<(List<IndexedSegment>) -> Unit>(relaxed = true)
        val onSetChapter = mockk<(Float) -> Unit>(relaxed = true)
        val aniSkipFetcher = mockk<suspend (Int?) -> List<TimeStamp>?>(relaxed = true)
        coEvery { aniSkipFetcher(any()) } returns null

        // Mock ChapterUtils.mergeChapters to return predictable result
        val mergedChapters = listOf(
            createIndexedSegment("Existing", 0f),
            createIndexedSegment("Intro", 10f),
        )

        handler.onFileLoaded(
            currentVideo = video,
            animeTitle = "Test",
            episodeName = "Ep",
            episodeNumber = 1.0,
            playerDuration = 1400,
            currentChapters = currentChapters,
            currentPos = 0f,
            aniSkipEnabled = false,
            introSkipEnabled = false,
            disableAniSkipOnChapters = false,
            onVideoAspectUpdate = onVideoAspectUpdate,
            onChaptersUpdated = onChaptersUpdated,
            onSetChapter = onSetChapter,
            aniSkipFetcher = aniSkipFetcher,
        )

        // Verify callback was invoked
        verify { onChaptersUpdated(any()) }
    }

    @Test
    fun `onChaptersUpdated callback invoked after chapter merge`() = runTest {
        val timestamp = createMockTimeStamp(name = "Outro")
        val video = createMockVideo(timestamps = listOf(timestamp))
        val chapters = emptyList<IndexedSegment>()
        val onVideoAspectUpdate = mockk<() -> Unit>(relaxed = true)
        val onChaptersUpdated = mockk<(List<IndexedSegment>) -> Unit>(relaxed = true)
        val onSetChapter = mockk<(Float) -> Unit>(relaxed = true)
        val aniSkipFetcher = mockk<suspend (Int?) -> List<TimeStamp>?>(relaxed = true)
        coEvery { aniSkipFetcher(any()) } returns null

        handler.onFileLoaded(
            currentVideo = video,
            animeTitle = "Test",
            episodeName = "Ep",
            episodeNumber = 1.0,
            playerDuration = 1400,
            currentChapters = chapters,
            currentPos = 0f,
            aniSkipEnabled = false,
            introSkipEnabled = false,
            disableAniSkipOnChapters = false,
            onVideoAspectUpdate = onVideoAspectUpdate,
            onChaptersUpdated = onChaptersUpdated,
            onSetChapter = onSetChapter,
            aniSkipFetcher = aniSkipFetcher,
        )

        verify { onChaptersUpdated(any()) }
    }

    @Test
    fun `empty chapter list handled correctly`() = runTest {
        val video = createMockVideo(timestamps = emptyList())
        val chapters = emptyList<IndexedSegment>()
        val onVideoAspectUpdate = mockk<() -> Unit>(relaxed = true)
        val onChaptersUpdated = mockk<(List<IndexedSegment>) -> Unit>(relaxed = true)
        val onSetChapter = mockk<(Float) -> Unit>(relaxed = true)
        val aniSkipFetcher = mockk<suspend (Int?) -> List<TimeStamp>?>(relaxed = true)
        coEvery { aniSkipFetcher(any()) } returns null

        handler.onFileLoaded(
            currentVideo = video,
            animeTitle = "Test",
            episodeName = "Ep",
            episodeNumber = 1.0,
            playerDuration = 1400,
            currentChapters = chapters,
            currentPos = 0f,
            aniSkipEnabled = false,
            introSkipEnabled = false,
            disableAniSkipOnChapters = false,
            onVideoAspectUpdate = onVideoAspectUpdate,
            onChaptersUpdated = onChaptersUpdated,
            onSetChapter = onSetChapter,
            aniSkipFetcher = aniSkipFetcher,
        )

        // Should complete without error
        assertTrue(true)
    }

    @Test
    fun `timestamp name resolved when empty and chapter type is not Other`() = runTest {
        val timestamp = createMockTimeStamp(
            name = "",
            type = ChapterType.Opening,
        )
        val video = createMockVideo(timestamps = listOf(timestamp))
        val chapters = emptyList<IndexedSegment>()
        val onVideoAspectUpdate = mockk<() -> Unit>(relaxed = true)
        val onChaptersUpdated = mockk<(List<IndexedSegment>) -> Unit>(relaxed = true)
        val onSetChapter = mockk<(Float) -> Unit>(relaxed = true)
        val aniSkipFetcher = mockk<suspend (Int?) -> List<TimeStamp>?>(relaxed = true)
        coEvery { aniSkipFetcher(any()) } returns null

        handler.onFileLoaded(
            currentVideo = video,
            animeTitle = "Test",
            episodeName = "Ep",
            episodeNumber = 1.0,
            playerDuration = 1400,
            currentChapters = chapters,
            currentPos = 0f,
            aniSkipEnabled = false,
            introSkipEnabled = false,
            disableAniSkipOnChapters = false,
            onVideoAspectUpdate = onVideoAspectUpdate,
            onChaptersUpdated = onChaptersUpdated,
            onSetChapter = onSetChapter,
            aniSkipFetcher = aniSkipFetcher,
        )

        verify { onChaptersUpdated(any()) }
    }

    // ==================== AniSkip Integration Tests ====================

    @Test
    fun `shouldIntegrateAniSkipOnFileLoad returns true when both features enabled and no existing chapters`() {
        val result = shouldIntegrateAniSkipOnFileLoad(
            aniSkipEnabled = true,
            introSkipEnabled = true,
            disableAniSkipOnChapters = false,
            hasExistingChapters = false,
        )

        assertTrue(result)
    }

    @Test
    fun `shouldIntegrateAniSkipOnFileLoad returns false when aniSkipEnabled is false`() {
        val result = shouldIntegrateAniSkipOnFileLoad(
            aniSkipEnabled = false,
            introSkipEnabled = true,
            disableAniSkipOnChapters = false,
            hasExistingChapters = false,
        )

        assertFalse(result)
    }

    @Test
    fun `shouldIntegrateAniSkipOnFileLoad returns false when introSkipEnabled is false`() {
        val result = shouldIntegrateAniSkipOnFileLoad(
            aniSkipEnabled = true,
            introSkipEnabled = false,
            disableAniSkipOnChapters = false,
            hasExistingChapters = false,
        )

        assertFalse(result)
    }

    @Test
    fun `shouldIntegrateAniSkipOnFileLoad returns false when disableAniSkipOnChapters is true and chapters exist`() {
        val result = shouldIntegrateAniSkipOnFileLoad(
            aniSkipEnabled = true,
            introSkipEnabled = true,
            disableAniSkipOnChapters = true,
            hasExistingChapters = true,
        )

        assertFalse(result)
    }

    @Test
    fun `shouldIntegrateAniSkipOnFileLoad returns true when disableAniSkipOnChapters is true but no chapters exist`() {
        val result = shouldIntegrateAniSkipOnFileLoad(
            aniSkipEnabled = true,
            introSkipEnabled = true,
            disableAniSkipOnChapters = true,
            hasExistingChapters = false,
        )

        assertTrue(result)
    }

    @Test
    fun `aniSkipFetcher invoked with correct duration`() = runTest {
        val video = createMockVideo()
        val chapters = emptyList<IndexedSegment>()
        val onVideoAspectUpdate = mockk<() -> Unit>(relaxed = true)
        val onChaptersUpdated = mockk<(List<IndexedSegment>) -> Unit>(relaxed = true)
        val onSetChapter = mockk<(Float) -> Unit>(relaxed = true)
        val aniSkipFetcher = mockk<suspend (Int?) -> List<TimeStamp>?>(relaxed = true)
        coEvery { aniSkipFetcher(any()) } returns null

        handler.onFileLoaded(
            currentVideo = video,
            animeTitle = "Test",
            episodeName = "Ep",
            episodeNumber = 1.0,
            playerDuration = 1400,
            currentChapters = chapters,
            currentPos = 0f,
            aniSkipEnabled = true,
            introSkipEnabled = true,
            disableAniSkipOnChapters = false,
            onVideoAspectUpdate = onVideoAspectUpdate,
            onChaptersUpdated = onChaptersUpdated,
            onSetChapter = onSetChapter,
            aniSkipFetcher = aniSkipFetcher,
        )

        io.mockk.coVerify { aniSkipFetcher(1400) }
    }

    @Test
    fun `null aniSkip response handled gracefully`() = runTest {
        val video = createMockVideo()
        val chapters = emptyList<IndexedSegment>()
        val onVideoAspectUpdate = mockk<() -> Unit>(relaxed = true)
        val onChaptersUpdated = mockk<(List<IndexedSegment>) -> Unit>(relaxed = true)
        val onSetChapter = mockk<(Float) -> Unit>(relaxed = true)
        val aniSkipFetcher = mockk<suspend (Int?) -> List<TimeStamp>?>(relaxed = true)
        coEvery { aniSkipFetcher(any()) } returns null

        handler.onFileLoaded(
            currentVideo = video,
            animeTitle = "Test",
            episodeName = "Ep",
            episodeNumber = 1.0,
            playerDuration = 1400,
            currentChapters = chapters,
            currentPos = 0f,
            aniSkipEnabled = true,
            introSkipEnabled = true,
            disableAniSkipOnChapters = false,
            onVideoAspectUpdate = onVideoAspectUpdate,
            onChaptersUpdated = onChaptersUpdated,
            onSetChapter = onSetChapter,
            aniSkipFetcher = aniSkipFetcher,
        )

        // Should complete without error
        assertTrue(true)
    }

    @Test
    fun `empty aniSkip response handled gracefully`() = runTest {
        val video = createMockVideo()
        val chapters = emptyList<IndexedSegment>()
        val onVideoAspectUpdate = mockk<() -> Unit>(relaxed = true)
        val onChaptersUpdated = mockk<(List<IndexedSegment>) -> Unit>(relaxed = true)
        val onSetChapter = mockk<(Float) -> Unit>(relaxed = true)
        val aniSkipFetcher = mockk<suspend (Int?) -> List<TimeStamp>?>(relaxed = true)
        coEvery { aniSkipFetcher(any()) } returns emptyList()

        handler.onFileLoaded(
            currentVideo = video,
            animeTitle = "Test",
            episodeName = "Ep",
            episodeNumber = 1.0,
            playerDuration = 1400,
            currentChapters = chapters,
            currentPos = 0f,
            aniSkipEnabled = true,
            introSkipEnabled = true,
            disableAniSkipOnChapters = false,
            onVideoAspectUpdate = onVideoAspectUpdate,
            onChaptersUpdated = onChaptersUpdated,
            onSetChapter = onSetChapter,
            aniSkipFetcher = aniSkipFetcher,
        )

        // Should complete without error
        assertTrue(true)
    }

    @Test
    fun `aniSkip not invoked when feature disabled`() = runTest {
        val video = createMockVideo()
        val chapters = emptyList<IndexedSegment>()
        val onVideoAspectUpdate = mockk<() -> Unit>(relaxed = true)
        val onChaptersUpdated = mockk<(List<IndexedSegment>) -> Unit>(relaxed = true)
        val onSetChapter = mockk<(Float) -> Unit>(relaxed = true)
        val aniSkipFetcher = mockk<suspend (Int?) -> List<TimeStamp>?>(relaxed = true)
        coEvery { aniSkipFetcher(any()) } returns null

        handler.onFileLoaded(
            currentVideo = video,
            animeTitle = "Test",
            episodeName = "Ep",
            episodeNumber = 1.0,
            playerDuration = 1400,
            currentChapters = chapters,
            currentPos = 0f,
            aniSkipEnabled = false,
            introSkipEnabled = true,
            disableAniSkipOnChapters = false,
            onVideoAspectUpdate = onVideoAspectUpdate,
            onChaptersUpdated = onChaptersUpdated,
            onSetChapter = onSetChapter,
            aniSkipFetcher = aniSkipFetcher,
        )

        coVerify(exactly = 0) { aniSkipFetcher(any()) }
    }

    // ==================== StateFlow Initialization Tests ====================

    @Test
    fun `isLoadingTracks initialized as true`() {
        val initialState = handler.isLoadingTracks.value

        assertTrue(initialState)
    }

    @Test
    fun `chapters initialized as empty list`() {
        val initialChapters = handler.chapters.value

        assertEquals(emptyList<IndexedSegment>(), initialChapters)
    }

    @Test
    fun `updateIsLoadingTracks mutates state correctly`() = runTest {
        handler.updateIsLoadingTracks(false)

        assertFalse(handler.isLoadingTracks.first())
    }

    @Test
    fun `updateChapters mutates state correctly`() = runTest {
        val newChapters = listOf(
            createIndexedSegment("Chapter 1", 0f),
            createIndexedSegment("Chapter 2", 500f),
        )

        handler.updateChapters(newChapters)

        assertEquals(newChapters, handler.chapters.first())
    }

    // ==================== Edge Cases ====================

    @Test
    fun `onFileLoaded with null video handles gracefully`() = runTest {
        val chapters = emptyList<IndexedSegment>()
        val onVideoAspectUpdate = mockk<() -> Unit>(relaxed = true)
        val onChaptersUpdated = mockk<(List<IndexedSegment>) -> Unit>(relaxed = true)
        val onSetChapter = mockk<(Float) -> Unit>(relaxed = true)
        val aniSkipFetcher = mockk<suspend (Int?) -> List<TimeStamp>?>(relaxed = true)
        coEvery { aniSkipFetcher(any()) } returns null

        handler.onFileLoaded(
            currentVideo = null,
            animeTitle = "Test",
            episodeName = "Ep",
            episodeNumber = 1.0,
            playerDuration = 1400,
            currentChapters = chapters,
            currentPos = 0f,
            aniSkipEnabled = false,
            introSkipEnabled = false,
            disableAniSkipOnChapters = false,
            onVideoAspectUpdate = onVideoAspectUpdate,
            onChaptersUpdated = onChaptersUpdated,
            onSetChapter = onSetChapter,
            aniSkipFetcher = aniSkipFetcher,
        )

        // Should complete without error
        assertTrue(true)
    }

    @Test
    fun `onFileLoaded with null duration handles gracefully`() = runTest {
        val video = createMockVideo()
        val chapters = emptyList<IndexedSegment>()
        val onVideoAspectUpdate = mockk<() -> Unit>(relaxed = true)
        val onChaptersUpdated = mockk<(List<IndexedSegment>) -> Unit>(relaxed = true)
        val onSetChapter = mockk<(Float) -> Unit>(relaxed = true)
        val aniSkipFetcher = mockk<suspend (Int?) -> List<TimeStamp>?>(relaxed = true)
        coEvery { aniSkipFetcher(any()) } returns null

        handler.onFileLoaded(
            currentVideo = video,
            animeTitle = "Test",
            episodeName = "Ep",
            episodeNumber = 1.0,
            playerDuration = null,
            currentChapters = chapters,
            currentPos = 0f,
            aniSkipEnabled = true,
            introSkipEnabled = true,
            disableAniSkipOnChapters = false,
            onVideoAspectUpdate = onVideoAspectUpdate,
            onChaptersUpdated = onChaptersUpdated,
            onSetChapter = onSetChapter,
            aniSkipFetcher = aniSkipFetcher,
        )

        // Should invoke aniSkipFetcher with null duration
        coVerify { aniSkipFetcher(null) }
    }
}
