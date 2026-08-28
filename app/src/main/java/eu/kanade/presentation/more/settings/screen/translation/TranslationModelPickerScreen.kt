package eu.kanade.presentation.more.settings.screen.translation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.translation.TranslationPreferences
import eu.kanade.tachiyomi.data.translation.TranslationProvider
import eu.kanade.tachiyomi.data.translation.catalog.TranslationCatalogResult
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelCatalogRepository
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelChoice
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelChoiceType
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelEntry
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelPickerState
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelResolution
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelResolver
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TranslationModelPickerScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { TranslationModelPickerScreenModel() }
        val state by screenModel.state.collectAsStateWithLifecycle()
        var expandedModelIds by rememberSaveable(
            stateSaver = listSaver<Set<String>, String>(
                save = { it.toList() },
                restore = { it.toSet() },
            ),
        ) { mutableStateOf(emptySet<String>()) }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(AYMR.strings.pref_translation_model_picker_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        AppBarActions(
                            persistentListOf(
                                AppBar.Action(
                                    title = stringResource(AYMR.strings.pref_translation_model_refresh),
                                    icon = Icons.Outlined.Refresh,
                                    onClick = screenModel::refresh,
                                    enabled = !state.pickerState.isLoading,
                                ),
                            ),
                        )
                    },
                )
            },
        ) { contentPadding ->
            TranslationModelPickerContent(
                state = state.pickerState,
                provider = state.provider,
                choiceType = state.choiceType,
                selectedModelId = state.selectedModelId,
                expandedModelIds = expandedModelIds,
                contentPadding = contentPadding,
                onSelectAutomatic = {
                    screenModel.selectAutomatic()
                    navigator.pop()
                },
                onSelectModel = { modelId ->
                    screenModel.selectModel(modelId)
                    navigator.pop()
                },
                onToggleDetails = { id ->
                    expandedModelIds = if (id in expandedModelIds) {
                        expandedModelIds - id
                    } else {
                        expandedModelIds + id
                    }
                },
            )
        }
    }
}

internal class TranslationModelPickerScreenModel(
    private val preferences: TranslationPreferences = Injekt.get(),
    private val repository: TranslationModelCatalogRepository = Injekt.get(),
    private val loadCatalog: suspend (TranslationProvider, String, Boolean) -> TranslationCatalogResult =
        repository::load,
) : StateScreenModel<TranslationModelPickerScreenModel.State>(State()) {

    data class State(
        val provider: TranslationProvider = TranslationProvider.NONE,
        val choiceType: TranslationModelChoiceType = TranslationModelChoiceType.AUTOMATIC,
        val selectedModelId: String = "",
        val pickerState: TranslationModelPickerState = TranslationModelPickerState(isLoading = true),
    )

    private val provider = preferences.translationProvider().get()
    private val modelPreference = preferences.translationModel(provider)
    private val choiceTypePreference = preferences.translationModelChoiceType(provider)

    init {
        mutableState.update { it.copy(provider = provider, selectedModelId = modelPreference.get()) }
        screenModelScope.launchIO { load(forceRefresh = false) }
    }

    fun refresh() {
        screenModelScope.launchIO { load(forceRefresh = true) }
    }

    fun selectModel(modelId: String) {
        modelPreference.set(modelId)
        choiceTypePreference.set(TranslationModelChoiceType.PINNED)
        mutableState.update {
            it.copy(
                choiceType = TranslationModelChoiceType.PINNED,
                selectedModelId = modelId,
            )
        }
    }

    fun selectAutomatic() {
        choiceTypePreference.set(TranslationModelChoiceType.AUTOMATIC)
        val resolvedId = resolveAutomaticModel(mutableState.value.pickerState.models)
        mutableState.update {
            it.copy(
                choiceType = TranslationModelChoiceType.AUTOMATIC,
                selectedModelId = resolvedId,
            )
        }
    }

    private suspend fun load(forceRefresh: Boolean) {
        mutableState.update { it.copy(pickerState = it.pickerState.copy(isLoading = true)) }
        try {
            val apiKey = preferences.translationApiKey(provider).get()
            val result = loadCatalog(provider, apiKey, forceRefresh)
            val pickerState = TranslationModelPickerState.fromResult(result, provider)
            val resolvedId = if (result is TranslationCatalogResult.Success) {
                resolveAutomaticModel(pickerState.models)
            } else {
                modelPreference.get()
            }
            mutableState.update {
                it.copy(
                    pickerState = pickerState,
                    selectedModelId = resolvedId,
                )
            }
        } finally {
            mutableState.update {
                it.copy(pickerState = it.pickerState.copy(isLoading = false))
            }
        }
    }

    private fun resolveAutomaticModel(visibleModels: List<TranslationModelEntry>): String {
        if (choiceTypePreference.get() != TranslationModelChoiceType.AUTOMATIC) {
            return modelPreference.get()
        }
        val resolution = TranslationModelResolver.resolve(
            provider = provider,
            choice = TranslationModelChoice(TranslationModelChoiceType.AUTOMATIC),
            models = visibleModels,
        )
        return if (resolution is TranslationModelResolution.Selected) {
            modelPreference.set(resolution.model.id)
            resolution.model.id
        } else {
            modelPreference.set("")
            ""
        }
    }
}
