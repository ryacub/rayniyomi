package eu.kanade.tachiyomi.ui.entries.manga

import eu.kanade.tachiyomi.data.translation.TranslationState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.chapter.model.Chapter

class TranslationSummaryTest {

    private val chapters = listOf(
        chapter(id = 1, name = "Chapter 1"),
        chapter(id = 2, name = "Chapter 2"),
        chapter(id = 3, name = "Chapter 3"),
        chapter(id = 4, name = "Chapter 4"),
    )

    @Test
    fun `empty states map returns null`() {
        assertNull(translationSummaryFrom(emptyMap(), chapters))
    }

    @Test
    fun `states for other manga chapters return null`() {
        val states = mapOf(
            100L to TranslationState.Translating(1, 20),
            200L to TranslationState.Error("boom"),
        )
        assertNull(translationSummaryFrom(states, chapters))
    }

    @Test
    fun `all translated states return null`() {
        val states = mapOf(
            1L to TranslationState.Translated,
            2L to TranslationState.Translated,
        )
        assertNull(translationSummaryFrom(states, chapters))
    }

    @Test
    fun `mixed states produce correct counts`() {
        val states = mapOf(
            1L to TranslationState.Translating(14, 28),
            2L to TranslationState.Translating(3, 30),
            3L to TranslationState.Translated,
            4L to TranslationState.Error("provider offline"),
        )

        val summary = translationSummaryFrom(states, chapters)!!

        assertEquals(2, summary.translatingCount)
        assertEquals(1, summary.completedCount)
        assertEquals(1, summary.failedCount)
    }

    @Test
    fun `per chapter rows carry names and page progress in list order`() {
        val states = mapOf(
            4L to TranslationState.Error("boom"),
            1L to TranslationState.Translating(14, 28),
        )

        val rows = translationSummaryFrom(states, chapters)!!.chapters

        assertEquals(2, rows.size)
        assertEquals(
            ChapterTranslationProgress(chapterId = 1, chapterName = "Chapter 1", currentPage = 14, totalPages = 28),
            rows[0],
        )
        assertEquals(
            ChapterTranslationProgress(
                chapterId = 4,
                chapterName = "Chapter 4",
                currentPage = 0,
                totalPages = 0,
                isFailed = true,
            ),
            rows[1],
        )
    }

    @Test
    fun `summary updates when a state flips to translated`() {
        val translating = mapOf(
            1L to TranslationState.Translating(28, 28),
            2L to TranslationState.Translating(5, 10),
        )
        val completed = mapOf(
            1L to TranslationState.Translated,
            2L to TranslationState.Translating(5, 10),
        )

        val before = translationSummaryFrom(translating, chapters)!!
        val after = translationSummaryFrom(completed, chapters)!!

        assertEquals(2, before.translatingCount)
        assertEquals(0, translationSummaryFrom(completed, listOf(chapters[0], chapters[1]))!!.failedCount)
        assertEquals(1, after.completedCount)

        // Once the last Translating/Error entry is gone the summary disappears.
        assertNull(translationSummaryFrom(mapOf(1L to TranslationState.Translated), chapters))
    }

    @Test
    fun `removed state drops the chapter from summary`() {
        val states = mapOf(
            1L to TranslationState.Translating(1, 20),
            2L to TranslationState.Error("boom"),
        )
        val afterRemoval = states - 2L

        val summary = translationSummaryFrom(afterRemoval, chapters)!!

        assertEquals(1, summary.translatingCount)
        assertEquals(0, summary.failedCount)
    }

    @Test
    fun `translationStateOf returns the mapped state`() {
        val states = mapOf(1L to TranslationState.Translating(2, 10))
        assertEquals(TranslationState.Translating(2, 10), translationStateOf(states, 1L))
    }

    @Test
    fun `translationStateOf falls back to Idle for an absent chapter`() {
        assertEquals(TranslationState.Idle, translationStateOf(emptyMap(), 42L))
    }

    @Test
    fun `a failed chapter row carries the failed flag`() {
        val states = mapOf(
            1L to TranslationState.Error("boom"),
            2L to TranslationState.Translating(1, 20),
        )
        val summary = translationSummaryFrom(states, chapters)

        val rows = summary!!.chapters.associateBy { it.chapterId }
        assertEquals(true, rows.getValue(1L).isFailed)
        assertEquals(false, rows.getValue(2L).isFailed)
    }

    @Test
    fun `an incomplete chapter row carries resolved page progress`() {
        val summary = translationSummaryFrom(
            mapOf(
                1L to TranslationState.Incomplete(3, 5, listOf(4, 5), "Page 4 failed"),
            ),
            chapters,
        )!!

        assertEquals(1, summary.failedCount)
        assertEquals(3, summary.chapters.single().currentPage)
        assertEquals(5, summary.chapters.single().totalPages)
        assertEquals(true, summary.chapters.single().isIncomplete)
        assertEquals(listOf(4, 5), summary.chapters.single().unresolvedPages)
    }
}

private fun chapter(id: Long, name: String): Chapter {
    return Chapter.create().copy(id = id, mangaId = 42L, name = name)
}
