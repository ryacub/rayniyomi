package eu.kanade.tachiyomi.data.translation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
}
