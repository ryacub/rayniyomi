package eu.kanade.domain.source.anime.interactor

import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import tachiyomi.domain.source.anime.model.AnimeSource
import tachiyomi.domain.source.anime.model.Pin
import tachiyomi.domain.source.anime.model.Pins
import tachiyomi.domain.source.anime.repository.AnimeSourceRepository
import tachiyomi.domain.source.manga.model.SourceHealthStatus
import tachiyomi.domain.source.manga.repository.SourceHealthRepository
import tachiyomi.source.local.entries.anime.LocalAnimeSource

class GetEnabledAnimeSources(
    private val repository: AnimeSourceRepository,
    private val preferences: SourcePreferences,
    private val healthRepository: SourceHealthRepository,
) {

    fun subscribe(): Flow<List<AnimeSource>> {
        return combine(
            preferences.pinnedAnimeSources().changes(),
            preferences.enabledLanguages().changes(),
            combine(
                preferences.disabledAnimeSources().changes(),
                preferences.lastUsedAnimeSource().changes(),
            ) { disabled, lastUsed -> Pair(disabled, lastUsed) },
            combine(
                healthRepository.getAll(),
                preferences.showBrokenAnimeSources().changes(),
            ) { health, showBroken -> Pair(health, showBroken) },
            repository.getAnimeSources(),
        ) { pinnedSourceIds, enabledLanguages, (disabledSources, lastUsedSource), (healthMap, showBroken), sources ->
            sources
                .filter { it.lang in enabledLanguages || it.id == LocalAnimeSource.ID }
                .filterNot { it.id.toString() in disabledSources }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                .map { source ->
                    val health = healthMap[source.id]
                    val healthStatus = when {
                        source.id == LocalAnimeSource.ID -> SourceHealthStatus.HEALTHY
                        source.isStub -> SourceHealthStatus.UNKNOWN
                        health != null -> health.status
                        else -> SourceHealthStatus.UNKNOWN
                    }
                    source.copy(healthStatus = healthStatus)
                }
                .filterNot { !showBroken && it.healthStatus == SourceHealthStatus.BROKEN }
                .flatMap {
                    val flag = if ("${it.id}" in pinnedSourceIds) Pins.pinned else Pins.unpinned
                    val source = it.copy(pin = flag)
                    val toFlatten = mutableListOf(source)
                    if (source.id == lastUsedSource) {
                        toFlatten.add(source.copy(isUsedLast = true, pin = source.pin - Pin.Actual))
                    }
                    toFlatten
                }
        }
            .distinctUntilChanged()
    }
}
