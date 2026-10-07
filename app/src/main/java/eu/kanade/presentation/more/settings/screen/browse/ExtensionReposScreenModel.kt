package eu.kanade.presentation.more.settings.screen.browse

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.presentation.util.StateViewModel
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import logcat.logcat
import mihon.domain.extensionrepo.model.ExtensionRepo
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.i18n.MR

/**
 * Consolidated screen model for extension repo management.
 * Supports both Anime and Manga repos via dependency injection.
 */
@AssistedInject
class ExtensionReposScreenModel(
    @Assisted private val deps: Dependencies,
) : StateViewModel<RepoScreenState>(RepoScreenState.Loading) {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(@Assisted deps: Dependencies): ExtensionReposScreenModel
    }

    private val _events: Channel<RepoEvent> = Channel(Channel.BUFFERED) // 64-event buffer, sufficient for UI events
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launchIO {
            deps.subscribeAll()
                .collectLatest { repos ->
                    mutableState.update {
                        RepoScreenState.Success(
                            repos = repos.toImmutableSet(),
                        )
                    }
                }
        }
    }

    /**
     * Creates and adds a new repo to the database.
     *
     * @param baseUrl The baseUrl of the repo to create.
     */
    fun createRepo(baseUrl: String) {
        viewModelScope.launchIO {
            when (val result = deps.createRepo(baseUrl)) {
                CreateResult.InvalidUrl -> _events.send(RepoEvent.InvalidUrl)
                CreateResult.RepoAlreadyExists -> _events.send(RepoEvent.RepoAlreadyExists)
                is CreateResult.DuplicateFingerprint -> {
                    showDialog(RepoDialog.Conflict(result.oldRepo, result.newRepo))
                }
                CreateResult.Success -> { /* Handled by state update in subscribeAll */ }
                CreateResult.Error -> {
                    logcat(LogPriority.ERROR) { "Failed to create extension repo: $baseUrl" }
                }
            }
        }
    }

    /**
     * Inserts a repo to the database, replacing a matching repo with the same signing key fingerprint.
     *
     * @param newRepo The repo to insert
     */
    fun replaceRepo(newRepo: ExtensionRepo) {
        viewModelScope.launchIO {
            deps.replaceRepo(newRepo)
        }
    }

    /**
     * Refreshes information for each repository.
     */
    fun refreshRepos() {
        val status = state.value

        if (status is RepoScreenState.Success) {
            viewModelScope.launchIO {
                deps.updateAll()
            }
        }
    }

    /**
     * Deletes the given repo from the database
     */
    fun deleteRepo(baseUrl: String) {
        viewModelScope.launchIO {
            deps.deleteRepo(baseUrl)
        }
    }

    fun showDialog(dialog: RepoDialog) {
        mutableState.update {
            when (it) {
                RepoScreenState.Loading -> it
                is RepoScreenState.Success -> it.copy(dialog = dialog)
            }
        }
    }

    fun dismissDialog() {
        mutableState.update {
            when (it) {
                RepoScreenState.Loading -> it
                is RepoScreenState.Success -> it.copy(dialog = null)
            }
        }
    }

    /**
     * Dependency interface for extension repo operations.
     * Allows injection of either Anime or Manga implementations.
     */
    interface Dependencies {
        fun subscribeAll(): Flow<List<ExtensionRepo>>
        suspend fun createRepo(baseUrl: String): CreateResult
        suspend fun replaceRepo(newRepo: ExtensionRepo)
        suspend fun updateAll()
        suspend fun deleteRepo(baseUrl: String)
    }

    sealed interface CreateResult {
        data class DuplicateFingerprint(val oldRepo: ExtensionRepo, val newRepo: ExtensionRepo) : CreateResult
        data object InvalidUrl : CreateResult
        data object RepoAlreadyExists : CreateResult
        data object Success : CreateResult
        data object Error : CreateResult
    }
}

sealed class RepoEvent {
    sealed class LocalizedMessage(val stringRes: StringResource) : RepoEvent()
    data object InvalidUrl : LocalizedMessage(MR.strings.invalid_repo_name)
    data object RepoAlreadyExists : LocalizedMessage(MR.strings.error_repo_exists)
}

sealed class RepoDialog {
    data object Create : RepoDialog()
    data class Delete(val repo: String) : RepoDialog()
    data class Conflict(val oldRepo: ExtensionRepo, val newRepo: ExtensionRepo) : RepoDialog()
    data class Confirm(val url: String) : RepoDialog()
}

sealed class RepoScreenState {

    @Immutable
    data object Loading : RepoScreenState()

    @Immutable
    data class Success(
        val repos: ImmutableSet<ExtensionRepo>,
        val oldRepos: ImmutableSet<String>? = null,
        val dialog: RepoDialog? = null,
    ) : RepoScreenState() {

        val isEmpty: Boolean
            get() = repos.isEmpty()
    }
}
