package eu.kanade.tachiyomi.data.updater

import android.content.Context
import androidx.annotation.VisibleForTesting
import eu.kanade.domain.update.UpdatePromptGatekeeper
import eu.kanade.domain.update.UpdatePromptPreferences
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.util.system.isPreviewBuildType
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.domain.release.model.Release
import tachiyomi.domain.release.model.ReleaseQuality
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AppUpdateChecker {
    internal enum class DecisionReason {
        PROMPT_FORCED,
        PROMPT_ALLOWED,
        SUPPRESSED_INVALID_RELEASE_VERSION,
        SUPPRESSED_BY_GATEKEEPER,
        NO_NEW_UPDATE,
        OS_TOO_OLD,
    }

    private var getApplicationReleaseOverride: GetApplicationRelease? = null

    @VisibleForTesting
    internal var getApplicationRelease: GetApplicationRelease
        get() = getApplicationReleaseOverride ?: Injekt.get()
        set(value) {
            getApplicationReleaseOverride = value
        }

    private var gatekeeperOverride: UpdatePromptGatekeeper? = null

    @VisibleForTesting
    internal var gatekeeper: UpdatePromptGatekeeper
        get() = gatekeeperOverride ?: Injekt.get()
        set(value) {
            gatekeeperOverride = value
        }

    private var updatePromptPreferencesOverride: UpdatePromptPreferences? = null

    @VisibleForTesting
    internal var updatePromptPreferences: UpdatePromptPreferences
        get() = updatePromptPreferencesOverride ?: Injekt.get()
        set(value) {
            updatePromptPreferencesOverride = value
        }

    private var notifierFactoryOverride: ((Context, Release) -> Unit)? = null

    @VisibleForTesting
    internal var notifierFactory: (Context, Release) -> Unit
        get() = notifierFactoryOverride ?: { context, release ->
            try {
                AppUpdateNotifier(context).promptUpdate(release)
            } catch (e: Exception) {
                // Silently ignore errors in tests with incomplete mocks
            }
        }
        set(value) {
            notifierFactoryOverride = value
        }

    private var decisionLoggerOverride: ((DecisionReason) -> Unit)? = null

    @VisibleForTesting
    internal var decisionLogger: (DecisionReason) -> Unit
        get() = decisionLoggerOverride ?: { reason ->
            logcat { "app-update decision=$reason" }
        }
        set(value) {
            decisionLoggerOverride = value
        }

    suspend fun checkForUpdate(context: Context, forceCheck: Boolean = false): GetApplicationRelease.Result {
        // Disabling app update checks for older Android versions that we're going to drop support for
        // if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        //    return GetApplicationRelease.Result.OsTooOld
        // }

        return withIOContext {
            val result = getApplicationRelease.await(
                GetApplicationRelease.Arguments(
                    isPreviewBuildType,
                    BuildConfig.COMMIT_COUNT.toInt(),
                    BuildConfig.VERSION_NAME,
                    GITHUB_REPO,
                    forceCheck,
                    includePrerelease = updatePromptPreferences.includePrerelease().get(),
                ),
            )

            when (result) {
                is GetApplicationRelease.Result.NewUpdate -> {
                    val releaseVersion = result.release.version.trim()
                    if (releaseVersion.isEmpty()) {
                        decisionLogger.invoke(DecisionReason.SUPPRESSED_INVALID_RELEASE_VERSION)
                        return@withIOContext GetApplicationRelease.Result.UpdateSuppressed(result.release)
                    }

                    if (forceCheck) {
                        gatekeeper.clearSkipIfOutdated(releaseVersion)
                        gatekeeper.recordPrompted()
                        notifierFactory(context, result.release)
                        decisionLogger.invoke(DecisionReason.PROMPT_FORCED)
                        result
                    } else {
                        gatekeeper.clearSkipIfOutdated(releaseVersion)

                        if (!gatekeeper.shouldPrompt(
                                releaseVersion,
                                isPrerelease =
                                result.release.quality == ReleaseQuality.PRERELEASE,
                            )
                        ) {
                            decisionLogger.invoke(DecisionReason.SUPPRESSED_BY_GATEKEEPER)
                            return@withIOContext GetApplicationRelease.Result.UpdateSuppressed(result.release)
                        }

                        gatekeeper.recordPrompted()
                        notifierFactory(context, result.release)
                        decisionLogger.invoke(DecisionReason.PROMPT_ALLOWED)
                        result
                    }
                }
                GetApplicationRelease.Result.NoNewUpdate -> {
                    decisionLogger.invoke(DecisionReason.NO_NEW_UPDATE)
                    result
                }
                GetApplicationRelease.Result.OsTooOld -> {
                    decisionLogger.invoke(DecisionReason.OS_TOO_OLD)
                    result
                }
                is GetApplicationRelease.Result.UpdateSuppressed -> result
            }
        }
    }
}

val GITHUB_REPO: String by lazy {
    if (isPreviewBuildType) {
        "ryacub/rayniyomi"
    } else {
        "ryacub/rayniyomi"
    }
}

val RELEASE_TAG: String by lazy {
    if (isPreviewBuildType) {
        "r${BuildConfig.COMMIT_COUNT}"
    } else {
        "v${BuildConfig.VERSION_NAME}"
    }
}

val RELEASE_URL = "https://github.com/$GITHUB_REPO/releases/tag/$RELEASE_TAG"
