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

class AnimeRecoveryApplierImplTest {

    private val originalId = 1L

    private suspend fun AnimeRecoveryDb.readyEntry(): Long {
        insertAnime(originalId)
        val repository = AnimeRecoveryRepositoryImpl(handler)
        val sessionId = repository.createSession(
            destinationSourceIds = listOf(20L),
            keepOriginal = true,
            entries = listOf(NewRecoveryEntry(originalId, "Title 1", RecoveryState.READY)),
            createdAt = 1_000,
        )
        val entryId = repository.getEntries(sessionId).single().id
        driver.claim(listOf(entryId), RecoveryState.APPLYING, "run")
        return entryId
    }

    private fun request(
        entryId: Long,
        seasons: Boolean = false,
        items: List<RecoveryItem> = listOf(1.0, 2.0, 3.0).map { episode("d${it.toInt()}", it) },
        pairs: List<Pair<String, String>> = listOf("o1" to "d1", "o2" to "d2", "o3" to "d3"),
    ) = RecoveryApplyRequest(
        entryId = entryId,
        runToken = "run",
        originalId = originalId,
        destination = RecoveryDestination(
            sourceId = 20,
            url = "/a/1",
            title = "Destination",
            items = items,
            seasons = seasons,
        ),
        pairs = pairs.map { (from, to) -> RecoveryItemPair(from, to) },
        tracks = emptyList(),
        keepOriginal = true,
        now = 5_000,
    )

    private fun episode(url: String, number: Double) = RecoveryItem(
        url = url,
        name = "Episode $number",
        number = number,
    )

    @Test
    fun `episodes keep seen gaps and move history, and positions stay on the original`() = runTest {
        AnimeRecoveryDb().use { db ->
            val entryId = db.readyEntry()
            db.insertEpisode(11, originalId, "o1", 1.0, seen = true)
            db.insertEpisode(12, originalId, "o2", 2.0, seen = false, lastSecond = 300)
            db.insertEpisode(13, originalId, "o3", 3.0, seen = true, lastSecond = 600)
            db.insertHistory(episodeId = 13, lastSeen = 4_000)

            val result = AnimeRecoveryApplierImpl(db.handler).apply(request(entryId))

            val destination = db.driver.longs("SELECT _id FROM animes WHERE source = 20").single()
            assertEquals(RecoveryApplyResult.Completed(destination), result)
            assertEquals(listOf(1L, 0L, 1L), listOf("d1", "d2", "d3").map { db.episodeValue(destination, it, "seen") })
            assertEquals(0L, db.episodeValue(destination, "d2", "last_second_seen"))
            assertEquals(0L, db.episodeValue(destination, "d3", "last_second_seen"))
            val d3 = db.driver.longs("SELECT _id FROM episodes WHERE anime_id = $destination AND url = 'd3'").single()
            assertEquals(listOf(4_000L), db.driver.longs("SELECT last_seen FROM animehistory WHERE episode_id = $d3"))
        }
    }

    @Test
    fun `a season-type original is never applied`() = runTest {
        AnimeRecoveryDb().use { db ->
            val entryId = db.readyEntry()
            db.setSeasonsFetchType(originalId)

            val result = AnimeRecoveryApplierImpl(db.handler).apply(request(entryId))

            assertEquals(RecoveryApplyResult.Unsupported, result)
            assertTrue(db.driver.longs("SELECT _id FROM animes WHERE source = 20").isEmpty())
            assertEquals(
                listOf("UNSUPPORTED"),
                db.driver.strings("SELECT state FROM recovery_entries WHERE _id = $entryId"),
            )
        }
    }

    @Test
    fun `a season-type candidate is never applied`() = runTest {
        AnimeRecoveryDb().use { db ->
            val entryId = db.readyEntry()

            val result = AnimeRecoveryApplierImpl(db.handler).apply(request(entryId, seasons = true))

            assertEquals(RecoveryApplyResult.NeedsReview(RecoveryReviewReason.SEASONS_CANDIDATE), result)
            assertTrue(db.driver.longs("SELECT _id FROM animes WHERE source = 20").isEmpty())
        }
    }

    @Test
    fun `an existing season-type destination row is never applied`() = runTest {
        AnimeRecoveryDb().use { db ->
            val entryId = db.readyEntry()
            db.insertAnime(2, source = 20, url = "/a/1")
            db.setSeasonsFetchType(2)

            val result = AnimeRecoveryApplierImpl(db.handler).apply(request(entryId))

            assertEquals(RecoveryApplyResult.NeedsReview(RecoveryReviewReason.SEASONS_CANDIDATE), result)
            assertTrue(db.driver.longs("SELECT _id FROM episodes WHERE anime_id = 2").isEmpty())
            assertEquals(
                listOf("NEEDS_REVIEW"),
                db.driver.strings("SELECT state FROM recovery_entries WHERE _id = $entryId"),
            )
        }
    }

    @Test
    fun `a paired episode keeps the original fetch date so it does not show as a new update`() = runTest {
        AnimeRecoveryDb().use { db ->
            val entryId = db.readyEntry()
            db.insertEpisode(11, originalId, "o1", 1.0, dateFetch = 777)
            db.insertEpisode(12, originalId, "o2", 2.0, dateFetch = 9_000)
            db.insertEpisode(13, originalId, "o3", 3.0, dateFetch = 778)

            AnimeRecoveryApplierImpl(db.handler).apply(request(entryId))

            val destination = db.driver.longs("SELECT _id FROM animes WHERE source = 20").single()
            assertEquals(777L, db.episodeValue(destination, "d1", "date_fetch"))
            assertEquals(5_000L, db.episodeValue(destination, "d2", "date_fetch"))
        }
    }

    @Test
    fun `an episode URL the source lists twice is inserted once`() = runTest {
        AnimeRecoveryDb().use { db ->
            val entryId = db.readyEntry()

            AnimeRecoveryApplierImpl(db.handler).apply(
                request(entryId, items = listOf(episode("d1", 1.0), episode("d1", 1.0)), pairs = emptyList()),
            )

            val destination = db.driver.longs("SELECT _id FROM animes WHERE source = 20").single()
            assertEquals(listOf("d1"), db.driver.strings("SELECT url FROM episodes WHERE anime_id = $destination"))
        }
    }

    @Test
    fun `a reviewed pair to an episode the source no longer lists needs review and writes nothing`() = runTest {
        AnimeRecoveryDb().use { db ->
            val entryId = db.readyEntry()
            db.insertAnime(2, source = 20, url = "/a/1")
            db.insertEpisode(21, 2, "stale", 1.0, seen = false)
            db.insertEpisode(11, originalId, "o1", 1.0, seen = true)

            val result = AnimeRecoveryApplierImpl(db.handler).apply(
                request(entryId, items = listOf(episode("d2", 2.0)), pairs = listOf("o1" to "stale")),
            )

            assertEquals(RecoveryApplyResult.NeedsReview(RecoveryReviewReason.MAPPING_CHANGED), result)
            assertEquals(0L, db.episodeValue(2, "stale", "seen"))
            assertEquals(listOf("NEEDS_REVIEW"), db.driver.strings("SELECT state FROM recovery_entries"))
        }
    }
}
