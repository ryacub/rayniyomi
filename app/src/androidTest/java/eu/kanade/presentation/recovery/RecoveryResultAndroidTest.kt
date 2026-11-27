package eu.kanade.presentation.recovery

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.ui.recovery.RecoveryMedia
import eu.kanade.tachiyomi.ui.recovery.RecoveryScreenModel
import eu.kanade.tachiyomi.ui.recovery.RecoveryScreenModel.Step
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.recovery.model.RecoveryCandidate
import tachiyomi.domain.recovery.model.RecoveryEntry
import tachiyomi.domain.recovery.model.RecoveryState
import tachiyomi.i18n.MR

@RunWith(AndroidJUnit4::class)
class RecoveryResultAndroidTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var reviewCalls = 0

    private val actions = RecoveryActions(
        navigateUp = {}, toggleOriginal = {}, toggleSource = {}, setKeepOriginal = {}, findReplacements = {},
        choose = { _, _ -> }, approve = {}, skip = {}, reopen = {}, searchAgain = { _, _ -> },
        searchRemaining = {}, apply = {}, cancel = {}, retryFailed = {}, reviewUnresolved = { reviewCalls++ },
        startAnother = {}, openTitle = {}, migrateAlone = {},
    )

    private fun entry(
        id: Long,
        state: RecoveryState,
        choice: RecoveryCandidate? = null,
        candidates: List<RecoveryCandidate> = emptyList(),
    ) = RecoveryEntry(
        id = id, sessionId = 1, originalId = id, selected = true, state = state, searchQuery = "Title $id",
        candidates = candidates, searchErrors = emptyMap(), choice = choice, pairs = emptyList(),
        resultId = null, error = null, runToken = null,
    )

    private fun show(
        step: Step,
        entries: List<RecoveryEntry>,
        busy: RecoveryScreenModel.Busy? = null,
    ) {
        composeRule.setContent {
            MaterialTheme {
                RecoveryScreenContent(
                    state = RecoveryScreenModel.State(step = step, entries = entries, busy = busy),
                    media = RecoveryMedia.MANGA,
                    failedSourceName = null,
                    actions = actions,
                )
            }
        }
    }

    private fun text(resource: StringResource) = context.stringResource(resource)

    @Test
    fun failedOnlyResultsOffersRetryAndReview() {
        show(Step.Result, listOf(entry(1, RecoveryState.COMPLETED), entry(2, RecoveryState.FAILED)))

        composeRule.onNodeWithText(text(MR.strings.recovery_action_retry_failed)).assertIsEnabled()
        composeRule.onNodeWithText(text(MR.strings.recovery_action_review_failed)).assertIsEnabled().performClick()

        assertEquals(1, reviewCalls)
    }

    @Test
    fun mixedFailedAndUnresolvedResultsOffersReview() {
        show(Step.Result, listOf(entry(1, RecoveryState.FAILED), entry(2, RecoveryState.NEEDS_REVIEW)))

        composeRule.onNodeWithText(text(MR.strings.recovery_action_review_unresolved)).assertIsEnabled()
        composeRule.onNodeWithText(text(MR.strings.recovery_action_retry_failed)).assertIsEnabled()
        composeRule.onNodeWithText(text(MR.strings.recovery_action_review_failed)).assertDoesNotExist()
    }

    @Test
    fun fullyRecoveredResultsOffersNoReview() {
        show(Step.Result, listOf(entry(1, RecoveryState.COMPLETED)))

        composeRule.onNodeWithText(text(MR.strings.recovery_action_review_failed)).assertDoesNotExist()
        composeRule.onNodeWithText(text(MR.strings.recovery_action_review_unresolved)).assertDoesNotExist()
        composeRule.onNodeWithText(text(MR.strings.recovery_action_retry_failed)).assertDoesNotExist()
    }

    @Test
    fun busyResultsDisablesRetryAndReview() {
        show(
            Step.Result,
            listOf(entry(1, RecoveryState.COMPLETED), entry(2, RecoveryState.FAILED)),
            busy = RecoveryScreenModel.Busy.Applying,
        )

        composeRule.onNodeWithText(text(MR.strings.recovery_action_retry_failed)).assertIsNotEnabled()
        composeRule.onNodeWithText(text(MR.strings.recovery_action_review_failed)).assertIsNotEnabled()
    }

    @Test
    fun failedRowWithChoiceOffersChangeMatch() {
        val choice = RecoveryCandidate(20, "/m/2", "Chosen match")
        show(Step.Review, listOf(entry(2, RecoveryState.FAILED, choice = choice)))

        composeRule.onNodeWithText("Chosen match").assertExists()
        composeRule.onNodeWithText(text(MR.strings.recovery_action_change_match)).assertExists()
    }

    @Test
    fun failedRowWithoutChoiceOffersCandidatesAndManualSearch() {
        val candidate = RecoveryCandidate(20, "/m/2", "Candidate match")
        show(Step.Review, listOf(entry(2, RecoveryState.FAILED, candidates = listOf(candidate))))

        composeRule.onNodeWithText("Candidate match", substring = true).assertExists()
        composeRule.onNodeWithText(text(MR.strings.recovery_action_search_manually)).assertExists()
        composeRule.onNodeWithText(text(MR.strings.recovery_action_change_match)).assertDoesNotExist()
    }

    @Test
    fun failedSearchRowOffersManualSearchWithoutChangeMatch() {
        val searchFailed = entry(2, RecoveryState.FAILED).copy(searchErrors = mapOf(20L to "timeout"))
        show(Step.Review, listOf(searchFailed))

        composeRule.onNodeWithText(text(MR.strings.recovery_action_search_manually)).assertExists()
        composeRule.onNodeWithText(text(MR.strings.recovery_action_change_match)).assertDoesNotExist()
    }
}
