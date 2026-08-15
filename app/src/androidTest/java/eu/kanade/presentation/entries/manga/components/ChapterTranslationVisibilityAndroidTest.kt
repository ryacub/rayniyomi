package eu.kanade.presentation.entries.manga.components

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.data.translation.TranslationState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.aniyomi.AYMR
import java.io.File

/**
 * Renders the chapter row in the downloaded and the not-downloaded state.
 *
 * The translate action shows only on a downloaded chapter, so the R850 download
 * deadlock hid it. The first test writes a screenshot of the downloaded row.
 */
@RunWith(AndroidJUnit4::class)
class ChapterTranslationVisibilityAndroidTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun translateActionShowsOnADownloadedChapter() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val translate = context.stringResource(AYMR.strings.translation_action_translate)

        composeRule.setContent {
            MaterialTheme {
                Surface {
                    ChapterRow(downloadState = MangaDownload.State.DOWNLOADED)
                }
            }
        }

        composeRule.onNodeWithContentDescription(translate).assertExists()

        val image = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val target = File(context.getExternalFilesDir(null), "translate-action-downloaded.png")
        target.outputStream().use { out ->
            image.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    @Test
    fun translateActionHidesOnAChapterThatIsNotDownloaded() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val translate = context.stringResource(AYMR.strings.translation_action_translate)

        composeRule.setContent {
            MaterialTheme {
                Surface {
                    ChapterRow(downloadState = MangaDownload.State.NOT_DOWNLOADED)
                }
            }
        }

        composeRule.onNodeWithContentDescription(translate).assertDoesNotExist()
    }

    @Composable
    private fun ChapterRow(downloadState: MangaDownload.State) {
        MangaChapterListItem(
            title = "Chapter 1",
            date = "Aug 14, 2026",
            readProgress = null,
            scanlator = null,
            read = false,
            bookmark = false,
            selected = false,
            downloadIndicatorEnabled = true,
            downloadStateProvider = { downloadState },
            downloadProgressProvider = { 100 },
            chapterSwipeStartAction = LibraryPreferences.ChapterSwipeAction.ToggleBookmark,
            chapterSwipeEndAction = LibraryPreferences.ChapterSwipeAction.ToggleRead,
            onLongClick = {},
            onClick = {},
            onDownloadClick = {},
            onChapterSwipe = {},
            translationStateProvider = { TranslationState.Idle },
            onTranslationClick = {},
        )
    }
}
