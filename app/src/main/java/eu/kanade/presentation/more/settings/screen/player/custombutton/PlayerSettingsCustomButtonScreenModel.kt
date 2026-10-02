package eu.kanade.presentation.more.settings.screen.player.custombutton

import androidx.compose.runtime.Immutable
import eu.kanade.presentation.util.StateViewModel
import androidx.lifecycle.viewModelScope
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.di.appGraph
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.custombuttons.interactor.CreateCustomButton
import tachiyomi.domain.custombuttons.interactor.DeleteCustomButton
import tachiyomi.domain.custombuttons.interactor.GetCustomButtons
import tachiyomi.domain.custombuttons.interactor.ReorderCustomButton
import tachiyomi.domain.custombuttons.interactor.ToggleFavoriteCustomButton
import tachiyomi.domain.custombuttons.interactor.UpdateCustomButton
import tachiyomi.domain.custombuttons.model.CustomButton
import tachiyomi.domain.custombuttons.model.CustomButtonUpdate
import tachiyomi.i18n.MR

class PlayerSettingsCustomButtonScreenModel(
    private val getCustomButtons: GetCustomButtons = appGraph.getCustomButtons,
    private val createCustomButton: CreateCustomButton = appGraph.createCustomButton,
    private val deleteCustomButton: DeleteCustomButton = appGraph.deleteCustomButton,
    private val updateCustomButton: UpdateCustomButton = appGraph.updateCustomButton,
    private val reorderCustomButton: ReorderCustomButton = appGraph.reorderCustomButton,
    private val toggleFavoriteCustomButton: ToggleFavoriteCustomButton = appGraph.toggleFavoriteCustomButton,
) : StateViewModel<CustomButtonScreenState>(CustomButtonScreenState.Loading) {

    private val _events: Channel<CustomButtonEvent> = Channel(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            getCustomButtons.subscribeAll()
                .collectLatest { customButtons ->
                    mutableState.update {
                        CustomButtonScreenState.Success(
                            customButtons = customButtons.toImmutableList(),
                        )
                    }
                }
        }
    }

    fun createCustomButton(name: String, content: String, longPressContent: String, onStartup: String) {
        viewModelScope.launch {
            when (createCustomButton.await(name, content, longPressContent, onStartup)) {
                is CreateCustomButton.Result.InternalError -> _events.send(
                    CustomButtonEvent.InternalError,
                )
                else -> {}
            }
        }
    }

    fun togglePrimaryButton(customButton: CustomButton) {
        viewModelScope.launch {
            when (toggleFavoriteCustomButton.await(customButton)) {
                is ToggleFavoriteCustomButton.Result.InternalError -> _events.send(
                    CustomButtonEvent.InternalError,
                )
                else -> {}
            }
        }
    }

    fun editCustomButton(customButtonUpdate: CustomButtonUpdate) {
        viewModelScope.launch {
            when (updateCustomButton.await(customButtonUpdate)) {
                is UpdateCustomButton.Result.InternalError -> _events.send(
                    CustomButtonEvent.InternalError,
                )
                else -> {}
            }
        }
    }

    fun deleteCustomButton(customButton: CustomButton) {
        viewModelScope.launch {
            when (deleteCustomButton.await(customButton.id)) {
                is DeleteCustomButton.Result.InternalError -> _events.send(
                    CustomButtonEvent.InternalError,
                )
                else -> {}
            }
        }
    }

    fun changeOrder(customButton: CustomButton, newIndex: Int) {
        viewModelScope.launch {
            when (reorderCustomButton.changeOrder(customButton, newIndex)) {
                is ReorderCustomButton.Result.InternalError -> _events.send(
                    CustomButtonEvent.InternalError,
                )
                else -> {}
            }
        }
    }

    fun showDialog(dialog: CustomButtonDialog) {
        mutableState.update {
            when (it) {
                CustomButtonScreenState.Loading -> it
                is CustomButtonScreenState.Success -> it.copy(dialog = dialog)
            }
        }
    }

    fun dismissDialog() {
        mutableState.update {
            when (it) {
                CustomButtonScreenState.Loading -> it
                is CustomButtonScreenState.Success -> it.copy(dialog = null)
            }
        }
    }
}

sealed interface CustomButtonDialog {
    data object Create : CustomButtonDialog
    data class Edit(val customButton: CustomButton) : CustomButtonDialog
    data class Delete(val customButton: CustomButton) : CustomButtonDialog
}

sealed interface CustomButtonEvent {
    sealed class LocalizedMessage(val stringRes: StringResource) : CustomButtonEvent
    data object InternalError : LocalizedMessage(MR.strings.internal_error)
}

sealed interface CustomButtonScreenState {
    @Immutable
    data object Loading : CustomButtonScreenState

    @Immutable
    data class Success(
        val customButtons: ImmutableList<CustomButton>,
        val dialog: CustomButtonDialog? = null,
    ) : CustomButtonScreenState {
        val isEmpty: Boolean
            get() = customButtons.isEmpty()
    }
}

sealed interface CustomButtonFetchState {
    @Immutable
    data object Loading : CustomButtonFetchState

    @Immutable
    data class Success(val customButtons: ImmutableList<CustomButton>) : CustomButtonFetchState

    @Immutable
    data class Error(val errorMessage: String) : CustomButtonFetchState
}

fun CustomButtonFetchState.getButtons(): ImmutableList<CustomButton> {
    return when (this) {
        is CustomButtonFetchState.Success -> this.customButtons
        else -> emptyList<CustomButton>().toImmutableList()
    }
}
