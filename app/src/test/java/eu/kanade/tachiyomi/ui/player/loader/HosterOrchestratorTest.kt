package eu.kanade.tachiyomi.ui.player.loader

import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.HosterState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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

@OptIn(ExperimentalCoroutinesApi::class)
class HosterOrchestratorTest {

    private lateinit var testScope: TestScope
    private lateinit var orchestrator: HosterOrchestrator

    @BeforeEach
    fun setup() {
        testScope = TestScope()
        orchestrator = HosterOrchestrator(testScope)
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
}
