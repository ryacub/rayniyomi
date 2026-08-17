package eu.kanade.tachiyomi.extension.manga.api

import android.content.Context
import eu.kanade.tachiyomi.extension.ExtensionUpdateNotifier
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.extension.manga.model.MangaLoadResult
import eu.kanade.tachiyomi.extension.manga.util.MangaExtensionLoader
import eu.kanade.tachiyomi.extension.selectPreferredExtensionCandidate
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import mihon.data.extension.manga.model.MangaExtensionStoreDecoder
import mihon.data.extension.manga.model.NetworkMangaExtensionRepo
import mihon.data.extension.manga.model.NetworkMangaExtensionStore
import mihon.domain.extensionrepo.manga.interactor.GetMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.UpdateMangaExtensionRepo
import mihon.domain.extensionrepo.model.ExtensionRepo
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.days

internal class MangaExtensionApi {

    private val networkService: NetworkHelper by injectLazy()
    private val preferenceStore: PreferenceStore by injectLazy()
    private val getExtensionRepo: GetMangaExtensionRepo by injectLazy()
    private val updateExtensionRepo: UpdateMangaExtensionRepo by injectLazy()
    private val extensionManager: MangaExtensionManager by injectLazy()
    private val json: Json by injectLazy()
    private val protoBuf: ProtoBuf by injectLazy()

    private val lastExtCheck: Preference<Long> by lazy {
        preferenceStore.getLong("last_ext_check", 0)
    }

    suspend fun findExtensions(): List<MangaExtension.Available> {
        return withIOContext {
            getExtensionRepo.getAll()
                .map { async { getExtensions(it) } }
                .awaitAll()
                .flatten()
        }
    }

    private suspend fun getExtensions(extRepo: ExtensionRepo): List<MangaExtension.Available> {
        val repoBaseUrl = extRepo.baseUrl
        return try {
            val repoJson = fetchRepoJson(repoBaseUrl)
            val indexV2Url = MangaExtensionStoreDecoder.resolveIndexUrl(repoJson)
            if (indexV2Url != null) {
                fetchStoreExtensions(
                    indexV2Url = indexV2Url,
                    repoBaseUrl = repoBaseUrl,
                    signingKeyFingerprint = extRepo.signingKeyFingerprint,
                )
            } else {
                fetchLegacyExtensions(
                    repoBaseUrl = repoBaseUrl,
                    signingKeyFingerprint = extRepo.signingKeyFingerprint,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Failed to get extensions from $repoBaseUrl" }
            emptyList()
        }
    }

    /**
     * Fetches `<repo>/repo.json`.
     *
     * Returns null on HTTP 404, which is expected for legacy repos that do not
     * serve `repo.json`. A network or parse failure propagates, and the caller
     * logs it and returns an empty catalogue. It does NOT fall back to a
     * possibly-stale `index.min.json` placeholder.
     */
    private suspend fun fetchRepoJson(repoBaseUrl: String): NetworkMangaExtensionRepo? {
        return try {
            networkService.client
                .newCall(GET("$repoBaseUrl/repo.json"))
                .awaitSuccess()
                .body
                .source()
                .use { source ->
                    json.decodeFromBufferedSource<NetworkMangaExtensionRepo>(source)
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            // A repo without repo.json is a legacy repo: fall back to index.min.json.
            if (e.code != 404) {
                throw e
            }
            null
        }
    }

    /** Reads the gzipped protobuf store at `index_v2` and maps its catalogue. */
    private suspend fun fetchStoreExtensions(
        indexV2Url: String,
        repoBaseUrl: String,
        signingKeyFingerprint: String,
    ): List<MangaExtension.Available> {
        val response = networkService.client
            .newCall(GET(indexV2Url))
            .awaitSuccess()
        return response.body.source()
            .use { source ->
                MangaExtensionStoreDecoder.decode(source, protoBuf).extensionList?.extensions.orEmpty()
            }
            .toMangaExtensions(
                repoUrl = repoBaseUrl,
                signingKeyFingerprint = signingKeyFingerprint,
            )
    }

    /** Reads the legacy `index.min.json` JSON catalogue. */
    private suspend fun fetchLegacyExtensions(
        repoBaseUrl: String,
        signingKeyFingerprint: String,
    ): List<MangaExtension.Available> {
        val response = networkService.client
            .newCall(GET("$repoBaseUrl/index.min.json"))
            .awaitSuccess()
        return with(json) {
            response
                .parseAs<List<ExtensionJsonObject>>()
                .toExtensions(
                    repoUrl = repoBaseUrl,
                    signingKeyFingerprint = signingKeyFingerprint,
                )
        }
    }

    suspend fun checkForUpdates(
        context: Context,
        fromAvailableExtensionList: Boolean = false,
    ): List<MangaExtension.Installed>? = withIOContext {
        // Limit checks to once a day at most
        if (fromAvailableExtensionList &&
            Instant.now().toEpochMilli() < lastExtCheck.get() + 1.days.inWholeMilliseconds
        ) {
            return@withIOContext null
        }

        // Update extension repo details
        updateExtensionRepo.awaitAll()

        val extensions = if (fromAvailableExtensionList) {
            extensionManager.availableExtensionsFlow.value
        } else {
            findExtensions().also { lastExtCheck.set(Instant.now().toEpochMilli()) }
        }

        val installedExtensions = MangaExtensionLoader.loadMangaExtensions(context)
            .filterIsInstance<MangaLoadResult.Success>()
            .map { it.extension }

        val extensionsWithUpdate = mutableListOf<MangaExtension.Installed>()
        val availableExtensionsByPackage = extensions.groupBy { it.pkgName }
        for (installedExt in installedExtensions) {
            val availableExt = availableExtensionsByPackage[installedExt.pkgName]
                ?.let {
                    selectPreferredExtensionCandidate(
                        candidates = it,
                        installedSignatureHash = installedExt.signatureHash,
                        installedRepoUrl = installedExt.repoUrl,
                        candidateSignatureHash = MangaExtension.Available::signingKeyFingerprint,
                        candidateRepoUrl = MangaExtension.Available::repoUrl,
                    )
                }
                ?: continue
            val hasUpdatedVer = availableExt.versionCode > installedExt.versionCode
            val hasUpdatedLib = availableExt.libVersion > installedExt.libVersion
            val hasUpdate = hasUpdatedVer || hasUpdatedLib
            if (hasUpdate) {
                extensionsWithUpdate.add(installedExt)
            }
        }

        if (extensionsWithUpdate.isNotEmpty()) {
            ExtensionUpdateNotifier(context).promptUpdates(extensionsWithUpdate.map { it.name })
        }

        extensionsWithUpdate
    }

    private fun List<ExtensionJsonObject>.toExtensions(
        repoUrl: String,
        signingKeyFingerprint: String,
    ): List<MangaExtension.Available> {
        return this
            .filter {
                val libVersion = it.extractLibVersion()
                libVersion >= MangaExtensionLoader.LIB_VERSION_MIN && libVersion <= MangaExtensionLoader.LIB_VERSION_MAX
            }
            .map {
                MangaExtension.Available(
                    name = it.name.substringAfter("Tachiyomi: "),
                    pkgName = it.pkg,
                    versionName = it.version,
                    versionCode = it.code,
                    libVersion = it.extractLibVersion(),
                    lang = it.lang,
                    isNsfw = it.nsfw == 1,
                    sources = it.sources?.map(extensionSourceMapper).orEmpty(),
                    apkName = it.apk,
                    iconUrl = "$repoUrl/icon/${it.pkg}.png",
                    repoUrl = repoUrl,
                    signingKeyFingerprint = signingKeyFingerprint,
                )
            }
    }

    private fun List<NetworkMangaExtensionStore.Extension>.toMangaExtensions(
        repoUrl: String,
        signingKeyFingerprint: String,
    ): List<MangaExtension.Available> {
        return this.mapNotNull { extension ->
            val libVersion = extension.extensionLib.toDoubleOrNull()
            if (
                libVersion == null ||
                libVersion < MangaExtensionLoader.LIB_VERSION_MIN ||
                libVersion > MangaExtensionLoader.LIB_VERSION_MAX
            ) {
                return@mapNotNull null
            }
            if (extension.name.isBlank() || extension.packageName.isBlank()) {
                return@mapNotNull null
            }
            val resources = extension.resources
            if (resources == null) {
                return@mapNotNull null
            }
            val languages = extension.sources.map { source -> source.language }.distinct()
            MangaExtension.Available(
                name = extension.name,
                pkgName = extension.packageName,
                versionName = extension.versionName,
                versionCode = extension.versionCode,
                libVersion = libVersion,
                lang = if (languages.size == 1) languages.first() else "all",
                // contentWarning maps to the APK's tachiyomi.extension.nsfw
                // metadata that MangaExtensionLoader enforces at install time:
                // warned (1) = nsfw 0, MIXED (2) and NSFW (3) = nsfw 1. So >= 2
                // mirrors the legacy index.min.json nsfw==1 semantic. MangaDex,
                // Tapas and Toonily are mixed-content sources and were hidden
                // behind the NSFW toggle under the legacy index too. Decoded as
                // raw Int so an unknown wire value cannot fail the whole
                // catalogue; a future value >= 2 maps to NSFW, which fails safe.
                isNsfw = extension.contentWarning >= 2,
                sources = extension.sources.map { source ->
                    MangaExtension.Available.MangaSource(
                        id = source.id,
                        lang = source.language,
                        name = source.name,
                        baseUrl = source.homeUrl,
                    )
                },
                apkName = resources.apkUrl.substringAfterLast('/'),
                iconUrl = resources.iconUrl,
                repoUrl = repoUrl,
                signingKeyFingerprint = signingKeyFingerprint,
                apkUrl = resources.apkUrl.ifBlank { null },
            )
        }
    }

    fun getApkUrl(extension: MangaExtension.Available): String {
        return extension.apkUrl ?: "${extension.repoUrl}/apk/${extension.apkName}"
    }

    private fun ExtensionJsonObject.extractLibVersion(): Double {
        return version.substringBeforeLast('.').toDouble()
    }
}

@Serializable
private data class ExtensionJsonObject(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Long,
    val version: String,
    val nsfw: Int,
    val sources: List<ExtensionSourceJsonObject>?,
)

@Serializable
private data class ExtensionSourceJsonObject(
    val id: Long,
    val lang: String,
    val name: String,
    val baseUrl: String,
)

private val extensionSourceMapper: (ExtensionSourceJsonObject) -> MangaExtension.Available.MangaSource = {
    MangaExtension.Available.MangaSource(
        id = it.id,
        lang = it.lang,
        name = it.name,
        baseUrl = it.baseUrl,
    )
}
