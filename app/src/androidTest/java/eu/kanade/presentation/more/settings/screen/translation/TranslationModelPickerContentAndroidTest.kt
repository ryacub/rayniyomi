package eu.kanade.presentation.more.settings.screen.translation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.translation.TranslationProvider
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelChoiceType
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelEntry
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelPickerState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@RunWith(AndroidJUnit4::class)
class TranslationModelPickerContentAndroidTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var res: ResStrings

    @Test
    fun allProviders_renderModelNamesAsPrimaryLabel() {
        val providers = listOf(
            TranslationProvider.CLAUDE,
            TranslationProvider.OPENAI,
            TranslationProvider.GOOGLE,
            TranslationProvider.OPENROUTER,
        )
        providers.forEach { provider ->
            val models = TranslationModelPickerFixtures.forProvider(provider)
            setContent(provider = provider, models = models)

            models.forEach { model ->
                composeRule.onNodeWithText(model.displayName).assertIsDisplayed()
                composeRule.onNodeWithText(model.id).assertDoesNotExist()
            }
            composeRule.waitForIdle()
        }
    }

    @Test
    fun selectingModel_marksRowSelectedAndClearsAutomatic() {
        val models = TranslationModelPickerFixtures.forProvider(TranslationProvider.CLAUDE)
        val target = models.first()
        setContent(provider = TranslationProvider.CLAUDE, models = models)

        composeRule.onNodeWithText(target.displayName).performClick()

        composeRule.onNodeWithText(target.displayName).assertIsSelected()
        composeRule.onNodeWithText(res.automatic).assertIsNotSelected()
    }

    @Test
    fun automaticRow_isSelectedByDefaultAndReportsSelectedSemantics() {
        val models = TranslationModelPickerFixtures.forProvider(TranslationProvider.CLAUDE)
        setContent(provider = TranslationProvider.CLAUDE, models = models)

        composeRule.onNodeWithText(res.automatic).assertIsSelected()
        models.forEach { model ->
            composeRule.onNodeWithText(model.displayName).assertIsNotSelected()
        }
    }

    @Test
    fun openRouter_showsPaidWarningBeforeSelection() {
        setContent(provider = TranslationProvider.OPENROUTER, models = emptyList())

        composeRule.onNodeWithText(res.paidWarning).assertIsDisplayed()
    }

    @Test
    fun nonOpenRouterProviders_doNotShowPaidWarning() {
        val providers = listOf(
            TranslationProvider.CLAUDE,
            TranslationProvider.OPENAI,
            TranslationProvider.GOOGLE,
        )
        providers.forEach { provider ->
            setContent(provider = provider, models = TranslationModelPickerFixtures.forProvider(provider))

            composeRule.onNodeWithText(res.paidWarning).assertDoesNotExist()
            composeRule.waitForIdle()
        }
    }

    @Test
    fun expandingDetails_revealsModelIdAndHidesItAgain() {
        val models = TranslationModelPickerFixtures.forProvider(TranslationProvider.CLAUDE)
        val target = models.first()
        setContent(provider = TranslationProvider.CLAUDE, models = models)

        composeRule.onNodeWithContentDescription(res.detailsShow).performClick()

        composeRule.onNodeWithText(target.id).assertIsDisplayed()

        composeRule.onNodeWithContentDescription(res.detailsHide).performClick()

        composeRule.onNodeWithText(target.id).assertDoesNotExist()
    }

    @Test
    fun longModelName_wrapsWithoutClipping() {
        val short = TranslationModelPickerFixtures.forProvider(TranslationProvider.CLAUDE).first()
        val long = TranslationModelPickerFixtures.longNameModel()
        setContent(
            provider = TranslationProvider.CLAUDE,
            models = listOf(short, long),
            fontScale = 2.0f,
            widthDp = 320,
        )

        composeRule.runOnIdle {
            val shortHeight = composeRule.onNodeWithText(short.displayName).fetchSemanticsNode().size.height
            val longHeight = composeRule.onNodeWithText(long.displayName).fetchSemanticsNode().size.height
            val longNode = composeRule.onNodeWithText(long.displayName).fetchSemanticsNode()
            val rootNode = composeRule.onRoot().fetchSemanticsNode()

            composeRule.onNodeWithText(long.displayName).assertIsDisplayed()
            assertTrue("long height $longHeight should exceed short height $shortHeight", longHeight > shortHeight)
            assertTrue("long overflows right", longNode.boundsInRoot.right <= rootNode.boundsInRoot.right + 0.5f)
            assertTrue("long starts left of parent", longNode.boundsInRoot.left >= -0.5f)
        }
    }

    @Test
    fun loadingWithNoModels_showsLoadingIndicatorOnly() {
        setContent(provider = TranslationProvider.CLAUDE, models = emptyList(), isLoading = true)

        composeRule.onNodeWithText(res.empty).assertDoesNotExist()
    }

    @Test
    fun loadingWithCachedModels_keepsListVisible() {
        val models = TranslationModelPickerFixtures.forProvider(TranslationProvider.CLAUDE)
        setContent(provider = TranslationProvider.CLAUDE, models = models, isLoading = true)

        models.forEach { model ->
            composeRule.onNodeWithText(model.displayName).assertIsDisplayed()
        }
    }

    @Test
    fun failureWithCachedModels_showsErrorAndKeepsList() {
        val models = TranslationModelPickerFixtures.forProvider(TranslationProvider.CLAUDE)
        setContent(
            provider = TranslationProvider.CLAUDE,
            models = models,
            errorMessage = "boom",
        )

        composeRule.onNodeWithText(res.refreshFailed).assertIsDisplayed()
        composeRule.onNodeWithText("boom").assertDoesNotExist()
        models.forEach { model ->
            composeRule.onNodeWithText(model.displayName).assertIsDisplayed()
        }
    }

    @Test
    fun emptyCatalog_showsEmptyState() {
        setContent(provider = TranslationProvider.CLAUDE, models = emptyList(), isLoading = false)

        composeRule.onNodeWithText(res.empty).assertIsDisplayed()
    }

    private fun setContent(
        provider: TranslationProvider,
        models: List<TranslationModelEntry>,
        isLoading: Boolean = false,
        errorMessage: String? = null,
        fontScale: Float = 1.0f,
        widthDp: Int? = null,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                MaterialTheme {
                    var choiceType by mutableStateOf(TranslationModelChoiceType.AUTOMATIC)
                    var selectedModelId by mutableStateOf("")
                    var expanded by mutableStateOf(emptySet<String>())
                    res = ResStrings(
                        automatic = stringResource(AYMR.strings.pref_translation_model_automatic),
                        refreshFailed = stringResource(AYMR.strings.pref_translation_model_refresh_failed),
                        empty = stringResource(AYMR.strings.pref_translation_model_empty),
                        paidWarning = stringResource(AYMR.strings.pref_translation_model_automatic_paid_warning),
                        detailsShow = stringResource(AYMR.strings.pref_translation_model_details_show),
                        detailsHide = stringResource(AYMR.strings.pref_translation_model_details_hide),
                    )

                    Box(if (widthDp != null) Modifier.width(widthDp.dp) else Modifier) {
                        TranslationModelPickerContent(
                            state = TranslationModelPickerState(
                                models = models,
                                isLoading = isLoading,
                                errorMessage = errorMessage,
                            ),
                            provider = provider,
                            choiceType = choiceType,
                            selectedModelId = selectedModelId,
                            expandedModelIds = expanded,
                            contentPadding = PaddingValues(0.dp),
                            onSelectAutomatic = {
                                choiceType = TranslationModelChoiceType.AUTOMATIC
                                selectedModelId = ""
                            },
                            onSelectModel = { id ->
                                choiceType = TranslationModelChoiceType.PINNED
                                selectedModelId = id
                            },
                            onToggleDetails = { id ->
                                expanded = if (id in expanded) expanded - id else expanded + id
                            },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private data class ResStrings(
        val automatic: String,
        val refreshFailed: String,
        val empty: String,
        val paidWarning: String,
        val detailsShow: String,
        val detailsHide: String,
    )
}
