package eu.kanade.presentation.more

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.feature.novel.LightNovelPluginManager.InstallErrorCode
import tachiyomi.i18n.aniyomi.AYMR

/**
 * Maps a light novel plugin [InstallErrorCode] to a concise, user-facing status message.
 *
 * Shared by the More screen (Light Novels row) and the light novel settings screen so a single
 * failure produces the same wording everywhere. The `when` is exhaustive with no `else` branch:
 * a newly added error code fails to compile until it is given an explicit, distinct message
 * instead of silently collapsing into a generic one.
 */
fun lightNovelInstallErrorMessageRes(code: InstallErrorCode): StringResource = when (code) {
    InstallErrorCode.INSTALL_DISABLED ->
        AYMR.strings.light_novel_plugin_error_install_disabled
    InstallErrorCode.MANIFEST_FETCH_FAILED ->
        AYMR.strings.light_novel_plugin_error_manifest_fetch_failed
    InstallErrorCode.MANIFEST_PACKAGE_MISMATCH ->
        AYMR.strings.light_novel_plugin_error_manifest_package_mismatch
    InstallErrorCode.MANIFEST_API_MISMATCH ->
        AYMR.strings.light_novel_plugin_error_manifest_api_mismatch
    InstallErrorCode.MANIFEST_HOST_TOO_OLD ->
        AYMR.strings.light_novel_plugin_error_manifest_host_too_old
    InstallErrorCode.MANIFEST_HOST_TOO_NEW ->
        AYMR.strings.light_novel_plugin_error_manifest_host_too_new
    InstallErrorCode.MANIFEST_PLUGIN_TOO_OLD ->
        AYMR.strings.light_novel_plugin_error_manifest_plugin_too_old
    InstallErrorCode.DOWNLOAD_FAILED ->
        AYMR.strings.light_novel_plugin_error_download_failed
    InstallErrorCode.INVALID_PLUGIN_APK ->
        AYMR.strings.light_novel_plugin_error_invalid_apk
    InstallErrorCode.ARCHIVE_PACKAGE_MISMATCH ->
        AYMR.strings.light_novel_plugin_error_archive_package_mismatch
    InstallErrorCode.INSTALL_LAUNCH_FAILED ->
        AYMR.strings.light_novel_plugin_error_install_launch_failed
}
