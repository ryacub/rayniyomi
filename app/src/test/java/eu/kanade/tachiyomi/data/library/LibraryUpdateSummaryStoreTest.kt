package eu.kanade.tachiyomi.data.library

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class LibraryUpdateSummaryStoreTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `worker results persist across store reload`() {
        val preferences = StringBackedPreferenceStore()
        val store = LibraryUpdateSummaryStore(preferences, json)
        val mangaResult = LibraryUpdateResult(
            updated = listOf(entry(1, "Updated manga")),
            skipped = listOf(
                LibraryUpdateSkippedEntry(entry(2, "Skipped manga"), AutoUpdateSkipReason.NOT_STARTED),
            ),
            failed = listOf(LibraryUpdateFailedEntry(entry(3, "Failed manga"), "Source unavailable")),
            skippedLogPath = "/cache/manga-skipped.txt",
            errorLogPath = "/cache/manga-errors.txt",
        )
        val animeResult = LibraryUpdateResult(
            updated = listOf(entry(4, "Updated anime")),
            skipped = listOf(
                LibraryUpdateSkippedEntry(entry(5, "Skipped anime"), AutoUpdateSkipReason.COMPLETED),
            ),
            failed = listOf(LibraryUpdateFailedEntry(entry(6, "Failed anime"), "HTTP 500")),
            skippedLogPath = "/cache/anime-skipped.txt",
            errorLogPath = "/cache/anime-errors.txt",
        )

        store.start(LibraryUpdateMedia.MANGA, "manga-run")
        val expectedManga = store.finish(
            media = LibraryUpdateMedia.MANGA,
            executionId = "manga-run",
            result = mangaResult,
            finishedAt = 100L,
        )
        store.start(LibraryUpdateMedia.ANIME, "anime-run")
        val expectedAnime = store.finish(
            media = LibraryUpdateMedia.ANIME,
            executionId = "anime-run",
            result = animeResult,
            finishedAt = 200L,
        )

        val reloaded = LibraryUpdateSummaryStore(preferences, json)

        assertEquals(expectedManga, reloaded.get(LibraryUpdateMedia.MANGA))
        assertEquals(expectedAnime, reloaded.get(LibraryUpdateMedia.ANIME))
        assertEquals(1, expectedManga?.updatedCount)
        assertEquals(1, expectedManga?.skippedCount)
        assertEquals(1, expectedManga?.failedCount)
    }

    @Test
    fun `close remains closed and cannot delete newer summary`() {
        val preferences = StringBackedPreferenceStore()
        val store = LibraryUpdateSummaryStore(preferences, json)

        store.start(LibraryUpdateMedia.MANGA, "first")
        store.finish(LibraryUpdateMedia.MANGA, "first", LibraryUpdateResult(), 100L)
        store.start(LibraryUpdateMedia.ANIME, "anime")
        val anime = store.finish(LibraryUpdateMedia.ANIME, "anime", LibraryUpdateResult(), 110L)

        assertTrue(store.close(LibraryUpdateMedia.MANGA, "first"))
        assertNull(LibraryUpdateSummaryStore(preferences, json).get(LibraryUpdateMedia.MANGA))
        assertEquals(anime, store.get(LibraryUpdateMedia.ANIME))

        store.start(LibraryUpdateMedia.MANGA, "second")
        val second = store.finish(LibraryUpdateMedia.MANGA, "second", LibraryUpdateResult(), 200L)

        assertFalse(store.close(LibraryUpdateMedia.MANGA, "first"))
        assertEquals(second, store.get(LibraryUpdateMedia.MANGA))
    }

    @Test
    fun `user cancel preserves partial results without recording cancellation as failure`() {
        val collector = LibraryUpdateResultAccumulator()
        val updated = entry(1, "Handled before cancel")
        collector.addUpdated(updated)

        assertThrows(CancellationException::class.java) {
            collector.addFailed(entry(2, "Active during cancel"), CancellationException("cancelled")) {
                "This mapper must not run"
            }
        }

        val store = LibraryUpdateSummaryStore(StringBackedPreferenceStore(), json)
        store.requestUserCancellation(LibraryUpdateMedia.MANGA, "cancelled-run")
        store.start(LibraryUpdateMedia.MANGA, "cancelled-run")
        val summary = store.finish(
            media = LibraryUpdateMedia.MANGA,
            executionId = "cancelled-run",
            result = collector.snapshot(),
            finishedAt = 300L,
            interrupted = true,
        )

        assertEquals(LibraryUpdateSummaryOutcome.CANCELLED, summary?.outcome)
        assertEquals(listOf(updated), summary?.updated)
        assertTrue(summary?.failed.orEmpty().isEmpty())
    }

    @Test
    fun `system stop does not become user cancel or overwrite next run`() {
        val store = LibraryUpdateSummaryStore(StringBackedPreferenceStore(), json)
        store.start(LibraryUpdateMedia.ANIME, "interrupted-run")

        val interrupted = store.finish(
            media = LibraryUpdateMedia.ANIME,
            executionId = "interrupted-run",
            result = LibraryUpdateResult(updated = listOf(entry(1, "Handled anime"))),
            finishedAt = 400L,
            interrupted = true,
        )

        assertEquals(LibraryUpdateSummaryOutcome.INTERRUPTED, interrupted?.outcome)

        store.start(LibraryUpdateMedia.MANGA, "old-run")
        store.start(LibraryUpdateMedia.MANGA, "new-run")
        assertNull(
            store.finish(
                media = LibraryUpdateMedia.MANGA,
                executionId = "old-run",
                result = LibraryUpdateResult(failed = listOf(LibraryUpdateFailedEntry(entry(2, "Old"), "Old error"))),
                finishedAt = 500L,
                interrupted = true,
            ),
        )

        val current = store.finish(
            media = LibraryUpdateMedia.MANGA,
            executionId = "new-run",
            result = LibraryUpdateResult(updated = listOf(entry(3, "New"))),
            finishedAt = 600L,
        )

        assertEquals(LibraryUpdateSummaryOutcome.COMPLETED, current?.outcome)
        assertEquals(current, store.get(LibraryUpdateMedia.MANGA))
    }

    private fun entry(id: Long, title: String) = LibraryUpdateSummaryEntry(id, title)
}

private class StringBackedPreferenceStore : PreferenceStore {
    private val values = mutableMapOf<String, String>()

    override fun <T> getObject(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): Preference<T> = object : Preference<T> {
        private val state = MutableStateFlow(get())

        override fun key() = key

        override fun get(): T = values[key]?.let(deserializer) ?: defaultValue

        override fun set(value: T) {
            values[key] = serializer(value)
            state.value = value
        }

        override fun isSet() = key in values

        override fun delete() {
            values.remove(key)
            state.value = defaultValue
        }

        override fun defaultValue() = defaultValue

        override fun changes(): Flow<T> = state

        override fun stateIn(scope: CoroutineScope): StateFlow<T> = state
    }

    override fun getString(key: String, defaultValue: String) = unused<String>()
    override fun getLong(key: String, defaultValue: Long) = unused<Long>()
    override fun getInt(key: String, defaultValue: Int) = unused<Int>()
    override fun getFloat(key: String, defaultValue: Float) = unused<Float>()
    override fun getBoolean(key: String, defaultValue: Boolean) = unused<Boolean>()
    override fun getStringSet(key: String, defaultValue: Set<String>) = unused<Set<String>>()
    override fun getAll(): Map<String, *> = values.toMap()

    private fun <T> unused(): Preference<T> = error("This test store only supports object preferences")
}
