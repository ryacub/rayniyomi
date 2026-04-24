package eu.kanade.tachiyomi.ui.more

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.update.UpdatePromptGatekeeper
import eu.kanade.presentation.more.AppUpdatePromptDialog
import eu.kanade.tachiyomi.data.updater.AppUpdateChecker
import eu.kanade.tachiyomi.data.updater.AppUpdateDownloadJob
import eu.kanade.tachiyomi.data.updater.AppUpdatePermissionPolicy
import eu.kanade.tachiyomi.util.system.canPostNotifications
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
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun AppUpdatePromptDialogHost(
    stateHolder: AppUpdatePromptStateHolder,
) {
    val state = stateHolder.state.collectAsStateWithLifecycle().value

    val context = LocalContext.current
    val navigator = LocalNavigator.currentOrThrow
    var pendingPermissionRelease by remember { mutableStateOf<Release?>(null) }
    val permissionRequester = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val pendingRelease = pendingPermissionRelease ?: return@rememberLauncherForActivityResult
        pendingPermissionRelease = null

        val mode = AppUpdatePermissionPolicy.startModeAfterPermissionResult(granted)
        stateHolder.onUpdateNow(
            showFallbackPermissionMessage = mode == AppUpdatePermissionPolicy.StartMode.START_WITH_IN_APP_FALLBACK,
        )
        if (mode == AppUpdatePermissionPolicy.StartMode.START_WITH_IN_APP_FALLBACK) {
            navigator.push(
                NewUpdateScreen(
                    versionName = pendingRelease.version,
                    changelogInfo = pendingRelease.info,
                    releaseLink = pendingRelease.releaseLink,
                    downloadLink = pendingRelease.downloadLink,
                    releaseDateEpochMillis = pendingRelease.publishedAt,
                ),
            )
        }
    }

    state.pendingRelease?.let { release ->
        AppUpdatePromptDialog(
            versionName = release.version,
            onUpdateNow = {
                when (
                    AppUpdatePermissionPolicy.initialStartMode(
                        Build.VERSION.SDK_INT,
                        context.canPostNotifications(),
                    )
                ) {
                    AppUpdatePermissionPolicy.StartMode.START_WITH_NOTIFICATIONS -> {
                        stateHolder.onUpdateNow(showFallbackPermissionMessage = false)
                    }
                    AppUpdatePermissionPolicy.StartMode.REQUEST_NOTIFICATIONS_PERMISSION -> {
                        pendingPermissionRelease = release
                        permissionRequester.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    AppUpdatePermissionPolicy.StartMode.START_WITH_IN_APP_FALLBACK -> {
                        stateHolder.onUpdateNow(showFallbackPermissionMessage = true)
                    }
                }
            },
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

    if (state.showNotificationPermissionDeniedMessage) {
        AlertDialog(
            onDismissRequest = stateHolder::dismissNotificationPermissionDeniedMessage,
            title = { Text(text = stringResource(MR.strings.onboarding_permission_notifications)) },
            text = { Text(text = stringResource(MR.strings.update_check_notifications_denied_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            },
                        )
                        stateHolder.dismissNotificationPermissionDeniedMessage()
                    },
                ) {
                    Text(text = stringResource(MR.strings.pref_manage_notifications))
                }
            },
            dismissButton = {
                TextButton(onClick = stateHolder::dismissNotificationPermissionDeniedMessage) {
                    Text(text = stringResource(MR.strings.action_not_now))
                }
            },
        )
    }
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

    fun onUpdateNow(showFallbackPermissionMessage: Boolean) {
        val release = state.value.pendingRelease ?: return
        AppUpdateDownloadJob.start(
            context = appContext,
            url = release.downloadLink,
            title = release.version,
            expectedVersion = release.version,
        )
        if (showFallbackPermissionMessage) {
            mutableState.update { it.copy(showNotificationPermissionDeniedMessage = true) }
        }
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

    fun dismissNotificationPermissionDeniedMessage() {
        mutableState.update { it.copy(showNotificationPermissionDeniedMessage = false) }
    }

    data class State(
        val pendingRelease: Release? = null,
        val isChecking: Boolean = false,
        val showNotificationPermissionDeniedMessage: Boolean = false,
    )
}
