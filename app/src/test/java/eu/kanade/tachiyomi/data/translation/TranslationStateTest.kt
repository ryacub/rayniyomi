package eu.kanade.tachiyomi.data.translation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TranslationStateTest {

    @Test
    fun `Idle state is singleton`() {
        val a = TranslationState.Idle
        val b = TranslationState.Idle
        assertTrue(a === b)
    }

    @Test
    fun `Translating state holds progress`() {
        val state = TranslationState.Translating(currentPage = 3, totalPages = 10)
        assertEquals(3, state.currentPage)
        assertEquals(10, state.totalPages)
    }

    @Test
    fun `Translated state is singleton`() {
        val a = TranslationState.Translated
        val b = TranslationState.Translated
        assertTrue(a === b)
    }

    @Test
    fun `Error state holds message`() {
        val state = TranslationState.Error("API rate limit exceeded")
        assertEquals("API rate limit exceeded", state.message)
    }

    @Test
    fun `different states are not equal`() {
        assertFalse(TranslationState.Idle == TranslationState.Translated)
        assertFalse(TranslationState.Translating(1, 5) == TranslationState.Translating(2, 5))
    }

    @Test
    fun `Translating defaults to the progressing phase`() {
        assertEquals(TranslationPhase.Progressing, TranslationState.Translating(3, 10).phase)
    }

    @Test
    fun `Retrying phase carries the one-based page being retried`() {
        val state = TranslationState.Translating(7, 32, phase = TranslationPhase.Retrying(page = 8))
        assertEquals(TranslationPhase.Retrying(8), state.phase)
        assertEquals(7, state.currentPage)
    }

    @Test
    fun `Translating states differing only in phase are not equal`() {
        assertFalse(
            TranslationState.Translating(7, 32) ==
                TranslationState.Translating(7, 32, phase = TranslationPhase.Retrying(8)),
        )
    }

    @Test
    fun `Incomplete state identifies unresolved pages`() {
        val state = TranslationState.Incomplete(
            resolvedPages = 3,
            totalPages = 5,
            unresolvedPages = listOf(4, 5),
            reason = "Translation failed on page 4",
        )

        assertEquals(3, state.resolvedPages)
        assertEquals(5, state.totalPages)
        assertEquals(listOf(4, 5), state.unresolvedPages)
    }
}
