package eu.kanade.tachiyomi.ui.browse.anime.extension

import android.app.Application
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.extension.anime.interactor.GetAnimeExtensionsByType
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.anime.model.AnimeLoadResult
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.activeNetworkState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import okhttp3.OkHttpClient
import okhttp3.Request
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class AnimeExtensionsScreenModel(
    preferences: SourcePreferences = Injekt.get(),
    basePreferences: BasePreferences = Injekt.get(),
    private val extensionManager: AnimeExtensionManager = Injekt.get(),
    private val getExtensions: GetAnimeExtensionsByType = Injekt.get(),
    private val network: NetworkHelper = Injekt.get(),
    private val application: Application = Injekt.get(),
) : StateScreenModel<AnimeExtensionsScreenModel.State>(State()) {

    private val currentDownloads = MutableStateFlow<Map<String, InstallStep>>(hashMapOf())
    private val invalidNoticeKeys = mutableSetOf<String>()

    private val probeTimestamps = ConcurrentHashMap<String, Long>()
    private val probeSemaphore = Semaphore(10)
    private val probeClient: OkHttpClient by lazy {
        network.nonCloudflareClient.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        val extensionMapper: (Map<String, InstallStep>) -> ((AnimeExtension) -> AnimeExtensionUiModel.Item) = { map ->
            {
                AnimeExtensionUiModel.Item(it, map[it.pkgName] ?: InstallStep.Idle)
            }
        }
        val queryFilter: (String) -> ((AnimeExtension) -> Boolean) = { query ->
            filter@{ extension ->
                if (query.isEmpty()) return@filter true
                query.split(",").any { _input ->
                    val input = _input.trim()
                    if (input.isEmpty()) return@any false
                    when (extension) {
                        is AnimeExtension.Available -> {
                            extension.sources.any {
                                it.name.contains(input, ignoreCase = true) ||
                                    it.baseUrl.contains(input, ignoreCase = true) ||
                                    it.id == input.toLongOrNull()
                            } ||
                                extension.name.contains(input, ignoreCase = true)
                        }
                        is AnimeExtension.Installed -> {
                            extension.sources.any {
                                it.name.contains(input, ignoreCase = true) ||
                                    it.id == input.toLongOrNull() ||
                                    if (it is AnimeHttpSource) {
                                        it.baseUrl.contains(
                                            input,
                                            ignoreCase = true,
                                        )
                                    } else {
                                        false
                                    }
                            } ||
                                extension.name.contains(input, ignoreCase = true)
                        }
                        is AnimeExtension.Untrusted -> extension.name.contains(
                            input,
                            ignoreCase = true,
                        )
                    }
                }
            }
        }

        screenModelScope.launchIO {
            combine(
                state.map { it.searchQuery }.distinctUntilChanged().debounce(SEARCH_DEBOUNCE_MILLIS),
                currentDownloads,
                getExtensions.subscribe(),
            ) { query, downloads, (_updates, _installed, _available, _untrusted) ->
                val searchQuery = query ?: ""

                val itemsGroups: ItemGroups = mutableMapOf()

                val updates = _updates.filter(queryFilter(searchQuery)).map(
                    extensionMapper(downloads),
                )
                if (updates.isNotEmpty()) {
                    itemsGroups[AnimeExtensionUiModel.Header.Resource(MR.strings.ext_updates_pending)] = updates
                }

                val installed = _installed.filter(queryFilter(searchQuery)).map(
                    extensionMapper(downloads),
                )
                val untrusted = _untrusted.filter(queryFilter(searchQuery)).map(
                    extensionMapper(downloads),
                )
                if (installed.isNotEmpty() || untrusted.isNotEmpty()) {
                    itemsGroups[AnimeExtensionUiModel.Header.Resource(MR.strings.ext_installed)] = installed + untrusted
                }

                val languagesWithExtensions = _available
                    .filter(queryFilter(searchQuery))
                    .groupBy { it.lang }
                    .toSortedMap(LocaleHelper.comparator)
                    .map { (lang, exts) ->
                        AnimeExtensionUiModel.Header.Text(
                            LocaleHelper.getSourceDisplayName(lang, application),
                        ) to exts.map(extensionMapper(downloads))
                    }

                if (languagesWithExtensions.isNotEmpty()) {
                    itemsGroups.putAll(languagesWithExtensions)
                }

                itemsGroups
            }
                .collectLatest {
                    mutableState.update { state ->
                        state.copy(
                            isLoading = false,
                            items = it,
                        )
                    }
                }
        }
        screenModelScope.launchIO { findAvailableExtensions() }

        extensionManager.invalidExtensionNotices
            .onEach { invalid ->
                if (invalidNoticeKeys.add(invalid.noticeKey())) {
                    _events.send(Event.InvalidExtensionRevoked(invalid))
                }
            }
            .launchIn(screenModelScope)

        // Probe available extensions for availability whenever the list changes
        screenModelScope.launchIO {
            getExtensions.subscribe()
                .map { it.available }
                .distinctUntilChanged()
                .collectLatest { available ->
                    checkExtensionAvailability(available)
                }
        }

        preferences.animeExtensionUpdatesCount().changes()
            .onEach { mutableState.update { state -> state.copy(updates = it) } }
            .launchIn(screenModelScope)

        basePreferences.extensionInstaller().changes()
            .onEach { mutableState.update { state -> state.copy(installer = it) } }
            .launchIn(screenModelScope)
    }

    fun search(query: String?) {
        mutableState.update {
            it.copy(searchQuery = query)
        }
    }

    fun updateAllExtensions() {
        screenModelScope.launchIO {
            state.value.items.values.flatten()
                .map { it.extension }
                .filterIsInstance<AnimeExtension.Installed>()
                .filter { it.hasUpdate }
                .forEach(::updateExtension)
        }
    }

    fun installExtension(extension: AnimeExtension.Available) {
        screenModelScope.launchIO {
            try {
                extensionManager.installExtension(extension).collectToInstallUpdate(extension)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to install extension ${extension.name}" }
                _events.send(Event.InstallError(extension))
            }
        }
    }

    fun updateExtension(extension: AnimeExtension.Installed) {
        screenModelScope.launchIO {
            extensionManager.updateExtension(extension).collectToInstallUpdate(extension)
        }
    }

    fun cancelInstallUpdateExtension(extension: AnimeExtension) {
        extensionManager.cancelInstallUpdateExtension(extension)
    }

    private fun addDownloadState(extension: AnimeExtension, installStep: InstallStep) {
        currentDownloads.update { it + Pair(extension.pkgName, installStep) }
    }

    private fun removeDownloadState(extension: AnimeExtension) {
        currentDownloads.update { it - extension.pkgName }
    }

    private suspend fun Flow<InstallStep>.collectToInstallUpdate(extension: AnimeExtension) =
        this
            .onEach { installStep -> addDownloadState(extension, installStep) }
            .onCompletion { removeDownloadState(extension) }
            .collect()

    fun uninstallExtension(extension: AnimeExtension) {
        extensionManager.uninstallExtension(extension)
    }

    fun uninstallExtension(pkgName: String) {
        extensionManager.uninstallExtension(pkgName)
    }

    fun findAvailableExtensions() {
        screenModelScope.launchIO {
            mutableState.update { it.copy(isRefreshing = true) }
            extensionManager.findAvailableExtensions()

            // Fake slower refresh so it doesn't seem like it's not doing anything
            delay(1.seconds)

            mutableState.update { it.copy(isRefreshing = false) }
        }
    }

    fun trustExtension(extension: AnimeExtension.Untrusted) {
        screenModelScope.launch {
            extensionManager.trust(extension)
        }
    }

    fun retryProbe(pkgName: String, url: String) {
        screenModelScope.launchIO {
            if (!application.activeNetworkState().isOnline) {
                _events.send(Event.DeviceOffline)
                return@launchIO
            }
            probeTimestamps.remove(pkgName)
            val status = probeSemaphore.withPermit { probeUrl(url) }
            probeTimestamps[pkgName] = System.currentTimeMillis()
            mutableState.update { state ->
                state.copy(extensionHealthStatuses = state.extensionHealthStatuses + (pkgName to status))
            }
        }
    }

    private suspend fun checkExtensionAvailability(available: List<AnimeExtension.Available>) {
        if (!application.activeNetworkState().isOnline) {
            _events.send(Event.DeviceOffline)
            return
        }
        val ttlMillis = 5.minutes.inWholeMilliseconds
        val now = System.currentTimeMillis()

        // Mark blank URLs as BROKEN immediately
        val brokenImmediate = available.filter { ext ->
            ext.sources.firstOrNull()?.baseUrl.orEmpty().isBlank()
        }
        if (brokenImmediate.isNotEmpty()) {
            mutableState.update { state ->
                state.copy(
                    extensionHealthStatuses = state.extensionHealthStatuses +
                        brokenImmediate.associate { it.pkgName to AnimeExtensionUiModel.HealthStatus.BROKEN },
                )
            }
        }

        val toProbe = available.filter { ext ->
            val url = ext.sources.firstOrNull()?.baseUrl.orEmpty()
            if (url.isBlank()) return@filter false
            if (url.contains("localhost") || url.contains("127.0.0.1")) return@filter false
            val lastProbed = probeTimestamps[ext.pkgName] ?: 0L
            now - lastProbed >= ttlMillis
        }

        coroutineScope {
            toProbe.map { ext ->
                async {
                    probeSemaphore.withPermit {
                        val url = ext.sources.first().baseUrl
                        val status = probeUrl(url)
                        probeTimestamps[ext.pkgName] = System.currentTimeMillis()
                        mutableState.update { state ->
                            state.copy(
                                extensionHealthStatuses = state.extensionHealthStatuses + (ext.pkgName to status),
                            )
                        }
                    }
                }
            }.awaitAll()
        }
    }

    private suspend fun probeUrl(url: String): AnimeExtensionUiModel.HealthStatus {
        return try {
            val request = Request.Builder().url(url).head().build()
            probeClient.newCall(request).await().close()
            AnimeExtensionUiModel.HealthStatus.HEALTHY
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AnimeExtensionUiModel.HealthStatus.BROKEN
        }
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val items: ItemGroups = mutableMapOf(),
        val updates: Int = 0,
        val installer: BasePreferences.ExtensionInstaller? = null,
        val searchQuery: String? = null,
        val extensionHealthStatuses: Map<String, AnimeExtensionUiModel.HealthStatus> = emptyMap(),
    ) {
        val isEmpty = items.isEmpty()
    }

    sealed interface Event {
        data object DeviceOffline : Event
        data class InvalidExtensionRevoked(val extension: AnimeLoadResult.Invalid) : Event
        data class InstallError(val extension: AnimeExtension.Available) : Event
    }
}

typealias ItemGroups = MutableMap<AnimeExtensionUiModel.Header, List<AnimeExtensionUiModel.Item>>

object AnimeExtensionUiModel {
    sealed interface Header {
        data class Resource(val textRes: StringResource) : Header
        data class Text(val text: String) : Header
    }
    data class Item(
        val extension: AnimeExtension,
        val installStep: InstallStep,
    )
    enum class HealthStatus { UNKNOWN, HEALTHY, BROKEN }
}

private fun AnimeLoadResult.Invalid.noticeKey(): String {
    return "$pkgName:$versionCode:$signatureHash"
}
