package eu.kanade.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import eu.kanade.presentation.entries.anime.components.EpisodeDownloadIndicator
import eu.kanade.presentation.entries.manga.components.ChapterDownloadIndicator
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

@RunWith(AndroidJUnit4::class)
class ItemDownloadIndicatorAndroidTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun activeEpisodeWithUnknownTotalShowsIndeterminateProgress() {
        composeRule.setContent {
            MaterialTheme {
                EpisodeDownloadIndicator(
                    enabled = true,
                    useTitleListStyle = true,
                    downloadStateProvider = { AnimeDownload.State.DOWNLOADING },
                    downloadProgressProvider = { -1 },
                    onClick = {},
                )
            }
        }

        composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun fullTransferShowsProgressUntilChapterDownloadSucceeds() {
        val state = mutableStateOf(MangaDownload.State.DOWNLOADING)
        val downloaded = InstrumentationRegistry.getInstrumentation().targetContext
            .stringResource(MR.strings.label_downloaded)

        composeRule.setContent {
            MaterialTheme {
                ChapterDownloadIndicator(
                    enabled = true,
                    useTitleListStyle = true,
                    downloadStateProvider = { state.value },
                    downloadProgressProvider = { 100 },
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithText("100%").assertExists()
        composeRule.onNodeWithContentDescription(downloaded).assertDoesNotExist()
        composeRule.runOnIdle { state.value = MangaDownload.State.DOWNLOADED }
        composeRule.onNodeWithContentDescription(downloaded).assertExists()
        composeRule.onNodeWithText("100%").assertDoesNotExist()
    }
}
