package eu.kanade.presentation.recovery

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.then
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import eu.kanade.tachiyomi.ui.recovery.RecoveryMedia
import eu.kanade.tachiyomi.ui.recovery.RecoveryScreenModel
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.recovery.model.RecoveryCandidate
import tachiyomi.domain.recovery.model.RecoveryEntry
import tachiyomi.domain.recovery.model.RecoveryState
import tachiyomi.i18n.MR

@RunWith(AndroidJUnit4::class)
class RecoveryReviewBottomBarAndroidTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun landscape_phone_puts_all_review_actions_in_one_row() {
        show(LANDSCAPE_PHONE, readyEntry())

        val buttons = listOf(searchRemaining(), apply(), startAnother()).map { bounds(it) }
        val rowHeight = buttons.maxOf { it.bottom - it.top }
        val span = buttons.maxOf { it.bottom } - buttons.minOf { it.top }
        assertTrue("Review actions span $span, one row is $rowHeight", span <= rowHeight)
    }

    @Test
    fun portrait_phone_stacks_review_actions() {
        show(PORTRAIT_PHONE, readyEntry())

        val tops = listOf(searchRemaining(), apply(), startAnother()).map { bounds(it).top }
        assertTrue("Review action tops $tops", tops[0] < tops[1] && tops[1] < tops[2])
    }

    @Test
    fun landscape_phone_keeps_apply_disabled_without_a_ready_title() {
        show(LANDSCAPE_PHONE)

        composeRule.onNodeWithText(apply(readyCount = 0)).assertIsNotEnabled()
        composeRule.onNodeWithText(searchRemaining()).assertIsEnabled()
        composeRule.onNodeWithText(startAnother()).assertIsEnabled()
    }

    @Test
    fun narrow_phone_with_large_text_moves_review_actions_without_wrapping_labels() {
        show(DpSize(360.dp, 800.dp), fontScale = 2f)

        val labels = listOf(
            MR.strings.recovery_action_use_match,
            MR.strings.recovery_action_search_manually,
            MR.strings.recovery_action_skip,
        ).map { context.stringResource(it) }
        assertTrue("Review actions need more than one row", bounds(labels.first()).top < bounds(labels.last()).top)

        for (label in labels) {
            val node = composeRule.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
            val layouts = mutableListOf<TextLayoutResult>()
            check(requireNotNull(node.config[SemanticsActions.GetTextLayoutResult].action)(layouts))
            val layout = layouts.single()
            assertTrue("Review action wraps or clips: $label", layout.lineCount == 1 && !layout.hasVisualOverflow)
        }
    }

    private fun searchRemaining() = context.stringResource(MR.strings.recovery_action_search_remaining, 1)

    private fun apply(readyCount: Int = 1) = context.stringResource(MR.strings.recovery_action_apply, readyCount)

    private fun startAnother() = context.stringResource(MR.strings.recovery_action_start_another)

    private fun bounds(text: String): DpRect = composeRule.onNodeWithText(text).getUnclippedBoundsInRoot()

    private fun searchableEntry() = entry(id = 1, state = RecoveryState.SELECTED, choice = null)

    private fun readyEntry() = entry(
        id = 2,
        state = RecoveryState.READY,
        choice = RecoveryCandidate(20, "/m/2", "Title 2"),
    )

    private fun entry(id: Long, state: RecoveryState, choice: RecoveryCandidate?) = RecoveryEntry(
        id = id,
        sessionId = 1,
        originalId = id,
        selected = true,
        state = state,
        searchQuery = "Title $id",
        candidates = listOfNotNull(choice),
        searchErrors = emptyMap(),
        choice = choice,
        pairs = emptyList(),
        resultId = null,
        error = null,
        runToken = null,
    )

    private fun show(window: DpSize, vararg extraEntries: RecoveryEntry, fontScale: Float = 1f) {
        val entries = listOf(searchableEntry()) + extraEntries
        val state = RecoveryScreenModel.State(
            step = RecoveryScreenModel.Step.Review,
            sessionId = 1,
            titles = entries.associate { it.originalId to it.searchQuery },
            entries = entries,
        )
        composeRule.setContent {
            DeviceConfigurationOverride(windowOf(window) then DeviceConfigurationOverride.FontScale(fontScale)) {
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

    private fun windowOf(size: DpSize) = DeviceConfigurationOverride.ForcedSize(size) then
        DeviceConfigurationOverride { content ->
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = size.width.value.toInt()
                screenHeightDp = size.height.value.toInt()
            }
            CompositionLocalProvider(LocalConfiguration provides configuration, content = content)
        }

    private companion object {
        val PORTRAIT_PHONE = DpSize(411.dp, 914.dp)
        val LANDSCAPE_PHONE = DpSize(914.dp, 411.dp)
    }
}
