package eu.kanade.tachiyomi.feature.novel

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import eu.kanade.domain.novel.NovelFeaturePreferences
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.util.lang.Hash
import eu.kanade.tachiyomi.util.storage.getUriCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.security.MessageDigest

class LightNovelPluginManager(
    private val context: Context,
    private val network: NetworkHelper,
    private val json: Json,
) : LightNovelPluginReadiness {
    private val telemetry = PluginTelemetry()
    private val installMutex = Mutex()
    private val installScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlightInstallMutex = Mutex()
    private var inFlightInstall: Deferred<InstallResult>? = null
    init {
        cleanupOrphanedPluginApk()
    }

    data class PluginStatus(
        val installed: Boolean,
        val signedAndTrusted: Boolean,
        val compatible: Boolean,
        val installedVersionCode: Long?,
    )

    sealed interface InstallResult {
        data object AlreadyReady : InstallResult
        data object InstallLaunched : InstallResult
        data class Error(val code: InstallErrorCode) : InstallResult
    }

    enum class InstallErrorCode {
        INSTALL_DISABLED,
        MANIFEST_FETCH_FAILED,
        MANIFEST_PACKAGE_MISMATCH,
        MANIFEST_API_MISMATCH,
        MANIFEST_HOST_TOO_OLD,
        MANIFEST_HOST_TOO_NEW,
        DOWNLOAD_FAILED,
        INVALID_PLUGIN_APK,
        ARCHIVE_PACKAGE_MISMATCH,
        INSTALL_LAUNCH_FAILED,
    }

    override fun isPluginReady(): Boolean {
        val status = getPluginStatus()
        return status.installed && status.signedAndTrusted && status.compatible
    }

    fun isInstalled(): Boolean = getInstalledPackageInfo() != null

    fun getPluginStatus(): PluginStatus {
        val packageInfo = getInstalledPackageInfo()
        if (packageInfo == null) {
            return PluginStatus(
                installed = false,
                signedAndTrusted = false,
                compatible = false,
                installedVersionCode = null,
            )
        }

        return PluginStatus(
            installed = true,
            signedAndTrusted = verifyPinnedSignature(packageInfo),
            compatible = isCompatible(packageInfo),
            installedVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
        )
    }

    suspend fun ensurePluginReady(channel: String): InstallResult {
        val installDeferred = inFlightInstallMutex.withLock {
            val inFlight = inFlightInstall
            if (inFlight != null && inFlight.isActive) {
                inFlight
            } else {
                installScope.async {
                    ensurePluginReadyInternal(channel)
                }.also {
                    inFlightInstall = it
                }
            }
        }
        return installDeferred.await()
    }

    private suspend fun ensurePluginReadyInternal(channel: String): InstallResult {
        if (!isPluginInstallEnabled()) {
            return InstallResult.Error(InstallErrorCode.INSTALL_DISABLED)
        }

        return installMutex.withLock {
            if (isPluginReady()) return@withLock InstallResult.AlreadyReady

            // --- FETCH stage ---
            val manifest = fetchManifest(channel).getOrElse {
                telemetry.recordEvent(
                    stage = PluginStage.FETCH,
                    result = PluginResult.Failure(
                        reason = PluginFailureReason.NETWORK_TIMEOUT,
                        isFatal = false,
                    ),
                    channel = channel,
                    enabled = ::isPluginInstallEnabled,
                )
                return@withLock InstallResult.Error(InstallErrorCode.MANIFEST_FETCH_FAILED)
            }
            if (manifest.packageName != PLUGIN_PACKAGE_NAME) {
                telemetry.recordEvent(
                    stage = PluginStage.FETCH,
                    result = PluginResult.Failure(
                        reason = PluginFailureReason.CORRUPT_MANIFEST,
                        isFatal = true,
                    ),
                    channel = channel,
                    enabled = ::isPluginInstallEnabled,
                )
                return@withLock InstallResult.Error(InstallErrorCode.MANIFEST_PACKAGE_MISMATCH)
            }
            // Manifest fetched successfully — record FETCH success before VERIFY.
            telemetry.recordEvent(
                stage = PluginStage.FETCH,
                result = PluginResult.Success,
                channel = channel,
                enabled = ::isPluginInstallEnabled,
            )

            // --- VERIFY stage ---
            val manifestCompatibility = evaluateLightNovelPluginCompatibility(
                pluginApiVersion = manifest.pluginApiVersion,
                minHostVersion = manifest.minHostVersion,
                targetHostVersion = manifest.targetHostVersion,
                hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
                expectedPluginApiVersion = EXPECTED_PLUGIN_API_VERSION,
            )
            if (manifestCompatibility != LightNovelPluginCompatibilityResult.COMPATIBLE) {
                telemetry.recordEvent(
                    stage = PluginStage.VERIFY,
                    result = PluginResult.Failure(
                        reason = PluginFailureReason.VERSION_INCOMPATIBLE,
                        isFatal = true,
                    ),
                    channel = channel,
                    enabled = ::isPluginInstallEnabled,
                )
                return@withLock InstallResult.Error(
                    manifestCompatibility.toInstallErrorCode(),
                )
            }
            telemetry.recordEvent(
                stage = PluginStage.VERIFY,
                result = PluginResult.Success,
                channel = channel,
                enabled = ::isPluginInstallEnabled,
            )

            // --- INSTALL stage ---
            val apkFile = downloadPlugin(manifest).getOrElse {
                telemetry.recordEvent(
                    stage = PluginStage.INSTALL,
                    result = PluginResult.Failure(
                        reason = PluginFailureReason.NETWORK_TIMEOUT,
                        isFatal = false,
                    ),
                    channel = channel,
                    enabled = ::isPluginInstallEnabled,
                )
                return@withLock InstallResult.Error(InstallErrorCode.DOWNLOAD_FAILED)
            }

            val archivePackage = context.packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                ARCHIVE_PACKAGE_FLAGS,
            )
                ?: run {
                    telemetry.recordEvent(
                        stage = PluginStage.INSTALL,
                        result = PluginResult.Failure(
                            reason = PluginFailureReason.CORRUPT_APK,
                            isFatal = true,
                        ),
                        channel = channel,
                        enabled = ::isPluginInstallEnabled,
                    )
                    return@withLock InstallResult.Error(InstallErrorCode.INVALID_PLUGIN_APK)
                }

            if (archivePackage.packageName != PLUGIN_PACKAGE_NAME) {
                apkFile.delete()
                telemetry.recordEvent(
                    stage = PluginStage.INSTALL,
                    result = PluginResult.Failure(
                        reason = PluginFailureReason.SIGNATURE_MISMATCH,
                        isFatal = true,
                    ),
                    channel = channel,
                    enabled = ::isPluginInstallEnabled,
                )
                return@withLock InstallResult.Error(InstallErrorCode.ARCHIVE_PACKAGE_MISMATCH)
            }

            return@withLock try {
                launchInstall(apkFile)
                // Best-effort cleanup. Package installer may already have opened the stream.
                apkFile.delete()
                telemetry.recordEvent(
                    stage = PluginStage.INSTALL,
                    result = PluginResult.Success,
                    channel = channel,
                    enabled = ::isPluginInstallEnabled,
                )
                InstallResult.InstallLaunched
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e) { "Failed to launch plugin install" }
                apkFile.delete()
                telemetry.recordEvent(
                    stage = PluginStage.INSTALL,
                    result = PluginResult.Failure(
                        reason = PluginFailureReason.UNKNOWN,
                        isFatal = true,
                    ),
                    channel = channel,
                    enabled = ::isPluginInstallEnabled,
                )
                InstallResult.Error(InstallErrorCode.INSTALL_LAUNCH_FAILED)
            }
        }
    }

    fun uninstallPlugin() {
        cleanupOrphanedPluginApk()
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$PLUGIN_PACKAGE_NAME"))
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            logcat(LogPriority.ERROR, e) { "No activity found to uninstall light novel plugin" }
        }
    }

    private fun getManifestUrl(channel: String): String {
        return when (channel) {
            NovelFeaturePreferences.CHANNEL_BETA -> BETA_MANIFEST_URL
            else -> STABLE_MANIFEST_URL
        }
    }

    private fun verifyPinnedSignature(packageInfo: PackageInfo): Boolean {
        val signatures = getSignatures(packageInfo)
        return signatures.any { it in TRUSTED_PLUGIN_CERT_SHA256 }
    }

    private suspend fun fetchManifest(channel: String): Result<LightNovelPluginManifest> {
        return runCatching {
            val url = getManifestUrl(channel)
            val response = network.client.newCall(GET(url)).awaitSuccess()
            response.use {
                json.decodeFromString<LightNovelPluginManifest>(it.body.string())
            }
        }
    }

    private suspend fun downloadPlugin(manifest: LightNovelPluginManifest): Result<File> {
        return runCatching {
            withIOContext {
                val response = network.client.newCall(GET(manifest.apkUrl)).awaitSuccess()
                response.use {
                    val apkFile = File(context.cacheDir, PLUGIN_APK_FILE_NAME)
                    it.body.byteStream().use { input ->
                        apkFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    val sha256 = sha256File(apkFile)
                    if (!sha256.equals(manifest.apkSha256, ignoreCase = true)) {
                        apkFile.delete()
                        error("Plugin checksum validation failed")
                    }

                    apkFile
                }
            }
        }
    }

    private suspend fun launchInstall(apkFile: File) {
        withUIContext {
            val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE)
                .setDataAndType(apkFile.getUriCompat(context), APK_MIME)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(installIntent)
        }
    }

    private fun getInstalledPackageInfo(): PackageInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    PLUGIN_PACKAGE_NAME,
                    PackageManager.PackageInfoFlags.of(INSTALLED_PACKAGE_FLAGS.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(PLUGIN_PACKAGE_NAME, INSTALLED_PACKAGE_FLAGS)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun isCompatible(packageInfo: PackageInfo): Boolean {
        val metaData = packageInfo.applicationInfo?.metaData ?: return false
        val pluginApiVersion = metaData.getInt(META_PLUGIN_API_VERSION, -1)
        val minHostVersion = metaData.getLong(META_MIN_HOST_VERSION, Long.MAX_VALUE)
        val targetHostVersion = metaData.getLong(META_TARGET_HOST_VERSION, 0L)
            .takeIf { it > 0L }
        val compatibility = evaluateLightNovelPluginCompatibility(
            pluginApiVersion = pluginApiVersion,
            minHostVersion = minHostVersion,
            targetHostVersion = targetHostVersion,
            hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
            expectedPluginApiVersion = EXPECTED_PLUGIN_API_VERSION,
        )

        return compatibility == LightNovelPluginCompatibilityResult.COMPATIBLE
    }

    private fun LightNovelPluginCompatibilityResult.toInstallErrorCode(): InstallErrorCode {
        return when (this) {
            LightNovelPluginCompatibilityResult.API_MISMATCH -> InstallErrorCode.MANIFEST_API_MISMATCH
            LightNovelPluginCompatibilityResult.HOST_TOO_OLD -> InstallErrorCode.MANIFEST_HOST_TOO_OLD
            LightNovelPluginCompatibilityResult.HOST_TOO_NEW -> InstallErrorCode.MANIFEST_HOST_TOO_NEW
            else -> error("unreachable")
        }
    }

    private fun getSignatures(packageInfo: PackageInfo): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo ?: return emptyList()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures
        }
            ?.map { Hash.sha256(it.toByteArray()) }
            .orEmpty()
    }

    private fun sha256File(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        val hashBytes = digest.digest()
        return hashBytes.joinToString(separator = "") { "%02x".format(it) }
    }

    private fun cleanupOrphanedPluginApk() {
        File(context.cacheDir, PLUGIN_APK_FILE_NAME)
            .takeIf { it.exists() }
            ?.delete()
    }

    private fun isPluginInstallEnabled(): Boolean {
        return BuildConfig.DEBUG || ENABLE_PLUGIN_INSTALL_FOR_RELEASE
    }

    companion object {
        const val PLUGIN_PACKAGE_NAME = "xyz.rayniyomi.plugin.lightnovel"

        private const val PLUGIN_APK_FILE_NAME = "lightnovel-plugin.apk"
        private const val APK_MIME = "application/vnd.android.package-archive"

        private const val META_PLUGIN_API_VERSION = "rayniyomi.plugin.api_version"
        private const val META_MIN_HOST_VERSION = "rayniyomi.plugin.min_host_version"
        private const val META_TARGET_HOST_VERSION = "rayniyomi.plugin.target_host_version"

        private const val EXPECTED_PLUGIN_API_VERSION = 1
        private const val ENABLE_PLUGIN_INSTALL_FOR_RELEASE = false

        private const val STABLE_MANIFEST_URL =
            "https://github.com/ryacub/rayniyomi/releases/latest/download/lightnovel-plugin-manifest.json"
        private const val BETA_MANIFEST_URL =
            "https://github.com/ryacub/rayniyomi/releases/download/plugin-beta/lightnovel-plugin-manifest.json"

        // TODO(R236-B): Replace with real SHA-256 certificate fingerprints before enabling in release.
        // These placeholder values keep the gate fail-closed; plugin installs are blocked in release
        // builds by ENABLE_PLUGIN_INSTALL_FOR_RELEASE = false until real certs are pinned.
        private val TRUSTED_PLUGIN_CERT_SHA256 = setOf(
            "7b7f000000000000000000000000000000000000000000000000000000000000",
            "8c8f000000000000000000000000000000000000000000000000000000000000",
        )

        @Suppress("DEPRECATION")
        private val INSTALLED_PACKAGE_FLAGS = PackageManager.GET_META_DATA or
            PackageManager.GET_SIGNATURES or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)

        private const val ARCHIVE_PACKAGE_FLAGS = PackageManager.GET_META_DATA
    }
}
