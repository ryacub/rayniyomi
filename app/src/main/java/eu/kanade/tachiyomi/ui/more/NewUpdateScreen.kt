package eu.kanade.tachiyomi.ui.more

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.update.UpdatePromptGatekeeper
import eu.kanade.presentation.more.NewUpdateScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.updater.AppUpdateDownloadJob
import eu.kanade.tachiyomi.data.updater.AppUpdatePermissionPolicy
import eu.kanade.tachiyomi.util.system.canPostNotifications
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.workManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NewUpdateScreen(
    private val versionName: String,
    private val changelogInfo: String,
    private val releaseLink: String,
    private val downloadLink: String,
    private val releaseDateEpochMillis: Long? = null,
) : Screen() {

    private var gatekeeperOverride: UpdatePromptGatekeeper? = null

    @VisibleForTesting
    internal var gatekeeper: UpdatePromptGatekeeper
        get() = gatekeeperOverride ?: Injekt.get()
        set(value) {
            gatekeeperOverride = value
        }

    @VisibleForTesting
    internal fun buildOnSkipVersion(navigator: Navigator): () -> Unit = {
        gatekeeper.skipVersion(versionName)
        navigator.pop()
    }

    @VisibleForTesting
    internal fun buildOnRejectUpdate(navigator: Navigator): () -> Unit = navigator::pop

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val workInfos by context.workManager
            .getWorkInfosForUniqueWorkFlow(AppUpdateDownloadJob.TAG)
            .collectAsStateWithLifecycle(initialValue = emptyList())
        var installStateRefreshNonce by remember { mutableStateOf(0) }
        val hasInstallCandidate = remember(workInfos, installStateRefreshNonce) {
            AppUpdateDownloadJob.hasValidDownloadedUpdate(
                context = context,
                expectedVersion = versionName,
            )
        }
        var showPermissionDeniedDialog by remember { mutableStateOf(false) }
        var pendingPermissionRequest by remember { mutableStateOf(false) }
        val permissionRequester = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            val mode = AppUpdatePermissionPolicy.startModeAfterPermissionResult(granted)
            when (mode) {
                AppUpdatePermissionPolicy.StartMode.START_WITH_NOTIFICATIONS -> {
                    AppUpdateDownloadJob.start(
                        context = context,
                        url = downloadLink,
                        title = versionName,
                        expectedVersion = versionName,
                    )
                    navigator.pop()
                }
                AppUpdatePermissionPolicy.StartMode.START_WITH_IN_APP_FALLBACK -> {
                    AppUpdateDownloadJob.start(
                        context = context,
                        url = downloadLink,
                        title = versionName,
                        expectedVersion = versionName,
                    )
                    showPermissionDeniedDialog = true
                }
                AppUpdatePermissionPolicy.StartMode.REQUEST_NOTIFICATIONS_PERMISSION -> Unit
            }
            pendingPermissionRequest = false
        }
        val changelogInfoNoChecksum = remember {
            changelogInfo.replace("""---(\R|.)*Checksums(\R|.)*""".toRegex(), "")
        }

        NewUpdateScreen(
            versionName = versionName,
            changelogInfo = changelogInfoNoChecksum,
            releaseDateEpochMillis = releaseDateEpochMillis,
            isInstallAction = hasInstallCandidate,
            onOpenInBrowser = { context.openInBrowser(releaseLink) },
            onRejectUpdate = buildOnRejectUpdate(navigator),
            onAcceptUpdate = {
                if (hasInstallCandidate) {
                    val installed = AppUpdateDownloadJob.installDownloadedUpdate(
                        context = context,
                        expectedVersion = versionName,
                    )
                    if (installed) {
                        navigator.pop()
                    } else {
                        installStateRefreshNonce++
                        context.toast(MR.strings.update_check_notification_download_error)
                    }
                    return@NewUpdateScreen
                }

                when (
                    AppUpdatePermissionPolicy.initialStartMode(
                        Build.VERSION.SDK_INT,
                        context.canPostNotifications(),
                    )
                ) {
                    AppUpdatePermissionPolicy.StartMode.START_WITH_NOTIFICATIONS -> {
                        AppUpdateDownloadJob.start(
                            context = context,
                            url = downloadLink,
                            title = versionName,
                            expectedVersion = versionName,
                        )
                        navigator.pop()
                    }
                    AppUpdatePermissionPolicy.StartMode.REQUEST_NOTIFICATIONS_PERMISSION -> {
                        pendingPermissionRequest = true
                        permissionRequester.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    AppUpdatePermissionPolicy.StartMode.START_WITH_IN_APP_FALLBACK -> {
                        AppUpdateDownloadJob.start(
                            context = context,
                            url = downloadLink,
                            title = versionName,
                            expectedVersion = versionName,
                        )
                        showPermissionDeniedDialog = true
                    }
                }
            },
            onSkipVersion = buildOnSkipVersion(navigator),
        )

        if (showPermissionDeniedDialog && !pendingPermissionRequest) {
            AlertDialog(
                onDismissRequest = { showPermissionDeniedDialog = false },
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
                            showPermissionDeniedDialog = false
                        },
                    ) {
                        Text(text = stringResource(MR.strings.pref_manage_notifications))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPermissionDeniedDialog = false }) {
                        Text(text = stringResource(MR.strings.action_not_now))
                    }
                },
            )
        }
    }
}
