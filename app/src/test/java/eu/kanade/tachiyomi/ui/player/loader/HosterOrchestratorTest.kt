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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
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

    @Test
    fun `a load failure marks the current video failed and loads the next server`() {
        val first = Video(videoUrl = "https://example.invalid/first", videoTitle = "first")
        val second = Video(videoUrl = "https://example.invalid/second", videoTitle = "second")
        val readyVideos = LinkedBlockingQueue<Video>()
        orchestrator.onVideoReady = { readyVideos += it }
        orchestrator.setHosterStateForTest(
            listOf(
                HosterState.Ready("first", listOf(first), listOf(Video.State.READY)),
                HosterState.Ready("second", listOf(second), listOf(Video.State.READY)),
            ),
        )
        orchestrator.onVideoClicked(0, 0, currentSource = null, onSuccess = {}, onFailure = {})
        assertEquals(first, readyVideos.poll(5, TimeUnit.SECONDS))

        orchestrator.onVideoLoadFailed(first, source = null)

        assertEquals(second, readyVideos.poll(5, TimeUnit.SECONDS))
        assertEquals(Pair(1, 0), orchestrator.selectedHosterVideoIndex.value)
        assertEquals(Video.State.ERROR, (orchestrator.hosterState.value[0] as HosterState.Ready).videoState[0])
    }

    @Test
    fun `when every server fails no available videos is reported once`() {
        val first = Video(videoUrl = "https://example.invalid/first", videoTitle = "first")
        val second = Video(videoUrl = "https://example.invalid/second", videoTitle = "second")
        val readyVideos = LinkedBlockingQueue<Video>()
        val errorCount = AtomicInteger()
        val errorLatch = CountDownLatch(1)
        val loadingStates = LinkedBlockingQueue<Boolean>()
        val localOrchestrator = HosterOrchestrator(
            scope = testScope,
            onNoVideosAvailable = {
                errorCount.incrementAndGet()
                errorLatch.countDown()
            },
        )
        localOrchestrator.onVideoReady = { readyVideos += it }
        localOrchestrator.onLoadingStateChanged = { loadingStates += it }
        localOrchestrator.setHosterStateForTest(
            listOf(HosterState.Ready("hoster", listOf(first, second), List(2) { Video.State.READY })),
        )
        localOrchestrator.onVideoClicked(0, 0, currentSource = null, onSuccess = {}, onFailure = {})
        assertEquals(first, readyVideos.poll(5, TimeUnit.SECONDS))

        localOrchestrator.onVideoLoadFailed(first, source = null)
        assertEquals(second, readyVideos.poll(5, TimeUnit.SECONDS))
        localOrchestrator.onVideoLoadFailed(second, source = null)

        assertEquals(true, errorLatch.await(5, TimeUnit.SECONDS))
        localOrchestrator.onVideoLoadFailed(second, source = null)
        Thread.sleep(200)
        assertEquals(1, errorCount.get())
        assertEquals(false, loadingStates.last())
        assertNull(localOrchestrator.currentVideo.value)
    }

    @Test
    fun `a stale error for a video that is no longer current is ignored`() {
        val first = Video(videoUrl = "https://example.invalid/first", videoTitle = "first")
        val second = Video(videoUrl = "https://example.invalid/second", videoTitle = "second")
        val readyVideos = LinkedBlockingQueue<Video>()
        orchestrator.onVideoReady = { readyVideos += it }
        orchestrator.setHosterStateForTest(
            listOf(HosterState.Ready("hoster", listOf(first, second), List(2) { Video.State.READY })),
        )
        orchestrator.onVideoClicked(0, 0, currentSource = null, onSuccess = {}, onFailure = {})
        assertEquals(first, readyVideos.poll(5, TimeUnit.SECONDS))
        orchestrator.onVideoClicked(0, 1, currentSource = null, onSuccess = {}, onFailure = {})
        assertEquals(second, readyVideos.poll(5, TimeUnit.SECONDS))

        orchestrator.onVideoLoadFailed(first, source = null)

        assertNull(readyVideos.poll(200, TimeUnit.MILLISECONDS))
        assertEquals(second, orchestrator.currentVideo.value)
        assertEquals(Pair(0, 1), orchestrator.selectedHosterVideoIndex.value)
        assertEquals(
            List(2) { Video.State.READY },
            (orchestrator.hosterState.value[0] as HosterState.Ready).videoState,
        )
    }

    @Test
    fun `a failure while a hoster is still loading waits for it`() {
        val fast = Video(videoUrl = "https://example.invalid/fast", videoTitle = "fast", preferred = true)
        val slow = Video(videoUrl = "https://example.invalid/slow", videoTitle = "slow")
        val fastHoster = Hoster(hosterName = "fast", videoList = listOf(fast))
        val slowHoster = Hoster(hosterName = "slow", videoList = null)
        val releaseSlowHoster = CompletableDeferred<Unit>()
        val source = mockk<AnimeHttpSource>()
        every { with(source) { listOf(fast).sortVideos() } } returns listOf(fast)
        every { with(source) { listOf(slow).sortVideos() } } returns listOf(slow)
        coEvery { source.getVideoList(slowHoster) } coAnswers {
            releaseSlowHoster.await()
            listOf(slow)
        }
        coEvery { source.resolveVideo(any()) } answers { firstArg() }
        val readyVideos = LinkedBlockingQueue<Video>()
        val errorCount = AtomicInteger()
        val scope = CoroutineScope(SupervisorJob())
        val localOrchestrator = HosterOrchestrator(
            scope = scope,
            onNoVideosAvailable = { errorCount.incrementAndGet() },
        )
        localOrchestrator.onVideoReady = { readyVideos += it }

        try {
            localOrchestrator.loadHosters(source, listOf(fastHoster, slowHoster), hosterIndex = -1, videoIndex = -1)
            val loadedFast = readyVideos.poll(5, TimeUnit.SECONDS)
            assertEquals(fast.copy(initialized = true), loadedFast)

            localOrchestrator.onVideoLoadFailed(loadedFast!!, source)
            assertNull(readyVideos.poll(200, TimeUnit.MILLISECONDS))
            assertEquals(0, errorCount.get())

            releaseSlowHoster.complete(Unit)

            assertEquals(slow.copy(initialized = true), readyVideos.poll(5, TimeUnit.SECONDS))
            assertEquals(Pair(1, 0), localOrchestrator.selectedHosterVideoIndex.value)
            assertEquals(0, errorCount.get())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `a manual selection after a failure loads the selected video`() {
        val first = Video(videoUrl = "https://example.invalid/first", videoTitle = "first")
        val second = Video(videoUrl = "https://example.invalid/second", videoTitle = "second")
        val third = Video(videoUrl = "https://example.invalid/third", videoTitle = "third")
        val readyVideos = LinkedBlockingQueue<Video>()
        orchestrator.onVideoReady = { readyVideos += it }
        orchestrator.setHosterStateForTest(
            listOf(HosterState.Ready("hoster", listOf(first, second, third), List(3) { Video.State.READY })),
        )
        orchestrator.onVideoClicked(0, 0, currentSource = null, onSuccess = {}, onFailure = {})
        assertEquals(first, readyVideos.poll(5, TimeUnit.SECONDS))
        orchestrator.onVideoLoadFailed(first, source = null)
        assertEquals(second, readyVideos.poll(5, TimeUnit.SECONDS))

        orchestrator.onVideoClicked(0, 2, currentSource = null, onSuccess = {}, onFailure = {})

        assertEquals(third, readyVideos.poll(5, TimeUnit.SECONDS))
        assertEquals(Pair(0, 2), orchestrator.selectedHosterVideoIndex.value)
    }

    @Test
    fun `an error for the current video is ignored once the selection moved to another`() {
        val first = Video(videoUrl = "https://example.invalid/first", videoTitle = "first")
        val second = Video(videoUrl = "https://example.invalid/second", videoTitle = "second")
        val readyVideos = LinkedBlockingQueue<Video>()
        orchestrator.onVideoReady = { readyVideos += it }
        orchestrator.setHosterStateForTest(
            listOf(HosterState.Ready("hoster", listOf(first, second), List(2) { Video.State.READY })),
        )
        orchestrator.onVideoClicked(0, 0, currentSource = null, onSuccess = {}, onFailure = {})
        assertEquals(first, readyVideos.poll(5, TimeUnit.SECONDS))
        // A manual selection of the second video is still resolving.
        orchestrator.setSelectedHosterVideoIndexForTest(Pair(0, 1))

        orchestrator.onVideoLoadFailed(first, source = null)

        assertNull(readyVideos.poll(200, TimeUnit.MILLISECONDS))
        assertEquals(first, orchestrator.currentVideo.value)
        assertEquals(Pair(0, 1), orchestrator.selectedHosterVideoIndex.value)
        assertEquals(
            List(2) { Video.State.READY },
            (orchestrator.hosterState.value[0] as HosterState.Ready).videoState,
        )
    }

    @Test
    fun `a fallback still resolving does not replace a new episode`() {
        val fixture = ResolvingFallbackFixture()

        orchestrator.reset()
        fixture.releaseResolve.complete(Unit)

        assertNull(fixture.readyVideos.poll(300, TimeUnit.MILLISECONDS))
        assertNull(orchestrator.currentVideo.value)
    }

    @Test
    fun `a fallback still resolving does not replace a manual selection`() {
        val fixture = ResolvingFallbackFixture()

        orchestrator.onVideoClicked(0, 2, fixture.source, onSuccess = {}, onFailure = {})
        assertEquals(fixture.manual, fixture.readyVideos.poll(5, TimeUnit.SECONDS))
        fixture.releaseResolve.complete(Unit)

        assertNull(fixture.readyVideos.poll(300, TimeUnit.MILLISECONDS))
        assertEquals(fixture.manual, orchestrator.currentVideo.value)
        assertEquals(Pair(0, 2), orchestrator.selectedHosterVideoIndex.value)
        assertEquals(
            listOf(Video.State.ERROR, Video.State.QUEUE, Video.State.READY),
            (orchestrator.hosterState.value[0] as HosterState.Ready).videoState,
        )
    }

    @Test
    fun `a failure waits for a lazy hoster the user started loading`() {
        val first = Video(videoUrl = "https://example.invalid/first", videoTitle = "first")
        val lazy = Video(videoUrl = "https://example.invalid/lazy", videoTitle = "lazy")
        val readyVideos = LinkedBlockingQueue<Video>()
        val errorCount = AtomicInteger()
        val localOrchestrator = HosterOrchestrator(testScope, onNoVideosAvailable = { errorCount.incrementAndGet() })
        localOrchestrator.onVideoReady = { readyVideos += it }
        localOrchestrator.setHosterStateForTest(
            listOf(
                HosterState.Ready("first", listOf(first), listOf(Video.State.READY)),
                HosterState.Loading("lazy"),
            ),
        )
        localOrchestrator.onVideoClicked(0, 0, currentSource = null, onSuccess = {}, onFailure = {})
        assertEquals(first, readyVideos.poll(5, TimeUnit.SECONDS))

        localOrchestrator.onVideoLoadFailed(first, source = null)
        assertNull(readyVideos.poll(200, TimeUnit.MILLISECONDS))
        localOrchestrator.setHosterStateAtForTest(1, HosterState.Ready("lazy", listOf(lazy), listOf(Video.State.READY)))

        assertEquals(lazy, readyVideos.poll(5, TimeUnit.SECONDS))
        assertEquals(Pair(1, 0), localOrchestrator.selectedHosterVideoIndex.value)
        assertEquals(0, errorCount.get())
    }

    @Test
    fun `a lazy hoster that loads no videos ends in the terminal error`() {
        val first = Video(videoUrl = "https://example.invalid/first", videoTitle = "first")
        val readyVideos = LinkedBlockingQueue<Video>()
        val errorLatch = CountDownLatch(1)
        val localOrchestrator = HosterOrchestrator(testScope, onNoVideosAvailable = { errorLatch.countDown() })
        localOrchestrator.onVideoReady = { readyVideos += it }
        localOrchestrator.setHosterStateForTest(
            listOf(
                HosterState.Ready("first", listOf(first), listOf(Video.State.READY)),
                HosterState.Loading("lazy"),
            ),
        )
        localOrchestrator.onVideoClicked(0, 0, currentSource = null, onSuccess = {}, onFailure = {})
        assertEquals(first, readyVideos.poll(5, TimeUnit.SECONDS))

        localOrchestrator.onVideoLoadFailed(first, source = null)
        assertEquals(false, errorLatch.await(200, TimeUnit.MILLISECONDS))
        localOrchestrator.setHosterStateAtForTest(1, HosterState.Error("lazy"))

        assertEquals(true, errorLatch.await(5, TimeUnit.SECONDS))
        assertNull(readyVideos.poll(200, TimeUnit.MILLISECONDS))
    }

    /** The first video plays and fails; the fallback to the second is held in resolveVideo. */
    private inner class ResolvingFallbackFixture {
        val failed = Video(videoUrl = "https://example.invalid/failed", videoTitle = "failed")
        val fallback = Video(videoUrl = "https://example.invalid/fallback", videoTitle = "fallback")
        val manual = Video(videoUrl = "https://example.invalid/manual", videoTitle = "manual")
        val releaseResolve = CompletableDeferred<Unit>()
        val source = mockk<AnimeHttpSource>()
        val readyVideos = LinkedBlockingQueue<Video>()

        init {
            val resolving = CountDownLatch(1)
            coEvery { source.resolveVideo(fallback) } coAnswers {
                resolving.countDown()
                withContext(NonCancellable) { releaseResolve.await() }
                fallback
            }
            orchestrator.onVideoReady = { readyVideos += it }
            orchestrator.setHosterStateForTest(
                listOf(
                    HosterState.Ready(
                        "hoster",
                        listOf(failed, fallback, manual),
                        listOf(Video.State.READY, Video.State.QUEUE, Video.State.READY),
                    ),
                ),
            )
            orchestrator.onVideoClicked(0, 0, source, onSuccess = {}, onFailure = {})
            assertEquals(failed, readyVideos.poll(5, TimeUnit.SECONDS))
            orchestrator.onVideoLoadFailed(failed, source)
            assertEquals(true, resolving.await(5, TimeUnit.SECONDS))
        }
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
    private fun HosterOrchestrator.setHosterStateAtForTest(index: Int, value: HosterState) {
        val field = HosterOrchestrator::class.java.getDeclaredField("_hosterState")
        field.isAccessible = true
        (field.get(this) as MutableStateFlow<List<HosterState>>).update { states ->
            states.toMutableList().also { it[index] = value }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun HosterOrchestrator.setSelectedHosterVideoIndexForTest(value: Pair<Int, Int>) {
        val field = HosterOrchestrator::class.java.getDeclaredField("_selectedHosterVideoIndex")
        field.isAccessible = true
        (field.get(this) as MutableStateFlow<Pair<Int, Int>>).value = value
    }

    @Suppress("UNCHECKED_CAST")
    private fun HosterOrchestrator.setCurrentVideoForTest(value: Video) {
        val field = HosterOrchestrator::class.java.getDeclaredField("_currentVideo")
        field.isAccessible = true
        (field.get(this) as MutableStateFlow<Video?>).value = value
    }
}
