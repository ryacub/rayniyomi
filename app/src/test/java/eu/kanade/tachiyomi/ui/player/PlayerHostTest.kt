package eu.kanade.tachiyomi.ui.player

import eu.kanade.tachiyomi.animesource.model.Video
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import tachiyomi.domain.custombuttons.model.CustomButton

class PlayerHostTest {

    @Test
    fun `activity host forwards player action arguments`() {
        val activity = mockk<PlayerActivity>(relaxed = true)
        val host = PlayerActivityHost(activity)
        val video = Video(
            videoUrl = "https://example.invalid/video",
            videoTitle = "720p",
        )
        val buttons = emptyList<CustomButton>()

        host.setVideo(video, position = 42L)
        host.showToast("message")
        host.setupCustomButtons(buttons)
        host.changeEpisode(episodeId = 7L, autoPlay = true)

        verify(exactly = 1) {
            activity.setVideo(video, 42L)
            activity.showToast("message")
            activity.setupCustomButtons(buttons)
            activity.changeEpisode(7L, true)
        }
    }
}
