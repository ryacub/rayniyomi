package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.data.translation.TranslationState
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReaderTranslationProgressTest {

    @Test
    fun `emits running state as first value when chapter opens mid-translation`() = runTest {
        val states = MutableStateFlow(mapOf(1L to TranslationState.Translating(3, 10)))
        val chapterIds = MutableStateFlow<Long?>(1L)

        val result = translationStateFlow(states, chapterIds).first()

        assertEquals(TranslationState.Translating(3, 10), result)
    }

    @Test
    fun `forwards progress updates for the open chapter`() = runTest {
        val states = MutableStateFlow<Map<Long, TranslationState>>(
            mapOf(1L to TranslationState.Translating(1, 10)),
        )
        val chapterIds = MutableStateFlow<Long?>(1L)

        val emissions = async {
            translationStateFlow(states, chapterIds).take(2).toList()
        }
        runCurrent()
        states.value = mapOf(1L to TranslationState.Translating(4, 10))
        advanceUntilIdle()

        assertEquals(
            listOf(TranslationState.Translating(1, 10), TranslationState.Translating(4, 10)),
            emissions.await(),
        )
    }

    @Test
    fun `forwards Translated when map holds completed state`() = runTest {
        val states = MutableStateFlow<Map<Long, TranslationState>>(
            mapOf(1L to TranslationState.Translating(10, 10)),
        )
        val chapterIds = MutableStateFlow<Long?>(1L)

        val result = translationStateFlow(states, chapterIds).first()

        assertEquals(TranslationState.Translating(10, 10), result)
        states.value = mapOf(1L to TranslationState.Translated)
        assertEquals(TranslationState.Translated, translationStateFlow(states, chapterIds).first())
    }

    @Test
    fun `forwards Error with its message`() = runTest {
        val states = MutableStateFlow<Map<Long, TranslationState>>(
            mapOf(1L to TranslationState.Error("msg")),
        )
        val chapterIds = MutableStateFlow<Long?>(1L)

        val result = translationStateFlow(states, chapterIds).first()

        assertEquals(TranslationState.Error("msg"), result)
    }

    @Test
    fun `yields Idle after chapter switch away from translating chapter`() = runTest {
        val states = MutableStateFlow<Map<Long, TranslationState>>(
            mapOf(1L to TranslationState.Translating(2, 10)),
        )
        val chapterIds = MutableStateFlow<Long?>(1L)

        val emissions = async {
            translationStateFlow(states, chapterIds).take(2).toList()
        }
        runCurrent()
        chapterIds.value = 2L
        advanceUntilIdle()

        assertEquals(
            listOf(TranslationState.Translating(2, 10), TranslationState.Idle),
            emissions.await(),
        )
    }

    @Test
    fun `maps null chapter id to Idle`() = runTest {
        val states = MutableStateFlow<Map<Long, TranslationState>>(
            mapOf(1L to TranslationState.Translating(2, 10)),
        )
        val chapterIds = MutableStateFlow<Long?>(null)

        val result = translationStateFlow(states, chapterIds).first()

        assertEquals(TranslationState.Idle, result)
    }
}
