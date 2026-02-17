package eu.kanade.tachiyomi.feature.novel

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.core.content.pm.PackageInfoCompat
import eu.kanade.domain.novel.NovelFeaturePreferences
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.util.lang.Hash
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.storage.saveTo
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
    private val packageInspector: PackageInspector = AndroidPackageInspector(context.packageManager),
) : LightNovelPluginReadiness {

    private val installMutex = Mutex()
    private var manifestFetcher: suspend (String) -> Result<LightNovelPluginManifest> = ::fetchManifestFromNetwork
    private var pluginDownloader: suspend (LightNovelPluginManifest) -> Result<File> = ::downloadPluginFromNetwork
    private var installLauncher: suspend (File) -> Result<Unit> = ::launchInstallIntent
    private var signerPinsProvider: () -> Set<String> = ::trustedSignerPinsFromBuildConfig

    init {
        cleanupOrphanedPluginApk()
    }

    @VisibleForTesting
    internal constructor(
        context: Context,
        network: NetworkHelper,
        json: Json,
        packageInspector: PackageInspector = AndroidPackageInspector(context.packageManager),
        testHooks: TestHooks,
    ) : this(context, network, json, packageInspector) {
        testHooks.manifestFetcher?.let { manifestFetcher = it }
        testHooks.pluginDownloader?.let { pluginDownloader = it }
        testHooks.installLauncher?.let { installLauncher = it }
        testHooks.signerPinsProvider?.let { signerPinsProvider = it }
    }

    enum class VersionState {
        MISSING,
        INSTALLED_CURRENT,
        INSTALLED_OUTDATED,
        INSTALLED_NEWER,
        INSTALLED_UNKNOWN,
    }

    enum class CompatibilityState {
        READY,
        MISSING_METADATA,
        API_MISMATCH,
        HOST_TOO_OLD,
        HOST_TOO_NEW,
    }

    data class PluginStatus(
        val installed: Boolean,
        val signedAndTrusted: Boolean,
        val compatibilityState: CompatibilityState,
        val installedVersionCode: Long?,
        val availableVersionCode: Long?,
        val versionState: VersionState,
    ) {
        val compatible: Boolean
            get() = compatibilityState == CompatibilityState.READY
    }

    enum class RejectionReason {
        INSTALL_DISABLED,
        MANIFEST_FETCH_FAILED,
        INVALID_MANIFEST,
        DOWNLOAD_FAILED,
        CHECKSUM_MISMATCH,
        INVALID_PLUGIN_APK,
        UNSIGNED_PLUGIN_APK,
        PACKAGE_NAME_MISMATCH,
        MISSING_SIGNER_PINS,
        SIGNER_MISMATCH,
        PLUGIN_API_MISMATCH,
        HOST_VERSION_TOO_OLD,
        HOST_VERSION_TOO_NEW,
        INSTALL_LAUNCH_FAILED,
    }

    sealed interface InstallResult {
        data object AlreadyReady : InstallResult
        data object InstallLaunched : InstallResult
        data class Rejected(val reason: RejectionReason) : InstallResult
    }

    interface PackageInspector {
        fun getInstalledPackageInfo(packageName: String): PackageInfo?
        fun getArchivePackageInfo(apkPath: String): PackageInfo?
        fun getSignatures(packageInfo: PackageInfo): List<String>
        fun getCompatibilityState(packageInfo: PackageInfo): CompatibilityState? = null
    }

    @VisibleForTesting
    internal data class TestHooks(
        val manifestFetcher: (suspend (String) -> Result<LightNovelPluginManifest>)? = null,
        val pluginDownloader: (suspend (LightNovelPluginManifest) -> Result<File>)? = null,
        val installLauncher: (suspend (File) -> Result<Unit>)? = null,
        val signerPinsProvider: (() -> Set<String>)? = null,
    )

    override fun isPluginReady(): Boolean {
        val status = getPluginStatus()
        return status.installed && status.signedAndTrusted && status.compatible
    }

    fun isInstalled(): Boolean = getInstalledPackageInfo() != null

    fun getPluginStatus(): PluginStatus {
        val packageInfo = getInstalledPackageInfo()
            ?: return PluginStatus(
                installed = false,
                signedAndTrusted = false,
                compatibilityState = CompatibilityState.MISSING_METADATA,
                installedVersionCode = null,
                availableVersionCode = null,
                versionState = VersionState.MISSING,
            )

        val installedVersion = PackageInfoCompat.getLongVersionCode(packageInfo)
        val compatibilityState = compatibilityStateFor(packageInfo)

        return PluginStatus(
            installed = true,
            signedAndTrusted = verifyPinnedSignature(packageInfo),
            compatibilityState = compatibilityState,
            installedVersionCode = installedVersion,
            availableVersionCode = null,
            versionState = VersionState.INSTALLED_UNKNOWN,
        )
    }

    suspend fun getPluginStatus(channel: String): PluginStatus {
        val baseStatus = getPluginStatus()
        val manifestVersion = fetchManifest(channel)
            .getOrNull()
            ?.takeIf { it.packageName == PLUGIN_PACKAGE_NAME }
            ?.versionCode

        val versionState = when {
            baseStatus.installedVersionCode == null -> VersionState.MISSING
            manifestVersion == null -> VersionState.INSTALLED_UNKNOWN
            baseStatus.installedVersionCode < manifestVersion -> VersionState.INSTALLED_OUTDATED
            baseStatus.installedVersionCode == manifestVersion -> VersionState.INSTALLED_CURRENT
            else -> VersionState.INSTALLED_NEWER
        }

        return baseStatus.copy(
            availableVersionCode = manifestVersion,
            versionState = versionState,
        )
    }

    suspend fun ensurePluginReady(channel: String): InstallResult {
        if (!isPluginInstallEnabled()) {
            return InstallResult.Rejected(RejectionReason.INSTALL_DISABLED)
        }

        return installMutex.withLock {
            if (isPluginReady()) return@withLock InstallResult.AlreadyReady

            val trustedSignerPins = trustedSignerPins()
            if (trustedSignerPins.isEmpty()) {
                logcat(LogPriority.ERROR) { "Light Novel plugin signer pins are missing" }
                return@withLock InstallResult.Rejected(RejectionReason.MISSING_SIGNER_PINS)
            }

            val manifest = fetchManifest(channel).getOrElse {
                logcat(LogPriority.ERROR, it) { "Light Novel manifest fetch failed" }
                return@withLock InstallResult.Rejected(RejectionReason.MANIFEST_FETCH_FAILED)
            }

            if (!manifestLooksValid(manifest)) {
                logcat(LogPriority.ERROR) { "Light Novel manifest is invalid" }
                return@withLock InstallResult.Rejected(RejectionReason.INVALID_MANIFEST)
            }

            val apkFile = downloadPlugin(manifest).getOrElse {
                logcat(LogPriority.ERROR, it) { "Light Novel plugin download failed" }
                return@withLock mapDownloadFailure(it)
            }

            try {
                val archivePackage = packageInspector.getArchivePackageInfo(apkFile.absolutePath)
                    ?: return@withLock rejectWithCleanup(
                        apkFile,
                        RejectionReason.INVALID_PLUGIN_APK,
                        "Archive package info not found",
                    )

                if (archivePackage.packageName != PLUGIN_PACKAGE_NAME ||
                    archivePackage.packageName != manifest.packageName
                ) {
                    return@withLock rejectWithCleanup(
                        apkFile,
                        RejectionReason.PACKAGE_NAME_MISMATCH,
                        "Package mismatch for archive=${archivePackage.packageName}, manifest=${manifest.packageName}",
                    )
                }

                val archiveSignatures = packageInspector.getSignatures(archivePackage)
                if (archiveSignatures.isEmpty()) {
                    return@withLock rejectWithCleanup(
                        apkFile,
                        RejectionReason.UNSIGNED_PLUGIN_APK,
                        "Archive APK has no signatures",
                    )
                }

                if (archiveSignatures.none { it in trustedSignerPins }) {
                    return@withLock rejectWithCleanup(
                        apkFile,
                        RejectionReason.SIGNER_MISMATCH,
                        "Archive signer does not match pinned certificate",
                    )
                }

                when (compatibilityStateFor(archivePackage)) {
                    CompatibilityState.READY -> Unit
                    CompatibilityState.MISSING_METADATA,
                    CompatibilityState.API_MISMATCH,
                    -> {
                        return@withLock rejectWithCleanup(
                            apkFile,
                            RejectionReason.PLUGIN_API_MISMATCH,
                            "Archive plugin API metadata mismatch",
                        )
                    }
                    CompatibilityState.HOST_TOO_OLD -> {
                        return@withLock rejectWithCleanup(
                            apkFile,
                            RejectionReason.HOST_VERSION_TOO_OLD,
                            "Archive requires newer host",
                        )
                    }
                    CompatibilityState.HOST_TOO_NEW -> {
                        return@withLock rejectWithCleanup(
                            apkFile,
                            RejectionReason.HOST_VERSION_TOO_NEW,
                            "Archive targets older host",
                        )
                    }
                }

                val installResult = launchInstall(apkFile)
                if (installResult.isFailure) {
                    logcat(LogPriority.ERROR, installResult.exceptionOrNull()) {
                        "Failed to launch Light Novel installer"
                    }
                    return@withLock rejectWithCleanup(
                        apkFile,
                        RejectionReason.INSTALL_LAUNCH_FAILED,
                        "Install launcher failed",
                    )
                }

                // Best-effort cleanup. Package installer may already hold an open stream.
                apkFile.delete()
                InstallResult.InstallLaunched
            } finally {
                if (apkFile.exists()) {
                    apkFile.delete()
                }
            }
        }
    }

    fun uninstallPlugin() {
        cleanupOrphanedPluginApk()
        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:$PLUGIN_PACKAGE_NAME"))
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun getManifestUrl(channel: String): String {
        return when (channel) {
            NovelFeaturePreferences.CHANNEL_BETA -> BETA_MANIFEST_URL
            else -> STABLE_MANIFEST_URL
        }
    }

    private fun mapDownloadFailure(error: Throwable): InstallResult.Rejected {
        val reason = if (error.message?.contains("checksum", ignoreCase = true) == true) {
            RejectionReason.CHECKSUM_MISMATCH
        } else {
            RejectionReason.DOWNLOAD_FAILED
        }
        return InstallResult.Rejected(reason)
    }

    private suspend fun fetchManifest(channel: String): Result<LightNovelPluginManifest> {
        return manifestFetcher(channel)
    }

    private suspend fun downloadPlugin(manifest: LightNovelPluginManifest): Result<File> {
        return pluginDownloader(manifest)
    }

    private suspend fun launchInstall(apkFile: File): Result<Unit> {
        return installLauncher(apkFile)
    }

    private fun trustedSignerPins(): Set<String> {
        return signerPinsProvider()
    }

    private fun rejectWithCleanup(apkFile: File, reason: RejectionReason, logMessage: String): InstallResult.Rejected {
        logcat(LogPriority.ERROR) { "Light Novel plugin rejected: $reason ($logMessage)" }
        apkFile.delete()
        return InstallResult.Rejected(reason)
    }

    private fun trustedSignerPinsFromBuildConfig(): Set<String> {
        return listOf(
            BuildConfig.LIGHT_NOVEL_PLUGIN_SIGNER_SHA256_PRIMARY,
            BuildConfig.LIGHT_NOVEL_PLUGIN_SIGNER_SHA256_SECONDARY,
            BuildConfig.LIGHT_NOVEL_PLUGIN_SIGNER_SHA256_TERTIARY,
        )
            .map { it.trim().lowercase() }
            .filter { it.length == SHA256_HEX_LENGTH && it.all(Char::isLetterOrDigit) }
            .toSet()
    }

    private fun verifyPinnedSignature(packageInfo: PackageInfo): Boolean {
        val trustedPins = trustedSignerPins()
        if (trustedPins.isEmpty()) return false
        val signatures = packageInspector.getSignatures(packageInfo)
        return signatures.any { it in trustedPins }
    }

    private fun manifestLooksValid(manifest: LightNovelPluginManifest): Boolean {
        if (manifest.packageName != PLUGIN_PACKAGE_NAME) return false
        if (manifest.apkUrl.isBlank()) return false
        if (manifest.apkSha256.length != SHA256_HEX_LENGTH) return false
        if (manifest.pluginApiVersion <= 0) return false
        if (manifest.minHostVersion <= 0) return false
        return true
    }

    private suspend fun fetchManifestFromNetwork(channel: String): Result<LightNovelPluginManifest> {
        return runCatching {
            val url = getManifestUrl(channel)
            val response = network.client.newCall(GET(url)).awaitSuccess()
            response.use {
                json.decodeFromString<LightNovelPluginManifest>(it.body.string())
            }
        }
    }

    private suspend fun downloadPluginFromNetwork(manifest: LightNovelPluginManifest): Result<File> {
        return runCatching {
            withIOContext {
                val response = network.client.newCall(GET(manifest.apkUrl)).awaitSuccess()
                response.use {
                    val apkFile = File(context.cacheDir, PLUGIN_APK_FILE_NAME)
                    it.body.source().saveTo(apkFile)

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

    private suspend fun launchInstallIntent(apkFile: File): Result<Unit> {
        return runCatching {
            withUIContext {
                val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE)
                    .setDataAndType(apkFile.getUriCompat(context), APK_MIME)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(installIntent)
            }
        }
    }

    private fun compatibilityStateFor(packageInfo: PackageInfo): CompatibilityState {
        packageInspector.getCompatibilityState(packageInfo)?.let { return it }

        val metaData = packageInfo.applicationInfo?.metaData ?: return CompatibilityState.MISSING_METADATA

        val pluginApiVersion = metaData.getInt(META_PLUGIN_API_VERSION, -1)
        val minHostVersion = metaData.getLong(META_MIN_HOST_VERSION, Long.MAX_VALUE)
        val targetHostVersion = metaData.getLong(META_TARGET_HOST_VERSION, Long.MIN_VALUE)
        val hostVersionCode = BuildConfig.VERSION_CODE.toLong()

        if (pluginApiVersion != EXPECTED_PLUGIN_API_VERSION) {
            return CompatibilityState.API_MISMATCH
        }
        if (hostVersionCode < minHostVersion) {
            return CompatibilityState.HOST_TOO_OLD
        }
        if (targetHostVersion != Long.MIN_VALUE && hostVersionCode > targetHostVersion) {
            return CompatibilityState.HOST_TOO_NEW
        }

        return CompatibilityState.READY
    }

    private fun getInstalledPackageInfo(): PackageInfo? {
        return packageInspector.getInstalledPackageInfo(PLUGIN_PACKAGE_NAME)
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

    private class AndroidPackageInspector(
        private val packageManager: PackageManager,
    ) : PackageInspector {

        override fun getInstalledPackageInfo(packageName: String): PackageInfo? {
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getPackageInfo(
                        packageName,
                        PackageManager.PackageInfoFlags.of(INSTALLED_PACKAGE_FLAGS.toLong()),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(packageName, INSTALLED_PACKAGE_FLAGS)
                }
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }

        override fun getArchivePackageInfo(apkPath: String): PackageInfo? {
            return packageManager.getPackageArchiveInfo(apkPath, ARCHIVE_PACKAGE_FLAGS)
        }

        override fun getSignatures(packageInfo: PackageInfo): List<String> {
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
    }

    companion object {
        const val PLUGIN_PACKAGE_NAME = "xyz.rayniyomi.plugin.lightnovel"

        private const val PLUGIN_APK_FILE_NAME = "lightnovel-plugin.apk"
        private const val APK_MIME = "application/vnd.android.package-archive"
        private const val SHA256_HEX_LENGTH = 64

        private const val META_PLUGIN_API_VERSION = "rayniyomi.plugin.api_version"
        private const val META_MIN_HOST_VERSION = "rayniyomi.plugin.min_host_version"
        private const val META_TARGET_HOST_VERSION = "rayniyomi.plugin.target_host_version"

        private const val EXPECTED_PLUGIN_API_VERSION = 1
        private const val ENABLE_PLUGIN_INSTALL_FOR_RELEASE = false

        private const val STABLE_MANIFEST_URL =
            "https://github.com/ryacub/rayniyomi/releases/latest/download/lightnovel-plugin-manifest.json"
        private const val BETA_MANIFEST_URL =
            "https://github.com/ryacub/rayniyomi/releases/download/plugin-beta/lightnovel-plugin-manifest.json"

        @Suppress("DEPRECATION")
        private val INSTALLED_PACKAGE_FLAGS = PackageManager.GET_META_DATA or
            PackageManager.GET_SIGNATURES or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)

        @Suppress("DEPRECATION")
        private val ARCHIVE_PACKAGE_FLAGS = PackageManager.GET_META_DATA or
            PackageManager.GET_SIGNATURES or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)
    }
}
