package eu.kanade.tachiyomi.ui.more

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.update.UpdatePromptGatekeeper
import eu.kanade.presentation.more.AppUpdatePromptDialog
import eu.kanade.tachiyomi.data.updater.AppUpdateDownloadJob
import tachiyomi.domain.release.model.Release
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun AppUpdatePromptDialogHost(
    release: Release,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val navigator = LocalNavigator.currentOrThrow
    val updatePromptGatekeeper = remember { Injekt.get<UpdatePromptGatekeeper>() }

    AppUpdatePromptDialog(
        versionName = release.version,
        onUpdateNow = {
            AppUpdateDownloadJob.start(
                context = context,
                url = release.downloadLink,
                title = release.version,
            )
            onDismiss()
        },
        onLater = onDismiss,
        onSkipVersion = {
            updatePromptGatekeeper.skipVersion(release.version)
            onDismiss()
        },
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
            onDismiss()
        },
    )
}
