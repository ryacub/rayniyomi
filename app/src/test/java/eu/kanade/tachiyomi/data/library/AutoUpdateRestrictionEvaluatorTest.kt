package eu.kanade.tachiyomi.data.library

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_HAS_UNVIEWED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_VIEWED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_OUTSIDE_RELEASE_PERIOD

class AutoUpdateRestrictionEvaluatorTest {

    @Test
    fun `always update strategy is mandatory regardless of restrictions`() {
        evaluate(
            restrictions = emptySet(),
            alwaysUpdate = false,
        ) shouldBe AutoUpdateSkipReason.NOT_ALWAYS_UPDATE
    }

    @Test
    fun `completed restriction is applied only when enabled`() {
        evaluate(
            restrictions = setOf(ENTRY_NON_COMPLETED),
            isCompleted = true,
        ) shouldBe AutoUpdateSkipReason.COMPLETED

        evaluate(
            restrictions = emptySet(),
            isCompleted = true,
        ) shouldBe null
    }

    @Test
    fun `unviewed restriction is applied only when enabled`() {
        evaluate(
            restrictions = setOf(ENTRY_HAS_UNVIEWED),
            hasUnviewed = true,
        ) shouldBe AutoUpdateSkipReason.NOT_CAUGHT_UP

        evaluate(
            restrictions = emptySet(),
            hasUnviewed = true,
        ) shouldBe null
    }

    @Test
    fun `non viewed restriction skips only non-started candidates with content`() {
        evaluate(
            restrictions = setOf(ENTRY_NON_VIEWED),
            totalCount = 4,
            hasStarted = false,
        ) shouldBe AutoUpdateSkipReason.NOT_STARTED

        evaluate(
            restrictions = setOf(ENTRY_NON_VIEWED),
            totalCount = 0,
            hasStarted = false,
        ) shouldBe null
    }

    @Test
    fun `release period restriction uses fetch window upper bound only when enabled`() {
        evaluate(
            restrictions = setOf(ENTRY_OUTSIDE_RELEASE_PERIOD),
            nextUpdate = 1_001,
            fetchWindowUpperBound = 1_000,
        ) shouldBe AutoUpdateSkipReason.OUTSIDE_RELEASE_PERIOD

        evaluate(
            restrictions = emptySet(),
            nextUpdate = 1_001,
            fetchWindowUpperBound = 1_000,
        ) shouldBe null
    }

    @Test
    fun `manga and anime selection paths stay behaviorally identical`() {
        val scenarios = listOf(
            Scenario(
                restrictions = emptySet(),
                alwaysUpdate = false,
                expectedReason = AutoUpdateSkipReason.NOT_ALWAYS_UPDATE,
            ),
            Scenario(
                restrictions = setOf(ENTRY_NON_COMPLETED),
                isCompleted = true,
                expectedReason = AutoUpdateSkipReason.COMPLETED,
            ),
            Scenario(
                restrictions = setOf(ENTRY_HAS_UNVIEWED),
                hasUnviewed = true,
                expectedReason = AutoUpdateSkipReason.NOT_CAUGHT_UP,
            ),
            Scenario(
                restrictions = setOf(ENTRY_NON_VIEWED),
                hasStarted = false,
                totalCount = 3L,
                expectedReason = AutoUpdateSkipReason.NOT_STARTED,
            ),
            Scenario(
                restrictions = setOf(ENTRY_OUTSIDE_RELEASE_PERIOD),
                nextUpdate = 3_000L,
                fetchWindowUpperBound = 2_000L,
                expectedReason = AutoUpdateSkipReason.OUTSIDE_RELEASE_PERIOD,
            ),
            Scenario(
                restrictions = setOf(ENTRY_OUTSIDE_RELEASE_PERIOD),
                nextUpdate = 1_500L,
                fetchWindowUpperBound = 2_000L,
                expectedReason = null,
            ),
        )

        for (scenario in scenarios) {
            val mangaDecision = decideLikeManga(scenario)
            val animeDecision = decideLikeAnime(scenario)

            mangaDecision shouldBe animeDecision
            mangaDecision shouldBe scenario.expectedReason
        }
    }

    private fun evaluate(
        restrictions: Set<String>,
        alwaysUpdate: Boolean = true,
        isCompleted: Boolean = false,
        hasUnviewed: Boolean = false,
        hasStarted: Boolean = true,
        totalCount: Long = 1L,
        nextUpdate: Long = 0L,
        fetchWindowUpperBound: Long = 1_000L,
    ): AutoUpdateSkipReason? {
        return evaluateAutoUpdateCandidate(
            candidate = AutoUpdateCandidate(
                alwaysUpdate = alwaysUpdate,
                isCompleted = isCompleted,
                hasUnviewed = hasUnviewed,
                hasStarted = hasStarted,
                totalCount = totalCount,
                nextUpdate = nextUpdate,
            ),
            restrictions = restrictions,
            fetchWindowUpperBound = fetchWindowUpperBound,
        )
    }

    private fun decideLikeManga(scenario: Scenario): AutoUpdateSkipReason? {
        return evaluate(
            restrictions = scenario.restrictions,
            alwaysUpdate = scenario.alwaysUpdate,
            isCompleted = scenario.isCompleted,
            hasUnviewed = scenario.hasUnviewed,
            hasStarted = scenario.hasStarted,
            totalCount = scenario.totalCount,
            nextUpdate = scenario.nextUpdate,
            fetchWindowUpperBound = scenario.fetchWindowUpperBound,
        )
    }

    private fun decideLikeAnime(scenario: Scenario): AutoUpdateSkipReason? {
        return evaluate(
            restrictions = scenario.restrictions,
            alwaysUpdate = scenario.alwaysUpdate,
            isCompleted = scenario.isCompleted,
            hasUnviewed = scenario.hasUnviewed,
            hasStarted = scenario.hasStarted,
            totalCount = scenario.totalCount,
            nextUpdate = scenario.nextUpdate,
            fetchWindowUpperBound = scenario.fetchWindowUpperBound,
        )
    }

    private data class Scenario(
        val restrictions: Set<String>,
        val alwaysUpdate: Boolean = true,
        val isCompleted: Boolean = false,
        val hasUnviewed: Boolean = false,
        val hasStarted: Boolean = true,
        val totalCount: Long = 1L,
        val nextUpdate: Long = 0L,
        val fetchWindowUpperBound: Long = 1_000L,
        val expectedReason: AutoUpdateSkipReason?,
    )
}
