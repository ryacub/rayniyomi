package eu.kanade.tachiyomi.ui.player

import eu.kanade.tachiyomi.animesource.AnimeSource
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerSourceResolutionTest {

    @Test
    fun `failed initialization preserves its error`() {
        val expected = IllegalStateException("init failed")

        val result = resolvePlayerSourceAfterInit(Result.failure(expected), null)

        assertSame(expected, result.exceptionOrNull())
    }

    @Test
    fun `successful initialization without a source returns an error`() {
        val result = resolvePlayerSourceAfterInit(Result.success(true), null)

        assertTrue(result.isFailure)
        assertEquals("Player source unavailable after initialization", result.exceptionOrNull()?.message)
    }

    @Test
    fun `successful initialization returns the resolved source`() {
        val source = mockk<AnimeSource>()

        val result = resolvePlayerSourceAfterInit(Result.success(true), source)

        assertSame(source, result.getOrThrow())
    }
}
