package eu.kanade.tachiyomi.ui.reader

import androidx.activity.ComponentActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.tachiyomi.di.appGraph
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.util.view.setComposeContent
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderSettingsMetroConstructionAndroidTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun readerSettingsComposeViewProvidesFactoryAndKeepsOwnerKeyCallbacks() {
        var observedFactory: MetroViewModelFactory? = null
        var observedOwner: ViewModelStoreOwner? = null
        var treeOwner: ViewModelStoreOwner? = null
        lateinit var observedModel: ReaderSettingsScreenModel
        val readerState = MutableStateFlow(ReaderViewModel.State())
        val readingModes = mutableListOf<ReadingMode>()
        val orientations = mutableListOf<ReaderOrientation>()
        val generation = mutableIntStateOf(0)
        var creations = 0
        composeRule.activityRule.scenario.onActivity { activity ->
            val view = ComposeView(activity)
            activity.setContentView(view)
            treeOwner = view.findViewTreeViewModelStoreOwner()
            view.setComposeContent {
                observedFactory = LocalMetroViewModelFactory.current
                observedOwner = LocalViewModelStoreOwner.current
                val hasCutout = generation.intValue == 2
                val model = assistedMetroViewModel<ReaderSettingsScreenModel, ReaderSettingsScreenModel.Factory>(
                    key = "ReaderSettings:$hasCutout",
                ) {
                    creations++
                    create(readerState, hasCutout, readingModes::add, orientations::add)
                }
                SideEffect { observedModel = model }
            }
        }
        composeRule.waitForIdle()

        assertSame(appGraph.metroViewModelFactory, observedFactory)
        assertSame(treeOwner, observedOwner)
        assertSame(appGraph.readerPreferences, observedModel.preferences)
        val firstModel = observedModel

        composeRule.runOnIdle { generation.intValue = 1 }
        composeRule.waitForIdle()
        assertSame(firstModel, observedModel)
        assertEquals(1, creations)

        composeRule.runOnIdle {
            observedModel.onChangeReadingMode(ReadingMode.WEBTOON)
            observedModel.onChangeOrientation(ReaderOrientation.LANDSCAPE)
            generation.intValue = 2
        }
        composeRule.waitForIdle()
        assertEquals(listOf(ReadingMode.WEBTOON), readingModes)
        assertEquals(listOf(ReaderOrientation.LANDSCAPE), orientations)
        assertNotSame(firstModel, observedModel)
        assertEquals(true, observedModel.hasDisplayCutout)
        assertEquals(2, creations)

        composeRule.runOnIdle { generation.intValue = 3 }
        composeRule.waitForIdle()
        assertSame(firstModel, observedModel)
        assertEquals(2, creations)
    }
}
