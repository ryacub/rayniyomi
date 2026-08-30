package eu.kanade.presentation.track

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.kanade.presentation.track.anime.AnimeTrackInfoDialogHome
import eu.kanade.presentation.track.manga.MangaTrackInfoDialogHome
import eu.kanade.tachiyomi.ui.entries.anime.track.AnimeTrackInfoItem
import eu.kanade.tachiyomi.ui.entries.manga.track.MangaTrackInfoItem
import eu.kanade.test.DummyTracker
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@RunWith(AndroidJUnit4::class)
class TrackInfoDialogHomeAndroidTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun untrackedAnimeItemRendersAddTrackingAction() {
        val addTracking = ApplicationProvider.getApplicationContext<Context>()
            .stringResource(MR.strings.add_tracking)

        composeRule.setContent {
            MaterialTheme {
                AnimeTrackInfoDialogHome(
                    trackItems = listOf(AnimeTrackInfoItem.Untracked(DummyTracker(id = 1L, name = "Anime Tracker"))),
                    dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM),
                    onStatusClick = {},
                    onEpisodeClick = {},
                    onScoreClick = {},
                    onStartDateEdit = {},
                    onEndDateEdit = {},
                    onNewSearch = {},
                    onOpenInBrowser = {},
                    onRemoved = {},
                    onCopyLink = {},
                    onTogglePrivate = {},
                )
            }
        }

        composeRule.onNodeWithText(addTracking).assertIsDisplayed()
    }

    @Test
    fun untrackedMangaItemRendersAddTrackingAction() {
        val addTracking = ApplicationProvider.getApplicationContext<Context>()
            .stringResource(MR.strings.add_tracking)

        composeRule.setContent {
            MaterialTheme {
                MangaTrackInfoDialogHome(
                    trackItems = listOf(MangaTrackInfoItem.Untracked(DummyTracker(id = 1L, name = "Manga Tracker"))),
                    dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM),
                    onStatusClick = {},
                    onChapterClick = {},
                    onScoreClick = {},
                    onStartDateEdit = {},
                    onEndDateEdit = {},
                    onNewSearch = {},
                    onOpenInBrowser = {},
                    onRemoved = {},
                    onCopyLink = {},
                    onTogglePrivate = {},
                )
            }
        }

        composeRule.onNodeWithText(addTracking).assertIsDisplayed()
    }
}
