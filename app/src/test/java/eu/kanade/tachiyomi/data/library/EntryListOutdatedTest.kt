package eu.kanade.tachiyomi.data.library

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_HAS_UNVIEWED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_VIEWED

class EntryListOutdatedTest {

    @Test
    fun `a library entry past the fetch window is outdated`() {
        isOutdated() shouldBe true
    }

    @Test
    fun `an entry before its next update is not outdated`() {
        isOutdated(nextUpdate = WINDOW_END + 1) shouldBe false
    }

    @Test
    fun `an entry inside the grace window is not outdated`() {
        isOutdated(nextUpdate = WINDOW_START) shouldBe false
    }

    @Test
    fun `an entry with no computed next update is not outdated`() {
        isOutdated(nextUpdate = 0L) shouldBe false
    }

    @Test
    fun `a completed entry is not outdated even without the completed restriction`() {
        isOutdated(isCompleted = true, restrictions = emptySet()) shouldBe false
    }

    @Test
    fun `an entry outside the library is not outdated`() {
        isOutdated(isFavorite = false) shouldBe false
    }

    @Test
    fun `an entry that only fetches once is not outdated`() {
        isOutdated(alwaysUpdate = false) shouldBe false
    }

    @Test
    fun `an entry that smart update skips for unviewed items is not outdated`() {
        isOutdated(restrictions = setOf(ENTRY_HAS_UNVIEWED), hasUnviewed = true) shouldBe false
    }

    @Test
    fun `an entry that smart update skips as not started is not outdated`() {
        isOutdated(restrictions = setOf(ENTRY_NON_VIEWED), hasStarted = false) shouldBe false
    }

    @Test
    fun `an entry outside the update categories is not outdated`() {
        isOutdated(isInUpdateCategories = false) shouldBe false
    }

    @Test
    fun `every category is updated when no category is included or excluded`() {
        isInAutoUpdateCategories(listOf(5L), included = emptySet(), excluded = emptySet()) shouldBe true
    }

    @Test
    fun `an entry is updated only when one of its categories is included`() {
        isInAutoUpdateCategories(listOf(5L, 6L), included = setOf(6L), excluded = emptySet()) shouldBe true
        isInAutoUpdateCategories(listOf(5L), included = setOf(6L), excluded = emptySet()) shouldBe false
    }

    @Test
    fun `an excluded category wins over an included one`() {
        isInAutoUpdateCategories(listOf(5L, 6L), included = setOf(6L), excluded = setOf(5L)) shouldBe false
    }

    @Test
    fun `an entry without categories counts as uncategorized`() {
        val uncategorized = setOf(Category.UNCATEGORIZED_ID)
        isInAutoUpdateCategories(emptyList(), included = uncategorized, excluded = emptySet()) shouldBe true
        isInAutoUpdateCategories(emptyList(), included = emptySet(), excluded = uncategorized) shouldBe false
    }

    @Test
    fun `a next update at the window start is not past the window`() {
        isPastFetchWindow(WINDOW_START, window) shouldBe false
    }

    @Test
    fun `a next update one before the window start is past the window`() {
        isPastFetchWindow(WINDOW_START - 1, window) shouldBe true
    }

    @Test
    fun `a zero next update is not past the window`() {
        isPastFetchWindow(0L, window) shouldBe false
    }

    @Test
    fun `a favorite caught-up entry in the update categories is eligible`() {
        isAutoUpdateEligible(candidate(), isFavorite = true, context = updateContext()) shouldBe true
    }

    @Test
    fun `an entry outside the library is not eligible`() {
        isAutoUpdateEligible(candidate(), isFavorite = false, context = updateContext()) shouldBe false
    }

    @Test
    fun `an entry outside the update categories is not eligible`() {
        isAutoUpdateEligible(
            candidate(),
            isFavorite = true,
            context = updateContext(isInUpdateCategories = false),
        ) shouldBe false
    }

    @Test
    fun `a completed entry is not eligible`() {
        isAutoUpdateEligible(candidate(isCompleted = true), isFavorite = true, context = updateContext()) shouldBe false
    }

    @Test
    fun `an entry the update would skip is not eligible`() {
        isAutoUpdateEligible(
            candidate(hasUnviewed = true),
            isFavorite = true,
            context = updateContext(),
        ) shouldBe false
    }

    private val window = FetchWindow(WINDOW_START, WINDOW_END)

    private fun isOutdated(
        nextUpdate: Long = WINDOW_START - 1,
        isFavorite: Boolean = true,
        isInUpdateCategories: Boolean = true,
        alwaysUpdate: Boolean = true,
        isCompleted: Boolean = false,
        hasUnviewed: Boolean = false,
        hasStarted: Boolean = true,
        restrictions: Set<String> = setOf(ENTRY_HAS_UNVIEWED, ENTRY_NON_VIEWED),
    ): Boolean = isEntryListOutdated(
        candidate = AutoUpdateCandidate(
            alwaysUpdate = alwaysUpdate,
            isCompleted = isCompleted,
            hasUnviewed = hasUnviewed,
            hasStarted = hasStarted,
            totalCount = 3,
            nextUpdate = nextUpdate,
        ),
        isFavorite = isFavorite,
        context = updateContext(restrictions, isInUpdateCategories),
    )

    private fun updateContext(
        restrictions: Set<String> = setOf(ENTRY_HAS_UNVIEWED, ENTRY_NON_VIEWED),
        isInUpdateCategories: Boolean = true,
    ) = EntryUpdateContext(
        restrictions = restrictions,
        isInUpdateCategories = isInUpdateCategories,
        fetchWindow = FetchWindow(WINDOW_START, WINDOW_END),
    )

    private fun candidate(
        nextUpdate: Long = WINDOW_START - 1,
        isCompleted: Boolean = false,
        hasUnviewed: Boolean = false,
    ) = AutoUpdateCandidate(
        alwaysUpdate = true,
        isCompleted = isCompleted,
        hasUnviewed = hasUnviewed,
        hasStarted = true,
        totalCount = 3,
        nextUpdate = nextUpdate,
    )

    private companion object {
        const val WINDOW_START = 1_000_000L
        const val WINDOW_END = 3_000_000L
    }
}
