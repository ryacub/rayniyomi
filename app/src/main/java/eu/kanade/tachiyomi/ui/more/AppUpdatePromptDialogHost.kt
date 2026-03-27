package eu.kanade.tachiyomi.ui.more

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.update.UpdatePromptGatekeeper
import eu.kanade.presentation.more.AppUpdatePromptDialog
import eu.kanade.tachiyomi.data.updater.AppUpdateChecker
import eu.kanade.tachiyomi.data.updater.AppUpdateDownloadJob
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.domain.release.model.Release
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun AppUpdatePromptDialogHost(
    stateHolder: AppUpdatePromptStateHolder,
) {
    val state = stateHolder.state.collectAsStateWithLifecycle().value
    val release = state.pendingRelease ?: return

    val navigator = LocalNavigator.currentOrThrow

    AppUpdatePromptDialog(
        versionName = release.version,
        onUpdateNow = stateHolder::onUpdateNow,
        onLater = stateHolder::onLater,
        onSkipVersion = stateHolder::onSkipVersion,
        onViewDetails = {
            navigator.push(
                NewUpdateScreen(
                    versionName = release.version,
                    changelogInfo = release.info,
                    releaseLink = release.releaseLink,
                    downloadLink = release.downloadLink,
                    releaseDateEpochMillis = release.publishedAt,
                ),
            )
            stateHolder.onLater()
        },
    )
}

@Composable
fun rememberAppUpdatePromptStateHolder(): AppUpdatePromptStateHolder {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(context.applicationContext, scope) {
        AppUpdatePromptStateHolder(
            context = context.applicationContext,
            scope = scope,
        )
    }
}

class AppUpdatePromptStateHolder(
    context: Context,
    private val scope: CoroutineScope,
) {

    private val updatePromptGatekeeper = Injekt.get<UpdatePromptGatekeeper>()
    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState.asStateFlow()
    private val appContext = context.applicationContext

    fun checkForUpdates(forceCheck: Boolean, showResultToasts: Boolean) {
        if (state.value.isChecking) return

        mutableState.update { it.copy(isChecking = true) }
        scope.launch {
            try {
                when (
                    val result = AppUpdateChecker().checkForUpdate(
                        context = appContext,
                        forceCheck = forceCheck,
                    )
                ) {
                    is GetApplicationRelease.Result.NewUpdate -> {
                        mutableState.update { it.copy(pendingRelease = result.release) }
                    }
                    is GetApplicationRelease.Result.NoNewUpdate -> {
                        if (showResultToasts) {
                            appContext.toast(MR.strings.update_check_no_new_updates)
                        }
                    }
                    is GetApplicationRelease.Result.OsTooOld -> {
                        if (showResultToasts) {
                            appContext.toast(MR.strings.update_check_eol)
                        }
                    }
                    else -> Unit
                }
            } catch (e: Exception) {
                if (showResultToasts) {
                    appContext.toast(e.message)
                }
                logcat(LogPriority.ERROR, e)
            } finally {
                mutableState.update { it.copy(isChecking = false) }
            }
        }
    }

    fun onUpdateNow() {
        val release = state.value.pendingRelease ?: return
        AppUpdateDownloadJob.start(
            context = appContext,
            url = release.downloadLink,
            title = release.version,
        )
        onLater()
    }

    fun onSkipVersion() {
        state.value.pendingRelease?.let { release ->
            updatePromptGatekeeper.skipVersion(release.version)
        }
        onLater()
    }

    fun onLater() {
        mutableState.update { it.copy(pendingRelease = null) }
    }

    data class State(
        val pendingRelease: Release? = null,
        val isChecking: Boolean = false,
    )
}
