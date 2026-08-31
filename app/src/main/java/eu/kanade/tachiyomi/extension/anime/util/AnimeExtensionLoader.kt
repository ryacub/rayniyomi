package eu.kanade.tachiyomi.extension.anime.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import dalvik.system.PathClassLoader
import eu.kanade.domain.extension.anime.interactor.TrustAnimeExtension
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.anime.model.AnimeLoadResult
import eu.kanade.tachiyomi.util.lang.Hash
import eu.kanade.tachiyomi.util.storage.copyAndSetReadOnlyTo
import eu.kanade.tachiyomi.util.system.ChildFirstPathClassLoader
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import java.io.File

/**
 * Class that handles the loading of the extensions installed in the system.
 */
@SuppressLint("PackageManagerGetSignatures")
internal object AnimeExtensionLoader {

    private val preferences: SourcePreferences by injectLazy()
    private val trustExtension: TrustAnimeExtension by injectLazy()
    private val loadNsfwSource by lazy {
        preferences.showNsfwSource().get()
    }

    private const val EXTENSION_FEATURE = "tachiyomi.animeextension"
    private const val METADATA_SOURCE_CLASS = "tachiyomi.animeextension.class"
    private const val METADATA_SOURCE_FACTORY = "tachiyomi.animeextension.factory"
    private const val METADATA_NSFW = "tachiyomi.animeextension.nsfw"
    private const val METADATA_HAS_README = "tachiyomi.animeextension.hasReadme"
    private const val METADATA_HAS_CHANGELOG = "tachiyomi.animeextension.hasChangelog"
    const val LIB_VERSION_MIN = 12
    const val LIB_VERSION_MAX = 16

    @Suppress("DEPRECATION")
    private val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or
        PackageManager.GET_META_DATA or
        PackageManager.GET_SIGNATURES or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)

    private const val PRIVATE_EXTENSION_EXTENSION = "ext"

    private fun getPrivateExtensionDir(context: Context) = File(context.filesDir, "exts")

    fun installPrivateExtensionFile(context: Context, file: File): Boolean {
        val extension = context.packageManager.getPackageArchiveInfo(
            file.absolutePath,
            PACKAGE_FLAGS,
        )
            ?.takeIf { isPackageAnExtension(it) } ?: return false
        val currentExtension = getAnimeExtensionPackageInfoFromPkgName(
            context,
            extension.packageName,
        )

        if (currentExtension != null) {
            if (PackageInfoCompat.getLongVersionCode(extension) <
                PackageInfoCompat.getLongVersionCode(currentExtension)
            ) {
                logcat(LogPriority.ERROR) { "Installed extension version is higher. Downgrading is not allowed." }
                return false
            }

            val extensionSignatures = getSignatures(extension)
            if (extensionSignatures.isNullOrEmpty()) {
                logcat(LogPriority.ERROR) { "Extension to be installed is not signed." }
                return false
            }

            val currentExtensionSignatures = getSignatures(currentExtension)
            if (currentExtensionSignatures.isNullOrEmpty() ||
                !extensionSignatures.containsAll(currentExtensionSignatures)
            ) {
                logcat(LogPriority.ERROR) { "Installed extension signature is not matched." }
                return false
            }
        }

        val target = File(
            getPrivateExtensionDir(context),
            "${extension.packageName}.$PRIVATE_EXTENSION_EXTENSION",
        )
        return try {
            target.delete()
            file.copyAndSetReadOnlyTo(target, overwrite = true)
            if (currentExtension != null) {
                AnimeExtensionInstallReceiver.notifyReplaced(context, extension.packageName)
            } else {
                AnimeExtensionInstallReceiver.notifyAdded(context, extension.packageName)
            }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to copy extension file." }
            target.delete()
            false
        }
    }

    fun uninstallPrivateExtension(context: Context, pkgName: String) {
        File(getPrivateExtensionDir(context), "$pkgName.$PRIVATE_EXTENSION_EXTENSION").delete()
    }

    /**
     * Return a list of all the available extensions initialized concurrently.
     *
     * @param context The application context.
     */
    suspend fun loadExtensions(context: Context): List<AnimeLoadResult> {
        val pkgManager = context.packageManager

        val installedPkgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pkgManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()),
            )
        } else {
            pkgManager.getInstalledPackages(PACKAGE_FLAGS)
        }

        val sharedExtPkgs = installedPkgs
            .asSequence()
            .filter { isPackageAnExtension(it) }
            .map { AnimeExtensionInfo(packageInfo = it, isShared = true) }

        val privateExtPkgs = getPrivateExtensionDir(context)
            .listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension == PRIVATE_EXTENSION_EXTENSION }
            ?.mapNotNull {
                // Just in case, since Android 14+ requires them to be read-only
                if (it.canWrite()) {
                    it.setReadOnly()
                }

                val path = it.absolutePath
                pkgManager.getPackageArchiveInfo(path, PACKAGE_FLAGS)
                    ?.apply { applicationInfo?.fixBasePaths(path) }
            }
            ?.filter { isPackageAnExtension(it) }
            ?.map { AnimeExtensionInfo(packageInfo = it, isShared = false) }
            ?: emptySequence()

        val extPkgs = (sharedExtPkgs + privateExtPkgs)
            // Remove duplicates. Shared takes priority than private by default
            .distinctBy { it.packageInfo.packageName }
            // Compare version number
            .mapNotNull { sharedPkg ->
                val privatePkg = privateExtPkgs
                    .singleOrNull { it.packageInfo.packageName == sharedPkg.packageInfo.packageName }
                selectExtensionPackage(sharedPkg, privatePkg)
            }
            .toList()

        if (extPkgs.isEmpty()) return emptyList()

        // Load each extension concurrently and wait for completion
        return coroutineScope {
            extPkgs.map {
                async { loadExtension(context, it) }
            }.awaitAll()
        }
    }

    /**
     * Attempts to load an extension from the given package name. It checks if the extension
     * contains the required feature flag before trying to load it.
     */
    suspend fun loadExtensionFromPkgName(context: Context, pkgName: String): AnimeLoadResult {
        val extensionPackage = getAnimeExtensionInfoFromPkgName(context, pkgName)
        if (extensionPackage == null) {
            logcat(LogPriority.ERROR) { "Extension package is not found ($pkgName)" }
            return AnimeLoadResult.Error("Failed to load extension $pkgName: package is not found")
        }
        return loadExtension(context, extensionPackage)
    }

    fun getAnimeExtensionPackageInfoFromPkgName(context: Context, pkgName: String): PackageInfo? {
        return getAnimeExtensionInfoFromPkgName(context, pkgName)?.packageInfo
    }

    private fun getAnimeExtensionInfoFromPkgName(context: Context, pkgName: String): AnimeExtensionInfo? {
        val privateExtensionFile = File(
            getPrivateExtensionDir(context),
            "$pkgName.$PRIVATE_EXTENSION_EXTENSION",
        )
        val privatePkg = if (privateExtensionFile.isFile) {
            context.packageManager.getPackageArchiveInfo(
                privateExtensionFile.absolutePath,
                PACKAGE_FLAGS,
            )
                ?.takeIf { isPackageAnExtension(it) }
                ?.let {
                    it.applicationInfo?.fixBasePaths(privateExtensionFile.absolutePath)
                    AnimeExtensionInfo(
                        packageInfo = it,
                        isShared = false,
                    )
                }
        } else {
            null
        }

        val sharedPkg = try {
            context.packageManager.getPackageInfo(pkgName, PACKAGE_FLAGS)
                .takeIf { isPackageAnExtension(it) }
                ?.let {
                    AnimeExtensionInfo(
                        packageInfo = it,
                        isShared = true,
                    )
                }
        } catch (error: PackageManager.NameNotFoundException) {
            null
        }

        return selectExtensionPackage(sharedPkg, privatePkg)
    }

    /**
     * Loads an extension
     *
     * @param context The application context.
     * @param extensionInfo The extension to load.
     */
    private suspend fun loadExtension(context: Context, extensionInfo: AnimeExtensionInfo): AnimeLoadResult {
        val pkgManager = context.packageManager

        val pkgInfo = extensionInfo.packageInfo
        val pkgName = pkgInfo.packageName
        val appInfo = pkgInfo.applicationInfo ?: return loadError(pkgName, "missing application info")

        val extName = pkgManager.getApplicationLabel(appInfo).toString()
            .substringAfter("Rayniyomi: ")
            .substringAfter("Aniyomi: ")
        val metadata = appInfo.metaData ?: return loadError(extName, "missing metadata")
        val versionName = pkgInfo.versionName
        val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)

        if (versionName.isNullOrEmpty()) {
            logcat(LogPriority.WARN) { "Missing versionName for extension $extName" }
            return loadError(extName, "missing version name")
        }

        // Validate lib version
        val libVersion = versionName.substringBeforeLast('.').toDoubleOrNull()
        if (libVersion == null || libVersion < LIB_VERSION_MIN || libVersion > LIB_VERSION_MAX) {
            logcat(LogPriority.WARN) {
                "Lib version is $libVersion, while only versions " +
                    "$LIB_VERSION_MIN to $LIB_VERSION_MAX are allowed"
            }
            return loadError(extName, "unsupported library version")
        }

        val isNsfw = when (val value = metadata.get(METADATA_NSFW)) {
            null -> false
            is Int -> value == 1
            else -> return loadError(extName, "malformed metadata")
        }
        val sourceClassMetadata = when (val value = metadata.get(METADATA_SOURCE_CLASS)) {
            null -> return loadError(extName, "missing source class metadata")
            is String -> value.takeIf { it.isNotBlank() }
                ?: return loadError(extName, "malformed metadata")
            else -> return loadError(extName, "malformed metadata")
        }
        val pkgFactory = when (val value = metadata.get(METADATA_SOURCE_FACTORY)) {
            null -> null
            is String -> value
            else -> return loadError(extName, "malformed metadata")
        }

        val signatures = getSignatures(pkgInfo)
        if (signatures.isNullOrEmpty()) {
            logcat(LogPriority.WARN) { "Package $pkgName isn't signed" }
            return loadError(extName, "package is not signed")
        }
        val signatureHash = signatures.first()
        if (!trustExtension.isTrusted(pkgInfo, signatures)) {
            val extension = AnimeExtension.Untrusted(
                extName,
                pkgName,
                versionName,
                versionCode,
                libVersion,
                signatures.first(),
            )
            logcat(LogPriority.WARN, message = { "Extension $pkgName isn't trusted" })
            return AnimeLoadResult.Untrusted(extension)
        }

        if (!loadNsfwSource && isNsfw) {
            logcat(LogPriority.WARN) { "NSFW extension $pkgName not allowed" }
            return loadError(extName, "NSFW extensions are disabled")
        }

        val classLoader = try {
            ChildFirstPathClassLoader(appInfo.sourceDir, null, context.classLoader)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Extension load error: $extName ($pkgName)" }
            return loadError(extName, "source class loader failed")
        }

        val sources = sourceClassMetadata
            .split(";")
            .map {
                val sourceClass = it.trim()
                if (sourceClass.startsWith(".")) {
                    pkgInfo.packageName + sourceClass
                } else {
                    sourceClass
                }
            }
            .flatMap {
                instantiateSources(
                    extensionName = extName,
                    sourceClass = it,
                    instantiate = {
                        when (val obj = Class.forName(it, false, classLoader).getDeclaredConstructor().newInstance()) {
                            is AnimeSource -> listOf(obj)
                            is AnimeSourceFactory -> obj.createSources()
                            else -> throw Exception("Unknown source class type: ${obj.javaClass}")
                        }
                    },
                    fallback = {
                        val fallBackClassLoader = PathClassLoader(appInfo.sourceDir, null, context.classLoader)
                        when (
                            val obj = Class.forName(
                                it,
                                false,
                                fallBackClassLoader,
                            ).getDeclaredConstructor().newInstance()
                        ) {
                            is AnimeSource -> {
                                listOf(obj)
                            }
                            is AnimeSourceFactory -> obj.createSources()
                            else -> throw Exception("Unknown source class type: ${obj.javaClass}")
                        }
                    },
                ) ?: return loadError(extName, "source failed to load")
            }

        val langs = sources.filterIsInstance<AnimeCatalogueSource>()
            .map { it.lang }
            .toSet()
        val lang = when (langs.size) {
            0 -> ""
            1 -> langs.first()
            else -> "all"
        }

        val extension = AnimeExtension.Installed(
            name = extName,
            pkgName = pkgName,
            versionName = versionName,
            versionCode = versionCode,
            libVersion = libVersion,
            lang = lang,
            isNsfw = isNsfw,
            sources = sources,
            pkgFactory = pkgFactory,
            icon = appInfo.loadIcon(pkgManager),
            isShared = extensionInfo.isShared,
            signatureHash = signatureHash,
        )
        return AnimeLoadResult.Success(extension)
    }

    private fun loadError(extensionName: String, reason: String): AnimeLoadResult.Error {
        val message = "Failed to load extension $extensionName: $reason"
        logcat(LogPriority.ERROR) { message }
        return AnimeLoadResult.Error(message)
    }

    internal fun instantiateSources(
        extensionName: String,
        sourceClass: String,
        instantiate: () -> List<AnimeSource>,
        fallback: () -> List<AnimeSource>,
    ): List<AnimeSource>? {
        return try {
            instantiate()
        } catch (error: LinkageError) {
            runCatching(fallback).getOrElse {
                logcat(LogPriority.ERROR, it) { "Extension load error: $extensionName ($sourceClass)" }
                null
            }
        } catch (error: Throwable) {
            logcat(LogPriority.ERROR, error) { "Extension load error: $extensionName ($sourceClass)" }
            null
        }
    }

    /**
     * Choose which extension package to use based on version code
     *
     * @param shared extension installed to system
     * @param private extension installed to data directory
     */
    private fun selectExtensionPackage(shared: AnimeExtensionInfo?, private: AnimeExtensionInfo?): AnimeExtensionInfo? {
        val sharedPackage = shared ?: return private
        val privatePackage = private ?: return shared

        return if (PackageInfoCompat.getLongVersionCode(sharedPackage.packageInfo) >=
            PackageInfoCompat.getLongVersionCode(privatePackage.packageInfo)
        ) {
            sharedPackage
        } else {
            privatePackage
        }
    }

    /**
     * Returns true if the given package is an extension.
     *
     * @param pkgInfo The package info of the application.
     */
    private fun isPackageAnExtension(pkgInfo: PackageInfo): Boolean {
        return pkgInfo.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE }
    }

    /**
     * Returns the signatures of the package or null if it's not signed.
     *
     * @param pkgInfo The package info of the application.
     * @return List SHA256 digest of the signatures
     */
    private fun getSignatures(pkgInfo: PackageInfo): List<String>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = pkgInfo.signingInfo ?: return null
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.signatures
        }
            ?.map { Hash.sha256(it.toByteArray()) }
            ?.toList()
    }

    /**
     * On Android 13+ the ApplicationInfo generated by getPackageArchiveInfo doesn't
     * have sourceDir which breaks assets loading (used for getting icon here).
     */
    private fun ApplicationInfo.fixBasePaths(apkPath: String) {
        if (sourceDir == null) {
            sourceDir = apkPath
        }
        if (publicSourceDir == null) {
            publicSourceDir = apkPath
        }
    }

    private data class AnimeExtensionInfo(
        val packageInfo: PackageInfo,
        val isShared: Boolean,
    )
}
