package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupTracking
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.data.entries.manga.MangaRepositoryImpl
import tachiyomi.data.items.chapter.ChapterRepositoryImpl
import tachiyomi.data.track.manga.MangaTrackRepositoryImpl
import tachiyomi.domain.entries.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.domain.track.manga.interactor.InsertMangaTrack

class MangaRestorerTrackingTest {

    private fun restorer(db: InMemoryMangaDb): MangaRestorer {
        val tracks = MangaTrackRepositoryImpl(db.handler)
        return MangaRestorer(
            handler = db.handler,
            getCategories = mockk(relaxed = true),
            getMangaByUrlAndSourceId = GetMangaByUrlAndSourceId(MangaRepositoryImpl(db.handler)),
            getChaptersByMangaId = GetChaptersByMangaId(ChapterRepositoryImpl(db.handler)),
            updateManga = mockk(relaxed = true),
            getTracks = GetMangaTracks(tracks),
            insertTrack = InsertMangaTrack(tracks),
            fetchInterval = mockk(relaxed = true),
        )
    }

    private suspend fun storedTrackerIds(db: InMemoryMangaDb): List<Long> =
        db.handler.awaitList { manga_syncQueries.getTracks() }.map { it.sync_id }.sorted()

    @Test
    fun `restore skips Suwayomi tracks from an old backup and keeps other trackers`() = runTest {
        val backup = BackupManga(
            source = 1,
            url = "/series/1",
            title = "Series",
            tracking = listOf(
                BackupTracking(syncId = 9, libraryId = 0, mediaId = 10),
                BackupTracking(syncId = 1, libraryId = 0, mediaId = 20),
            ),
        )

        InMemoryMangaDb().use { db ->
            restorer(db).restore(backup, emptyMap())

            assertEquals(listOf(1L), storedTrackerIds(db))
        }
    }
}
