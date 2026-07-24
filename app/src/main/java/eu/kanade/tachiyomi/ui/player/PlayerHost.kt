package eu.kanade.tachiyomi.ui.player

import eu.kanade.tachiyomi.animesource.model.Video
import tachiyomi.domain.custombuttons.model.CustomButton

internal interface PlayerHost {
    fun setVideo(video: Video?, position: Long? = null)

    fun showToast(message: String)

    fun setupCustomButtons(buttons: List<CustomButton>)

    fun changeEpisode(episodeId: Long?, autoPlay: Boolean = false)
}

internal class PlayerActivityHost(
    private val activity: PlayerActivity,
) : PlayerHost {
    override fun setVideo(video: Video?, position: Long?) {
        activity.setVideo(video, position)
    }

    override fun showToast(message: String) {
        activity.showToast(message)
    }

    override fun setupCustomButtons(buttons: List<CustomButton>) {
        activity.setupCustomButtons(buttons)
    }

    override fun changeEpisode(episodeId: Long?, autoPlay: Boolean) {
        activity.changeEpisode(episodeId, autoPlay)
    }
}
