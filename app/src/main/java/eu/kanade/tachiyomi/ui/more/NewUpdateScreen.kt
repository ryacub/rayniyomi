package eu.kanade.tachiyomi.ui.more

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.update.UpdatePromptGatekeeper
import eu.kanade.presentation.more.NewUpdateScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.updater.AppUpdateDownloadJob
import eu.kanade.tachiyomi.util.system.openInBrowser
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
        val changelogInfoNoChecksum = remember {
            changelogInfo.replace("""---(\R|.)*Checksums(\R|.)*""".toRegex(), "")
        }

        NewUpdateScreen(
            versionName = versionName,
            changelogInfo = changelogInfoNoChecksum,
            releaseDateEpochMillis = releaseDateEpochMillis,
            onOpenInBrowser = { context.openInBrowser(releaseLink) },
            onRejectUpdate = buildOnRejectUpdate(navigator),
            onAcceptUpdate = {
                AppUpdateDownloadJob.start(
                    context = context,
                    url = downloadLink,
                    title = versionName,
                )
                navigator.pop()
            },
            onSkipVersion = buildOnSkipVersion(navigator),
        )
    }
}
