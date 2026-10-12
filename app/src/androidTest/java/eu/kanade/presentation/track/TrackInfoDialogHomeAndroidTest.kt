package eu.kanade.presentation.track

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.kanade.tachiyomi.ui.entries.track.EntryTrackInfoItem
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
    fun untrackedItemRendersAddTrackingAction() {
        val addTracking = ApplicationProvider.getApplicationContext<Context>()
            .stringResource(MR.strings.add_tracking)

        composeRule.setContent {
            MaterialTheme {
                TrackInfoDialogHome(
                    trackItems = listOf(EntryTrackInfoItem.Untracked(DummyTracker(id = 1L, name = "Tracker"))),
                    dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM),
                    onStatusClick = {},
                    onProgressClick = {},
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
