package eu.kanade.domain.track.manga.interactor

import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack
import eu.kanade.tachiyomi.data.track.MangaTracker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.model.Chapter

class AddMangaTracksTest {

    @ParameterizedTest(name = "with unnumbered chapter = {0}")
    @ValueSource(booleans = [false, true])
    fun `bind sends progress up to the first incomplete numbered chapter`(includeUnnumbered: Boolean) = runTest {
        val entryId = 10L
        val numbered = (1..5).map { number ->
            Chapter.create().copy(
                id = number.toLong(),
                mangaId = entryId,
                chapterNumber = number.toDouble(),
                read = number != 4,
            )
        }
        val items = if (includeUnnumbered) numbered + Chapter.create().copy(mangaId = entryId) else numbered
        val getItems = mockk<GetChaptersByMangaId>()
        coEvery { getItems.await(entryId) } returns items
        val track = MangaTrack.create(2L).apply {
            manga_id = entryId
            title = "Title"
            started_reading_date = 1L
        }
        val tracker = mockk<MangaTracker>()
        coEvery { tracker.bind(track, true) } returns track
        coEvery { tracker.setRemoteLastChapterRead(any(), any()) } returns Unit
        val addTracks = AddMangaTracks(
            mockk(relaxUnitFun = true),
            mockk<SyncChapterProgressWithTrack>(relaxUnitFun = true),
            getItems,
            mockk(),
        )

        addTracks.bind(tracker, track, entryId)

        coVerify(exactly = 1) {
            tracker.setRemoteLastChapterRead(match { it.last_chapter_read == 3.0 }, 3)
        }
    }
}
