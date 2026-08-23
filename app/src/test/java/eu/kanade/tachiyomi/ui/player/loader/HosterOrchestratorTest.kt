package eu.kanade.tachiyomi.ui.player.loader

import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.HosterState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class HosterOrchestratorTest {

    private lateinit var testScope: TestScope
    private lateinit var orchestrator: HosterOrchestrator

    @BeforeEach
    fun setup() {
        testScope = TestScope()
        orchestrator = HosterOrchestrator(testScope, onNoVideosAvailable = {})
    }

    @Test
    fun `reset clears all state`() = runTest {
        orchestrator.updateIsLoadingHosters(false)

        orchestrator.reset()

        assertEquals(emptyList<Hoster>(), orchestrator.hosterList.first())
        assertEquals(emptyList<HosterState>(), orchestrator.hosterState.first())
        assertEquals(emptyList<Boolean>(), orchestrator.hosterExpandedList.first())
        assertEquals(Pair(-1, -1), orchestrator.selectedHosterVideoIndex.first())
        assertNull(orchestrator.currentVideo.first())
    }

    @Test
    fun `updateIsLoadingHosters updates state`() = runTest {
        assertEquals(true, orchestrator.isLoadingHosters.first())

        orchestrator.updateIsLoadingHosters(false)

        assertEquals(false, orchestrator.isLoadingHosters.first())
    }

    @Test
    fun `initial state is correct`() = runTest {
        assertEquals(emptyList<Hoster>(), orchestrator.hosterList.first())
        assertEquals(true, orchestrator.isLoadingHosters.first())
        assertEquals(emptyList<HosterState>(), orchestrator.hosterState.first())
        assertEquals(emptyList<Boolean>(), orchestrator.hosterExpandedList.first())
        assertEquals(Pair(-1, -1), orchestrator.selectedHosterVideoIndex.first())
        assertNull(orchestrator.currentVideo.first())
    }

    @Test
    fun `cancelHosterVideoLinksJob does not throw`() {
        orchestrator.cancelHosterVideoLinksJob()
    }

    @Test
    fun `onVideoClicked ignores stale hoster index`() {
        assertDoesNotThrow {
            orchestrator.onVideoClicked(
                hosterIndex = 0,
                videoIndex = 0,
                currentSource = null,
                onSuccess = {},
                onFailure = {},
            )
        }
    }

    @Test
    fun `onHosterClicked ignores stale hoster index`() {
        assertDoesNotThrow {
            orchestrator.onHosterClicked(index = 0, currentSource = null)
        }
    }

    @Test
    fun `onHosterClicked toggles valid ready hoster expansion`() {
        orchestrator.setHosterStateForTest(
            listOf(
                HosterState.Ready(
                    name = "hoster",
                    videoList = listOf(Video(videoUrl = "https://example.invalid/video", videoTitle = "720p")),
                    videoState = listOf(Video.State.READY),
                ),
            ),
        )
        orchestrator.setHosterExpandedListForTest(listOf(false))

        orchestrator.onHosterClicked(index = 0, currentSource = null)

        assertEquals(listOf(true), orchestrator.hosterExpandedList.value)
    }

    @Test
    fun `onHosterClicked ignores idle hoster when source is missing`() = runTest {
        orchestrator.setHosterListForTest(listOf(Hoster(hosterName = "lazy", lazy = true)))
        orchestrator.setHosterStateForTest(listOf(HosterState.Idle("lazy")))
        orchestrator.setHosterExpandedListForTest(listOf(false))

        assertDoesNotThrow {
            orchestrator.onHosterClicked(index = 0, currentSource = null)
        }
        advanceUntilIdle()

        assertEquals(listOf(HosterState.Idle("lazy")), orchestrator.hosterState.value)
    }

    @Test
    fun `onVideoClicked ignores ready hoster with mismatched video state`() = runTest {
        orchestrator.setHosterStateForTest(
            listOf(
                HosterState.Ready(
                    name = "hoster",
                    videoList = listOf(Video(videoUrl = "https://example.invalid/video", videoTitle = "720p")),
                    videoState = emptyList(),
                ),
            ),
        )

        assertDoesNotThrow {
            orchestrator.onVideoClicked(
                hosterIndex = 0,
                videoIndex = 0,
                currentSource = null,
                onSuccess = {},
                onFailure = {},
            )
        }
        advanceUntilIdle()

        assertEquals(Pair(-1, -1), orchestrator.selectedHosterVideoIndex.value)
    }

    @Test
    fun `onVideoClicked loads valid ready video and calls success`() {
        val video = Video(videoUrl = "https://example.invalid/video", videoTitle = "720p")
        val successLatch = CountDownLatch(1)
        var readyVideo: Video? = null
        orchestrator.onVideoReady = { readyVideo = it }
        orchestrator.setHosterStateForTest(
            listOf(
                HosterState.Ready(
                    name = "hoster",
                    videoList = listOf(video),
                    videoState = listOf(Video.State.READY),
                ),
            ),
        )

        orchestrator.onVideoClicked(
            hosterIndex = 0,
            videoIndex = 0,
            currentSource = null,
            onSuccess = { successLatch.countDown() },
            onFailure = {},
        )

        assertEquals(true, successLatch.await(5, TimeUnit.SECONDS))
        assertEquals(video, readyVideo)
        assertEquals(Pair(0, 0), orchestrator.selectedHosterVideoIndex.value)
    }

    @Test
    fun `loadHosters reports no available videos without an uncaught exception`() {
        val uncaughtExceptions = mutableListOf<Throwable>()
        val scope = CoroutineScope(
            SupervisorJob() + CoroutineExceptionHandler { _, error ->
                synchronized(uncaughtExceptions) {
                    uncaughtExceptions += error
                }
            },
        )
        val errorCount = AtomicInteger()
        val errorLatch = CountDownLatch(1)
        val allowErrorCallbackToReturn = CompletableDeferred<Unit>()
        val localOrchestrator = HosterOrchestrator(
            scope = scope,
            onNoVideosAvailable = {
                errorCount.incrementAndGet()
                errorLatch.countDown()
                allowErrorCallbackToReturn.await()
            },
        )

        try {
            localOrchestrator.loadHosters(
                source = mockk(),
                hosterList = listOf(
                    Hoster(hosterName = "failed", videoList = null),
                    Hoster(hosterName = "empty", videoList = emptyList()),
                ),
                hosterIndex = -1,
                videoIndex = -1,
            )

            assertEquals(true, errorLatch.await(5, TimeUnit.SECONDS))
            val loadJob = scope.coroutineContext[Job]?.children?.single()
            allowErrorCallbackToReturn.complete(Unit)
            runBlocking { loadJob?.join() }
            assertEquals(1, errorCount.get())
            assertEquals(Pair(-1, -1), localOrchestrator.selectedHosterVideoIndex.value)
            assertNull(localOrchestrator.currentVideo.value)
            synchronized(uncaughtExceptions) {
                assertEquals(emptyList<Throwable>(), uncaughtExceptions)
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `onVideoClicked reports failure when the last video does not resolve`() {
        val uncaughtExceptions = mutableListOf<Throwable>()
        val completionLatch = CountDownLatch(1)
        val scope = CoroutineScope(
            SupervisorJob() + CoroutineExceptionHandler { _, error ->
                synchronized(uncaughtExceptions) {
                    uncaughtExceptions += error
                }
                completionLatch.countDown()
            },
        )
        val failureCount = AtomicInteger()
        val localOrchestrator = HosterOrchestrator(scope, onNoVideosAvailable = {})
        localOrchestrator.setHosterStateForTest(
            listOf(
                HosterState.Ready(
                    name = "hoster",
                    videoList = listOf(Video(videoUrl = "", videoTitle = "unavailable")),
                    videoState = listOf(Video.State.QUEUE),
                ),
            ),
        )

        try {
            localOrchestrator.onVideoClicked(
                hosterIndex = 0,
                videoIndex = 0,
                currentSource = mockk(),
                onSuccess = {},
                onFailure = {
                    failureCount.incrementAndGet()
                    completionLatch.countDown()
                },
            )

            assertEquals(true, completionLatch.await(5, TimeUnit.SECONDS))
            assertEquals(1, failureCount.get())
            synchronized(uncaughtExceptions) {
                assertEquals(emptyList<Throwable>(), uncaughtExceptions)
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `loadHosters keeps first viable video order`() {
        val firstVideo = Video(videoUrl = "https://example.invalid/first", videoTitle = "first")
        val secondVideo = Video(videoUrl = "https://example.invalid/second", videoTitle = "second")
        val readyLatch = CountDownLatch(1)
        var readyVideo: Video? = null
        orchestrator.onVideoReady = { video ->
            readyVideo = video
            readyLatch.countDown()
        }

        orchestrator.loadHosters(
            source = mockk(),
            hosterList = listOf(
                Hoster(hosterName = "first", videoList = listOf(firstVideo)),
                Hoster(hosterName = "second", videoList = listOf(secondVideo)),
            ),
            hosterIndex = -1,
            videoIndex = -1,
        )

        assertEquals(true, readyLatch.await(5, TimeUnit.SECONDS))
        assertEquals(firstVideo.copy(initialized = true), readyVideo)
        assertEquals(Pair(0, 0), orchestrator.selectedHosterVideoIndex.value)
    }

    @Test
    fun `loadHosters tries the next video before reporting a terminal error`() {
        val unavailableVideo = Video(videoUrl = "https://example.invalid/unavailable", videoTitle = "unavailable")
        val availableVideo = Video(videoUrl = "https://example.invalid/available", videoTitle = "available")
        val videos = listOf(unavailableVideo, availableVideo)
        val source = mockk<AnimeHttpSource>()
        every { with(source) { videos.sortVideos() } } returns videos
        coEvery { source.resolveVideo(unavailableVideo) } returns null
        coEvery { source.resolveVideo(availableVideo) } returns availableVideo
        val completionLatch = CountDownLatch(1)
        val errorCount = AtomicInteger()
        var readyVideo: Video? = null
        val localOrchestrator = HosterOrchestrator(
            scope = testScope,
            onNoVideosAvailable = {
                errorCount.incrementAndGet()
                completionLatch.countDown()
            },
        )
        localOrchestrator.onVideoReady = { video ->
            readyVideo = video
            completionLatch.countDown()
        }
        localOrchestrator.setCurrentVideoForTest(
            Video(videoUrl = "https://example.invalid/previous", videoTitle = "previous"),
        )

        localOrchestrator.loadHosters(
            source = source,
            hosterList = listOf(Hoster(hosterName = "hoster", videoList = videos)),
            hosterIndex = -1,
            videoIndex = -1,
        )

        assertEquals(true, completionLatch.await(5, TimeUnit.SECONDS))
        assertEquals(0, errorCount.get())
        assertEquals(availableVideo.copy(initialized = true), readyVideo)
        assertEquals(Pair(0, 1), localOrchestrator.selectedHosterVideoIndex.value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun HosterOrchestrator.setHosterListForTest(value: List<Hoster>) {
        val field = HosterOrchestrator::class.java.getDeclaredField("_hosterList")
        field.isAccessible = true
        (field.get(this) as MutableStateFlow<List<Hoster>>).value = value
    }

    @Suppress("UNCHECKED_CAST")
    private fun HosterOrchestrator.setHosterStateForTest(value: List<HosterState>) {
        val field = HosterOrchestrator::class.java.getDeclaredField("_hosterState")
        field.isAccessible = true
        (field.get(this) as MutableStateFlow<List<HosterState>>).value = value
    }

    @Suppress("UNCHECKED_CAST")
    private fun HosterOrchestrator.setHosterExpandedListForTest(value: List<Boolean>) {
        val field = HosterOrchestrator::class.java.getDeclaredField("_hosterExpandedList")
        field.isAccessible = true
        (field.get(this) as MutableStateFlow<List<Boolean>>).value = value
    }

    @Suppress("UNCHECKED_CAST")
    private fun HosterOrchestrator.setCurrentVideoForTest(value: Video) {
        val field = HosterOrchestrator::class.java.getDeclaredField("_currentVideo")
        field.isAccessible = true
        (field.get(this) as MutableStateFlow<Video?>).value = value
    }
}
