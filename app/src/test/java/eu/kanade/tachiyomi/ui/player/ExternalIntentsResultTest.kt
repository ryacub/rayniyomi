package eu.kanade.tachiyomi.ui.player

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.di.AppGraphHolder
import eu.kanade.tachiyomi.di.testAppGraph
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode

class ExternalIntentsResultTest {
    private lateinit var external: ExternalIntents
    private lateinit var resultScope: CoroutineScope
    private val anime = Anime.create().copy(id = 1L)
    private val episode = Episode.create().copy(id = 2L, animeId = 1L, episodeNumber = 1.0, totalSeconds = 1000L)

    @BeforeEach
    fun setUp() {
        AppGraphHolder.graphOrNull = null
        val graph = testAppGraph
        val base = mockk<BasePreferences>(relaxed = true)
        val player = mockk<PlayerPreferences>(relaxed = true)
        val track = mockk<TrackPreferences>(relaxed = true)
        val download = mockk<DownloadPreferences>(relaxed = true)
        every { graph.basePreferences } returns base
        every { graph.playerPreferences } returns player
        every { graph.trackPreferences } returns track
        every { graph.downloadPreferences } returns download
        every { base.incognitoMode().get() } returns false
        every { player.progressPreference().get() } returns 0.85f
        every { track.autoUpdateTrack().get() } returns true
        every { download.removeAfterReadSlots().get() } returns 0
        coEvery { graph.getAnimeTracks.await(anime.id) } returns emptyList()
        coEvery { graph.getEpisodesByAnimeId.await(anime.id) } returns listOf(
            episode.copy(seen = true, lastSecondSeen = 900L),
        )
        resultScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        external = ExternalIntents().apply {
            this.anime = this@ExternalIntentsResultTest.anime
            this.episode = this@ExternalIntentsResultTest.episode
            animeId = anime.id
            episodeId = episode.id
        }
    }

    @AfterEach
    fun tearDown() {
        resultScope.cancel()
        AppGraphHolder.graphOrNull = null
        unmockkAll()
    }

    @Test
    fun `external result after unregister saves progress and history`() = runTest {
        register()
        external.unregisterActivity()
        external.onActivityResult(result(100))
        coVerify(exactly = 1) { testAppGraph.updateEpisode.await(match { it.id == episode.id && it.lastSecondSeen == 100L }) }
        coVerify(exactly = 1) { testAppGraph.upsertAnimeHistory.await(match { it.episodeId == episode.id }) }
    }

    @Test
    fun `first external completion queries tracking`() = runTest {
        register()
        external.onActivityResult(result(900))
        resultScope.coroutineContext[Job]!!.children.toList().joinAll()
        coVerify(exactly = 1) { testAppGraph.getAnimeTracks.await(anime.id) }
    }

    @Test
    fun `external completion deletes the updated episode by id`() = runTest {
        register()
        external.onActivityResult(result(900))
        resultScope.coroutineContext[Job]!!.children.toList().joinAll()
        coVerify(exactly = 1) {
            testAppGraph.animeDownloadManager.enqueueEpisodesToDelete(
                match { it.single().id == episode.id && it.single().seen },
                anime,
            )
        }
    }

    private fun register() {
        external.registerActivity(
            mockk<MainActivity>(relaxed = true),
            mockk<ActivityResultLauncher<Intent>>(relaxed = true),
            resultScope,
        )
    }

    private fun result(position: Int): Intent = mockk<Intent>(relaxed = true).also {
        every { it.getStringExtra("end_by") } returns ""
        every { it.extras } returns null
        every { it.getIntExtra("position", 0) } returns position
        every { it.getIntExtra("duration", 0) } returns 1000
    }
}
