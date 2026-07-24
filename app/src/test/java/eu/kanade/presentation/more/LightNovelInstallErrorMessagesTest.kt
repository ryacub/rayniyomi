package eu.kanade.presentation.more

import eu.kanade.tachiyomi.feature.novel.LightNovelPluginManager.InstallErrorCode
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.i18n.aniyomi.AYMR

class LightNovelInstallErrorMessagesTest {

    // The message each code must resolve to. Kept explicit (not derived from the mapper) so a
    // wrong or duplicated mapping is caught rather than mirrored.
    private val expected = mapOf(
        InstallErrorCode.INSTALL_DISABLED to
            AYMR.strings.light_novel_plugin_error_install_disabled,
        InstallErrorCode.MANIFEST_FETCH_FAILED to
            AYMR.strings.light_novel_plugin_error_manifest_fetch_failed,
        InstallErrorCode.MANIFEST_PACKAGE_MISMATCH to
            AYMR.strings.light_novel_plugin_error_manifest_package_mismatch,
        InstallErrorCode.MANIFEST_API_MISMATCH to
            AYMR.strings.light_novel_plugin_error_manifest_api_mismatch,
        InstallErrorCode.MANIFEST_HOST_TOO_OLD to
            AYMR.strings.light_novel_plugin_error_manifest_host_too_old,
        InstallErrorCode.MANIFEST_HOST_TOO_NEW to
            AYMR.strings.light_novel_plugin_error_manifest_host_too_new,
        InstallErrorCode.MANIFEST_PLUGIN_TOO_OLD to
            AYMR.strings.light_novel_plugin_error_manifest_plugin_too_old,
        InstallErrorCode.DOWNLOAD_FAILED to
            AYMR.strings.light_novel_plugin_error_download_failed,
        InstallErrorCode.INVALID_PLUGIN_APK to
            AYMR.strings.light_novel_plugin_error_invalid_apk,
        InstallErrorCode.ARCHIVE_PACKAGE_MISMATCH to
            AYMR.strings.light_novel_plugin_error_archive_package_mismatch,
        InstallErrorCode.INSTALL_LAUNCH_FAILED to
            AYMR.strings.light_novel_plugin_error_install_launch_failed,
    )

    @Test
    fun `every install error code maps to its dedicated string resource`() {
        // Also guards the mapper's `when` stays exhaustive: a new code without an entry here fails.
        InstallErrorCode.entries.forEach { code ->
            lightNovelInstallErrorMessageRes(code) shouldBe expected.getValue(code)
        }
    }

    @Test
    fun `no two install error codes collapse to the same message`() {
        val resources = InstallErrorCode.entries.map { lightNovelInstallErrorMessageRes(it) }
        resources.toSet().size shouldBe InstallErrorCode.entries.size
    }
}
