package tachiyomi.data.recovery

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.recovery.model.NewRecoveryEntry
import tachiyomi.domain.recovery.model.RecoveryApplyRequest
import tachiyomi.domain.recovery.model.RecoveryApplyResult
import tachiyomi.domain.recovery.model.RecoveryDestination
import tachiyomi.domain.recovery.model.RecoveryItem
import tachiyomi.domain.recovery.model.RecoveryItemPair
import tachiyomi.domain.recovery.model.RecoveryReviewReason
import tachiyomi.domain.recovery.model.RecoveryState
import tachiyomi.domain.recovery.model.RecoveryTrack

class MangaRecoveryApplierImplTest {

    private val originalId = 1L
    private val runToken = "run"

    private class Fixture(val db: MangaRecoveryDb, val repository: MangaRecoveryRepositoryImpl, val entryId: Long) {
        val applier = MangaRecoveryApplierImpl(db.handler)
        fun destinationId(): Long = db.driver.longs(
            "SELECT _id FROM mangas WHERE source = 20 AND url = '/m/1'",
        ).single()
    }

    private suspend fun MangaRecoveryDb.fixture(): Fixture {
        insertManga(originalId)
        val repository = MangaRecoveryRepositoryImpl(handler)
        val sessionId = repository.createSession(
            destinationSourceIds = listOf(20L),
            keepOriginal = true,
            entries = listOf(NewRecoveryEntry(originalId, "Title 1", RecoveryState.READY)),
            createdAt = 1_000,
        )
        val entryId = repository.getEntries(sessionId).single().id
        driver.claim(listOf(entryId), RecoveryState.APPLYING, runToken)
        return Fixture(this, repository, entryId)
    }

    private fun Fixture.request(
        pairs: List<Pair<String, String>> = emptyList(),
        items: List<RecoveryItem> = listOf(item("d1", 1.0), item("d2", 2.0), item("d3", 3.0)),
        tracks: List<RecoveryTrack> = emptyList(),
        keepOriginal: Boolean = true,
        destinationSource: Long = 20,
        destinationUrl: String = "/m/1",
    ) = RecoveryApplyRequest(
        entryId = entryId,
        runToken = runToken,
        originalId = originalId,
        destination = RecoveryDestination(
            sourceId = destinationSource,
            url = destinationUrl,
            title = "Destination",
            items = items,
        ),
        pairs = pairs.map { (from, to) -> RecoveryItemPair(from, to) },
        tracks = tracks,
        keepOriginal = keepOriginal,
        now = 5_000,
    )

    private fun item(url: String, number: Double) = RecoveryItem(url = url, name = "Chapter $number", number = number)

    private fun track(syncId: Long, remoteId: Long, lastRead: Double) = RecoveryTrack(
        syncId = syncId,
        remoteId = remoteId,
        libraryId = null,
        title = "Remote",
        lastProgress = lastRead,
        totalItems = 10,
        status = 1,
        score = 0.0,
        remoteUrl = "https://t/$remoteId",
        startDate = 0,
        finishDate = 0,
        private = false,
    )

    private fun Fixture.state() = db.driver.strings("SELECT state FROM recovery_entries WHERE _id = $entryId").single()

    @Test
    fun `an unread gap below the highest read chapter stays unread`() = runTest {
        MangaRecoveryDb().use { db ->
            val f = db.fixture()
            db.insertChapter(11, originalId, "o1", 1.0, read = true)
            db.insertChapter(12, originalId, "o2", 2.0, read = false)
            db.insertChapter(13, originalId, "o3", 3.0, read = true)

            val result = f.applier.apply(f.request(pairs = listOf("o1" to "d1", "o2" to "d2", "o3" to "d3")))

            val destination = f.destinationId()
            assertEquals(RecoveryApplyResult.Completed(destination), result)
            assertEquals(1L, db.chapterValue(destination, "d1", "read"))
            assertEquals(0L, db.chapterValue(destination, "d2", "read"))
            assertEquals(1L, db.chapterValue(destination, "d3", "read"))
            assertEquals("COMPLETED", f.state())
        }
    }

    @Test
    fun `an existing destination keeps its chapters, progress, and reader settings`() = runTest {
        MangaRecoveryDb().use { db ->
            val f = db.fixture()
            db.setFlags(originalId, viewer = 3, chapterFlags = 7)
            db.insertManga(2, source = 20, url = "/m/1")
            db.setFlags(2, viewer = 5, chapterFlags = 9)
            db.insertChapter(21, 2, "d1", 1.0, read = true, bookmark = true)
            db.insertChapter(22, 2, "extra", 9.0, read = true)
            db.insertChapter(11, originalId, "o1", 1.0, read = false, bookmark = false)

            f.applier.apply(f.request(pairs = listOf("o1" to "d1")))

            assertEquals(2L, f.destinationId())
            assertEquals(1L, db.chapterValue(2, "d1", "read"))
            assertEquals(1L, db.chapterValue(2, "d1", "bookmark"))
            assertEquals(1L, db.chapterValue(2, "extra", "read"))
            assertEquals(
                listOf("d1", "d2", "d3", "extra"),
                db.driver.strings("SELECT url FROM chapters WHERE manga_id = 2 ORDER BY url"),
            )
            assertEquals(
                listOf(5L, 9L),
                db.driver.longs("SELECT viewer FROM mangas WHERE _id = 2") +
                    db.driver.longs("SELECT chapter_flags FROM mangas WHERE _id = 2"),
            )
        }
    }

    @Test
    fun `a new destination takes the original reader settings and joins the library`() = runTest {
        MangaRecoveryDb().use { db ->
            val f = db.fixture()
            db.setFlags(originalId, viewer = 3, chapterFlags = 7)

            f.applier.apply(f.request())

            val destination = f.destinationId()
            assertEquals(listOf(3L), db.driver.longs("SELECT viewer FROM mangas WHERE _id = $destination"))
            assertEquals(listOf(7L), db.driver.longs("SELECT chapter_flags FROM mangas WHERE _id = $destination"))
            assertEquals(listOf(1L), db.driver.longs("SELECT favorite FROM mangas WHERE _id = $destination"))
        }
    }

    @Test
    fun `history keeps the later read time and the longer duration`() = runTest {
        MangaRecoveryDb().use { db ->
            val f = db.fixture()
            db.insertManga(2, source = 20, url = "/m/1")
            db.insertChapter(21, 2, "d1", 1.0)
            db.insertHistory(21, lastRead = 9_000, timeRead = 40)
            db.insertChapter(11, originalId, "o1", 1.0, read = true)
            db.insertHistory(11, lastRead = 3_000, timeRead = 100)
            db.insertChapter(12, originalId, "o2", 2.0, read = true)
            db.insertHistory(12, lastRead = 4_000, timeRead = 70)

            f.applier.apply(f.request(pairs = listOf("o1" to "d1", "o2" to "d2")))

            val d2 = db.driver.longs("SELECT _id FROM chapters WHERE manga_id = 2 AND url = 'd2'").single()
            assertEquals(
                listOf(9_000L, 100L),
                db.driver.longs("SELECT last_read FROM history WHERE chapter_id = 21") +
                    db.driver.longs("SELECT time_read FROM history WHERE chapter_id = 21"),
            )
            assertEquals(
                listOf(4_000L, 70L),
                db.driver.longs("SELECT last_read FROM history WHERE chapter_id = $d2") +
                    db.driver.longs("SELECT time_read FROM history WHERE chapter_id = $d2"),
            )
        }
    }

    @Test
    fun `a page position stays on the original`() = runTest {
        MangaRecoveryDb().use { db ->
            val f = db.fixture()
            db.insertChapter(11, originalId, "o1", 1.0, lastPageRead = 12)

            f.applier.apply(f.request(pairs = listOf("o1" to "d1")))

            assertEquals(0L, db.chapterValue(f.destinationId(), "d1", "last_page_read"))
            assertEquals(12L, db.chapterValue(originalId, "o1", "last_page_read"))
        }
    }

    @Test
    fun `categories are united with the destination categories`() = runTest {
        MangaRecoveryDb().use { db ->
            val f = db.fixture()
            (1L..3L).forEach(db::insertCategory)
            db.insertManga(2, source = 20, url = "/m/1")
            db.insertMangaCategory(2, 1)
            db.insertMangaCategory(originalId, 1)
            db.insertMangaCategory(originalId, 3)

            f.applier.apply(f.request())

            assertEquals(
                listOf(1L, 3L),
                db.driver.longs("SELECT category_id FROM mangas_categories WHERE manga_id = 2 ORDER BY category_id"),
            )
        }
    }

    @Test
    fun `a tracker for a different remote title blocks the entry and changes nothing`() = runTest {
        MangaRecoveryDb().use { db ->
            val f = db.fixture()
            db.insertCategory(1)
            db.insertMangaCategory(originalId, 1)
            db.insertManga(2, source = 20, url = "/m/1")
            db.insertTrack(2, syncId = 1, remoteId = 500, lastRead = 2.0)

            val result = f.applier.apply(f.request(tracks = listOf(track(syncId = 1, remoteId = 999, lastRead = 4.0))))

            assertEquals(RecoveryApplyResult.NeedsReview(RecoveryReviewReason.TRACKER_CONFLICT), result)
            assertEquals(listOf(500L), db.driver.longs("SELECT remote_id FROM manga_sync WHERE manga_id = 2"))
            assertTrue(db.driver.longs("SELECT category_id FROM mangas_categories WHERE manga_id = 2").isEmpty())
            assertTrue(db.driver.longs("SELECT _id FROM chapters WHERE manga_id = 2").isEmpty())
            assertEquals("NEEDS_REVIEW", f.state())
        }
    }

    @Test
    fun `a missing tracker is copied and a shared tracker keeps the higher progress`() = runTest {
        MangaRecoveryDb().use { db ->
            val f = db.fixture()
            db.insertManga(2, source = 20, url = "/m/1")
            db.insertTrack(2, syncId = 1, remoteId = 500, lastRead = 6.0)

            f.applier.apply(
                f.request(
                    tracks = listOf(
                        track(syncId = 1, remoteId = 500, lastRead = 8.0),
                        track(syncId = 2, remoteId = 77, lastRead = 3.0),
                    ),
                ),
            )

            assertEquals(
                listOf(1L, 2L),
                db.driver.longs("SELECT sync_id FROM manga_sync WHERE manga_id = 2 ORDER BY sync_id"),
            )
            assertEquals(
                listOf("8.0"),
                db.driver.strings("SELECT last_chapter_read FROM manga_sync WHERE manga_id = 2 AND sync_id = 1"),
            )
            assertEquals(
                listOf("https://t/500"),
                db.driver.strings("SELECT remote_url FROM manga_sync WHERE manga_id = 2 AND sync_id = 1"),
            )
            assertEquals(
                listOf(77L),
                db.driver.longs("SELECT remote_id FROM manga_sync WHERE manga_id = 2 AND sync_id = 2"),
            )
        }
    }

    @Test
    fun `a write failure rolls back the whole entry and records it as failed`() = runTest {
        MangaRecoveryDb().use { db ->
            val f = db.fixture()
            db.insertCategory(1)
            db.insertMangaCategory(originalId, 1)
            db.insertChapter(11, originalId, "o1", 1.0, read = true)
            db.driver.exec(
                "CREATE TRIGGER fail_category BEFORE INSERT ON mangas_categories " +
                    "BEGIN SELECT RAISE(ABORT, 'forced failure'); END",
            )

            val result = f.applier.apply(f.request(pairs = listOf("o1" to "d1")))

            assertTrue(result is RecoveryApplyResult.Failed)
            assertTrue(db.driver.longs("SELECT _id FROM mangas WHERE source = 20").isEmpty())
            assertEquals(listOf(1L), db.driver.longs("SELECT favorite FROM mangas WHERE _id = $originalId"))
            assertEquals("FAILED", f.state())
        }
    }

    @Test
    fun `applying an entry the run no longer owns writes nothing`() = runTest {
        MangaRecoveryDb().use { db ->
            val f = db.fixture()
            f.applier.apply(f.request())
            val chapters = db.driver.longs("SELECT count(*) FROM chapters").single()

            val second = f.applier.apply(f.request())

            assertEquals(RecoveryApplyResult.NotOwned, second)
            assertEquals(chapters, db.driver.longs("SELECT count(*) FROM chapters").single())
            assertEquals(listOf(1L), db.driver.longs("SELECT count(*) FROM mangas WHERE source = 20"))
            assertEquals("COMPLETED", f.state())
        }
    }

    @Test
    fun `replace removes only the original library membership`() = runTest {
        MangaRecoveryDb().use { db ->
            val f = db.fixture()
            db.insertChapter(11, originalId, "o1", 1.0, read = true)

            f.applier.apply(f.request(keepOriginal = false))

            assertEquals(listOf(0L), db.driver.longs("SELECT favorite FROM mangas WHERE _id = $originalId"))
            assertEquals(listOf(1L), db.driver.longs("SELECT read FROM chapters WHERE _id = 11"))
        }
    }

    @Test
    fun `a destination that is the original entry needs review`() = runTest {
        MangaRecoveryDb().use { db ->
            val f = db.fixture()

            val result = f.applier.apply(f.request(destinationSource = 1, destinationUrl = "url-1"))

            assertEquals(RecoveryApplyResult.NeedsReview(RecoveryReviewReason.SELF_TARGET), result)
            assertEquals("NEEDS_REVIEW", f.state())
        }
    }

    @Test
    fun `a reviewed pair whose original chapter is gone needs review and writes nothing`() = runTest {
        MangaRecoveryDb().use { db ->
            val f = db.fixture()
            db.insertChapter(11, originalId, "o1", 1.0, read = true)

            val result = f.applier.apply(f.request(pairs = listOf("o1" to "d1", "gone" to "d2")))

            assertEquals(RecoveryApplyResult.NeedsReview(RecoveryReviewReason.MAPPING_CHANGED), result)
            assertTrue(db.driver.longs("SELECT _id FROM mangas WHERE source = 20").isEmpty())
            assertEquals("NEEDS_REVIEW", f.state())
        }
    }

    @Test
    fun `a reviewed pair whose destination chapter is gone needs review`() = runTest {
        MangaRecoveryDb().use { db ->
            val f = db.fixture()
            db.insertChapter(11, originalId, "o1", 1.0, read = true)

            val result = f.applier.apply(f.request(pairs = listOf("o1" to "d9")))

            assertEquals(RecoveryApplyResult.NeedsReview(RecoveryReviewReason.MAPPING_CHANGED), result)
            assertEquals("NEEDS_REVIEW", f.state())
        }
    }

    @Test
    fun `a paired chapter keeps the original fetch date so it does not show as a new update`() = runTest {
        MangaRecoveryDb().use { db ->
            val f = db.fixture()
            db.insertChapter(11, originalId, "o1", 1.0, dateFetch = 777)

            f.applier.apply(f.request(pairs = listOf("o1" to "d1")))

            assertEquals(777L, db.chapterValue(f.destinationId(), "d1", "date_fetch"))
            assertEquals(5_000L, db.chapterValue(f.destinationId(), "d2", "date_fetch"))
        }
    }
}
