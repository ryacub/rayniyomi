package eu.kanade.presentation.recovery

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import eu.kanade.tachiyomi.ui.recovery.RecoveryMedia
import eu.kanade.tachiyomi.ui.recovery.RecoveryScreenModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.recovery.model.RecoveryCandidate
import tachiyomi.domain.recovery.model.RecoveryEntry
import tachiyomi.domain.recovery.model.RecoveryState
import tachiyomi.i18n.MR

@RunWith(AndroidJUnit4::class)
class RecoveryReviewBusyAndroidTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun manual_search_is_disabled_while_a_search_runs() {
        show(busy = RecoveryScreenModel.Busy.Searching)

        composeRule.onNodeWithText(context.stringResource(MR.strings.recovery_action_search_manually))
            .assertIsNotEnabled()
    }

    @Test
    fun manual_search_is_disabled_while_an_apply_runs() {
        show(busy = RecoveryScreenModel.Busy.Applying)

        composeRule.onNodeWithText(context.stringResource(MR.strings.recovery_action_search_manually))
            .assertIsNotEnabled()
    }

    @Test
    fun manual_search_is_enabled_when_no_work_runs() {
        show(busy = null)

        composeRule.onNodeWithText(context.stringResource(MR.strings.recovery_action_search_manually))
            .assertIsEnabled()
    }

    private fun show(busy: RecoveryScreenModel.Busy?) {
        val state = RecoveryScreenModel.State(
            step = RecoveryScreenModel.Step.Review,
            sessionId = 1,
            titles = mapOf(1L to "Title 1"),
            entries = listOf(
                RecoveryEntry(
                    id = 1,
                    sessionId = 1,
                    originalId = 1,
                    selected = true,
                    state = RecoveryState.NEEDS_REVIEW,
                    searchQuery = "Title 1",
                    candidates = listOf(RecoveryCandidate(20, "/m/1", "Title 1")),
                    searchErrors = emptyMap(),
                    choice = null,
                    pairs = emptyList(),
                    resultId = null,
                    error = null,
                    runToken = null,
                ),
            ),
            busy = busy,
        )
        composeRule.setContent {
            MaterialTheme {
                RecoveryScreenContent(
                    state = state,
                    media = RecoveryMedia.MANGA,
                    failedSourceName = null,
                    actions = RecoveryActions(
                        navigateUp = {}, toggleOriginal = {}, toggleSource = {}, setKeepOriginal = {},
                        findReplacements = {}, choose = { _, _ -> }, approve = {}, skip = {}, reopen = {},
                        searchAgain = { _, _ -> }, searchRemaining = {}, apply = {}, cancel = {},
                        retryFailed = {}, reviewUnresolved = {}, startAnother = {}, openTitle = {},
                        migrateAlone = {},
                    ),
                )
            }
        }
    }
}
