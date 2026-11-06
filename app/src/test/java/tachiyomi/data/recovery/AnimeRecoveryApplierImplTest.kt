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
        repository.claim(listOf(entryId), setOf(RecoveryState.READY), RecoveryState.APPLYING, "run")
        return entryId
    }

    private fun request(entryId: Long, copyPosition: Set<String>) = RecoveryApplyRequest(
        entryId = entryId,
        runToken = "run",
        originalId = originalId,
        destination = RecoveryDestination(
            sourceId = 20,
            url = "/a/1",
            title = "Destination",
            items = listOf(1.0, 2.0, 3.0).map { RecoveryItem(url = "d${it.toInt()}", name = "Episode $it", number = it) },
        ),
        pairs = listOf("o1" to "d1", "o2" to "d2", "o3" to "d3").map { (from, to) ->
            RecoveryItemPair(from, to, copyPosition = from in copyPosition)
        },
        tracks = emptyList(),
        keepOriginal = true,
        now = 5_000,
    )

    @Test
    fun `episodes keep seen gaps, move history, and move a position only when allowed`() = runTest {
        AnimeRecoveryDb().use { db ->
            val entryId = db.readyEntry()
            db.insertEpisode(11, originalId, "o1", 1.0, seen = true)
            db.insertEpisode(12, originalId, "o2", 2.0, seen = false, lastSecond = 300)
            db.insertEpisode(13, originalId, "o3", 3.0, seen = true, lastSecond = 600)
            db.insertHistory(episodeId = 13, lastSeen = 4_000)

            val result = AnimeRecoveryApplierImpl(db.handler).apply(request(entryId, copyPosition = setOf("o2")))

            val destination = db.driver.longs("SELECT _id FROM animes WHERE source = 20").single()
            assertEquals(RecoveryApplyResult.Completed(destination), result)
            assertEquals(listOf(1L, 0L, 1L), listOf("d1", "d2", "d3").map { db.episodeValue(destination, it, "seen") })
            assertEquals(300L, db.episodeValue(destination, "d2", "last_second_seen"))
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

            val result = AnimeRecoveryApplierImpl(db.handler).apply(request(entryId, copyPosition = emptySet()))

            assertEquals(RecoveryApplyResult.Unsupported, result)
            assertTrue(db.driver.longs("SELECT _id FROM animes WHERE source = 20").isEmpty())
            assertEquals(listOf("UNSUPPORTED"), db.driver.strings("SELECT state FROM recovery_entries WHERE _id = $entryId"))
        }
    }
}
