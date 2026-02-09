package eu.kanade.tachiyomi.data.track

import eu.kanade.tachiyomi.data.track.kavita.Kavita
import eu.kanade.tachiyomi.data.track.suwayomi.Suwayomi
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Tests verifying graceful handling of unsupported search in trackers (R27 changes).
 *
 * R27 replaced TODO() exceptions with empty list returns in searchManga()
 * for Kavita and Suwayomi trackers where search is not supported.
 */
class TrackerUnsupportedSearchTest {

    @Test
    fun `Kavita searchManga returns empty list for unsupported search`() = runTest {
        val tracker = Kavita(id = 1L)

        val result = tracker.searchManga("test query")

        result shouldBe emptyList()
    }

    @Test
    fun `Kavita searchManga does not throw exception`() = runTest {
        val tracker = Kavita(id = 1L)

        // Should not throw TODO or any exception
        val result = runCatching {
            tracker.searchManga("any query")
        }

        result.isSuccess shouldBe true
        result.getOrNull() shouldBe emptyList()
    }

    @Test
    fun `Suwayomi searchManga returns empty list for unsupported search`() = runTest {
        val tracker = Suwayomi(id = 2L)

        val result = tracker.searchManga("test query")

        result shouldBe emptyList()
    }

    @Test
    fun `Suwayomi searchManga does not throw exception`() = runTest {
        val tracker = Suwayomi(id = 2L)

        // Should not throw TODO or any exception
        val result = runCatching {
            tracker.searchManga("any query")
        }

        result.isSuccess shouldBe true
        result.getOrNull() shouldBe emptyList()
    }

    @Test
    fun `Kavita searchManga with empty query returns empty list`() = runTest {
        val tracker = Kavita(id = 1L)

        val result = tracker.searchManga("")

        result shouldBe emptyList()
    }

    @Test
    fun `Suwayomi searchManga with empty query returns empty list`() = runTest {
        val tracker = Suwayomi(id = 2L)

        val result = tracker.searchManga("")

        result shouldBe emptyList()
    }

    @Test
    fun `Kavita searchManga with special characters returns empty list`() = runTest {
        val tracker = Kavita(id = 1L)

        val result = tracker.searchManga("!@#$%^&*()")

        result shouldBe emptyList()
    }

    @Test
    fun `Suwayomi searchManga with special characters returns empty list`() = runTest {
        val tracker = Suwayomi(id = 2L)

        val result = tracker.searchManga("!@#$%^&*()")

        result shouldBe emptyList()
    }
}
