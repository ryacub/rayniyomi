package eu.kanade.presentation.entries.anime

import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.HosterState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EpisodeOptionsDialogStateGuardTest {

    @Test
    fun `readyVideo returns null when hoster state is missing or indexes are stale`() {
        val state = Result.success(
            listOf(
                HosterState.Ready(
                    name = "hoster",
                    videoList = listOf(video("720p")),
                    videoState = listOf(Video.State.LOAD_VIDEO),
                ),
            ),
        )

        assertNull(EpisodeOptionsDialogStateGuard.readyVideo(null, 0, 0))
        assertNull(EpisodeOptionsDialogStateGuard.readyVideo(state, -1, 0))
        assertNull(EpisodeOptionsDialogStateGuard.readyVideo(state, 1, 0))
        assertNull(EpisodeOptionsDialogStateGuard.readyVideo(state, 0, -1))
        assertNull(EpisodeOptionsDialogStateGuard.readyVideo(state, 0, 1))
    }

    @Test
    fun `readyVideo returns null when video state is missing for selected video`() {
        val state = Result.success(
            listOf(
                HosterState.Ready(
                    name = "hoster",
                    videoList = listOf(video("720p")),
                    videoState = emptyList(),
                ),
            ),
        )

        assertNull(EpisodeOptionsDialogStateGuard.readyVideo(state, 0, 0))
    }

    @Test
    fun `readyVideo returns selected ready hoster and video for valid indexes`() {
        val selectedVideo = video("720p")
        val state = Result.success(
            listOf(
                HosterState.Ready(
                    name = "hoster",
                    videoList = listOf(selectedVideo),
                    videoState = listOf(Video.State.LOAD_VIDEO),
                ),
            ),
        )

        val selection = EpisodeOptionsDialogStateGuard.readyVideo(state, 0, 0)

        assertEquals(selectedVideo, selection?.video)
        assertEquals("hoster", selection?.hosterState?.name)
    }

    @Test
    fun `hosterAt returns null when source or hoster index is unavailable`() {
        assertNull(EpisodeOptionsDialogStateGuard.hosterAt(sourceAvailable = false, hosterList = listOf(hoster()), 0))
        assertNull(EpisodeOptionsDialogStateGuard.hosterAt(sourceAvailable = true, hosterList = emptyList(), 0))
        assertNull(EpisodeOptionsDialogStateGuard.hosterAt(sourceAvailable = true, hosterList = listOf(hoster()), 1))
    }

    @Test
    fun `hosterAt returns hoster when source and index are available`() {
        val hoster = hoster()

        assertEquals(
            hoster,
            EpisodeOptionsDialogStateGuard.hosterAt(sourceAvailable = true, hosterList = listOf(hoster), 0),
        )
    }

    private fun video(title: String): Video {
        return Video(videoUrl = "https://example.invalid/$title", videoTitle = title)
    }

    private fun hoster(): Hoster {
        return Hoster(hosterName = "hoster")
    }
}
